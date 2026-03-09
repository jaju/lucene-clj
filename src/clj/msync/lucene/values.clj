(ns msync.lucene.values
  (:import [clojure.lang Named]
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

(defn -normalize-long-value
  "Normalize an integer value into a Java long for :long fields."
  [field-name value]
  (cond
    (nil? value)
    (invalid-text-value! field-name value "nil values are not indexed or queried")

    (integer? value)
    (try
      (long value)
      (catch ArithmeticException _
        (invalid-text-value! field-name value "integer value is outside the signed 64-bit range")))

    :else
    (invalid-text-value! field-name value "expected an integer value for a :long field")))

(defn -normalize-boolean-value
  "Normalize a boolean value for :boolean fields."
  [field-name value]
  (cond
    (nil? value)
    (invalid-text-value! field-name value "nil values are not indexed or queried")

    (instance? Boolean value)
    value

    :else
    (invalid-text-value! field-name value "expected true or false for a :boolean field")))

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
