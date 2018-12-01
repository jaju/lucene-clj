(ns msync.lucene.suggestions
  (:require [msync.lucene.document :as d])
  (:import [org.apache.lucene.search ScoreDoc]
           [org.apache.lucene.search.suggest.document
            TopSuggestDocs SuggestIndexSearcher ContextQuery
            PrefixCompletionQuery FuzzyCompletionQuery Completion50PostingsFormat]
           [org.apache.lucene.index Term IndexReader]
           [org.apache.lucene.codecs.lucene70 Lucene70Codec]))


(defn >filter-codec-for-suggestions
  "Creates a codec for storing fields that support returning suggestions for given prefix strings.
  Chooses the codec based on the field name prefix - which is fixed/pre-decided and not designed to be
  overridden."
  []
  (let [comp-postings-format (Completion50PostingsFormat.)]
    (proxy [Lucene70Codec] []
      (getPostingsFormatForField [field-name]
        (if (.startsWith field-name d/suggest-field-prefix)
          comp-postings-format
          (proxy-super getPostingsFormatForField field-name))))))

(defn suggest [^IndexReader index-reader
               ^String field-name
               ^String prefix-query
               {:keys [contexts analyzer max-results document-xformer fuzzy? skip-duplicates?]
                :or   {fuzzy? false skip-duplicates? false document-xformer identity}}]
  {:pre [(-> analyzer nil? not) (-> max-results nil? not)]}
  (let [suggest-field        (str d/suggest-field-prefix field-name)
        term                 (Term. suggest-field prefix-query)
        pcq                  (if fuzzy?
                               (FuzzyCompletionQuery. analyzer term)
                               (PrefixCompletionQuery. analyzer term))
        cq                   (ContextQuery. pcq)
        contexts             (or contexts [])
        _                    (doseq [context contexts]
                               (.addContext cq context))
        suggester            (SuggestIndexSearcher. index-reader)
        num-hits             (min 10 max-results)
        ^TopSuggestDocs hits (.suggest suggester cq num-hits skip-duplicates?)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id (.doc hit)
              doc    (.doc suggester doc-id)
              score  (.score hit)]
          {:hit (document-xformer doc) :score score :doc-id doc-id})))))