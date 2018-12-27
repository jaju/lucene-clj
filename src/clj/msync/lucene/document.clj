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

(defn- ^IndexableFieldType field-type
  [{:keys [index-type store? tokenize?]
    :or   {tokenize? true store? false}}]
  (let [opts (index-options index-type IndexOptions/NONE)]
    (doto (FieldType.)
      (.setIndexOptions opts)
      (.setStored store?)
      (.setTokenized tokenize?))))

(defn- ^Field field
  "Document Field"
  [k ^String v opts]
  {:pre [(not (.startsWith (name k) suggest-field-prefix))]}
  (let [ft (field-type opts)
        v  (if (keyword? v) (name v) (str v))]
    (Field. (name k) v ft)))

(defn- ^SuggestField suggest-field
  "Document SuggestField"
  [key value contexts weight]
  (let [key (str suggest-field-prefix (name key))]
    (if (empty? contexts)
      (SuggestField. key value weight)
      (ContextSuggestField. key value weight (into-array String contexts)))))

(defn- add-fields!
  [document field-meta field-values field-creator]
  (let [field-values (if (sequential? field-values) field-values [field-values])]
    (doseq [field-value field-values]
      (.add document (field-creator field-meta field-value)))))

(defn map->document [m {:keys [keyword-fields stored-fields indexed-fields suggest-fields context-fn]}]
  "Convert a map to a Lucene document.
  Lossy on the way back. Also, string field names come back as keywords."
  (let [field-keys            (keys m)
        keyword-fields        (or keyword-fields #{})
        stored-fields         (or stored-fields #{})
        suggest-fields        (or suggest-fields {})
        indexed-fields        (or indexed-fields (zipmap (keys m) (repeat :full)))
        field-creator         (fn [k v]
                                (field k v
                                  {:index-type (get indexed-fields k :none)
                                   :store?     (contains? stored-fields k)
                                   :tokenize?  (-> k keyword-fields nil?)}))
        context-fn            (or context-fn (constantly []))
        contexts              (context-fn m)
        suggest-field-creator (fn [[field-name weight] v]
                                (let [value v]
                                  (suggest-field field-name value contexts weight)))
        doc                   (Document.)]
    (doseq [field-key field-keys]
      (add-fields! doc field-key (get m field-key) field-creator))
    (doseq [[field-key weight] suggest-fields]
      (add-fields! doc [field-key weight] (get m field-key) suggest-field-creator))
    doc))


(defn- field->kv [^Field f]
  [(-> f .name keyword) (.stringValue f)])

(defn- update-conj [m k v]
  (assoc m k (conj (m k []) v)))

(defn document->map
  "Convenience function.
  Lucene document to map. Keys are always keywords. Values come back as string.
  Only stored fields come back."
  [^Document document & {:keys [fields-to-keep multi-fields]
                         :or   {multi-fields #{} fields-to-keep :all}}]
  (let [fields-to-keep (if (= :all fields-to-keep)
                         (constantly true)
                         fields-to-keep)]
    (reduce
      (fn [m ^Field field]
        (let [[k v] (field->kv field)]
          (if (fields-to-keep k)
            (if (multi-fields k)
              (update-conj m k v)
              (assoc m k v))
            m)))
      {}
      document)))