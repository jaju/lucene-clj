;; [[file:~/github/lucene-clj/README.org::*Some Background - Data Preparation][Some Background - Data Preparation:2]]
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

;; The two datasets
(defonce sample-data-file "sample-data.csv")
(defonce albums-file "albumlist.csv")

;; Simple sample data - straightforward splits of columns
(defonce sample-data (-> sample-data-file
                         read-csv-resource-file
                         ld/vecs->maps))

;; Album data - handler for multi-valued columns,
;; which happen to be comma-separated themselves.
(defn- process-csv-column [coll column]
  (assoc coll column
         (map s/trim (s/split (get coll column) #","))))

;; These two columns are multi-valued
(defn process-album-data-row [row]
  (-> row
      (process-csv-column :Genre)
      (process-csv-column :Subgenre)))

(defonce album-data (->> albums-file
                         read-csv-resource-file
                         ld/vecs->maps
                         (map process-album-data-row)))
;; Some Background - Data Preparation:2 ends here

;; [[file:~/github/lucene-clj/README.org::*Creating Analyzers][Creating Analyzers:1]]
;; In the common namespace
;; This is the default analyzer, an instance of the StandardAnalyzer
;; of Lucene
(defonce default-analyzer (analyzers/standard-analyzer))

;; This analyzer considers field values verbatim
;; Will not tokenize and stem
(defonce keyword-analyzer (analyzers/keyword-analyzer))

;; A per-field analyzer, which composes other kinds of analyzers
;; For album data, we have marked some fields as verbatim
(defonce album-data-analyzer
  (analyzers/per-field-analyzer default-analyzer
                                {:Year     keyword-analyzer
                                 :Genre    keyword-analyzer
                                 :Subgenre keyword-analyzer}))
;; Creating Analyzers:1 ends here
