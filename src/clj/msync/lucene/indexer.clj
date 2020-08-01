(ns msync.lucene.indexer
  (:require [clojure.java.io :as io]
            [msync.lucene
             [utils :as utils]]
            [msync.lucene.document :as d])
  (:import [org.apache.lucene.index IndexWriter DirectoryReader IndexReader IndexWriterConfig]
           [org.apache.lucene.store FSDirectory Directory MMapDirectory]
           [java.io File]
           [org.apache.lucene.analysis Analyzer]
           [org.apache.lucene.codecs.lucene86 Lucene86Codec]
           [org.apache.lucene.search.suggest.document Completion84PostingsFormat]))

(defrecord IndexConfig [directory analyzer])

(defn create-filter-codec-for-suggestions
  "Creates a codec for storing fields that support returning suggestions for given prefix strings.
  Chooses the codec based on the field name prefix - which is fixed/pre-decided and not designed to be
  overridden."
  []
  (let [comp-postings-format (Completion84PostingsFormat.)]
    (proxy [Lucene86Codec] []
      (getPostingsFormatForField [field-name]
        (if (.startsWith field-name d/suggest-field-prefix)
          comp-postings-format
          (proxy-super getPostingsFormatForField field-name))))))

(defn index-writer-config
  "IndexWriterConfig instance."
  [^Analyzer analyzer]
  (doto (IndexWriterConfig. analyzer)
    (.setCodec (create-filter-codec-for-suggestions))))

(defn ^IndexWriter index-writer
  "IndexWriter instance."
  [^Directory directory
   ^IndexWriterConfig index-writer-config]
  (IndexWriter. directory index-writer-config))

(defn ^IndexReader index-reader
  "An IndexReader instance."
  [^Directory directory]
  (DirectoryReader/open directory))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ->index-config [directory analyzer]
  (->IndexConfig directory analyzer))

(defn- ^Directory ->mmap-directory
  "Lucene Directory for transient indexes"
  []
  (let [temp-path (utils/temp-path)
        d         (MMapDirectory. temp-path)]
    (utils/delete-on-exit! d)
    d))

(declare clear!)
(defn- ^Directory ->disk-directory
  "Persistent index on disk"
  [^String dir-path re-create?]
  (let [path (.toPath ^File (io/as-file dir-path))
        dir  (FSDirectory/open path)]
    (when re-create?
      (clear! dir))
    dir))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti clear! class)

(defmethod clear! IndexConfig
  [^IndexConfig {:keys [directory analyzer]}]
  (with-open [iw (index-writer directory (index-writer-config analyzer))]
    (clear! iw)))

(defmethod clear! IndexWriter
  [^IndexWriter iw]
  (.deleteAll iw))

(defn ^IndexConfig create!
  "Create an appropriate index - where path is either the keyword :memory, or
  a string representing the path on disk where the index is created."
  [{:keys [type path analyzer re-create?]}]
  (let [directory (condp = type
                    :memory (->mmap-directory)
                    :disk (->disk-directory path (or re-create? false)))]
    (->index-config directory analyzer)))

(defmulti index! (fn [o & _] (class o)))

(defmethod index! IndexConfig
  [^IndexConfig store doc-maps doc-opts]
  (let [analyzer  (:analyzer store)
        directory (:directory store)
        iwc       (index-writer-config analyzer)]
    (with-open [iw (index-writer directory iwc)]
      (index! iw doc-maps (dissoc doc-opts :analyzer)))))

(defmethod index! IndexWriter
  [^IndexWriter iw
   doc-maps
   {:keys [indexed-fields stored-fields keyword-fields suggest-fields context-fn] :as doc-opts}]
  (let [doc-maps (if (map? doc-maps) [doc-maps] doc-maps)
        doc-fn   (d/fn:map->document doc-opts)]
    (doseq [document (map doc-fn doc-maps)]
      (.addDocument iw document))))

