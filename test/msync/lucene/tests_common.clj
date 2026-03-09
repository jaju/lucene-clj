;; [[file:../../../README.org::*Some Background - Data Preparation][Some Background - Data Preparation:2]]
(ns msync.lucene.tests-common
  (:require [msync.lucene :as lucene]
            [msync.lucene
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

(def album-fields
  {:Number   {:type :text
              :stored? true
              :indexed? true}
   :Year     {:type :keyword
              :stored? true
              :indexed? true}
   :Album    {:type    :text
              :stored? true
              :indexed? true
              :suggest {:weight 5
                        :contexts-from :Genre}}
   :Artist   {:type    :text
              :stored? true
              :indexed? true
              :suggest {:contexts-from :Genre}}
   :Genre    {:type          :keyword
              :stored?       true
              :indexed?      true
              :multi-valued? true}
   :Subgenre {:type          :keyword
              :stored?       true
              :indexed?      true
              :multi-valued? true}})

(def suggestion-context-fields
  [:real])

(defn sample-contexts
  "Derive suggestion contexts for the sample dataset."
  [doc-map]
  (->> (select-keys doc-map suggestion-context-fields)
       vals
       (map s/lower-case)))

(def sample-fields
  {:first-name {:type    :text
                :stored? true
                :indexed? true
                :suggest {:contexts-from sample-contexts}}
   :last-name  {:type :text
                :stored? true
                :indexed? true}
   :age        {:type :keyword
                :stored? true
                :indexed? true}
   :real       {:type :text
                :stored? true
                :indexed? true}
   :gender     {:type :text
                :stored? true
                :indexed? true}
   :bio        {:type :text
                :stored? true
                :indexed? true}})

(defn create-sample-store
  "Create and populate the in-memory sample index used by semantic API tests."
  []
  (let [store (lucene/create-index! :type :memory :analyzer default-analyzer)]
    (lucene/index! store sample-data
                   {:fields sample-fields})
    store))
