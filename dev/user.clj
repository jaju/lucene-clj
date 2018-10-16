(ns user
  (:require [clojure.data.csv :as csv]
            #_[msync.lucene :as lucene])
  (:import [org.apache.lucene.index Term]
           [org.apache.lucene.search.suggest.document PrefixCompletionQuery SuggestIndexSearcher Completion50PostingsFormat]
           [org.apache.lucene.codecs.lucene70 Lucene70Codec]))

(defonce test-doc-maps
         (let [csv-string (slurp "test-resources/sample-data.csv")
               csv-stream (csv/read-csv csv-string)]
           (map zipmap
                (->> (first csv-stream)
                     (map keyword)
                     repeat)
                (rest csv-stream))))

(defn >filter-codec []
  (proxy
    [Lucene70Codec] []

    (getPostingsFormatForField [field-name]
      (if (.startsWith field-name "$suggest-")
        (Completion50PostingsFormat.)
        (proxy-super getPostingsFormatForField field-name)))))

#_(defn comp-iw-config> []
    (let [c (#'lucene/>index-writer-config)]
      (.setCodec c (>filter-codec))
      c))

(comment
  (def mem-dir (lucene/>memory-index))
  (def comp-iw-config (comp-iw-config>))
  (def writer (#'lucene/>index-writer mem-dir comp-iw-config))
  (lucene/index-all! writer test-doc-maps {:suggest-fields {:first-name 5}})
  (.commit writer)
  (def reader (#'lucene/>index-reader mem-dir))
  (def term (Term. "suggest-first-name" "S"))
  (def analyzer (lucene/>standard-analyzer))
  (def pcq (PrefixCompletionQuery. analyzer term))
  (def suggester (SuggestIndexSearcher. reader))
  (.suggest suggester pcq 5 false)
  (lucene/search mem-dir "suppandi" {:field-name "first-name"}))
