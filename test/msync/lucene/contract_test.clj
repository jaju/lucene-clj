(ns msync.lucene.contract-test
  (:require [clojure.test :refer :all]
            [msync.lucene :as lucene]
            [msync.lucene.analyzers :as analyzers]
            [msync.lucene.document :as document]
            [msync.lucene.tests-common :as common]))

(defn- create-store
  [docs doc-opts analyzer]
  (let [store (lucene/create-index! :type :memory :analyzer analyzer)]
    (lucene/index! store docs doc-opts)
    store))

(deftest create-index-validates-required-options
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"requires :type to be one of :memory or :disk"
        (lucene/create-index! :type :ram :analyzer common/default-analyzer)))
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"requires a non-blank :path"
        (lucene/create-index! :type :disk :path "" :analyzer common/default-analyzer)))
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"requires a non-nil :analyzer"
        (lucene/create-index! :type :memory))))

(deftest search-validates-query-preconditions
  (let [store (common/create-sample-store)]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires :field-name"
          (lucene/search store "shikari")))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires a field-to-term map query"
          (lucene/search store "shikari" {:field-name :first-name :fuzzy? true})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires :results-per-page to be a positive integer"
          (lucene/search store {:first-name "Oliver"} {:results-per-page 0})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires :page to be a natural integer"
          (lucene/search store {:first-name "Oliver"} {:page -1})))))

(deftest scalar-values-are-searchable-after-normalization
  (let [analyzer (analyzers/keyword-analyzer)
        store    (create-store [{:title  :rumours
                                 :year   1977
                                 :rating 4.5
                                 :active true}]
                               {:fields {:title  {:type :keyword}
                                         :year   {:type :long}
                                         :rating {:type :double}
                                         :active {:type :boolean}}}
                               analyzer)]
    (is (= 1 (count (lucene/search store {:title :rumours}))))
    (is (= 1 (count (lucene/search store {:year 1977}))))
    (is (= 1 (count (lucene/search store {:rating 4.5}))))
    (is (= 1 (count (lucene/search store {:active true}))))))

(deftest typed-hit-projections-can-decode-stored-values-with-field-specs
  (let [fields   {:year {:type :long}
                  :rating {:type :double}
                  :active {:type :boolean}}
        analyzer (analyzers/keyword-analyzer)
        store    (create-store [{:year 1977 :rating 4.5 :active true}]
                               {:fields fields}
                               analyzer)
        hits     (lucene/search store
                                {:active true}
                                {:hit->doc (document/fn:document->map :field-specs fields)})]
    (is (= [{:active true :rating 4.5 :year 1977}]
           (mapv :hit hits)))))

(deftest typed-fields-support-exact-queries-after-reopening-an-index
  (let [index-path (str (java.nio.file.Files/createTempDirectory
                          "lucene-clj-typed-"
                          (make-array java.nio.file.attribute.FileAttribute 0)))
        docs       [{:year 1977 :active true}
                    {:year 1980 :active false}]
        fields     {:fields {:year   {:type :long}
                             :active {:type :boolean}}}
        analyzer   (analyzers/keyword-analyzer)]
    (lucene/index! (lucene/create-index! :type :disk
                                         :path index-path
                                         :analyzer analyzer)
                   docs
                   fields)
    (let [reopened-store (lucene/create-index! :type :disk
                                               :path index-path
                                               :analyzer analyzer)]
      (is (= 1 (count (lucene/search reopened-store {:year 1977}))))
      (is (= 1 (count (lucene/search reopened-store {:active false})))))))

(deftest typed-fields-reject-incompatible-query-values
  (let [analyzer (analyzers/keyword-analyzer)
        store    (create-store [{:year 1977 :rating 4.5 :active true}]
                               {:fields {:year   {:type :long}
                                         :rating {:type :double}
                                         :active {:type :boolean}}}
                               analyzer)]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"Numeric query values require a :long or :double field definition"
          (lucene/search store {:title 1977})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"expected an integer value for a :long field"
          (lucene/search store {:year "1977"})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"expected a numeric value for a :double field"
          (lucene/search store {:rating "4.5"})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"expected true or false for a :boolean field"
          (lucene/search store {:active "true"})))))

(deftest suggest-respects-max-results-without-a-hidden-cap
  (let [analyzer (analyzers/keyword-analyzer)
        docs     (mapv (fn [n] {:name (str "sample-" n)}) (range 15))
        store    (create-store docs {:fields {:name {:type    :keyword
                                                     :suggest {:weight 1}}}}
                               analyzer)
        results  (lucene/suggest store :name "sample-" {:max-results 12
                                                        :hit->doc document/document->map})]
    (is (= 12 (count results)))))

(deftest suggest-validates-max-results
  (let [store (common/create-sample-store)]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires :max-results to be a positive integer"
          (lucene/suggest store :first-name "S" {:max-results 0})))))

(deftest index-requires-canonical-field-specs
  (let [store (lucene/create-index! :type :memory :analyzer common/default-analyzer)
        docs  [{:title "Rumours"}]]
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires a :fields map"
          (lucene/index! store docs {})))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"no longer accepts bucketed field options"
          (lucene/index! store docs {:stored-fields [:title]})))))
