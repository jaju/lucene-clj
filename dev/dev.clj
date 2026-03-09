;; [[file:../README.org::*Preamble][Preamble:1]]
(ns dev
  (:require [msync.lucene :as lucene]
            [msync.lucene
             [document :as ld]
             [tests-common :as common]]))
;; Preamble:1 ends here

;; [[file:../README.org::*Create an index][Create an index:1]]
(defonce album-index (lucene/create-index! :type :memory
                                           :analyzer common/album-data-analyzer))
;; Create an index:1 ends here

;; [[file:../README.org::*Index documents][Index documents:1]]
(lucene/index! album-index common/album-data
               {:fields common/album-fields})
;; Index documents:1 ends here

;; [[file:../README.org::*Now, we can search][Now, we can search:1]]
(clojure.pprint/pprint
 (lucene/search album-index {:Year "1977"}
                {:results-per-page 2}))
;; Now, we can search:1 ends here

;; [[file:../README.org::*Now, we can search][Now, we can search:3]]
(clojure.pprint/pprint
 (lucene/search album-index {:Year "1977"}
                {:results-per-page 2
                 :hit->doc ld/document->map}))
;; Now, we can search:3 ends here

;; [[file:../README.org::*Now, we can search][Now, we can search:5]]
(clojure.pprint/pprint
 (lucene/search album-index
                {:Year "1977"}
                {:results-per-page 2
                 :hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])}))
;; Now, we can search:5 ends here

;; [[file:../README.org::*Now, we can search][Now, we can search:7]]
(clojure.pprint/pprint
 (lucene/search album-index
                {:Year "1968"} ;; Map of field-values to search with
                {:results-per-page 5 ;; Control the number of results returned
                 :page 2             ;; Page number, starting 0 as default
                 :hit->doc         #(-> %
                                        ld/document->map
                                        (select-keys [:Year :Album]))}))
;; Now, we can search:7 ends here

;; [[file:../README.org::*Suggestions for suggest-enabled fields][Suggestions for suggest-enabled fields:1]]
(clojure.pprint/pprint
 (lucene/suggest album-index :Album "par"
                 {:hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])
                  :contexts ["Electronic"]}))
;; Suggestions for suggest-enabled fields:1 ends here

;; [[file:../README.org::*Suggestions for suggest-enabled fields][Suggestions for suggest-enabled fields:3]]
(clojure.pprint/pprint
 (lucene/suggest album-index :Album "per"
                 {:hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])
                  :fuzzy? true
                  :contexts ["Electronic"]}))
;; Suggestions for suggest-enabled fields:3 ends here
