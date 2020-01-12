(ns msync.lucene.index
  (:require [clojure.java.io :as io]
            [msync.lucene
             [utils :as utils]
             [suggestions :as su]])
  (:import [org.apache.lucene.index IndexWriter DirectoryReader IndexReader IndexWriterConfig]
           [org.apache.lucene.store FSDirectory Directory MMapDirectory]
           [java.io File]
           [org.apache.lucene.analysis Analyzer]))

(defrecord ^:private IndexConfig [directory analyzer])

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

(defmethod delete-all! IndexConfig
  [^IndexConfig {:keys [directory analyzer]}]
  (with-open [iw (index-writer directory (index-writer-config analyzer))]
    (delete-all! iw)))

(defmethod delete-all! IndexWriter
  [^IndexWriter iw]
  (.deleteAll iw))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- new-index-config [directory analyzer]
  (->IndexConfig directory analyzer))

(defn- ^Directory create-mmap-directory
  "Lucene Directory for transient indexes"
  []
  (let [temp-path (utils/temp-path)
        d         (MMapDirectory. temp-path)]
    (utils/delete-on-exit! d)
    d))

(defn- ^Directory create-disk-directory
  "Persistent index on disk"
  [^String dir-path re-create?]
  (let [path (.toPath ^File (io/as-file dir-path))
        dir  (FSDirectory/open path)]
    (when re-create?
      (delete-all! dir))
    dir))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^IndexConfig create!
  "Create an appropriate index - where path is either the keyword :memory, or
  a string representing the path on disk where the index is created."
  [& {:keys [type path analyzer re-create?]}]
  (let [index (condp = type
                :memory (create-mmap-directory)
                :disk (create-disk-directory path (or re-create? false)))]
    (new-index-config index analyzer)))
