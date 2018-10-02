(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [msync.lucene :as lucene]
            [msync.lucene.utils :as utils]
            [clojure.string :as string]
            [msync.lucene.query :as query]))

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
  ;; This is to hold onto the index when created during REPL-driven development, and this buffer is eval'ed.

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll index)))))

  (deftest basic-search
    (testing "by first name"
      (is (= 1 (count
                 (lucene/search index "Shikari" {:field-name "first-name"})))))

    (testing "by last name"
      (is (= 1 (count
                 (lucene/search index "Jupiterwala" {:field-name "last-name"})))))

    (testing "query details via a map, and no opts"
      (is (= 1 (count
                 (lucene/search index {:last-name "Jupiterwala"}))))))

  (deftest phrase-search
    (testing "in the bio field"
      (is (= 1 (count
                 (lucene/search index "then some more" {:field-name "bio"}))))
      (is (= 2 (count
                 (lucene/search index "love him" {:field-name "bio"}))))))

  (deftest suggestions
    (testing "suggest first names"
      (is (= 4 (count
                 (lucene/suggest index :first-name "S"))))
      (is (= 2 (count
                 (lucene/suggest index :first-name "Cha"))))))

  ;; Using contexts. Contexts work differently than fields. All contexts are clubbed together.
  ;; I wish I understood this design rationale better.
  (deftest suggestions-with-context
    (testing "suggest first names with and without context"
      (is (= 2 (count
                 (lucene/suggest index :first-name "Oli"))))
      (is (= 1 (count
                 (lucene/suggest index :first-name "Oli" {:contexts ["true"]}))))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (= 2 (count
                 (lucene/search index #{"Shambhu" "Jupiterwala"} {:field-name "last-name"}))))))

  (deftest search-with-map-multi-fields
    (testing "multiple fields"
      (is (= 2 (count
                 (lucene/search index {:first-name "Oliver"}))))
      (is (= 1 (count
                 (lucene/search index {:first-name "Oliver" :real "true"}))))))

  (deftest parse-query-dsl
    (testing "Given a classic query string"
      (is (= "name:shikari name:shambhu (real:true)^2.0"
             (str (query/parse-dsl "Shikari Shambhu real:true^2" "name" (lucene/>analyzer)))))))

  (deftest search-with-query-dsl
    (testing "Search for the Shikari using the classic query DSL"
      (is (= 1 (count
                 (lucene/search index (query/parse-dsl "shikari" :first-name (lucene/>analyzer))))))

      (is (= 2 (count
                 (lucene/search index (query/parse-dsl "gender:f" (lucene/>analyzer)))))))))