(ns msync.lucene.lucene-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [msync.lucene.tests-common :refer :all]
            [msync.lucene :as lucene]
            [msync.lucene.document :as document]
            [msync.lucene.query :as query]
            [msync.lucene.analyzers :as a]))

(defonce store* nil)
(defonce analyzer default-analyzer)

(defn- search-results
  "Search the sample store and decode each hit into a stored-field map."
  [store query-form & [opts]]
  (lucene/search store query-form
                 (assoc (or opts {}) :hit->doc document/document->map)))

(defn- search-docs
  "Return only stored document maps from sample search results."
  [store query-form & [opts]]
  (mapv :hit (search-results store query-form opts)))

(defn- suggest-results
  "Return decoded suggestion hits for the sample store."
  [store field-name prefix-query & [opts]]
  (lucene/suggest store field-name prefix-query
                  (assoc (or opts {}) :hit->doc document/document->map)))

(defn- suggest-docs
  "Return only stored document maps from sample suggestions."
  [store field-name prefix-query & [opts]]
  (mapv :hit (suggest-results store field-name prefix-query opts)))

(let [store          (create-sample-store)
      directory      (:directory store)]

  ;; This is to hold onto the index when created during REPL-driven development, and this buffer is eval'ed.
  (alter-var-root #'store* (constantly store))

  (deftest basic-tests
    (testing "directory"
      (is (not-empty (.listAll directory)))))

  (deftest basic-search
    (testing "by first name"
      (is (match? [{:first-name "Shikari"
                    :last-name "Shambhu"}]
                  (search-docs store "shikari" {:field-name "first-name"
                                                :analyzer analyzer}))))

    (testing "by last name"
      (is (match? [{:first-name "Sabu"
                    :last-name "Jupiterwala"}]
                  (search-docs store "Jupiterwala" {:field-name "last-name"}))))

    (testing "query details via a map, and no opts"
      (is (match? [{:first-name "Sabu"
                    :last-name "Jupiterwala"}]
                  (search-docs store {:last-name "Jupiterwala"})))))

  (deftest phrase-search
    (testing "in the bio field"
      (is (match? [{:first-name "Chacha"
                    :last-name "Chaudhary"}]
                  (search-docs store "then some more" {:field-name "bio"})))
      (is (match? (m/in-any-order [{:first-name "Sabu"
                                    :last-name "Jupiterwala"}
                                   {:first-name "Shikari"
                                    :last-name "Shambhu"}])
                  (search-docs store "love him" {:field-name "bio"})))))

  (deftest suggestions
    (testing "suggest first names"
      (is (match? (m/in-any-order [{:first-name "Suppandi"}
                                   {:first-name "Shikari"}
                                   {:first-name "Sabu"}
                                   {:first-name "Stanley"}])
                  (suggest-docs store :first-name "S")))
      (is (match? (m/in-any-order [{:first-name "Chacha"}
                                   {:first-name "Charlie"}])
                  (suggest-docs store :first-name "Cha")))))

  ;; Using contexts. Contexts work differently than fields. All contexts are clubbed together.
  ;; I wish I understood this design rationale better.
  (deftest suggestions-with-context
    (testing "suggest first names with and without context"
      (is (match? (m/in-any-order [{:first-name "Oliver"
                                    :last-name "Hardy"}
                                   {:first-name "Oliver"
                                    :last-name "Twist"}])
                  (suggest-docs store :first-name "Oli")))
      (is (match? [{:first-name "Oliver"
                    :last-name "Hardy"
                    :real "True"}]
                  (suggest-docs store :first-name "Oli" {:contexts ["true"]})))))

  (deftest or-search-with-set
    (testing "test an OR set"
      (is (match? (m/in-any-order [{:first-name "Shikari"
                                    :last-name "Shambhu"}
                                   {:first-name "Sabu"
                                    :last-name "Jupiterwala"}])
                  (search-docs store #{"Shambhu" "Jupiterwala"} {:field-name "last-name"})))))

  (deftest search-with-map-multi-fields
    (testing "multiple fields"
      (is (match? (m/in-any-order [{:first-name "Oliver"
                                    :last-name "Hardy"}
                                   {:first-name "Oliver"
                                    :last-name "Twist"}])
                  (search-docs store {:first-name "Oliver"})))
      (is (match? [{:first-name "Oliver"
                    :last-name "Hardy"
                    :real "True"}]
                  (search-docs store {:first-name "Oliver" :real "true"})))))

  (deftest parse-query-dsl
    (testing "Given a classic query string"
      (is (= "name:shikari name:shambhu (real:true)^2.0"
             (str (query/parse-dsl "Shikari Shambhu real:true^2" "name" (a/standard-analyzer [])))))))

  (deftest search-with-query-dsl
    (testing "Search for the Shikari using the classic query DSL"
      (is (match? [{:first-name "Shikari"
                    :last-name "Shambhu"}]
                  (search-docs store
                               (query/parse-dsl "shikari" :first-name (a/standard-analyzer [])))))
      (is (match? (m/in-any-order [{:first-name "Wonder"
                                    :last-name "Woman"}
                                   {:first-name "Pinki"
                                    :last-name "Sharma"}])
                  (search-docs store
                               (query/parse-dsl "gender:f" (a/standard-analyzer [])))))))

  (deftest paginated-results
    (let [query       {:bio #{"love" "enjoy"}}
          page-0      (search-results store query {:page 0 :results-per-page 2})
          page-1      (search-results store query {:page 1 :results-per-page 2})
          page-2      (search-results store query {:page 2 :results-per-page 2})]
      (testing "Fetching from a large (ahem!) repository, one page at a time"
        (is (= 2 (count page-0)))
        (is (= 2 (count page-1)))
        (is (= 2 (count page-2))))

      (testing "All pages have non-overlapping documents"
        (is (= 6 (count (into #{} (map :doc-id (flatten [page-0 page-1 page-2])))))))))

  (deftest search-after-results
    (with-open [search-session (lucene/open-session store)]
      (let [query  {:bio #{"love" "enjoy"}}
            page-0 (lucene/search search-session query {:results-per-page 2
                                                        :hit->doc document/document->map})
            page-1 (lucene/search search-session query {:results-per-page 2
                                                        :search-after (last page-0)
                                                        :hit->doc document/document->map})
            page-2 (lucene/search search-session query {:results-per-page 2
                                                        :search-after (last page-1)
                                                        :hit->doc document/document->map})]
        (testing "search-after returns the next page on the same reader snapshot"
          (is (= 2 (count page-0)))
          (is (= 2 (count page-1)))
          (is (= 2 (count page-2))))

        (testing "search-after pages do not overlap"
          (is (= 6 (count (into #{} (map :doc-id (flatten [page-0 page-1 page-2]))))))))))

  (deftest suggestions-with-limit-params
    (let [query    "S"
          result-1 (suggest-results store :first-name query {:max-results 2})
          result-2 (suggest-results store :first-name query {:max-results 4})]
      (testing "Asking for suggestions from a large (ahem!) repository, one page at a time"
        (is (= 2 (count result-1)))
        (is (= 4 (count result-2)))
        (is (match? (m/in-any-order [{:hit {:first-name "Suppandi"}}
                                     {:hit {:first-name "Shikari"}}
                                     {:hit {:first-name "Sabu"}}
                                     {:hit {:first-name "Stanley"}}])
                    result-2))))))

(comment
  (run-all-tests (-> *ns* str re-pattern)))
