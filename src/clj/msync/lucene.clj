(ns msync.lucene
  (:require [msync.lucene
             [search :as search]
             [suggestions :as suggestions]
             [indexer :as indexer]])
  (:import [java.util.logging Logger]
           [msync.lucene.indexer IndexConfig]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(do
  (Logger/getLogger "msync.lucene"))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^IndexConfig create-index!
  "Create an in-memory or on-disk Lucene index."
  [& {:keys [type path analyzer re-create?]}]
  (indexer/create! {:type type :path path :analyzer analyzer :re-create? re-create?}))

(defn index!
  "Index one document map or a collection of document maps using canonical :fields specs."
  [store doc-maps indexing-options]
  (indexer/index! store doc-maps indexing-options))

(defn search
  "Search an index using lucene-clj query shapes or a raw Lucene Query."
  ([^IndexConfig index-config query-form]
   (search index-config query-form {}))
  ([^IndexConfig index-config query-form opts]
   (let [{:keys [directory analyzer]} index-config
         opts (assoc opts :analyzer analyzer)]
     (with-open [reader (indexer/index-reader directory)]
       (search/search reader query-form opts)))))

(defn suggest
  "Return completion suggestions for a suggest-enabled field."
  [^IndexConfig index-config field-name ^String prefix-query & [opts]]
  (let [{:keys [directory analyzer]} index-config
        opts (assoc (or opts {}) :analyzer analyzer)]
    (with-open [index-reader (indexer/index-reader directory)]
      (suggestions/suggest index-reader (name field-name) prefix-query opts))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
