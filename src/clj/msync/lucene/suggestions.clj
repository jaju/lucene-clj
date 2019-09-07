(ns msync.lucene.suggestions
  (:require [msync.lucene.document :as d])
  (:import [org.apache.lucene.search ScoreDoc]
           [org.apache.lucene.search.suggest.document
            TopSuggestDocs SuggestIndexSearcher ContextQuery
            PrefixCompletionQuery FuzzyCompletionQuery Completion50PostingsFormat CompletionQuery]
           [org.apache.lucene.index Term IndexReader]
           [org.apache.lucene.codecs.lucene80 Lucene80Codec]))


(defn create-filter-codec-for-suggestions
  "Creates a codec for storing fields that support returning suggestions for given prefix strings.
  Chooses the codec based on the field name prefix - which is fixed/pre-decided and not designed to be
  overridden."
  []
  (let [comp-postings-format (Completion50PostingsFormat.)]
    (proxy [Lucene80Codec] []
      (getPostingsFormatForField [field-name]
        (if (.startsWith field-name d/suggest-field-prefix)
          comp-postings-format
          (proxy-super getPostingsFormatForField field-name))))))

(defn suggest [^IndexReader index-reader
               ^String field-name
               ^String prefix-query
               {:keys [contexts analyzer max-results hit->doc fuzzy? skip-duplicates?]
                :or   {fuzzy? false skip-duplicates? false hit->doc identity}}]
  {:pre [(-> analyzer nil? not) (-> max-results nil? not)]}
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
        suggester            (SuggestIndexSearcher. index-reader)
        num-hits             (min 10 max-results)
        ^TopSuggestDocs hits (.suggest suggester cq num-hits skip-duplicates?)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id (.doc hit)
              doc    (.doc suggester doc-id)
              score  (.score hit)]
          {:hit (hit->doc doc) :score score :doc-id doc-id})))))