(ns msync.lucene-test
  (:require [clojure.test :refer :all]
            [msync.tests-common :refer :all]
            [msync.lucene :as lucene]
            [clojure.string :as string]
            [msync.lucene.query :as query]
            [msync.lucene.analyzers :as a]))

(def store')
(defonce analyzer (a/standard-analyzer))
(defonce suggestion-context-fields [:real])
(defn context-fn [doc-map]
  (->> (select-keys doc-map suggestion-context-fields)
       vals
       (map string/lower-case)))

(let [data             sample-data
      store            (lucene/store :memory :analyzer analyzer)
      _                (lucene/index! store data
                         {:suggest-fields {:first-name 5}
                          :context-fn     context-fn
                          :keyword-fields #{:age}})
      directory        (:directory store)]

  ;; This is to hold onto the index when created during REPL-driven development, and this buffer is eval'ed.
  (alter-var-root #'store' (constantly store))

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll directory)))))

  (deftest basic-search
    (testing "by first name"
      (is (= 1 (count
                 (lucene/search store "shikari" {:field-name "first-name" :analyzer analyzer})))))

    (testing "by last name"
      (is (= 1 (count
                 (lucene/search store "Jupiterwala" {:field-name "last-name"})))))

    (testing "query details via a map, and no opts"
      (is (= 1 (count
                 (lucene/search store {:last-name "Jupiterwala"}))))))

  (deftest phrase-search
    (testing "in the bio field"
      (is (= 1 (count
                 (lucene/search store "then some more" {:field-name "bio"}))))
      (is (= 2 (count
                 (lucene/search store "love him" {:field-name "bio"}))))))

  (deftest suggestions
    (testing "suggest first names"
      (is (= 4 (count
                 (lucene/suggest store :first-name "S"))))
      (is (= 2 (count
                 (lucene/suggest store :first-name "Cha"))))))

  ;; Using contexts. Contexts work differently than fields. All contexts are clubbed together.
  ;; I wish I understood this design rationale better.
  (deftest suggestions-with-context
    (testing "suggest first names with and without context"
      (is (= 2 (count
                 (lucene/suggest store :first-name "Oli"))))
      (is (= 1 (count
                 (lucene/suggest store :first-name "Oli" {:contexts ["true"]}))))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (= 2 (count
                 (lucene/search store #{"Shambhu" "Jupiterwala"} {:field-name "last-name"}))))))

  (deftest search-with-map-multi-fields
    (testing "multiple fields"
      (is (= 2 (count
                 (lucene/search store {:first-name "Oliver"}))))
      (is (= 1 (count
                 (lucene/search store {:first-name "Oliver" :real "true"}))))))

  (deftest parse-query-dsl
    (testing "Given a classic query string"
      (is (= "name:shikari name:shambhu (real:true)^2.0"
             (str (query/parse-dsl "Shikari Shambhu real:true^2" "name" (a/standard-analyzer [])))))))

  (deftest search-with-query-dsl
    (testing "Search for the Shikari using the classic query DSL"
      (is (= 1 (count
                 (lucene/search store (query/parse-dsl "shikari" :first-name (a/standard-analyzer []))))))

      (is (= 2 (count
                 (lucene/search store (query/parse-dsl "gender:f" (a/standard-analyzer []))))))))

  (deftest paginated-results
      (let [query {:bio #{"love" "enjoy"}}
            max-results 10
            page-0 (lucene/search store query {:max-results max-results :page 0 :results-per-page 2})
            page-1 (lucene/search store query {:max-results max-results :page 1 :results-per-page 2})
            page-2 (lucene/search store query {:max-results max-results :page 2 :results-per-page 2})]
        (testing "Fetching from a large (ahem!) repository, one page at a time"
          (is (= 2 (count page-0)))
          (is (= 2 (count page-1)))
          (is (= 2 (count page-2))))

        (testing "All pages have non-overlapping documents"
          (is (= 6 (count (into #{} (map :doc-id (flatten [page-0 page-1 page-2])))))))))

  (deftest suggestions-with-limit-params
    (let [query    "S"
          result-1 (lucene/suggest store :first-name query {:max-results 2})
          result-2 (lucene/suggest store :first-name query {:max-results 4})]
      (testing "Asking for suggestions from a large (ahem!) repository, one page at a time"
        (is (= 2 (count result-1)))
        (is (= 4 (count result-2)))))))

(comment
  (run-all-tests (-> *ns* str re-pattern)))