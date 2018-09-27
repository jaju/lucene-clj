(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [msync.lucene :as lucene]))

(let [content-resource (io/resource "sample-data.csv")
      content          (slurp content-resource)
      csv-coll         (csv/read-csv content)
      data             (map zipmap
                            (->> (first csv-coll)
                                 (map keyword)
                                 repeat)
                            (rest csv-coll))
      directory        (lucene/>memory-index)
      analyzer         (lucene/>analyzer)
      _                (lucene/index-all! directory data {:suggest-fields {:first-name 1}})]

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll directory)))))

  (deftest basic-search
    (testing "by first name"
      (is (= 1 (count (lucene/search directory "Shikari" {:field-name "first-name"})))))

    (testing "by last name"
      (is (= 1 (count (lucene/search directory "Jupiterwala" {:field-name "last-name"}))))))

  (deftest phrase-search
    (testing "in the bio field"
      (is (= 1 (count (lucene/phrase-search directory "then some more" {:field-name "bio"}))))
      (is (= 2 (count (lucene/phrase-search directory "love him" {:field-name "bio"}))))))

  (deftest suggestions
    (testing "suggest first names"
      (is (= 3 (count (lucene/suggest directory :first-name "S" {}))))
      (is (= 1 (count (lucene/suggest directory :first-name "Cha" {}))))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (= 2 (count (lucene/search directory #{"Shambhu" "Jupiterwala"} {:field-name "last-name"})))))))

