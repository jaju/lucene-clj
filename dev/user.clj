(ns user
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [msync.lucene :as lucene]
            [msync.lucene
             [analyzers :refer [standard-analyzer]]
             [store :as store]
             [utils :as utils]
             [document :as ld]
             [tests-common :refer :all]])
  (:import [org.apache.lucene.index Term]
           [org.apache.lucene.search.suggest.document PrefixCompletionQuery SuggestIndexSearcher Completion50PostingsFormat]
           [org.apache.lucene.codecs.lucene80 Lucene80Codec]))


(comment

  album-data-analyzer
  sample-data
  album-data

  (def index (store/store :memory :analyzer album-data-analyzer))

  (lucene/index! index album-data
                 {:context-fn     #(map clojure.string/trim (clojure.string/split (:Genre %) #","))
                  :suggest-fields [:Album :Artist]
                  :stored-fields  [:Number :Year :Album :Artist :Genre :Subgenre]})

  (lucene/search index {:Year "1967"}
                 {:results-per-page 5
                  :hit->doc         #(-> %
                                         ld/document->map
                                         (select-keys [:Year :Album]))})

  (lucene/suggest index :Album "par" {:hit->doc ld/document->map :fuzzy? false :contexts ["Electronic"]})
  (lucene/search index {:Album "forever"} {:hit->doc ld/document->map :fuzzy? true}))
