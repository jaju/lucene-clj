(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [msync.lucene :as lucene]
            [msync.lucene.utils :as utils]
            [clojure.string :as string]))

(def index')
(defonce analyzer (lucene/>analyzer))
(defonce suggestion-context-fields [:real])
(defn context-fn [doc-map]
  (->> (select-keys doc-map suggestion-context-fields)
       vals
       (map string/lower-case)
       into-array))

(let [data             (-> "sample-data.csv"
                           io/resource
                           slurp
                           csv/read-csv
                           utils/docfields-vecs-to-maps)

      index            (lucene/>memory-index)

      _                (lucene/index-all! index
                                          data
                                          {:suggest-fields {:first-name 5}
                                           :context-fn context-fn
                                           :analyzer analyzer})]

  (alter-var-root #'index' (constantly index))

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll index)))))

  (deftest basic-search
    (testing "by first name"
      (is (= 1 (count
                 (lucene/search index "Shikari" {:field-name "first-name"})))))

    (testing "by last name"
      (is (= 1 (count
                 (lucene/search index "Jupiterwala" {:field-name "last-name"}))))))

  (deftest phrase-search
    (testing "in the bio field"
      (is (= 1 (count
                 (lucene/search index "then some more" {:field-name "bio"}))))
      (is (= 2 (count
                 (lucene/search index "love him" {:field-name "bio"}))))))

  (deftest suggestions
    (testing "suggest first names"
      (is (= 4 (count
                 (lucene/suggest index :first-name "S" {}))))
      (is (= 2 (count
                 (lucene/suggest index :first-name "Cha" {}))))))

  (deftest suggestions-with-context
    (testing "suggest first names with and without context"
      (is (= 2 (count
                 (lucene/suggest index :first-name "Oli" {}))))
      (is (= 1 (count
                 (lucene/suggest index :first-name "Oli" ["true"] {}))))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (= 2 (count
                 (lucene/search index #{"Shambhu" "Jupiterwala"} {:field-name "last-name"}))))))

  (deftest search-with-map
    (testing "test an AND set"
      (is (= 1 (count
                 (lucene/search index {:last-name "Shambhu"} {}))))))

  (deftest search-with-map-multi-fields
    (testing "multiple fields"
      (is (= 2 (count
                 (lucene/search index {:first-name "Oliver"} {}))))
      (is (= 1 (count
                 (lucene/search index {:first-name "Oliver" :real "true"} {})))))))