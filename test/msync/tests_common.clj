(ns msync.tests-common
  (:require [msync.lucene :as lucene]
            [msync.lucene
             [analyzers :as analyzers]
             [document :as ld]]
            [clojure.test :refer :all]
            [msync.lucene.utils :as utils]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defonce sample-data (-> "sample-data.csv"
                       io/resource
                       slurp
                       csv/read-csv
                       utils/docfields-vecs-to-maps))

(defonce album-data (-> "albumlist.csv"
                      io/resource
                      slurp
                      csv/read-csv
                      utils/docfields-vecs-to-maps))

(defonce default-analyzer (analyzers/standard-analyzer))
(defonce keyword-analyzer (analyzers/keyword-analyzer))

(defonce album-data-analyzer
  (analyzers/per-field-analyzer default-analyzer
    {:Year     keyword-analyzer
     :Genre    keyword-analyzer
     :Subgenre keyword-analyzer}))

(comment
  (def index (lucene/store :memory :analyzer album-data-analyzer))

  (lucene/index! index album-data {:analyzer       album-data-analyzer
                                   :context-fn     (fn [d] [(:Genre d)])
                                   :suggest-fields {:Album 1 :Artist 1}})
  (lucene/delete-all! index)

  (lucene/search index {:Year "1967"} {:results-per-page 5
                                       :hit->doc         #(-> %
                                                            ld/document->map
                                                            (select-keys [:Year :Album :Artist :Subgenre]))})

  (lucene/suggest index :Album "fore" {:hit->doc ld/document->map :fuzzy? true})
  (lucene/search index {:Album "forever"} {:hit->doc ld/document->map :fuzzy? true}))