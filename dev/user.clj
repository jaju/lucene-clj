(ns user
  (:require [clojure.data.csv :as csv]
            [msync.lucene :as lucene]))

(defonce test-doc-maps
         (let [csv-string (slurp "test-resources/sample-data.csv")
               csv-stream (csv/read-csv csv-string)]
           (map zipmap
                (->> (first csv-stream)
                     (map keyword)
                     repeat)
                (rest csv-stream))))

(comment
  (def mem-dir (lucene/memory-index>))
  (lucene/index-all! mem-dir test-doc-maps {:suggest-fields {:first-name 5}})
  (lucene/search mem-dir "suppandi" {:field-name "first-name"}))
