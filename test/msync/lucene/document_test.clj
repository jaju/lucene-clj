(ns msync.lucene.document-test
  (:require [clojure.test :refer :all]
            [msync.lucene.document :as document]))

(deftest map->document-normalizes-supported-scalar-values
  (let [identifier (java.util.UUID/fromString "53ff6f5f-e4f2-4a5b-8a17-8a33cc0e8d61")
        doc        (document/map->document {:title  :rumours
                                            :year   1977
                                            :active true
                                            :id     identifier}
                                           {:fields {:title  {:type :keyword}
                                                     :year   {:type :long}
                                                     :active {:type :boolean}
                                                     :id     {:type :keyword}}})
        hit        (document/document->map doc)]
    (is (= {:title  "rumours"
            :year   1977
            :active "true"
            :id     (str identifier)}
           hit))))

(deftest map->document-rejects-nil-values
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"nil values are not indexed or queried"
        (document/map->document {:title nil}
                                {:fields {:title {:type :text}}}))))

(deftest map->document-rejects-nested-maps
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"nested maps are not supported"
        (document/map->document {:title {:nested "value"}}
                                {:fields {:title {:type :text}}}))))

(deftest map->document-rejects-multi-values-on-single-valued-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"not marked :multi-valued"
        (document/map->document {:tags ["rock" "pop"]}
                                {:fields {:tags {:type :keyword}}}))))

(deftest map->document-rejects-non-long-values-for-long-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"expected an integer value for a :long field"
        (document/map->document {:year "1977"}
                                {:fields {:year {:type :long}}}))))

(deftest map->document-rejects-non-boolean-values-for-boolean-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"expected true or false for a :boolean field"
        (document/map->document {:active "true"}
                                {:fields {:active {:type :boolean}}}))))
