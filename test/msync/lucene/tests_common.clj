;; [[file:../../../README.org::*Some Background - Data Preparation][Some Background - Data Preparation:2]]
(ns msync.lucene.tests-common
  (:require [msync.lucene
             [analyzers :as analyzers]
             [document :as ld]]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn read-csv-resource-file
  "Locate a file on the resource path and parse it as CSV,
  creating a sequence of rows - each row being a list of the
  CSV column-values"
  [filename]
  (-> filename
      io/resource
      slurp
      csv/read-csv))


(defonce sample-data-file "sample-data.csv")
(defonce albums-file "albumlist.csv")


(defonce sample-data (-> sample-data-file
                         read-csv-resource-file
                         ld/vecs->maps))



(defn- process-csv-column [coll column]
  (assoc coll column
         (map s/trim (s/split (get coll column) #","))))


(defn process-album-data-row [row]
  (-> row
      (process-csv-column :Genre)
      (process-csv-column :Subgenre)))

(defonce album-data (->> albums-file
                         read-csv-resource-file
                         ld/vecs->maps
                         (map process-album-data-row)))
;; Some Background - Data Preparation:2 ends here

;; [[file:../../../README.org::*Creating Analyzers][Creating Analyzers:1]]
(defonce default-analyzer (analyzers/standard-analyzer))



(defonce keyword-analyzer (analyzers/keyword-analyzer))




(defonce album-data-analyzer
  (analyzers/per-field-analyzer default-analyzer
                                {:Year     keyword-analyzer
                                 :Genre    keyword-analyzer
                                 :Subgenre keyword-analyzer}))
;; Creating Analyzers:1 ends here
