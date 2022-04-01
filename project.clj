(defproject com.github.piotr-yuxuan/walter-ci (-> "./resources/walter-ci.version" slurp .trim)
  :description "Walter Kohl is the younger son of Helmut Kohl. Like his father he likes to break down walls and reunify friends under one common fundamental law."
  :url "https://github.com/piotr-yuxuan/walter-ci"
  :license {:name "European Union Public License 1.2 or later"
            :url "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :repo}
  :scm {:name "git"
        :url "https://github.com/piotr-yuxuan/walter-ci"}
  :pom-addition [:developers [:developer
                              [:name "胡雨軒 Петр"]
                              [:url "https://github.com/piotr-yuxuan"]]]
  :dependencies [[org.clojure/clojure "1.11.1-rc1"]
                 [leiningen "2.9.6" :upgrade false :exclusions [org.apache.httpcomponents/httpcore]]
                 [leiningen-core "2.9.6" :upgrade false]
                 [com.brunobonacci/safely "0.7.0-alpha3"]
                 [clj-http "3.12.3" :exclusions [riddley]]
                 [com.github.piotr-yuxuan/malli-cli "2.0.0"] ; Command-line processing
                 [babashka/process "0.1.1"]
                 [camel-snake-kebab "0.4.2"]
                 [metosin/malli "0.8.4"]
                 [caesium "0.14.0"]
                 [metosin/jsonista "0.3.5"]
                 ;[clj-commons/clj-yaml "0.7.107"]
                 ;[com.arohner/uri "0.1.2"]
                 ;[io.forward/semver "0.1.0"]
                 ;[metosin/muuntaja "0.6.8"]
                 ;[vvvvalvalval/supdate "0.2.3"]
                 ]
  :main piotr-yuxuan.walter-ci.main
  :profiles {:github {:github/topics ["github" "actions" "automation" "clojure"]
                      :github/private? false}
             :provided {:dependencies [[org.clojure/clojure "1.11.1-rc1"]]}
             :dev {:global-vars {*warn-on-reflection* true}}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"
                                  "-Dclojure.compiler.disable-locals-clearing=false"
                                  "-Dclojure.compiler.elide-meta=[:doc :file :line :added]"]}})
