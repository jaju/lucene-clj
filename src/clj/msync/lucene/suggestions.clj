(ns msync.lucene.suggestions
  (:require [msync.lucene.document :as d]
            [msync.lucene.indexer :as indexer]
            [msync.lucene.utils :refer [!nil?]])
  (:import [org.apache.lucene.search ScoreDoc]
           [org.apache.lucene.search.suggest.document
            TopSuggestDocs SuggestIndexSearcher ContextQuery
            PrefixCompletionQuery FuzzyCompletionQuery]
           [org.apache.lucene.index Term IndexReader]
           [msync.lucene.indexer IndexConfig]))

(defn- suggest* [^IndexReader index
                 ^String field-name
                 ^String prefix-query
                 {:keys [contexts analyzer max-results hit->doc fuzzy? skip-duplicates?]
                  :or   {fuzzy? false skip-duplicates? false hit->doc identity}}]
  {:pre [(!nil? analyzer) (!nil? max-results)]}
  (let [suggest-field        (str d/suggest-field-prefix field-name)
        term                 (Term. suggest-field prefix-query)
        pcq                  (if fuzzy?
                               (FuzzyCompletionQuery. analyzer term)
                               (PrefixCompletionQuery. analyzer term))
        cq                   (if-not (empty? contexts)
                               (let [q (ContextQuery. pcq)]
                                 (doseq [context contexts]
                                   (.addContext q context))
                                 q)
                               pcq)
        suggester            (SuggestIndexSearcher. index)
        num-hits             (min 10 max-results)
        ^TopSuggestDocs hits (.suggest suggester cq num-hits skip-duplicates?)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id (.doc hit)
              doc    (.doc suggester doc-id)
              score  (.score hit)]
          {:hit (hit->doc doc) :score score :doc-id doc-id})))))

(defn suggest
  "Return suggestions for prefix-queries."
  ([^IndexConfig index-config field-name prefix-query]
   (suggest index-config field-name prefix-query {}))

  ([^IndexConfig index-config field-name prefix-query {:keys [max-results hit->doc fuzzy? skip-duplicates? contexts]}]
   (let [{:keys [directory analyzer]} index-config
         opts {:fuzzy?           (or fuzzy? false)
               :skip-duplicates? (or skip-duplicates? false)
               :max-results      (or max-results 10)
               :hit->doc         (or hit->doc identity)
               :contexts         contexts
               :analyzer         analyzer}]
     (with-open [index (indexer/index-reader directory)]
       (suggest* index (name field-name) prefix-query opts)))))
