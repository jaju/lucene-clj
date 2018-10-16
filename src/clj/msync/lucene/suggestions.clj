(ns msync.lucene.suggestions
  (:require [msync.lucene.document :as d])
  (:import [org.apache.lucene.search ScoreDoc]
           [org.apache.lucene.search.suggest.document TopSuggestDocs SuggestIndexSearcher ContextQuery PrefixCompletionQuery FuzzyCompletionQuery]
           [org.apache.lucene.index Term IndexReader]))

(defn suggest [^IndexReader reader
               ^String field-name
               ^String prefix-query
               {:keys [contexts analyzer max-results document-xformer fuzzy skip-duplicates]
                :or   {fuzzy false skip-duplicates false document-xformer identity}}]
  {:pre [(-> analyzer nil? not) (-> max-results nil? not)]}
  (let [suggest-field        (str d/suggest-field-prefix field-name)
        term                 (Term. suggest-field prefix-query)
        pcq                  (if fuzzy
                               (FuzzyCompletionQuery. analyzer term)
                               (PrefixCompletionQuery. analyzer term))
        cq                   (ContextQuery. pcq)
        contexts             (or contexts [])
        _                    (doseq [context contexts]
                               (.addContext cq context))
        suggester            (SuggestIndexSearcher. reader)
        num-hits             (min 10 max-results)
        ^TopSuggestDocs hits (.suggest suggester cq num-hits skip-duplicates)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id (.doc hit)
              doc    (.doc suggester doc-id)
              score  (.score hit)]
          {:hit (document-xformer doc) :score score :doc-id doc-id})))))