(ns msync.lucene.document
  (:require [msync.lucene.schema :as schema]
            [msync.lucene.values :as values])
  (:import [org.apache.lucene.index IndexOptions IndexableFieldType]
           [org.apache.lucene.search.suggest.document SuggestField ContextSuggestField]
           [org.apache.lucene.document FieldType Field Field$Store Document KeywordField LongField StoredField StoredValue$Type]))

(def suggest-field-prefix "$suggest-")

(def ^:private ->index-options
  {:full IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
   true IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS

   :none IndexOptions/NONE
   :nil IndexOptions/NONE
   false IndexOptions/NONE

   :docs-freqs IndexOptions/DOCS_AND_FREQS
   :docs-freqs-pos IndexOptions/DOCS_AND_FREQS_AND_POSITIONS})

(defn- ^IndexableFieldType ->field-type
  "Each field's information is carried in its corresponding IndexableFieldType attribute. Internal detail."
  [{:keys [index-type store? tokenize?]
    :or {tokenize? true store? false}}]
  (let [index-options (->index-options index-type IndexOptions/NONE)]
    (doto (FieldType.)
      (.setIndexOptions index-options)
      (.setStored store?)
      (.setTokenized tokenize?)
      (.freeze))))

(defn- user-field-name?
  [field-name]
  (not (.startsWith (name field-name) suggest-field-prefix)))

(defn- ->field-factory
  "Build a reusable Lucene field constructor for a logical field."
  [field-name field-options]
  {:pre [(user-field-name? field-name)]}
  (let [field-name (name field-name)
        field-type (->field-type field-options)]
    (fn [^String value]
      (Field. field-name value field-type))))

(defn- ->store-option
  [stored?]
  (if stored?
    Field$Store/YES
    Field$Store/NO))

(defn- ->keyword-field-factory
  [field-name {:keys [indexed? stored?]}]
  {:pre [(user-field-name? field-name)]}
  (let [field-name   (name field-name)
        store-option (->store-option stored?)]
    (cond
      indexed?
      (fn [^String value]
        (KeywordField. field-name value store-option))

      stored?
      (fn [^String value]
        (StoredField. field-name value)))))

(defn- ->long-field-factory
  [field-name {:keys [indexed? stored?]}]
  {:pre [(user-field-name? field-name)]}
  (let [field-name   (name field-name)
        store-option (->store-option stored?)]
    (cond
      indexed?
      (fn [value]
        (LongField. field-name (long value) store-option))

      stored?
      (fn [value]
        (StoredField. field-name (long value))))))

(defn- ->boolean-field-factory
  [field-name {:keys [indexed? stored?]}]
  (let [field-factory (->keyword-field-factory field-name {:indexed? indexed? :stored? stored?})]
    (when field-factory
      (fn [value]
        (field-factory (str value))))))

(defn- ->suggest-field-factory
  "Build a reusable suggestion field constructor for a logical field."
  [field-name weight]
  (let [suggest-field-name (str suggest-field-prefix (name field-name))]
    (fn [^String value contexts]
      (if (empty? contexts)
        (SuggestField. suggest-field-name value weight)
        (ContextSuggestField. suggest-field-name value weight (into-array String contexts))))))

(defn- add-fields!
  [document field-values field-factory]
  (doseq [field-value field-values]
    (.add document (field-factory field-value))))

(defn- add-suggest-fields!
  [document field-values contexts suggest-field-factory]
  (doseq [field-value field-values]
    (.add document (suggest-field-factory field-value contexts))))

(defn- stored-field-value
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

(defn- field->kv [^Field field]
  [(-> field .name keyword) (stored-field-value field)])

(defn vecs->maps
  "Collection of vectors, with the first considered the header.
  [[field1 field2] [f11 f12] [f21 f22]] =>
  [{:field1 f11 :field2 f22} {:field1 f21 :field2 f22}]
  Returns a collection of maps, where the key is the corresponding header field."
  ([doc-vecs-with-header]
   (apply vecs->maps ((juxt first rest) doc-vecs-with-header)))
  ([header-vec doc-vecs]
   (map zipmap
     (->> header-vec
       (map keyword)
       repeat)
     doc-vecs)))

(defn- field-options
  [{:keys [type indexed? stored?]}]
  {:index-type (if indexed? :full :none)
   :store?     stored?
   :tokenize?  (= :text type)})

(defn- multi-valued-input?
  [value]
  (and (coll? value)
       (not (string? value))
       (not (map? value))))

(defn- normalize-field-values
  [field-name {:keys [type multi-valued?] :as field-spec} raw-value]
  (when (and (multi-valued-input? raw-value)
             (not multi-valued?))
    (throw (ex-info "Field value is multi-valued, but the field is not marked :multi-valued?"
                    {:field-name field-name
                     :field-spec field-spec
                     :value      raw-value})))
  (case type
    (:text :keyword) (values/-normalize-text-values field-name raw-value)
    :long            (if (multi-valued-input? raw-value)
                       (mapv #(values/-normalize-long-value field-name %) raw-value)
                       [(values/-normalize-long-value field-name raw-value)])
    :boolean         (if (multi-valued-input? raw-value)
                       (mapv #(values/-normalize-boolean-value field-name %) raw-value)
                       [(values/-normalize-boolean-value field-name raw-value)])))

(defn- compile-contexts-fn
  [context-source]
  (cond
    (nil? context-source)
    (constantly [])

    (keyword? context-source)
    (fn [doc-map]
      (values/-normalize-optional-text-values context-source
                                              (get doc-map context-source)))

    (sequential? context-source)
    (fn [doc-map]
      (into []
            (mapcat (fn [context-field-name]
                      (values/-normalize-optional-text-values context-field-name
                                                              (get doc-map context-field-name))))
            context-source))

    :else
    (fn [doc-map]
      (values/-normalize-optional-text-values :suggest-contexts
                                              (context-source doc-map)))))

(defn- compile-field-spec
  [field-name {:keys [suggest] :as field-spec}]
  {:field-spec            field-spec
   :field-factory         (case (:type field-spec)
                            :text    (when (or (:indexed? field-spec) (:stored? field-spec))
                                       (->field-factory field-name (field-options field-spec)))
                            :keyword (->keyword-field-factory field-name field-spec)
                            :long    (->long-field-factory field-name field-spec)
                            :boolean (->boolean-field-factory field-name field-spec))
   :suggest-field-factory (when suggest
                            (->suggest-field-factory field-name (:weight suggest)))
   :contexts-fn           (when suggest
                            (compile-contexts-fn (:contexts-from suggest)))})

(defn- unknown-field!
  [field-name field-encoders]
  (throw (ex-info "Document contains a field that is missing from :fields"
                  {:field-name     field-name
                   :known-fields   (keys field-encoders)
                   :indexing-style :fields})))

(defn- compile-document-options
  "Compile canonical field specs into reusable document encoders."
  [indexing-options]
  (let [field-specs (:fields (schema/-normalize-indexing-options indexing-options))]
    {:field-encoders (into {}
                           (map (fn [[field-name field-spec]]
                                  [field-name (compile-field-spec field-name field-spec)]))
                           field-specs)}))

(defn- encode-document
  "Encode a Clojure document map as a Lucene document using compiled field encoders."
  [doc-map {:keys [field-encoders]}]
  (let [doc (Document.)]
    (doseq [[field-name raw-value] doc-map]
      (let [{:keys [field-spec field-factory suggest-field-factory contexts-fn]}
            (or (get field-encoders field-name)
                (unknown-field! field-name field-encoders))
            field-values (normalize-field-values field-name field-spec raw-value)]
        (when field-factory
          (add-fields! doc field-values field-factory))
        (when suggest-field-factory
          (add-suggest-fields! doc
                               field-values
                               (contexts-fn doc-map)
                               suggest-field-factory))))
    doc))

(defn map->document
  "Convert a map to a Lucene document.
  Lossy on the way back. Also, string field names come back as keywords.
  Indexing behavior is driven by canonical field specs under :fields."
  [document-map indexing-options]
  (encode-document document-map (compile-document-options indexing-options)))

(defn document->map
  "Convenience function.
  Lucene document to map. Keys are always keywords. Values come back as string.
  Only stored fields come back."
  [^Document document & {:keys [fields-to-keep multi-fields]}]
  (let [fields-to-keep (if (nil? fields-to-keep)
                         (constantly true)
                         fields-to-keep)
        multi-fields (into #{} multi-fields)]
    (reduce
      (fn [m ^Field field]
        (let [[k v] (field->kv field)]
          (if (fields-to-keep k)
            (if (multi-fields k)
              (update m k (fnil conj []) v)
              (assoc m k v))
            m)))
      {}
      document)))

(defn -map->document-fn
  "Build a document encoder function for the supplied indexing options."
  [indexing-options]
  (let [compiled-options (compile-document-options indexing-options)]
    (fn [document-map]
      (encode-document document-map compiled-options))))

(def fn:map->document -map->document-fn)

(defn -document->map-fn
  "Build a document decoder function for the supplied projection options."
  [& opt-args]
  (fn [doc]
    (apply document->map doc opt-args)))

(def fn:document->map -document->map-fn)
