(ns msync.lucene.search
  (:require [msync.lucene
             [query :as query]
             [schema :as schema]
             [validation :as validation]])
  (:import [org.apache.lucene.search ScoreDoc TopDocs Query IndexSearcher]
           [org.apache.lucene.index IndexReader]))


(defn search [^IndexReader index-store query-form
              {:keys [field-name results-per-page analyzer hit->doc page fuzzy?]
               :or   {results-per-page 10
                      page             0
                      hit->doc         identity
                      fuzzy?           false}}]
  (validation/-validate-search-opts query-form
                                    {:field-name field-name
                                     :results-per-page results-per-page
                                     :analyzer analyzer
                                     :hit->doc hit->doc
                                     :page page
                                     :fuzzy? fuzzy?})
  (let [field-specs             (schema/-read-field-specs index-store)
        ^IndexSearcher searcher (IndexSearcher. index-store)
        field-name              (if field-name (name field-name))
        ^Query query            (if fuzzy?
                                  (query/-combine-fuzzy-queries query-form field-specs)
                                  (query/parse query-form {:analyzer analyzer
                                                           :field-name field-name
                                                           :field-specs field-specs}))
        ^Integer num-hits       (+ (* page results-per-page) results-per-page)
        ^TopDocs hits           (.search searcher query ^Integer num-hits)
        start                   (* page results-per-page)
        end                     (min (+ start results-per-page) (.value (.totalHits hits)))]
    (vec
      (for [^ScoreDoc hit (map (partial aget (.scoreDocs hits))
                            (range start end))]
        (let [doc-id        (.doc hit)
              stored-fields (.storedFields searcher)
              doc           (.document stored-fields doc-id)
              score         (.score hit)]
          {:doc-id doc-id :score score :hit (hit->doc doc)})))))
