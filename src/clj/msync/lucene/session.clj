(ns msync.lucene.session
  (:require [msync.lucene.indexer :as indexer]
            [msync.lucene.schema :as schema])
  (:import [java.io Closeable]
           [msync.lucene.indexer IndexConfig]
           [org.apache.lucene.index IndexReader]
           [org.apache.lucene.search IndexSearcher]
           [org.apache.lucene.search.suggest.document SuggestIndexSearcher]))

(defrecord SearchSession [reader searcher suggester stored-fields field-specs analyzer]
  Closeable
  (close [_]
    (.close ^IndexReader reader)))

(defn -open-search-session
  "Open a stable Lucene search session that can be reused across multiple search or suggest calls."
  [^IndexConfig index-config]
  (let [{:keys [directory analyzer]} index-config
        reader        (indexer/index-reader directory)
        searcher      (IndexSearcher. reader)
        suggester     (SuggestIndexSearcher. reader)
        stored-fields (.storedFields searcher)
        field-specs   (schema/-read-field-specs reader)]
    (->SearchSession reader
                     searcher
                     suggester
                     stored-fields
                     field-specs
                     analyzer)))
