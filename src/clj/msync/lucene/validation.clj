(ns msync.lucene.validation
  (:require [clojure.string :as string])
  (:import [org.apache.lucene.search Query]))

(defn- fail!
  [message data]
  (throw (ex-info message data)))

(defn -validate-create-opts
  "Validate create-index! options before Lucene resources are opened."
  [{:keys [type path analyzer] :as opts}]
  (when-not (#{:memory :disk} type)
    (fail! "create-index! requires :type to be one of :memory or :disk"
           {:opts opts :type type}))
  (when (and (= :disk type)
             (not (and (string? path) (not (string/blank? path)))))
    (fail! "create-index! with :type :disk requires a non-blank :path"
           {:opts opts :path path}))
  (when-not analyzer
    (fail! "create-index! requires a non-nil :analyzer"
           {:opts opts}))
  opts)

(defn -normalize-document-maps
  "Normalize document input into a collection of maps and reject unsupported shapes early."
  [doc-maps]
  (let [docs (cond
               (map? doc-maps) [doc-maps]
               (and (coll? doc-maps) (not (string? doc-maps))) doc-maps
               :else
               (fail! "index! expects a map or a collection of maps"
                      {:doc-maps       doc-maps
                       :doc-maps-class (some-> doc-maps class str)}))]
    (doseq [doc docs]
      (when-not (map? doc)
        (fail! "index! expects every document to be a map"
               {:document       doc
                :document-class (some-> doc class str)})))
    docs))

(defn- query-form-requires-field-name?
  [query-form]
  (and (not (map? query-form))
       (not (instance? Query query-form))))

(defn -validate-search-opts
  "Validate search options against the public query-form contract."
  [query-form {:keys [field-name fuzzy? results-per-page page] :as opts}]
  (when (and (query-form-requires-field-name? query-form)
             (nil? field-name))
    (fail! "search requires :field-name when the top-level query form is not a field-to-value map"
           {:query-form query-form :opts opts}))
  (when (and fuzzy? (not (map? query-form)))
    (fail! "search with :fuzzy? true requires a field-to-term map query"
           {:query-form query-form :opts opts}))
  (when-not (pos-int? results-per-page)
    (fail! "search requires :results-per-page to be a positive integer"
           {:query-form query-form :opts opts}))
  (when-not (nat-int? page)
    (fail! "search requires :page to be a natural integer"
           {:query-form query-form :opts opts}))
  opts)

(defn -validate-suggest-opts
  "Validate suggest options before suggestion queries are executed."
  [field-name prefix-query {:keys [analyzer max-results] :as opts}]
  (when-not field-name
    (fail! "suggest requires a non-nil field name"
           {:field-name field-name :prefix-query prefix-query :opts opts}))
  (when-not analyzer
    (fail! "suggest requires a non-nil analyzer"
           {:field-name field-name :prefix-query prefix-query :opts opts}))
  (when-not (pos-int? max-results)
    (fail! "suggest requires :max-results to be a positive integer"
           {:field-name field-name :prefix-query prefix-query :opts opts}))
  opts)
