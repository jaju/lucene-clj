(ns msync.lucene.document
  (:require [msync.lucene.field-types :as field-types]
            [msync.lucene.schema :as schema]
            [msync.lucene.values :as values])
  (:import [org.apache.lucene.search.suggest.document SuggestField ContextSuggestField]
           [org.apache.lucene.document Field Document]))

(def suggest-field-prefix "$suggest-")

(defn- user-field-name?
  [field-name]
  (not (.startsWith (name field-name) suggest-field-prefix)))

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

(defn- field->entry
  [field-specs ^Field field]
  (let [field-name (-> field .name keyword)
        field-spec (schema/-field-spec field-specs field-name)]
    [field-name (field-types/-stored-field-value field-spec field)]))

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
  (assoc (field-types/-compile-field-codec field-name field-spec)
         :suggest-field-factory (when suggest
                                  (->suggest-field-factory field-name (:weight suggest)))
         :contexts-fn           (when suggest
                                  (compile-contexts-fn (:contexts-from suggest)))))

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
        (let [{:keys [normalize-values field-factory suggest-field-factory contexts-fn]}
            (or (get field-encoders field-name)
                (unknown-field! field-name field-encoders))
            field-values (normalize-values raw-value)]
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
  Lucene document to map. Keys are always keywords. Only stored fields come back.
  Pass :field-specs to decode typed stored values such as booleans."
  [^Document document & {:keys [field-specs fields-to-keep multi-fields]}]
  (let [fields-to-keep (if (nil? fields-to-keep)
                         (constantly true)
                         fields-to-keep)
        multi-fields (into #{} multi-fields)]
    (reduce
      (fn [m ^Field field]
        (let [[k v] (field->entry field-specs field)]
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
