(ns msync.lucene.document-test
  (:require [clojure.test :refer :all]
            [msync.lucene.document :as document]))

(deftest map->document-normalizes-supported-scalar-values
  (let [identifier (java.util.UUID/fromString "53ff6f5f-e4f2-4a5b-8a17-8a33cc0e8d61")
        published-at (java.time.Instant/parse "1977-02-04T00:00:00Z")
        field-specs {:title  {:type :keyword}
                     :year   {:type :long}
                     :rating {:type :double}
                     :published-at {:type :instant}
                     :active {:type :boolean}
                     :id     {:type :keyword}}
        doc        (document/map->document {:title  :rumours
                                            :year   1977
                                            :rating 4.5
                                            :published-at published-at
                                            :active true
                                            :id     identifier}
                                           {:fields field-specs})
        hit        (document/document->map doc :field-specs field-specs)]
    (is (= {:title  "rumours"
            :year   1977
            :rating 4.5
            :published-at published-at
            :active true
            :id     (str identifier)}
           hit))))

(deftest document->map-leaves-boolean-values-as-stored-strings-without-field-specs
  (let [doc (document/map->document {:active false}
                                    {:fields {:active {:type :boolean}}})]
    (is (= {:active "false"}
           (document/document->map doc)))))

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

(deftest map->document-rejects-non-double-values-for-double-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"expected a numeric value for a :double field"
        (document/map->document {:rating "4.5"}
                                {:fields {:rating {:type :double}}}))))

(deftest map->document-rejects-non-instant-values-for-instant-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"expected a java.time.Instant or java.util.Date for an :instant field"
        (document/map->document {:published-at "1977-02-04T00:00:00Z"}
                                {:fields {:published-at {:type :instant}}}))))

(deftest map->document-rejects-non-boolean-values-for-boolean-fields
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"expected true or false for a :boolean field"
        (document/map->document {:active "true"}
                                {:fields {:active {:type :boolean}}}))))
