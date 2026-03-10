(ns msync.lucene
  (:require [msync.lucene
             [search :as search]
             [session :as session]
             [suggestions :as suggestions]
             [indexer :as indexer]])
  (:import [java.util.logging Logger]
           [msync.lucene.indexer IndexConfig]
           [msync.lucene.session SearchSession]))

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

(defn ^SearchSession open-session
  "Open a reusable Lucene search session.
  Use with-open to keep a stable reader/searcher snapshot across multiple search or suggest calls."
  [^IndexConfig index-config]
  (session/-open-search-session index-config))

(defn search
  "Search an index using lucene-clj query shapes or a raw Lucene Query."
  ([search-target query-form]
   (search search-target query-form {}))
  ([search-target query-form opts]
   (cond
     (instance? SearchSession search-target)
     (search/search search-target query-form
                    (assoc (or opts {}) :analyzer (:analyzer search-target)))

     (instance? IndexConfig search-target)
     (with-open [search-session (open-session search-target)]
       (search/search search-session query-form
                      (assoc (or opts {}) :analyzer (:analyzer search-target))))

     :else
     (throw (ex-info "search requires an IndexConfig or SearchSession"
                     {:search-target       search-target
                      :search-target-class (some-> search-target class str)})))))

(defn suggest
  "Return completion suggestions for a suggest-enabled field."
  [search-target field-name ^String prefix-query & [opts]]
  (cond
    (instance? SearchSession search-target)
    (suggestions/suggest search-target (name field-name) prefix-query
                         (assoc (or opts {}) :analyzer (:analyzer search-target)))

    (instance? IndexConfig search-target)
    (with-open [search-session (open-session search-target)]
      (suggestions/suggest search-session (name field-name) prefix-query
                           (assoc (or opts {}) :analyzer (:analyzer search-target))))

    :else
    (throw (ex-info "suggest requires an IndexConfig or SearchSession"
                    {:search-target       search-target
                     :search-target-class (some-> search-target class str)}))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
