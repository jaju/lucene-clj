(ns msync.lucene.field-types
  (:require [msync.lucene.values :as values])
  (:import [org.apache.lucene.document DoubleField Field Field$Store FieldType LongField StoredField StoredValue$Type StringField]
           [org.apache.lucene.index IndexOptions IndexableFieldType Term]
           [org.apache.lucene.search TermQuery]))

(def ^:private indexed-text-options
  IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)

(defn- ->store-option
  [stored?]
  (if stored?
    Field$Store/YES
    Field$Store/NO))

(defn- ->text-field-type
  [{:keys [indexed? stored?]}]
  (doto (FieldType.)
    (.setIndexOptions (if indexed?
                        indexed-text-options
                        IndexOptions/NONE))
    (.setStored stored?)
    (.setTokenized true)
    (.freeze)))

(defn- ->text-field-factory
  [field-name {:keys [indexed? stored?] :as field-spec}]
  (when (or indexed? stored?)
    (let [lucene-field-name (name field-name)
          ^IndexableFieldType field-type (->text-field-type field-spec)]
      (fn [^String value]
        (Field. lucene-field-name value field-type)))))

(defn- ->exact-string-field-factory
  [field-name {:keys [indexed? stored?]}]
  (let [lucene-field-name (name field-name)
        store-option      (->store-option stored?)]
    (cond
      indexed?
      (fn [^String value]
        (StringField. lucene-field-name value store-option))

      stored?
      (fn [^String value]
        (StoredField. lucene-field-name value)))))

(defn- ->long-field-factory
  [field-name {:keys [indexed? stored?]}]
  (let [lucene-field-name (name field-name)
        store-option      (->store-option stored?)]
    (cond
      indexed?
      (fn [value]
        (LongField. lucene-field-name (long value) store-option))

      stored?
      (fn [value]
        (StoredField. lucene-field-name (long value))))))

(defn- ->double-field-factory
  [field-name {:keys [indexed? stored?]}]
  (let [lucene-field-name (name field-name)
        store-option      (->store-option stored?)]
    (cond
      indexed?
      (fn [value]
        (DoubleField. lucene-field-name (double value) store-option))

      stored?
      (fn [value]
        (StoredField. lucene-field-name (double value))))))

(defn- ->boolean-field-factory
  [field-name field-spec]
  (let [field-factory (->exact-string-field-factory field-name field-spec)]
    (when field-factory
      (fn [value]
        (field-factory (str value))))))

(defn- raw-stored-field-value
  [^Field field]
  (let [stored-value (.storedValue field)]
    (if (nil? stored-value)
      (.stringValue field)
      (condp = (.getType stored-value)
        StoredValue$Type/INTEGER (.getIntValue stored-value)
        StoredValue$Type/LONG (.getLongValue stored-value)
        StoredValue$Type/FLOAT (.getFloatValue stored-value)
        StoredValue$Type/DOUBLE (.getDoubleValue stored-value)
        StoredValue$Type/STRING (.getStringValue stored-value)
        StoredValue$Type/BINARY (.getBinaryValue stored-value)
        StoredValue$Type/DATA_INPUT (.getDataInputValue stored-value)))))

(defn- multi-valued-input?
  [value]
  (and (coll? value)
       (not (string? value))
       (not (map? value))))

(defn- ensure-field-cardinality!
  [field-name {:keys [multi-valued?] :as field-spec} raw-value]
  (when (and (multi-valued-input? raw-value)
             (not multi-valued?))
    (throw (ex-info "Field value is multi-valued, but the field is not marked :multi-valued?"
                    {:field-name field-name
                     :field-spec field-spec
                     :value      raw-value}))))

(defn- normalize-long-values
  [field-name raw-value]
  (if (multi-valued-input? raw-value)
    (mapv #(values/-normalize-long-value field-name %) raw-value)
    [(values/-normalize-long-value field-name raw-value)]))

(defn- normalize-double-values
  [field-name raw-value]
  (if (multi-valued-input? raw-value)
    (mapv #(values/-normalize-double-value field-name %) raw-value)
    [(values/-normalize-double-value field-name raw-value)]))

(defn- normalize-boolean-values
  [field-name raw-value]
  (if (multi-valued-input? raw-value)
    (mapv #(values/-normalize-boolean-value field-name %) raw-value)
    [(values/-normalize-boolean-value field-name raw-value)]))

(defn- compile-value-normalizer
  [field-name {:keys [type] :as field-spec}]
  (let [base-normalizer (case type
                          (:text :keyword) #(values/-normalize-text-values field-name %)
                          :long            #(normalize-long-values field-name %)
                          :double          #(normalize-double-values field-name %)
                          :boolean         #(normalize-boolean-values field-name %))]
    (fn [raw-value]
      (ensure-field-cardinality! field-name field-spec raw-value)
      (base-normalizer raw-value))))

(defn -compile-field-codec
  "Compile the encoding behavior for a canonical field spec."
  [field-name {:keys [type] :as field-spec}]
  {:field-spec       field-spec
   :normalize-values (compile-value-normalizer field-name field-spec)
   :field-factory    (case type
                       :text    (->text-field-factory field-name field-spec)
                       :keyword (->exact-string-field-factory field-name field-spec)
                       :long    (->long-field-factory field-name field-spec)
                       :double  (->double-field-factory field-name field-spec)
                       :boolean (->boolean-field-factory field-name field-spec))})

(defn -exact-query
  "Build an exact Lucene query for a canonical field spec when the field type supports it."
  [field-name field-spec value]
  (when field-spec
    (case (:type field-spec)
      :keyword (TermQuery. (Term. (name field-name)
                                  (values/-normalize-text-value field-name value)))
      :boolean (TermQuery. (Term. (name field-name)
                                  (str (values/-normalize-boolean-value field-name value))))
      :long    (LongField/newExactQuery (name field-name)
                                        (values/-normalize-long-value field-name value))
      :double  (DoubleField/newExactQuery (name field-name)
                                          (values/-normalize-double-value field-name value))
      nil)))

(defn -stored-field-value
  "Decode a stored Lucene field value. When a field spec is supplied, typed values are decoded explicitly."
  ([^Field field]
   (raw-stored-field-value field))
  ([field-spec ^Field field]
   (let [raw-value (raw-stored-field-value field)]
     (case (:type field-spec)
       :boolean (case raw-value
                  "true" true
                  "false" false
                  raw-value)
       raw-value))))
