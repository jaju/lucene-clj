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

(defn ^IndexConfig create-index! [& {:keys [type path analyzer re-create?]}]
  (indexer/create! {:type type :path path :analyzer analyzer :re-create? re-create?}))

(defn index! [store doc-maps doc-opts]
  (indexer/index! store doc-maps doc-opts))

(defn search
  ([^IndexConfig index-config query-form]
   (search index-config query-form {}))
  ([^IndexConfig index-config query-form opts]
   (let [{:keys [directory analyzer]} index-config
         opts (assoc opts :analyzer analyzer)]
     (with-open [reader (indexer/index-reader directory)]
       (search/search reader query-form opts)))))

(defn suggest
  [^IndexConfig index-config field-name ^String prefix-query & [opts]]
  (let [{:keys [directory analyzer]} index-config
        opts (assoc (or opts {}) :analyzer analyzer)]
    (with-open [index-reader (indexer/index-reader directory)]
      (suggestions/suggest index-reader (name field-name) prefix-query opts))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
