(ns piotr-yuxuan.walter-ci.main
  (:require [piotr-yuxuan.walter-ci.config :refer [load-config]]
            [piotr-yuxuan.walter-ci.core :as core]
            [clojure.pprint])
  (:gen-class))

(defn -main
  [& args]
  (let [config (load-config args)]
    (cond (:show-config? config) (clojure.pprint/pprint config)
          ;(:help config) (println (malli-cli/summary Config))

          ;; FIXME Validation is broken.
          #_(not (m/validate Config config))
          #_(do (println "Invalid configuration value")
                (clojure.pprint/pprint config)
                (clojure.pprint/pprint (System/getenv))
                (clojure.pprint/pprint (->> config
                                            (m/explain Config)
                                            me/humanize))
                (System/exit 1))

          true (core/start config)))
  (System/exit 0))
