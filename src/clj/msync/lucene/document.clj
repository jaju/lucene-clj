(ns msync.lucene.document
  (:require [msync.lucene.values :as values])
  (:import [org.apache.lucene.index IndexOptions IndexableFieldType]
           [org.apache.lucene.search.suggest.document SuggestField ContextSuggestField]
           [org.apache.lucene.document FieldType Field Document]))

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

(defn- reserved-name? [field-name]
  (not (.startsWith (name field-name) suggest-field-prefix)))

(defn- ->field-factory
  "Build a reusable Lucene field constructor for a logical field."
  [field-name opts]
  {:pre [(reserved-name? field-name)]}
  (let [field-name (name field-name)
        field-type (->field-type opts)]
    (fn [^String value]
      (Field. field-name value field-type))))

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

(defn- field->kv [^Field f]
  [(-> f .name keyword) (.stringValue f)])

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

(defn- normalize-suggest-fields
  [suggest-fields]
  (reduce
    (fn [weights entry]
      (if (and (sequential? entry)
               (= 2 (count entry)))
        (assoc weights (first entry) (second entry))
        (assoc weights entry 1)))
    {}
    suggest-fields))

(defn- compile-document-options
  "Compile document options into reusable field and suggestion constructors."
  [{:keys [indexed-fields stored-fields keyword-fields suggest-fields context-fn]}]
  (let [indexed-fields              (when indexed-fields
                                      (zipmap indexed-fields (repeat :full)))
        stored-fields               (when stored-fields
                                      (into #{} stored-fields))
        keyword-fields              (into #{} keyword-fields)
        suggest-field-weights       (normalize-suggest-fields suggest-fields)
        field-options-for           (fn [field-name]
                                      {:index-type (if indexed-fields
                                                     (get indexed-fields field-name :none)
                                                     :full)
                                       :store?     (if stored-fields
                                                     (contains? stored-fields field-name)
                                                     true)
                                       :tokenize?  (not (contains? keyword-fields field-name))})
        field-factory-for           (memoize
                                      (fn [field-name]
                                        (->field-factory field-name
                                                         (field-options-for field-name))))
        suggest-field-factory-for   (into {}
                                          (map (fn [[field-name weight]]
                                                 [field-name
                                                  (->suggest-field-factory field-name weight)]))
                                          suggest-field-weights)]
    {:field-factory-for         field-factory-for
     :suggest-field-factory-for suggest-field-factory-for
     :contexts-fn               (or context-fn (constantly []))}))

(defn- encode-document
  "Encode a normalized Clojure document map as a Lucene document using compiled options."
  [doc-map {:keys [field-factory-for suggest-field-factory-for contexts-fn]}]
  (let [contexts (values/-normalize-optional-text-values :suggest-contexts
                                                         (contexts-fn doc-map))
        doc      (Document.)]
    (doseq [[field-name raw-value] doc-map]
      (let [field-values (values/-normalize-text-values field-name raw-value)]
        (add-fields! doc field-values (field-factory-for field-name))
        (when-let [suggest-field-factory (get suggest-field-factory-for field-name)]
          (add-suggest-fields! doc field-values contexts suggest-field-factory))))
    doc))

(defn map->document
  "Convert a map to a Lucene document.
  Lossy on the way back. Also, string field names come back as keywords.

  indexed-fields => fields that are fully indexed
  stored-fields => fields that are stored in the index
  keyword-fields => fields that are considered verbatim, without tokenization
  suggest-fields => fields that support suggestion-querying. This is a list consisting of a mix of field-names and [field-name weight]
                    Default weight is 1
  context-fn => a function that takes the input map and returns a list of contexts. (This needs more explanation)"
  [m doc-opts]
  (encode-document m (compile-document-options doc-opts)))

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
  [doc-opts]
  (let [compiled-options (compile-document-options doc-opts)]
    (fn [doc-map]
      (encode-document doc-map compiled-options))))

(def fn:map->document -map->document-fn)

(defn -document->map-fn
  "Build a document decoder function for the supplied projection options."
  [& opt-args]
  (fn [doc]
    (apply document->map doc opt-args)))

(def fn:document->map -document->map-fn)
