(ns msync.lucene

  (:require [clojure.java.io :as io]
            [msync.lucene.document :as d]
            [msync.lucene.query :as query])

  (:import [org.apache.lucene.store RAMDirectory Directory FSDirectory]
           [org.apache.lucene.analysis CharArraySet Analyzer]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [java.util Collection]
           [java.io File]
           [org.apache.lucene.index IndexWriterConfig IndexWriter IndexReader DirectoryReader Term]
           [java.util.logging Logger Level]
           [clojure.lang Sequential]
           [org.apache.lucene.search IndexSearcher Query TopDocs ScoreDoc]
           [org.apache.lucene.search.suggest.document Completion50PostingsFormat TopSuggestDocs
                                                      PrefixCompletionQuery SuggestIndexSearcher ContextQuery]
           [org.apache.lucene.codecs.lucene70 Lucene70Codec]
           [org.apache.lucene.analysis.miscellaneous PerFieldAnalyzerWrapper]
           [org.apache.lucene.analysis.core KeywordAnalyzer]))

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
  ([^Analyzer analyzer fa-map] (PerFieldAnalyzerWrapper. analyzer fa-map)))

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
   {:keys [stored-fields indexed-fields suggest-fields context-fn]}]
  (doseq [document (map
                     #(d/map->document %
                                     {:stored-fields  stored-fields
                                      :indexed-fields indexed-fields
                                      :suggest-fields suggest-fields
                                      :context-fn     context-fn}) map-docs)]
    (.addDocument index-writer document)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
           analyzer         *analyzer*}}]
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
          {:hit doc :score score :doc-id doc-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn search
  ([store query-form]
   (search store query-form {}))
  ([store query-form opts]
   (search* store query-form opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti suggest
          "Return suggestions for prefix-queries. The index should have been created with
appropriate configuration for which fields should be analyzed for creating the suggestions
infrastructure."
          #(class (first %&)))

(defmethod suggest Directory

  ([directory field prefix-query]
   (suggest directory field prefix-query {}))
  ([directory field prefix-query opts]
   (with-open [reader (>index-reader directory)]
     (suggest reader field prefix-query opts))))

(defmethod suggest IndexReader
  ([reader field ^String prefix-query]
   (suggest reader field prefix-query {}))
  ([reader field ^String prefix-query {:keys [contexts analyzer max-results]}]
   (let [suggest-field        (str d/suggest-field-prefix (name field))
         term                 (Term. suggest-field prefix-query)
         analyzer             (or analyzer *analyzer*)
         pcq                  (PrefixCompletionQuery. analyzer term)
         cq                   (ContextQuery. pcq)
         contexts             (or contexts [])
         _                    (doseq [context contexts]
                                (.addContext cq context))
         suggester            (SuggestIndexSearcher. reader)
         num-hits             (min 10 (or max-results 10))
         ^TopSuggestDocs hits (.suggest suggester cq num-hits false)]
     (vec
       (for [^ScoreDoc hit (.scoreDocs hits)]
         (let [doc-id (.doc hit)
               doc    (.doc suggester doc-id)
               score  (.score hit)]
           {:hit doc :score score :doc-id doc-id}))))))