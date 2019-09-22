;; [[file:~/github/lucene-clj/README.org::*Given][Given:1]]
(ns msync.lucene.tests-common
  (:require [msync.lucene
             [analyzers :as analyzers]]
            [msync.lucene.utils :as utils]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn- process-csv-column [coll column]
  (assoc coll column
         (map s/trim (s/split (get coll column) #","))))

(defn process-album-data-row [row]
  (-> row
      (process-csv-column :Genre)
      (process-csv-column :Subgenre)))

(defonce sample-data (-> "sample-data.csv"
                         io/resource
                         slurp
                         csv/read-csv
                         utils/docs:vecs->maps))

(def album-data (->> "albumlist.csv"
                         io/resource
                         slurp
                         csv/read-csv
                         utils/docs:vecs->maps
                         (map process-album-data-row)))

(defonce default-analyzer (analyzers/standard-analyzer))
(defonce keyword-analyzer (analyzers/keyword-analyzer))

;; A per-field analyzer, which composes other kinds of analyzers
(defonce album-data-analyzer
  (analyzers/per-field-analyzer default-analyzer
                                {:Year     keyword-analyzer
                                 :Genre    keyword-analyzer
                                 :Subgenre keyword-analyzer}))
;; Given:1 ends here
