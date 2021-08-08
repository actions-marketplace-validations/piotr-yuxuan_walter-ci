(ns piotr-yuxuan.walter-ci.main
  (:require [clj-yaml.core :as yaml]
            [camel-snake-kebab.core :as csk]
            [clojure.java.io :as io]
            [leiningen.change]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clj-http.client :as http]
            [safely.core :as safely]
            [clojurewerkz.balagan.core :as balagan]
            [jsonista.core :as json]
            [medley.core :as medley])
  (:gen-class))

(defn forward-action-secret
  [{:keys [github-api-url github-repository github-actor walter-github-password] :as config}
   secret-name]
  (let [secret-value (get config secret-name)]
    (safely/safely
      (println (format "Forwarding secret %s for %s."
                       secret-name
                       github-repository)
               :type (type secret-value)
               :count (count secret-value))
      (http/request
        {:request-method :put
         :url (str/join "/" [github-api-url "repos" github-repository "actions" "secrets" secret-name])
         :body (json/write-value-as-string {:encrypted_value secret-value})
         :basic-auth [github-actor walter-github-password]
         :headers {"Content-Type" "application/json"
                   "Accept" "application/vnd.github.v3+json"}})
      :on-error
      :max-retries 1)))

(defn expand-env
  [workflow]
  (balagan/update
    workflow
    ;; Workflow env
    [:env] vec
    [:env :*] (juxt identity #(format "${{ secrets.%s }}" %))
    [:env] #(into {} %)
    ;; Jobs env
    [:jobs :* :env] vec
    [:jobs :* :env :*] (juxt identity #(format "${{ secrets.%s }}" %))
    [:jobs :* :env] #(into {} %)
    ;; Steps env
    [:jobs :* :steps :* :env] vec
    [:jobs :* :steps :* :env :*] (juxt identity #(format "${{ secrets.%s }}" %))
    [:jobs :* :steps :* :env] #(into {} %)))

(defn spit-workflow-yaml
  "Did you know that the alpha2 country code of Norway is `false`, and
  `on` is `true`? I just refuse to touch a file format so corrupted by
  syntactic sugar. Read edn data in `input-file` and write yaml
  equivalent in `output-file`. Prepend a header."
  [input-file output-file]
  (as-> input-file $
    (io/resource $)
    (slurp $)
    (edn/read-string $)
    (yaml/generate-string $ :dumper-options {:flow-style :block})
    (str (slurp (io/resource "header.yaml")) $)
    (spit output-file $ :append false)))

(defn load-config
  []
  (->> (System/getenv)
       (into {})
       (medley/map-keys csk/->kebab-case-keyword)))

(defn -main
  [& _]
  (let [{:keys [github-action-path github-workspace] :as config} (load-config)]
    #_(doseq [[input-file output-file] [["Update managed repositories.edn" "Update managed repositories.yml"]
                                        ["Code review.edn" "Code review.yml"]]]
        (spit-workflow-yaml
          (str/join "/" [github-action-path "workflows" input-file])
          (str/join "/" [github-workspace ".github" "workflows" output-file])))

    (doseq [{:keys [github-repository]} (-> (io/resource "state.edn")
                                            slurp
                                            clojure.edn/read-string)
            secret-name [:walter-clojars-username
                         :walter-clojars-password
                         :walter-github-password
                         :walter-git-email]]
      (forward-action-secret (assoc config :github-repository github-repository)
                             secret-name))
    (println :all-done)))

;;; Trigger documentation build:
;;; curl --verbose 'https://cljdoc.org/api/request-build2' \
;;;   --header 'Content-Type: application/x-www-form-urlencoded' \
;;;   --header 'Origin: https://cljdoc.org' \
;;;   --data-raw 'project=com.github.piotr-yuxuan%2Fmalli-cli&version=0.0.6'
