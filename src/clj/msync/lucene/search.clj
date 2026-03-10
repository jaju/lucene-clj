(ns msync.lucene.search
  (:require [msync.lucene
             [query :as query]
             [validation :as validation]])
  (:import [org.apache.lucene.search ScoreDoc TopDocs Query IndexSearcher]
           [msync.lucene.session SearchSession]))

(defn- ->score-doc
  [search-after]
  (cond
    (nil? search-after)
    nil

    (instance? ScoreDoc search-after)
    search-after

    :else
    (let [{:keys [doc-id score]} search-after]
      (ScoreDoc. (int doc-id) (float score)))))

(defn- last-score-doc
  [^TopDocs hits]
  (let [score-docs (.scoreDocs hits)]
    (when (pos? (alength score-docs))
      (aget score-docs (dec (alength score-docs))))))

(defn- search-page
  [^IndexSearcher searcher ^Query query results-per-page page search-after]
  (let [search-after-doc (->score-doc search-after)]
    (cond
      search-after-doc
      (.searchAfter searcher search-after-doc query results-per-page)

      (zero? page)
      (.search searcher query results-per-page)

      :else
      (loop [remaining-pages page
             after           nil]
        (let [hits     (if after
                         (.searchAfter searcher after query results-per-page)
                         (.search searcher query results-per-page))
              last-hit (last-score-doc hits)]
          (if (or (zero? remaining-pages)
                  (nil? last-hit))
            hits
            (recur (dec remaining-pages) last-hit)))))))

(defn search [^SearchSession search-session query-form
              {:keys [field-name results-per-page analyzer hit->doc page fuzzy? search-after]
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
                                     :search-after search-after
                                     :fuzzy? fuzzy?})
  (let [field-specs             (:field-specs search-session)
        ^IndexSearcher searcher (:searcher search-session)
        stored-fields           (:stored-fields search-session)
        field-name              (if field-name (name field-name))
        ^Query query            (if fuzzy?
                                  (query/-combine-fuzzy-queries query-form field-specs)
                                  (query/parse query-form {:analyzer analyzer
                                                           :field-name field-name
                                                           :field-specs field-specs}))
        ^TopDocs hits           (search-page searcher query results-per-page page search-after)]
    (vec
      (for [^ScoreDoc hit (.scoreDocs hits)]
        (let [doc-id        (.doc hit)
              doc           (.document stored-fields doc-id)
              score         (.score hit)]
          {:doc-id doc-id :score score :hit (hit->doc doc)})))))
