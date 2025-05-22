(ns msync.lucene.search
  (:require [msync.lucene.query :as query])
  (:import [org.apache.lucene.search ScoreDoc TopDocs Query IndexSearcher]
           [org.apache.lucene.index IndexReader]
           [java.util ArrayList]))

(defn- process-topdocs
  [^IndexSearcher searcher ^TopDocs top-docs hit->doc]
  (vec
    (for [^ScoreDoc hit (.scoreDocs top-docs)]
      (let [doc-id        (.doc hit)
            stored-fields (.storedFields searcher)
            doc           (.document stored-fields doc-id)
            score         (.score hit)]
        {:doc-id doc-id :score score :hit (hit->doc doc)}))))

(defn search [^IndexReader index-store query-form
              {:keys [field-name results-per-page analyzer hit->doc page fuzzy? raw-topdocs?]
               :or   {results-per-page 10
                      page             0
                      hit->doc         identity
                      fuzzy?           false
                      raw-topdocs?     false}}]
  (let [^IndexSearcher searcher (IndexSearcher. index-store)
        field-name              (if field-name (name field-name))
        ^Query query            (if fuzzy?
                                  (query/combine-fuzzy-queries query-form)
                                  (query/parse query-form {:analyzer analyzer :field-name field-name}))
        ^Integer num-hits       (+ (* page results-per-page) results-per-page)
        ^TopDocs top-docs       (.search searcher query ^Integer num-hits)]
    (if raw-topdocs?
      top-docs
      (let [start           (* page results-per-page)
            score-docs      (.scoreDocs top-docs)
            total-hits      (.value (.totalHits top-docs))
            effective-end   (min (+ start results-per-page) total-hits)
            docs-to-process (if (> effective-end start)
                              (java.util.Arrays/copyOfRange score-docs start effective-end)
                              (make-array ScoreDoc 0))
            processed-top-docs (TopDocs. (.totalHits top-docs) docs-to-process)]
        (process-topdocs searcher processed-top-docs hit->doc)))))

(defn reciprocal-rank-fusion
  [^IndexSearcher searcher top-docs-list top-n k {:keys [hit->doc] :or {hit->doc identity}}]
  (when (and searcher (seq top-docs-list))
    (let [java-top-docs-list (ArrayList. top-docs-list) ; Removed ^java.util.List hint from here
          ^TopDocs fused-top-docs (TopDocs/rrf ^java.util.List java-top-docs-list (int top-n) (int k))] ; Added hint here
      (process-topdocs searcher fused-top-docs hit->doc))))
