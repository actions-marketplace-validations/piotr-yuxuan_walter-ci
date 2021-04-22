(ns com.piotr-yuxuan.walter-ci.main
  "All the environment variable in the [Action
  reference](https://docs.github.com/en/actions/reference/environment-variables)
  may be used. In addition, the following environment variables may be
  used:

  - WALTER_CLOJARS_USERNAME
  - WALTER_CLOJARS_PASSWORD
  - WALTER_GITHUB_USERNAME
  - WALTER_GITHUB_PASSWORD"
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :as pprint]
            [clojure.string :as str])
  (:gen-class))

(defn pprint-or-sh-exit
  [{:keys [exit out err] :as ret}]
  (when-not (zero? exit)
    (throw (ex-info (str (first (str/split-lines err)))
                    ret)))
  (pprint/pprint out))

(println "I'm in the code, not the jar.")

(defn -main
  [& args]
  (println "Environment")
  (pprint/pprint (System/getenv))

  (println "Implicit environment")
  (pprint-or-sh-exit
    (shell/sh
      "env"))

  (println "Explicit environment")
  (pprint-or-sh-exit
    (shell/sh
      "env"
      :env (into {} (System/getenv))))

  (println "Controlled environment")
  (pprint-or-sh-exit
    (shell/sh
      "env"
      :env {"HOME" "/home/walter-ci"}))

  (println "Try to create a directory")
  (pprint-or-sh-exit
    (shell/sh
      "mkdir" "test-directory"
      :dir "/home/walter-ci"
      :env {"HOME" "/home/walter-ci"}))

  (println "Identify user")
  (pprint-or-sh-exit
    (shell/sh
      "id"))

  (println "Access rights")
  (pprint-or-sh-exit
    (shell/sh
      "ls" "-hal"
      :env {"HOME" "/home/walter-ci"}))

  (println "Copy project")
  (pprint-or-sh-exit
    (shell/sh
      "cp" "-R" "/github/workspace" "/home/walter-ci/workspace"
      :env {"HOME" "/home/walter-ci"}))

  (println "Try to change permissions")
  (pprint-or-sh-exit
    (shell/sh
      "chmod" "-R" "755" "/home/walter-ci/workspace"
      :dir "/home/walter-ci/workspace"
      :env {"HOME" "/home/walter-ci"}))

  (println "Retrieve dependencies")
  (pprint-or-sh-exit
    (shell/sh
      "lein" "deps"
      :dir "/home/walter-ci/workspace"
      :env {"HOME" "/home/walter-ci"}))

  (println ::test)
  (println
    (pr-str
      (shell/sh
        "lein" "test"
        :dir "/home/walter-ci/workspace"
        :env {"HOME" "/home/walter-ci"})))

  (println ::uberjar)
  (println
    (pr-str
      (shell/sh
        "lein" "uberjar"
        :dir "/home/walter-ci/workspace"
        :env {"HOME" "/home/walter-ci"})))

  (println ::deploy :clojars)
  (println
    (pr-str
      (shell/sh
        "lein" "deploy" "clojars"
        :dir "/home/walter-ci/workspace"
        :env (merge (into {} (System/getenv))
                    {"HOME" "/home/walter-ci"
                     "WALTER_CLOJARS_USERNAME" (System/getenv "WALTER_CLOJARS_USERNAME")
                     "WALTER_CLOJARS_PASSWORD" (System/getenv "WALTER_CLOJARS_PASSWORD")})))))
