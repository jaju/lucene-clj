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
               {:context-fn     :Genre
                :suggest-fields [:Album :Artist]
                :stored-fields  [:Number :Year :Album :Artist :Genre :Subgenre]})
;; Index documents - which are Clojure maps:1 ends here

;; [[file:~/github/lucene-clj/README.org::*Now,%20we%20can%20search][Now, we can search:1]]
(clojure.pprint/pprint (do (lucene/search index 
               {:Year "1968"} ;; Map of field-values to search with
               {:results-per-page 5 ;; Control the number of results returned
                :page 4             ;; Page number, starting 0 as default
                :hit->doc         #(-> %
                                       ld/document->map
                                       (select-keys [:Year :Album]))})))
;; Now, we can search:1 ends here

;; [[file:~/github/lucene-clj/README.org::*We%20can%20ask%20for%20suggestions%20on%20the%20fields%20indexed%20to%20support%20it][We can ask for suggestions on the fields indexed to support it:1]]
(clojure.pprint/pprint (do (lucene/suggest index :Album "par" {:hit->doc (fn [d] (ld/document->map d :multi-fields #{:Genre})) :fuzzy? false :contexts ["Electronic"]})))
;; We can ask for suggestions on the fields indexed to support it:1 ends here

;; [[file:~/github/lucene-clj/README.org::*We%20can%20ask%20for%20suggestions%20on%20the%20fields%20indexed%20to%20support%20it][We can ask for suggestions on the fields indexed to support it:3]]
(clojure.pprint/pprint (do (lucene/suggest index :Album "per" {:hit->doc ld/document->map :fuzzy? true :contexts ["Electronic"]})))
;; We can ask for suggestions on the fields indexed to support it:3 ends here
