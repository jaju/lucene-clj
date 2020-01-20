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
   (let [{:keys [directory analyzer]} index-config]
     (search/search directory query-form (assoc opts :analyzer analyzer)))))

(defn suggest
  ([index field-name ^String prefix-query]
   (suggestions/suggest index field-name prefix-query))
  ([index field-name ^String prefix-query opts]
   (suggestions/suggest index field-name prefix-query opts)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
