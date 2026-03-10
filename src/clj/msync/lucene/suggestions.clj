(ns msync.lucene.suggestions
  (:require [msync.lucene.document :as d]
            [msync.lucene
             [session :as session]
             [validation :as validation]
             [values :as values]])
  (:import [org.apache.lucene.search ScoreDoc]
           [org.apache.lucene.search.suggest.document
            TopSuggestDocs SuggestIndexSearcher ContextQuery
            PrefixCompletionQuery FuzzyCompletionQuery]
           [org.apache.lucene.index Term]
           [msync.lucene.session SearchSession]))

(defn- suggest* [^SearchSession search-session
                 ^String field-name
                 ^String prefix-query
                 {:keys [contexts analyzer max-results hit->doc fuzzy? skip-duplicates?]
                  :or   {fuzzy? false skip-duplicates? false hit->doc identity}}]
  {:pre [(some? analyzer) (some? max-results)]}
  (let [prefix-query         (values/-normalize-text-value field-name prefix-query)
        suggest-field        (str d/suggest-field-prefix field-name)
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
        ^SuggestIndexSearcher suggester (:suggester search-session)
        stored-fields        (:stored-fields search-session)
        num-hits             max-results
        ^TopSuggestDocs hits (.suggest suggester cq num-hits skip-duplicates?)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id (.doc hit)
              doc    (.document stored-fields doc-id)
              score  (.score hit)]
          {:hit (hit->doc doc) :score score :doc-id doc-id})))))

(defn suggest
  "Return suggestions for prefix-queries."
  [^SearchSession search-session field-name prefix-query {:keys [max-results hit->doc fuzzy? skip-duplicates? contexts analyzer]}]
  (let [opts {:fuzzy?           (or fuzzy? false)
              :skip-duplicates? (or skip-duplicates? false)
              :max-results      (or max-results 10)
              :hit->doc         (or hit->doc identity)
              :contexts         (values/-normalize-optional-text-values :suggest-contexts contexts)
              :analyzer         analyzer}]
    (validation/-validate-suggest-opts field-name prefix-query opts)
    (suggest* search-session (name field-name) prefix-query opts)))
