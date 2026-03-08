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

(defn- query-form-shape
  "Replace every scalar leaf with ::leaf so shape comparisons ignore concrete values."
  [query-form]
  (cond
    (map? query-form)
    (into (empty query-form)
          (map (fn [[field-name value]]
                 [field-name (query-form-shape value)]))
          query-form)

    (set? query-form)
    (into #{} (map query-form-shape) query-form)

    (sequential? query-form)
    (mapv query-form-shape query-form)

    :else
    ::leaf))

(defn- string-leaves?
  "Return true when every scalar leaf in a normalized query form is a string."
  [query-form]
  (cond
    (map? query-form)
    (every? string-leaves? (vals query-form))

    (set? query-form)
    (every? string-leaves? query-form)

    (sequential? query-form)
    (every? string-leaves? query-form)

    :else
    (string? query-form)))

(def query-form-generator
  (gen/recursive-gen
    (fn [inner]
      (gen/one-of [(gen/vector inner 0 4)
                   (gen/set inner {:max-elements 4})
                   (gen/map field-name-generator inner {:max-elements 4})]))
    supported-scalar-generator))

(defspec normalize-text-value-stringifies-supported-scalars 100
  (prop/for-all [value supported-scalar-generator]
    (= (expected-normalized-value value)
       (values/-normalize-text-value :field value))))

(defspec normalize-text-values-preserves-multi-valued-cardinality 100
  (prop/for-all [field-name field-name-generator
                 field-values (gen/vector supported-scalar-generator 1 6)]
    (= (mapv expected-normalized-value field-values)
       (values/-normalize-text-values field-name field-values))))

(defspec normalize-query-form-preserves-shape-and-stringifies-leaves 100
  (prop/for-all [query-form query-form-generator]
    (let [normalized-query (values/-normalize-query-form query-form)]
      (and (= (query-form-shape query-form)
              (query-form-shape normalized-query))
           (string-leaves? normalized-query)))))
