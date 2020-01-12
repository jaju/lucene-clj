;; [[file:~/github/lucene-clj/README.org::*Given][Given:2]]
(ns msync.lucene.tests-common
  (:require [msync.lucene
             [analyzers :as analyzers]
             [utils :as utils]]
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
;; Given:2 ends here

;; [[file:~/github/lucene-clj/README.org::*Given][Given:5]]
;; Simple sample data - straightforward splits of columns
(defonce sample-data (-> sample-data-file
                         read-csv-resource-file
                         utils/docs:vecs->maps))

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
                         utils/docs:vecs->maps
                         (map process-album-data-row)))
;; Given:5 ends here

;; [[file:~/github/lucene-clj/README.org::*Given][Given:6]]
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
;; Given:6 ends here
