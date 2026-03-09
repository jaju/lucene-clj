(ns msync.lucene.bench
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [criterium.bench :as cbench]
            [criterium.core :as criterium]
            [criterium.measured :as measured]
            [msync.lucene.document :as document]
            [msync.lucene.indexer :as indexer]
            [msync.lucene.tests-common :as common])
  (:import [java.time Instant]
           [org.apache.lucene.store ByteBuffersDirectory]))

(def ^:private criterium-version "0.5.153-ALPHA")

(def ^:private benchmark-doc-count 1000)

(def ^:private quick-benchmark-options
  {:verbose               false
   :num-samples           12
   :target-execution-time 100000000
   :warmup-jit-period     1000000000})

(def ^:private album-doc-opts
  {:fields
   {:Number   {:type :text
               :stored? true
               :indexed? true}
    :Year     {:type :keyword
               :stored? true
               :indexed? true}
    :Album    {:type    :text
               :stored? true
               :indexed? true
               :suggest {:contexts-from :Genre}}
    :Artist   {:type    :text
               :stored? true
               :indexed? true
               :suggest {:contexts-from :Genre}}
    :Genre    {:type          :keyword
               :stored?       true
               :indexed?      true
               :multi-valued? true}
    :Subgenre {:type          :keyword
               :stored?       true
               :indexed?      true
               :multi-valued? true}}})

(def ^:private benchmark-docs
  (vec (take benchmark-doc-count (cycle common/album-data))))

(def ^:private sample-doc
  (first benchmark-docs))

(defn- nanoseconds-per-op
  "Normalize an aggregate nanosecond measurement by the benchmark batch size."
  [value batch-size]
  (double (/ value batch-size)))

(defn- summarize-elapsed-time
  "Extract a serializable elapsed-time summary from Criterium's last benchmark."
  [bench-data]
  (let [batch-size (get-in bench-data [:data :stats :batch-size] 1)
        stats      (get-in bench-data [:data :stats :stats :elapsed-time])]
    {:batch-size          batch-size
     :sample-count        (:n stats)
     :mean-ns-per-op      (nanoseconds-per-op (:mean stats) batch-size)
     :min-ns-per-op       (nanoseconds-per-op (:min-val stats) batch-size)
     :max-ns-per-op       (nanoseconds-per-op (:max-val stats) batch-size)
     :lower-bound-ns-per-op (nanoseconds-per-op (:mean-minus-3sigma stats) batch-size)
     :upper-bound-ns-per-op (nanoseconds-per-op (:mean-plus-3sigma stats) batch-size)}))

(defn- benchmark-report
  "Run a benchmark thunk through Criterium and capture its report plus structured stats."
  [benchmark-fn]
  (let [report (with-out-str
                 (criterium/quick-benchmark*
                  (measured/callable benchmark-fn)
                  quick-benchmark-options))
        bench-data (cbench/last-bench)]
    {:report       report
     :elapsed-time (summarize-elapsed-time bench-data)}))

(defn- create-writer
  "Create a fresh in-memory writer for indexing benchmarks."
  []
  (let [directory (ByteBuffersDirectory.)
        writer    (indexer/index-writer directory
                                        (indexer/index-writer-config common/album-data-analyzer))]
    {:directory directory
     :writer    writer}))

(defn- benchmark-scenarios
  "Benchmark scenarios focused on document encoding and batch indexing."
  []
  (let [doc-fn (document/-map->document-fn album-doc-opts)]
    [{:name :compile-document-encoder
      :run  #(document/-map->document-fn album-doc-opts)}
     {:name :map->document-one-document
      :run  #(document/map->document sample-doc album-doc-opts)}
     {:name :compiled-encode-one-document
      :run  #(doc-fn sample-doc)}
     {:name :compiled-encode-benchmark-batch
      :run  #(run! doc-fn benchmark-docs)}
     {:name :index-benchmark-batch
      :run  #(let [{:keys [directory writer]} (create-writer)]
               (with-open [directory directory
                           writer writer]
                 (indexer/index! writer benchmark-docs album-doc-opts)))}]))

(defn- benchmark-result
  "Run and capture one named benchmark scenario."
  [{:keys [name run]}]
  (println "Running benchmark" name)
  (assoc (benchmark-report run) :name name))

(defn- benchmark-summary
  "Run the benchmark suite and return a serializable EDN summary."
  [label git-sha]
  {:label               label
   :git-sha             git-sha
   :run-at              (str (Instant/now))
   :java-version        (System/getProperty "java.version")
   :java-vm-name        (System/getProperty "java.vm.name")
   :os-name             (System/getProperty "os.name")
   :os-arch             (System/getProperty "os.arch")
   :criterium-version   criterium-version
   :benchmark-doc-count benchmark-doc-count
   :quick-benchmark-options quick-benchmark-options
   :benchmarks          (mapv benchmark-result (benchmark-scenarios))})

(defn- write-summary!
  "Write a benchmark summary to disk as pretty-printed EDN."
  [output-path summary]
  (io/make-parents output-path)
  (spit output-path (with-out-str (pprint/pprint summary)))
  (println "Wrote benchmark summary to" output-path))

(defn -main
  "Run the benchmark suite.
  Usage: lein bench [output-path] [label] [git-sha]"
  [& [output-path label git-sha]]
  (let [summary (benchmark-summary (or label "manual") git-sha)]
    (if output-path
      (write-summary! output-path summary)
      (pprint/pprint summary))))
