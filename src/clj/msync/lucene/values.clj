(ns msync.lucene.values
  (:import [clojure.lang Named]
           [org.apache.lucene.search Query]
           [java.net URI]
           [java.time.temporal TemporalAccessor]
           [java.util UUID]))

(defn- invalid-text-value!
  [field-name value reason]
  (throw
    (ex-info
      (str "Cannot normalize value for field " (pr-str field-name) ": " reason)
      {:field-name  field-name
       :value       value
       :value-class (some-> value class str)
       :reason      reason})))

(defn -normalize-text-value
  "Normalize a scalar Clojure value into the string form used by lucene-clj text fields and queries."
  [field-name value]
  (cond
    (nil? value)
    (invalid-text-value! field-name value "nil values are not indexed or queried")

    (string? value)
    value

    (instance? Named value)
    (name value)

    (or (char? value)
        (number? value)
        (boolean? value)
        (uuid? value)
        (inst? value)
        (instance? URI value)
        (instance? TemporalAccessor value))
    (str value)

    :else
    (invalid-text-value! field-name value "unsupported scalar type")))

(defn -normalize-text-values
  "Normalize a scalar or collection of values into Lucene-ready strings.
  Collections are treated as multi-valued fields; maps are rejected."
  [field-name value]
  (cond
    (nil? value)
    (invalid-text-value! field-name value "nil values are not indexed or queried")

    (map? value)
    (invalid-text-value! field-name value "nested maps are not supported")

    (and (coll? value) (not (string? value)))
    (mapv #(-normalize-text-value field-name %) value)

    :else
    [(-normalize-text-value field-name value)]))

(defn -normalize-optional-text-values
  "Normalize optional multi-valued text metadata, returning an empty vector for nil."
  [field-name value]
  (if (nil? value)
    []
    (-normalize-text-values field-name value)))

(defn -normalize-query-form
  "Recursively normalize a public query form so that all scalar leaf values are strings."
  [query-form]
  (cond
    (instance? Query query-form)
    query-form

    (map? query-form)
    (into (empty query-form)
          (map (fn [[field-name value]]
                 [field-name (-normalize-query-form value)]))
          query-form)

    (set? query-form)
    (into #{} (map -normalize-query-form) query-form)

    (sequential? query-form)
    (mapv -normalize-query-form query-form)

    :else
    (-normalize-text-value :query query-form)))
