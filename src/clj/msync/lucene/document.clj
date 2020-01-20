(ns msync.lucene.document
  (:import [org.apache.lucene.index IndexOptions IndexableFieldType]
           [org.apache.lucene.search.suggest.document SuggestField ContextSuggestField]
           [org.apache.lucene.document FieldType Field Document]))

(def suggest-field-prefix "$suggest-")

(def ^:private index-options
  {:full           IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
   true            IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS

   :none           IndexOptions/NONE
   :nil            IndexOptions/NONE
   false           IndexOptions/NONE

   :docs-freqs     IndexOptions/DOCS_AND_FREQS
   :docs-freqs-pos IndexOptions/DOCS_AND_FREQS_AND_POSITIONS})

(defn- ^IndexableFieldType ->field-type
  "Each field's information is carried in its corresponding IndexableFieldType attribute. Internal detail."
  [{:keys [index-type store? tokenize?]
    :or   {tokenize? true store? false}}]
  (let [opts (index-options index-type IndexOptions/NONE)]
    (doto (FieldType.)
      (.setIndexOptions opts)
      (.setStored store?)
      (.setTokenized tokenize?))))

(defn- reserved-name? [field-name]
  (not (.startsWith (name field-name) suggest-field-prefix)))

(defn- ^Field ->field
  "Document Field.
  TBD: Support values other than as strings. Currently, everything is converted to a string."
  [k ^String v opts]
  {:pre [(reserved-name? k)]}
  (Field. (name k) (name v) (->field-type opts)))

(defn- ^SuggestField ->suggest-field
  "Document SuggestField"
  [key ^String value contexts weight]
  (let [key (str suggest-field-prefix (name key))]
    (if (empty? contexts)
      (SuggestField. key value weight)
      (ContextSuggestField. key value weight (into-array String contexts)))))

(defn- add-fields!
  [document field-name field-values field-creator]
  (let [field-values (if (sequential? field-values) field-values [field-values])]
    (doseq [field-value field-values]
      (.add document (field-creator field-name field-value)))))

(defn- field->kv [^Field f]
  [(-> f .name keyword) (.stringValue f)])

(defn- assoc-conj [m k v]
  (assoc m k (conj (m k []) v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn vecs->maps
  "Collection of vectors, with the first considered the header.
  [[field1 field2] [f11 f12] [f21 f22]] =>
  [{:field1 f11 :field2 f22} {:field1 f21 :field2 f22}]
  Returns a collection of maps, where the key is the corresponding header field."
  ([doc-vecs-with-header]
   (apply vecs->maps ((juxt first rest) doc-vecs-with-header)))
  ([header-vec doc-vecs]
   (map zipmap (->> header-vec
                    (map keyword)
                    repeat)
        doc-vecs)))

(defn map->document [m {:keys [indexed-fields stored-fields keyword-fields suggest-fields context-fn]}]
  "Convert a map to a Lucene document.
  Lossy on the way back. Also, string field names come back as keywords."
  (let [field-names           (keys m)
        keyword-fields        (into #{} keyword-fields)
        stored-fields         (into #{} (or stored-fields field-names))
        suggest-fields        (reduce
                                (fn [m e]
                                  (if (and (sequential? e)
                                           (= 2 (count e)))
                                    (assoc m (first e) (second e))
                                    (assoc m e 1)))
                                {}
                                suggest-fields)
        indexed-fields        (zipmap (or indexed-fields field-names) (repeat :full))
        field-creator         (fn [k v]
                                (->field k v
                                         {:index-type (get indexed-fields k :none)
                                          :store?     (contains? stored-fields k)
                                          :tokenize?  (-> k keyword-fields nil?)}))
        context-fn            (or context-fn (constantly []))
        contexts              (context-fn m)
        suggest-field-creator (fn [[field-name weight] v]
                                (let [value v]
                                  (->suggest-field field-name value contexts weight)))
        doc                   (Document.)]
    (doseq [k field-names]
      (add-fields! doc k (get m k) field-creator))
    (doseq [[field-key weight] suggest-fields]
      (add-fields! doc [field-key weight] (get m field-key) suggest-field-creator))
    doc))


(defn document->map
  "Convenience function.
  Lucene document to map. Keys are always keywords. Values come back as string.
  Only stored fields come back."
  [^Document document & {:keys [fields-to-keep multi-fields]}]
  (let [fields-to-keep (if (nil? fields-to-keep)
                         (constantly true)
                         fields-to-keep)
        multi-fields   (into #{} multi-fields)]
    (reduce
      (fn [m ^Field field]
        (let [[k v] (field->kv field)]
          (if (fields-to-keep k)
            (if (multi-fields k)
              (assoc-conj m k v)
              (assoc m k v))
            m)))
      {}
      document)))

(defn fn:map->document [doc-opts]
  (fn [doc-map]
    (map->document doc-map doc-opts)))

(defn fn:document->map [& opt-args]
  (fn [doc]
    (apply document->map doc opt-args)))
