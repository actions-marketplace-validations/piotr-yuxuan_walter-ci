(ns piotr-yuxuan.walter-ci.core
  (:require [piotr-yuxuan.malli-cli.utils :refer [deep-merge]]
            [piotr-yuxuan.walter-ci.files :refer [->file ->tmp-dir ->tmp-file with-delete! delete! copy!]]
            [piotr-yuxuan.walter-ci.git :as git]
            [piotr-yuxuan.walter-ci.github :as github]
            [piotr-yuxuan.walter-ci.secrets :as secrets]
            [babashka.process :as process]
            [camel-snake-kebab.core :as csk]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [safely.core :refer [safely-fn]]
            [yaml.core :as yaml])
  (:import (java.io File)))

(defn update-workflow
  [config ^String workflow-file-name ^String yml]
  (with-delete! [working-directory (->tmp-dir "update-workflow")
                 yml-file (doto (->tmp-file) (spit yml))]
    (git/clone working-directory config)
    (io/copy ^File yml-file (doto (->file working-directory ".github" "workflows" workflow-file-name)
                              (io/make-parents)))
    (git/stage-all working-directory config)
    (when (git/need-commit? working-directory config)
      (git/commit working-directory config (format "Update %s" workflow-file-name))
      (git/push working-directory config))))


(defn walter-env
  []
  {:GIT_COMMITTER_NAME "${{ secrets.GITHUB_ACTOR }}"
   :GIT_COMMITTER_EMAIL "${{ secrets.WALTER_GIT_EMAIL }}"
   :GIT_AUTHOR_NAME "${{ secrets.WALTER_AUTHOR_NAME }}"
   :GIT_AUTHOR_EMAIL "${{ secrets.WALTER_GIT_EMAIL }}"
   :GIT_PASSWORD "${{ secrets.GITHUB_TOKEN }}"
   :GIT_ASKPASS "$HOME/.walter-ci/bin/askpass.sh"})

(defn cmd-retry
  [^String cmd]
  (apply safely-fn
         #(process/check
            (process/process cmd
                             {;:dir (.getPath working-directory)
                              :out :inherit
                              :err :inherit}))
         (mapcat vec {:max-retries 5})))

(defn walter-readers
  [steps managed-repositories]
  (letfn [(read-step [value]
            (cond (keyword? value)
                  (read-step [value {}])

                  (vector? value)
                  (let [[step-name step-opts] value]
                    (deep-merge (get steps step-name)
                                step-opts))))
          (wrap-in-job [steps]
            {:runs-on "ubuntu-latest"
             :steps steps})]
    {'step read-step
     'job/wrap wrap-in-job
     'cmd/retry cmd-retry
     'walter/env (fn [{:keys [git]}]
                   (merge {}
                          (when git
                            {:GIT_COMMITTER_NAME "${{ secrets.GITHUB_ACTOR }}"
                             :GIT_COMMITTER_EMAIL "${{ secrets.WALTER_GIT_EMAIL }}"
                             :GIT_AUTHOR_NAME "${{ secrets.WALTER_AUTHOR_NAME }}"
                             :GIT_AUTHOR_EMAIL "${{ secrets.WALTER_GIT_EMAIL }}"
                             :GIT_PASSWORD "${{ secrets.GITHUB_TOKEN }}"
                             :GIT_ASKPASS "${HOME}/.walter-ci/bin/askpass.sh"})))
     'cmd/join #(str/join \newline %)
     'str/join #(str/join \space %)
     'walter/deploy-jobs (fn [_]
                           (reduce (fn [jobs github-repo]
                                     (let [job-name (str/replace github-repo "/" "-")]
                                       (assoc jobs
                                         job-name {:runs-on "ubuntu-latest"
                                                   :environment {:name :production
                                                                 :url (format "https://www.github.com/%s" github-repo)}
                                                   :steps [(read-step :walter/use)
                                                           (read-step [:deploy {:run (format "walter self-deploy --github-repository %s" github-repo)}])]})))
                                   (sorted-map)
                                   (sort managed-repositories)))}))

(def source+targets
  [["edn-sources/action.edn" "action.yml"]
   ["edn-sources/workflows/deploy.edn" ".github/workflows/deploy.yml"]
   ["edn-sources/workflows/generate.edn" ".github/workflows/generate.yml"]
   ["edn-sources/workflows/walter-cd.edn" ".github/workflows/walter-cd.yml"]
   ["edn-sources/workflows/walter-ci.edn" ".github/workflows/walter-ci.yml"]
   ["edn-sources/workflows/walter-perf.edn" ".github/workflows/walter-perf.yml"]])

(def yml-header
  "# This file is maintained by Walter CI, and may be rewritten.\n# https://github.com/piotr-yuxuan/walter-ci\n#\n# You are free to remove this project from Walter CI realm by opening\n# a PR. You may also create another workflow besides this one.\n\n")

(defn steps+edn->yml
  [steps managed-repositories source-edn]
  (->> (slurp source-edn)
       (edn/read-string {:readers (walter-readers steps managed-repositories)})
       (#(yaml/generate-string % :dumper-options {:flow-style :block
                                                  :split-lines false
                                                  :width 1e3}))
       (str yml-header)))

(defn steps+edn->write-to-yml-file!
  [steps managed-repositories source-edn target-yml]
  (->> (steps+edn->yml steps managed-repositories source-edn)
       (spit target-yml)))

(defn self-deploy
  [{:keys [github-repository] :as config}]
  (let [config+github-repository (assoc config :github-repository github-repository)
        steps (edn/read-string {:readers {'cmd/join #(str/join \newline %)
                                          'str/join #(str/join \space %)}}
                               (slurp (io/resource "steps.edn")))]
    (doseq [secret-name [:walter-author-name
                         :walter-github-password
                         :walter-git-email]]
      (secrets/upsert-value config+github-repository
                            (csk/->SCREAMING_SNAKE_CASE_STRING secret-name)
                            (get config secret-name)))
    (doseq [[source-edn workflow-file-name] [["edn-sources/workflows/walter-cd.edn" "walter-cd.yml"]
                                             ["edn-sources/workflows/walter-ci.edn" "walter-ci.yml"]
                                             ["edn-sources/workflows/walter-perf.edn" "walter-perf.yml"]]]
      (->> source-edn
           (steps+edn->yml steps nil)
           (update-workflow config+github-repository workflow-file-name)))))

(defn update-git-ignore
  [{:keys [github-action-path github-workspace]}]
  (let [required-entries (set (line-seq (io/reader (->file github-action-path "resources" ".template-gitignore"))))
        current-entries (set (line-seq (io/reader (->file github-workspace ".gitignore"))))
        missing-entries (sort (set/difference required-entries current-entries))
        gitignore (->file github-workspace ".gitignore")]
    (spit gitignore
          (str (str/trim (slurp gitignore))
               (System/lineSeparator) (System/lineSeparator)
               (str/join (System/lineSeparator) missing-entries))
          :append false)))

;; When install Walter, we should install clojure CLI, lein CLI, practicalli configs, and lein default profiles.
;; So Walter is just a bunch of helpers around basic Bash script:
;; - Lein and profiles
;; - Clojure CLI and clojure-deps-edn aliases and configuration
;; - Walter executable commands

(comment
  (let [steps (edn/read-string {:readers {'cmd/join #(str/join \newline %)
                                          'str/join #(str/join \space %)}}
                               (slurp (io/resource "steps.edn")))
        managed-repositories (edn/read-string (slurp "managed-repositories.edn"))]
    (doseq [[source-edn target-yml] source+targets]
      (steps+edn->write-to-yml-file! steps managed-repositories source-edn target-yml))))

(defmulti start :command)

(defmethod start :conform-repository
  [config]
  (github/conform-repository config))

(defmethod start :retry
  [{:keys [sub-command]}]
  (cmd-retry sub-command))

(defmethod start :self-deploy
  [config]
  (self-deploy config))

(defmethod start :update-git-ignore
  [config]
  (update-git-ignore config))
