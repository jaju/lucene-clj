(def lucene-version "7.4.0")

(defproject org.msync/lucene-clj "0.1.0-SNAPSHOT"
  :description "Lucene bindings for Clojure"
  :url "https://github.com/jaju/lucene-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0-alpha8"]
                 [org.apache.lucene/lucene-core ~lucene-version]
                 [org.apache.lucene/lucene-queryparser ~lucene-version]
                 [org.apache.lucene/lucene-analyzers-common ~lucene-version]
                 [org.apache.lucene/lucene-suggest ~lucene-version]])
