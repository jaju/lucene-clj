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
(comment


  (defonce postal-data (-> "pincode-data-26022018.csv"
                         io/resource
                         slurp
                         csv/read-csv
                         utils/docfields-vecs-to-maps))

  (first postal-data)
  (defonce default-analyzer (analyzers/create-default-analyzer))
  (defonce keyword-analyzer (analyzers/create-keyword-analyzer))

  (defonce postal-data-analyzer
    (analyzers/create-per-field-analyzer default-analyzer
      {:delivery-status keyword-analyzer
       :pincode keyword-analyzer}))

  (def index (lucene/create-store :memory :analyzer postal-data-analyzer))

  (lucene/index! index postal-data {:analyzer postal-data-analyzer})
  (lucene/delete-all! index)

  (lucene/search index {:state "maharashtra"} {:analyzer postal-data-analyzer}))