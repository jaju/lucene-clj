(ns msync.lucene
  (:require [clojure.java.io :as io]
            [msync.lucene.input-iterator]
            [msync.lucene.document :as d]
            [msync.lucene.query :as query]
            [msync.lucene.suggestions :as su])
  (:import [org.apache.lucene.store RAMDirectory Directory FSDirectory]
           [org.apache.lucene.analysis CharArraySet Analyzer]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [java.util Collection]
           [java.io File]
           [org.apache.lucene.index IndexWriterConfig IndexWriter IndexReader DirectoryReader]
           [java.util.logging Logger Level]
           [clojure.lang Sequential]
           [org.apache.lucene.search IndexSearcher Query TopDocs ScoreDoc]
           [org.apache.lucene.search.suggest.document Completion50PostingsFormat]
           [org.apache.lucene.codecs.lucene70 Lucene70Codec]
           [org.apache.lucene.analysis.miscellaneous PerFieldAnalyzerWrapper]
           [org.apache.lucene.analysis.core KeywordAnalyzer]
           [org.apache.lucene.search.suggest.analyzing AnalyzingInfixSuggester BlendedInfixSuggester]
           [org.apache.lucene.search.suggest InputIterator Lookup]))

(defonce logger (Logger/getLogger "msync.lucene"))

(defn ^Analyzer >standard-analyzer
  "StandardAnalyzer, with configurable stop-words and case-ignore behavior."
  ([] (StandardAnalyzer.))
  ([^Collection stop-words] (>standard-analyzer stop-words true))
  ([^Collection stop-words ^Boolean ignore-case?] (StandardAnalyzer. (CharArraySet. stop-words ignore-case?))))

(defn ^Analyzer >keyword-analyzer [] (KeywordAnalyzer.))

(defn ^PerFieldAnalyzerWrapper >per-field-analyzer-wrapper
  ([] (PerFieldAnalyzerWrapper. (>standard-analyzer)))
  ([^Analyzer analyzer] (PerFieldAnalyzerWrapper. analyzer))
  ([^Analyzer analyzer fa-map]
   (let [fa-map' (reduce (fn [out [k v]] (assoc out (name k) v)) {} fa-map)]
     (PerFieldAnalyzerWrapper. analyzer fa-map'))))

(defn >analyzer [] (>standard-analyzer))
(def ^:dynamic *analyzer* (>analyzer))

(defn- >filter-codec-for-suggestions
  "Creates a codec for storing fields that support returning suggestions for given prefix strings.
  Chooses the codec based on the field name prefix - which is fixed/pre-decided and not designed to be
  overridden."
  []
  (let [comp-postings-format (Completion50PostingsFormat.)]
    (proxy [Lucene70Codec] []
      (getPostingsFormatForField [field-name]
        (if (.startsWith field-name d/suggest-field-prefix)
          comp-postings-format
          (proxy-super getPostingsFormatForField field-name))))))

(defn- >index-writer-config
  "IndexWriterConfig instance."
  ([]
   (>index-writer-config *analyzer*))
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

(defmulti delete-all! (fn [arg] (class arg)))
(defmethod delete-all! Directory
  [^Directory dir]
  (let [iw (>index-writer dir)]
    (delete-all! iw)
    (.commit iw)))
(defmethod delete-all! IndexWriter
  [^IndexWriter iw]
  (.deleteAll iw))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ^Directory >memory-index
  "Lucene Directory for transient indexes"
  []
  (RAMDirectory.))

(defn ^Directory >disk-index
  "Persistent index on disk"
  [^String dir-path & {:keys [re-create?] :or {re-create? false}}]
  (let [path (.toPath ^File (io/as-file dir-path))
        dir (FSDirectory/open path)]
    (when re-create?
      (delete-all! dir))
    dir))

(defn ^Directory >index
  "Create an appropriate index - where path is either the keyword :memory, or
  a string representing the path on disk where the index is created."
  [path & {:as opts}]
  (if (= path :memory)
    (>memory-index)
    (>disk-index opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti index-all! (fn [store & _] (class store)))

(defmethod index-all! Directory
  [^Directory directory
   ^Sequential map-docs
   {:keys [analyzer]
    :or   {analyzer *analyzer*}
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
   {:keys [stored-fields indexed-fields suggest-fields string-fields context-fn]}]
  (doseq [document (map
                     #(d/map->document %
                                     {:stored-fields  stored-fields
                                      :indexed-fields indexed-fields
                                      :suggest-fields suggest-fields
                                      :context-fn     context-fn
                                      :string-fields  string-fields}) map-docs)]
    (.addDocument index-writer document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti ^:private search* #(class (first %&)))

(defmethod search* Directory
  [^Directory index-store query-form opts]
  (with-open [reader (>index-reader index-store)]
    (search* reader query-form opts)))

(defmethod search* IndexReader
  [^IndexReader index-store query-form
   {:keys [field-name results-per-page max-results analyzer page document-xformer]
    :or   {results-per-page 10
           max-results      results-per-page
           page             0
           analyzer         *analyzer*
           document-xformer identity}}]
  (let [^IndexSearcher searcher (IndexSearcher. index-store)
        field-name              (if field-name (name field-name))
        ^Query query            (query/parse query-form {:analyzer analyzer :field-name field-name})
        ^TopDocs hits           (.search searcher query (int max-results))
        start                   (* page results-per-page)
        end                     (min (+ start results-per-page) max-results (.totalHits hits))]
    (vec
      (for [^ScoreDoc hit (map (partial aget (.scoreDocs hits))
                               (range start end))]
        (let [doc-id (.doc hit)
              doc    (.doc searcher doc-id)
              score  (.score hit)]
          {:hit (document-xformer doc) :score score :doc-id doc-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn search
  ([store query-form]
   (search store query-form {}))
  ([store query-form opts]
   (search* store query-form opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti suggest
          "Return suggestions for prefix-queries. The index should have been created with
appropriate configuration for which fields should be analyzed for creating the suggestions
infrastructure."
          #(class (first %&)))

(defmethod suggest Directory

  ([directory field-name prefix-query]
   (suggest directory field-name prefix-query {}))
  ([directory field-name prefix-query opts]
   (with-open [reader (>index-reader directory)]
     (suggest reader field-name prefix-query opts))))

(defmethod suggest IndexReader
  ([reader field-name ^String prefix-query]
   (suggest reader field-name prefix-query {}))
  ([reader
    field-name
    ^String prefix-query
    {:keys [analyzer max-results document-xformer fuzzy skip-duplicates contexts]
     :or   {fuzzy            false
            skip-duplicates  false
            analyzer         *analyzer*
            max-results      10
            document-xformer identity}}]
   (let [opts {:fuzzy            fuzzy
               :skip-duplicates  skip-duplicates
               :analyzer         analyzer
               :max-results      max-results
               :document-xformer document-xformer
               :contexts         contexts}]
     (su/suggest reader (name field-name) prefix-query opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn >infix-suggester-index [path ^InputIterator doc-maps-iterator & {:keys [analyzer suggester-class]
                                                                       :or   {suggester-class :infix}}]
  (let [index (>index path :re-create? true)
        suggester (case suggester-class
                    :infix (AnalyzingInfixSuggester. index analyzer)
                    :blended-infix (BlendedInfixSuggester. index analyzer))]
    (.build suggester doc-maps-iterator)
    suggester))

(defn lookup
  "lookup - because using suggest feels wrong after looking at the underlying implementation,
  which uses lookup."
  [^Lookup suggester prefix & {:keys [contexts max-results result-xformer match-all?]
                               :or   {result-xformer identity
                                      match-all? false
                                      max-results 10}}]
  (let [results (if contexts
                  (.lookup suggester prefix contexts match-all? max-results)
                  (.lookup suggester prefix max-results match-all? false))]
    (map result-xformer results)))