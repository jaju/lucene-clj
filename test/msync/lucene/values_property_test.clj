(ns msync.lucene.values-property-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [msync.lucene.values :as values]))

(def ^:private token-generator
  (gen/such-that seq gen/string-alphanumeric 100))

(def ^:private field-name-generator
  (gen/fmap keyword token-generator))

(def ^:private supported-scalar-generator
  (gen/one-of [token-generator
               (gen/fmap keyword token-generator)
               (gen/fmap symbol token-generator)
               gen/int
               gen/boolean
               gen/char-alphanumeric]))

(defn- expected-normalized-value
  "Mirror the public normalization contract for supported scalar values."
  [value]
  (if (instance? clojure.lang.Named value)
    (name value)
    (str value)))

(defspec normalize-text-value-stringifies-supported-scalars 100
  (prop/for-all [value supported-scalar-generator]
    (= (expected-normalized-value value)
       (values/-normalize-text-value :field value))))

(defspec normalize-text-values-preserves-multi-valued-cardinality 100
  (prop/for-all [field-name field-name-generator
                 field-values (gen/vector supported-scalar-generator 1 6)]
    (= (mapv expected-normalized-value field-values)
       (values/-normalize-text-values field-name field-values))))
