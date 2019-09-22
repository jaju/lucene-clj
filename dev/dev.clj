;; [[file:~/github/lucene-clj/README.org::*Preamble][Preamble:1]]
(ns dev
  (:require [msync.lucene :as lucene]
            [msync.lucene
             [store :as store]
             [document :as ld]
             [tests-common :refer :all]]))
;; Preamble:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Create%20an%20index][Create an index:1]]
(def index (store/store :memory :analyzer album-data-analyzer))
;; Create an index:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Index%20documents%20-%20which%20are%20Clojure%20maps][Index documents - which are Clojure maps:1]]
(lucene/index! index album-data
               {:stored-fields  [:Number :Year :Album :Artist :Genre :Subgenre]
                :suggest-fields [:Album :Artist]
                :context-fn     :Genre})
;; Index documents - which are Clojure maps:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Now,%20we%20can%20search][Now, we can search:1]]
(clojure.pprint/pprint (do (lucene/search index {:Year "1979"}
               {:results-per-page 2})))
;; Now, we can search:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Now,%20we%20can%20search][Now, we can search:3]]
(clojure.pprint/pprint (do (lucene/search index {:Year "1979"}
               {:results-per-page 2
                :hit->doc ld/document->map})))
;; Now, we can search:3 ends here

;; [[file:~/github/lucene-clj/README.org::*Now,%20we%20can%20search][Now, we can search:5]]
(clojure.pprint/pprint (do (lucene/search index {:Year "1979"}
               {:results-per-page 2
                :hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])})))
;; Now, we can search:5 ends here

;; [[file:~/github/lucene-clj/README.org::*Now,%20we%20can%20search][Now, we can search:7]]
(clojure.pprint/pprint (do (lucene/search index 
               {:Year "1968"} ;; Map of field-values to search with
               {:results-per-page 5 ;; Control the number of results returned
                :page 4             ;; Page number, starting 0 as default
                :hit->doc         #(-> %
                                       ld/document->map
                                       (select-keys [:Year :Album]))})))
;; Now, we can search:7 ends here

;; [[file:~/github/lucene-clj/README.org::*Suggestions%20support%20for%20fields%20passed%20via%20/:suggest-fields/][Suggestions support for fields passed via /:suggest-fields/:1]]
(clojure.pprint/pprint (do (lucene/suggest index :Album "par"
                {:hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])
                 :fuzzy? false
                 :contexts ["Electronic"]})))
;; Suggestions support for fields passed via /:suggest-fields/:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Suggestions%20support%20for%20fields%20passed%20via%20/:suggest-fields/][Suggestions support for fields passed via /:suggest-fields/:3]]
(clojure.pprint/pprint (do (lucene/suggest index :Album "per"
                {:hit->doc #(ld/document->map % :multi-fields [:Genre :Subgenre])
                 :fuzzy? true
                 :contexts ["Electronic"]})))
;; Suggestions support for fields passed via /:suggest-fields/:3 ends here
