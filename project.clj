(def lucene-version "9.11.1")

(defproject org.msync/lucene-clj "0.2.0-SNAPSHOT"
  :description "Lucene bindings for Clojure"
  :url "https://github.com/jaju/lucene-clj"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.4"]
                 [org.apache.lucene/lucene-core ~lucene-version]
                 [org.apache.lucene/lucene-queryparser ~lucene-version]
                 [org.apache.lucene/lucene-analysis-common ~lucene-version]
                 [org.apache.lucene/lucene-codecs ~lucene-version]
                 [org.apache.lucene/lucene-suggest ~lucene-version]]
  :implicits false

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "21" "-source" "21" "-deprecation" "-Xlint:-options"]
  :java-opts ["--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED"]

  :plugins [[lein-marginalia "0.9.1"]]

  :profiles {:dev  {:dependencies [[org.clojure/data.csv "1.1.0"]
                                   [criterium "0.4.6"]]
                    :source-paths ["test" "dev"]
                    :resource-paths ["test-resources"]}

             :test {:dependencies   [[org.clojure/data.csv "1.1.0"]]
                    :resource-paths ["test-resources"]}

             :1.10  {:dependencies [[org.clojure/clojure "1.10.3"]]}}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
