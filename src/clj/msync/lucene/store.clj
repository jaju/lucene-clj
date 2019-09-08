(ns msync.lucene.store
  (:require [clojure.java.io :as io]
            [msync.lucene.utils :as utils]
            [msync.lucene.suggestions :as su])
  (:import [org.apache.lucene.index IndexWriter DirectoryReader IndexReader IndexWriterConfig]
           [org.apache.lucene.store FSDirectory Directory MMapDirectory]
           [java.io File]
           [org.apache.lucene.analysis Analyzer]))

(defprotocol StoreConfig
  (directory [_])
  (analyzer [_])
  (set! [_ k v]))

(defrecord ^:private Store [directory analyzer opts]
  StoreConfig
  (directory [_] directory)
  (analyzer [_] analyzer)
  (set! [_ k v] (swap! opts assoc k v)))

(defn index-writer-config
  "IndexWriterConfig instance."

  [^Analyzer analyzer]
  (doto (IndexWriterConfig. analyzer)
    (.setCodec (su/create-filter-codec-for-suggestions))))

(defn ^IndexWriter index-writer
  "IndexWriter instance."
  [^Directory directory
   ^IndexWriterConfig index-writer-config]
  (IndexWriter. directory index-writer-config))

(defn ^IndexReader index-reader
  "An IndexReader instance."
  [^Directory directory]
  (DirectoryReader/open directory))

(defmulti delete-all! class)

(defmethod delete-all! Store
  [^Store store]
  (with-open [iw (index-writer (:directory store) (index-writer-config (:analyzer store)))]
    (delete-all! iw)))

(defmethod delete-all! IndexWriter
  [^IndexWriter iw]
  (.deleteAll iw))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- new-store [directory analyzer
                  & {:keys []
                     :as   opts}]
  (->Store directory analyzer (atom (or opts {}))))

(defn- ^Directory memory-index
  "Lucene Directory for transient indexes"
  []
  (let [temp-path (utils/temp-path)
        d         (MMapDirectory. temp-path)]
    (utils/delete-on-exit! d)
    d))

(defn- ^Directory disk-index
  "Persistent index on disk"
  [^String dir-path {:keys [re-create?] :or {re-create? false}}]
  (let [path (.toPath ^File (io/as-file dir-path))
        dir  (FSDirectory/open path)]
    (when re-create?
      (delete-all! dir))
    dir))

(defn ^Store store
  "Create an appropriate index - where path is either the keyword :memory, or
  a string representing the path on disk where the index is created."
  [path & {:keys [analyzer]
           :as   opts}]
  (let [index (if (= path :memory)
                (memory-index)
                (disk-index path opts))]
    (new-store index analyzer)))

