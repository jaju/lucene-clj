(ns msync.lucene
  (:require [clojure.java.io :as io]
            [msync.lucene.query :as query])
  (:import [org.apache.lucene.store RAMDirectory Directory FSDirectory]
           [org.apache.lucene.analysis CharArraySet Analyzer]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [java.util Collection]
           [java.io File]
           [org.apache.lucene.index IndexWriterConfig IndexWriter IndexableFieldType IndexOptions IndexReader DirectoryReader Term]
           [org.apache.lucene.document Field Document FieldType]
           [java.util.logging Logger Level]
           [clojure.lang Sequential]
           [org.apache.lucene.search IndexSearcher Query TopDocs ScoreDoc]
           [org.apache.lucene.search.suggest.document SuggestField Completion50PostingsFormat PrefixCompletionQuery SuggestIndexSearcher TopSuggestDocs ContextSuggestField ContextQuery CompletionAnalyzer]
           [org.apache.lucene.codecs.lucene70 Lucene70Codec]))

(defonce logger (Logger/getLogger "msync.lucene"))

(def ^:private index-options
  {:full           IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
   true            IndexOptions/DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS

   :none           IndexOptions/NONE
   :nil            IndexOptions/NONE
   false           IndexOptions/NONE

   :docs-freqs     IndexOptions/DOCS_AND_FREQS
   :docs-freqs-pos IndexOptions/DOCS_AND_FREQS_AND_POSITIONS})

(defn ^Analyzer >standard-analyzer
  "StandardAnalyzer, with configurable stop-words and case-ignore behavior."
  ([] (StandardAnalyzer.))
  ([^Collection stop-words] (>standard-analyzer stop-words true))
  ([^Collection stop-words ^Boolean ignore-case?] (StandardAnalyzer. (CharArraySet. stop-words ignore-case?))))

(def >analyzer >standard-analyzer)
(def ^:private suggest-field-prefix "$suggest-")

(defn- >filter-codec-for-suggestions
  "Creates a codec for storing fields that support returning suggestions for given prefix strings.
  Chooses the codec based on the field name prefix - which is fixed/pre-decided and not designed to be
  overridden."
  []
  (let [comp-postings-format (Completion50PostingsFormat.)]
    (proxy [Lucene70Codec] []
      (getPostingsFormatForField [field-name]
        (if (.startsWith field-name suggest-field-prefix)
          comp-postings-format
          (proxy-super getPostingsFormatForField field-name))))))

(defn- >index-writer-config
  "IndexWriteConfig instance."
  ([]
   (>index-writer-config (>analyzer)))
  ([^Analyzer analyzer]
   (let [config (IndexWriterConfig. analyzer)]
     (.setCodec config (>filter-codec-for-suggestions))
     config)))

(defn- ^IndexWriter >index-writer
  "IndexWriter instance."
  ([^Directory directory]
   (>index-writer directory (>index-writer-config)))
  ([^Directory directory
    ^IndexWriterConfig index-writer-config]
   (IndexWriter. directory index-writer-config)))

(defn- ^IndexReader >index-reader
  "An IndexReader instance."
  [^Directory directory]
  (DirectoryReader/open directory))

(defn- ^IndexableFieldType >field-type
  "FieldType information for the given field."
  [{:keys [index-type stored?]}]
  (let [index-option (index-options index-type IndexOptions/NONE)]
    (doto (FieldType.)
      (.setIndexOptions index-option)
      (.setStored stored?))))


(defn- ^Field >field
  "Document Field"
  [key value opts]
  {:pre [(not (.startsWith (name key) suggest-field-prefix))]}
  (let [^FieldType field-type (>field-type opts)
        value                 (if (keyword? value) (name value) (str value))]
    (Field. ^String (name key) ^String value field-type)))

(defn- ^SuggestField >suggest-field
  "Document SuggestField"
  [key contexts value weight]
  (let [key                        (str suggest-field-prefix (name key))
        contexts                   (if (empty? contexts) nil contexts)
        ^ContextSuggestField field (ContextSuggestField. key value weight contexts)]
    (.log logger Level/FINEST (str "Created suggest field with name " key " and value " value))
    field))

(defn map->document [m {:keys [stored-fields indexed-fields suggest-fields context-fn]}]
  "Convert a map to a Lucene document.
  Lossy on the way back. String field names come back as keywords."
  (let [field-keys            (keys m)
        stored-fields         (or stored-fields (into #{} field-keys))
        indexed-fields        (or indexed-fields (zipmap (keys m) (repeat :full)))
        suggest-fields        (or suggest-fields {})
        field-creator         (fn [k]
                                (>field k (get m k)
                                        {:index-type (get indexed-fields k false)
                                         :stored?    (contains? stored-fields k)}))
        context-fn            (or context-fn (constantly nil))
        contexts              (context-fn m)
        suggest-field-creator (fn [[field-name weight]]
                                (let [value (get m field-name)]
                                  (>suggest-field field-name contexts value weight)))
        fields                (map field-creator field-keys)
        suggest-fields        (map suggest-field-creator suggest-fields)
        doc                   (Document.)]
    (doseq [^Field field fields]
      (.add doc field))
    (doseq [^SuggestField field suggest-fields]
      (.add doc field))
    doc))

(defn ^Directory >memory-index
  "Lucene Directory for transient indexes"
  []
  (RAMDirectory.))

(defn ^Directory >disk-index
  "Persistent index on disk"
  [^String dir-path]
  (let [path (.toPath ^File (io/as-file dir-path))]
    (FSDirectory/open path)))

(defmulti index-all! (fn [store & _] (class store)))

(defmethod index-all! Directory
  [^Directory directory
   ^Sequential map-docs
   {:keys [analyzer]
    :or   {analyzer (>analyzer)}
    :as   opts}]
  (let [index-writer-config (>index-writer-config analyzer)
        index-writer        (>index-writer directory index-writer-config)]
    (try
      (index-all! index-writer map-docs (dissoc opts :analyzer))
      (.commit index-writer)
      (catch Exception e
        (.log logger Level/SEVERE
              (str *ns* " - Error in IO with index writer - " (.getMessage e))))
      (finally
        (.close index-writer)))))

(defmethod index-all! IndexWriter
  [^IndexWriter index-writer
   map-docs
   {:keys [stored-fields indexed-fields suggest-fields context-fn]}]
  (doseq [document (map
                     #(map->document %
                                     {:stored-fields  stored-fields
                                      :indexed-fields indexed-fields
                                      :suggest-fields suggest-fields
                                      :context-fn     context-fn}) map-docs)]
    (.addDocument index-writer document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn document->map [^Document document]
  "Convenience function.
  Lucene document to map. Keys are always keywords. Values come back as string.
  Only stored fields come back."
  (reduce
    (fn [m field]
      (assoc m (-> field .name keyword) (-> field .stringValue)))
    {}
    document))

(defmulti ^:private search* #(class (first %&)))

(defmethod search* Directory
  [^Directory index-store query-form opts]
  (with-open [reader (>index-reader index-store)]
    (search* reader query-form opts)))

(defmethod search* IndexReader
  [^IndexReader index-store query-form
   {:keys [field-name results-per-page max-results analyzer page]
    :or   {results-per-page 10
           max-results      results-per-page
           page             0
           analyzer         (>analyzer)}}]
  (let [^IndexSearcher searcher (IndexSearcher. index-store)
        field-name              (if field-name (name field-name))
        ^Query query            (query/parse-expression query-form {:analyzer analyzer :field-name field-name})
        ^TopDocs hits           (.search searcher query (int max-results))
        start                   (* page results-per-page)
        end                     (min (+ start results-per-page) max-results (.totalHits hits))]
    (vec
      (for [^ScoreDoc hit (map (partial aget (.scoreDocs hits))
                               (range start end))]
        (let [doc-id (.doc hit)
              doc    (.doc searcher doc-id)
              score  (.score hit)]
          {:hit doc :score score :doc-id doc-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn search [store query-form opts]
  (search* store query-form opts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti suggest #(class (first %&)))

(defmethod suggest Directory
  ([directory field prefix-query opts]
   (with-open [reader (>index-reader directory)]
     (suggest reader field prefix-query opts)))
  ([directory field prefix-query context opts]
   (with-open [reader (>index-reader directory)]
     (suggest reader field prefix-query context opts))))


(defmethod suggest IndexReader
  ([reader field ^String prefix-query {:keys [analyzer num-hits] :as opts}]
   (suggest reader field prefix-query [] opts))
  ([reader field ^String prefix-query contexts {:keys [analyzer num-hits]}]
   (let [suggest-field        (str suggest-field-prefix (name field))
         term                 (Term. suggest-field prefix-query)
         analyzer             (or analyzer (>analyzer))
         pcq                  (PrefixCompletionQuery. analyzer term)
         cq                   (ContextQuery. pcq)
         _                    (doseq [context contexts]
                                (.addContext cq context))
         suggester            (SuggestIndexSearcher. reader)
         num-hits             (min 10 (or num-hits 10))
         ^TopSuggestDocs hits (.suggest suggester cq num-hits false)]
     (vec
       (for [^ScoreDoc hit (.scoreDocs hits)]
         (let [doc-id (.doc hit)
               doc    (.doc suggester doc-id)
               score  (.score hit)]
           {:hit doc :score score :doc-id doc-id}))))))