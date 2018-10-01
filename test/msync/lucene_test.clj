(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [msync.lucene :as lucene]
            [clojure.string :as string]))

(def directory')

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
      context-fn       (fn [m]
                         (->> (select-keys m [:real])
                              vals
                              (map string/lower-case)
                              into-array))
      _                (lucene/index-all! directory data {:suggest-fields {:first-name 5} :context-fn context-fn :analyzer analyzer})]

  (alter-var-root #'directory' (constantly directory))

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
      (is (= 1 (count (lucene/search directory "then some more" {:field-name "bio"}))))
      (is (= 2 (count (lucene/search directory "love him" {:field-name "bio"}))))))

  (deftest suggestions
    (testing "suggest first names"
      (is (= 4 (count (lucene/suggest directory :first-name "S" {}))))
      (is (= 2 (count (lucene/suggest directory :first-name "Cha" {}))))))

  (deftest suggestions-with-context
    (testing "suggest first names with and without context"
      (is (= 2 (count (lucene/suggest directory :first-name "Oli" {}))))
      (is (= 1 (count (lucene/suggest directory :first-name "Oli" ["true"] {}))))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (= 2 (count (lucene/search directory #{"Shambhu" "Jupiterwala"} {:field-name "last-name"}))))))

  (deftest search-with-map
    (testing "test an AND set"
      (is (= 1 (count (lucene/search directory {:last-name "Shambhu"} {}))))))

  (deftest search-with-map-multi-fields
    (testing "multiple fields"
      (is (= 2 (count (lucene/search directory {:first-name "Oliver"} {}))))
      (is (= 1 (count (lucene/search directory {:first-name "Oliver" :real "true"} {})))))))