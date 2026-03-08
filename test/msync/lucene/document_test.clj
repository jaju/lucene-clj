(ns msync.lucene.document-test
  (:require [clojure.test :refer :all]
            [msync.lucene.document :as document]))

(deftest map->document-normalizes-supported-scalar-values
  (let [identifier (java.util.UUID/fromString "53ff6f5f-e4f2-4a5b-8a17-8a33cc0e8d61")
        doc        (document/map->document {:title  :rumours
                                            :year   1977
                                            :active true
                                            :id     identifier}
                                           {:stored-fields [:title :year :active :id]})
        hit        (document/document->map doc)]
    (is (= {:title  "rumours"
            :year   "1977"
            :active "true"
            :id     (str identifier)}
           hit))))

(deftest map->document-rejects-nil-values
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"nil values are not indexed or queried"
        (document/map->document {:title nil} {:stored-fields [:title]}))))

(deftest map->document-rejects-nested-maps
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"nested maps are not supported"
        (document/map->document {:title {:nested "value"}} {:stored-fields [:title]}))))
