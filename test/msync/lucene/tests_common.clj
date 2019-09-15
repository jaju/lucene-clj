(ns msync.lucene.tests-common
  (:require [msync.lucene
             [analyzers :as analyzers]]
            [msync.lucene.utils :as utils]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defonce sample-data (-> "sample-data.csv"
                         io/resource
                         slurp
                         csv/read-csv
                         utils/docs:vecs->maps))

(defonce album-data (-> "albumlist.csv"
                        io/resource
                        slurp
                        csv/read-csv
                        utils/docs:vecs->maps))

(defonce default-analyzer (analyzers/standard-analyzer))
(defonce keyword-analyzer (analyzers/keyword-analyzer))

;; A per-field analyzer, which composes other kinds of analyzers
(defonce album-data-analyzer
  (analyzers/per-field-analyzer default-analyzer
                                {:Year     keyword-analyzer
                                 :Genre    keyword-analyzer
                                 :Subgenre keyword-analyzer}))
