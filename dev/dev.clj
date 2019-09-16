(ns dev
  (:require [msync.lucene :as lucene]
            [msync.lucene
             [store :as store]
             [document :as ld]
             [tests-common :refer :all]]))

(def index (store/store :memory :analyzer album-data-analyzer))

(lucene/index! index album-data
               {:context-fn     #(map clojure.string/trim (clojure.string/split (:Genre %) #","))
                :suggest-fields [:Album :Artist]
                :stored-fields  [:Number :Year :Album :Artist :Genre :Subgenre]})

(clojure.pprint/pprint (do (lucene/search index 
               {:Year "1968"} ;; Map of field-values to search with
               {:results-per-page 5 ;; Control the number of results returned
                :page 4             ;; Page number, starting 0 as default
                :hit->doc         #(-> %
                                       ld/document->map
                                       (select-keys [:Year :Album]))})))

(clojure.pprint/pprint (do (lucene/suggest index :Album "par" {:hit->doc ld/document->map :fuzzy? false :contexts ["Electronic"]})))

(clojure.pprint/pprint (do (lucene/suggest index :Album "per" {:hit->doc ld/document->map :fuzzy? true :contexts ["Electronic"]})))
