(ns piotr-yuxuan.walter-ci.install
  (:require [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [malli.core :as m])
  (:import (java.io File)))

(defn git-commit-and-push
  [{{:keys [github-action-path github-workspace walter-git-email github-actor]} :env} commit-message ^File file-path]
  (shell/with-sh-dir (.getAbsolutePath (io/file github-workspace))
    (shell/sh "git" "add" (.getAbsolutePath file-path))
    (println :empty-diff? (seq (:out (shell/sh "git" "diff" "--porcelain"))))
    (when (seq (:out (shell/sh "git" "diff" "--porcelain")))
      (println
        (pr-str
          (shell/sh "git" "commit" "-m" commit-message
                    :env {"GIT_COMMITTER_NAME" github-actor
                          "GIT_COMMITTER_EMAIL" walter-git-email
                          "GIT_AUTHOR_NAME" github-actor
                          "GIT_AUTHOR_EMAIL" walter-git-email})))
      (println
        (pr-str
          (shell/sh "git" "push" "HEAD"
                    :env {"GIT_ASKPASS" (.getAbsolutePath (io/file github-action-path "resources" "git-askpass.sh"))}))))))

(def Config
  [:map
   [:env [:map
          [:github-action-path string?]
          [:github-workspace string?]
          [:walter-git-email string?]
          [:github-actor string?]]]])

(defn step
  [{{:keys [github-workspace github-action-path]} :env :as config}]
  (when-not (m/validate Config config)
    (println (pr-str (m/explain Config config)))
    (throw (ex-info "Config invalid" {})))
  (let [source (.getAbsolutePath (io/file github-action-path "resources" "walter-ci.standard.yml"))
        target (.getAbsolutePath (io/file github-workspace ".github" "workflows" "walter-ci.yml"))]
    (println :copy)
    (io/copy (io/file source) (io/file target))
    (git-commit-and-push config "Update walter-ci.yml" target)))

