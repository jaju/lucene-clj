(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [msync.lucene :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(let [content-resource (io/resource "sample-data.csv")
      content          (slurp content-resource)
      csv-coll         (csv/read-csv content)
      data             (map zipmap
                            (->> (first csv-coll)
                                 (map keyword)
                                 repeat)
                            (rest csv-coll))
      directory        (>memory-index)
      analyzer         (*analyzer>*)
      _                (index-all! directory data {})]

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll directory))))))
