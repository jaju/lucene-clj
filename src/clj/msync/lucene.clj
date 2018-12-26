(ns msync.lucene
  (:require [clojure.java.io :as io]
            [msync.lucene
             [input-iterator]
             [document :as d]
             [query :as query]
             [suggestions :as su]
             [utils :as utils]
             [analyzers :as a]])
  (:import [org.apache.lucene.store Directory FSDirectory MMapDirectory]
           [org.apache.lucene.analysis Analyzer]
           [java.util Set]
           [java.io File]
           [org.apache.lucene.index IndexWriterConfig IndexWriter IndexReader DirectoryReader Term]
           [java.util.logging Logger Level]
           [org.apache.lucene.search IndexSearcher Query TopDocs ScoreDoc FuzzyQuery BooleanQuery$Builder BooleanClause$Occur]
           [org.apache.lucene.search.suggest.analyzing AnalyzingInfixSuggester BlendedInfixSuggester]
           [org.apache.lucene.search.suggest InputIterator Lookup]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce logger (Logger/getLogger "msync.lucene"))
(defrecord ^:private Store [directory analyzer])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce default-analyzer (a/standard-analyzer))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-index-writer-config
  "IndexWriterConfig instance."

  ([] (create-index-writer-config default-analyzer))

  ([^Analyzer analyzer]
   (let [config (IndexWriterConfig. analyzer)]
     (.setCodec config (su/create-filter-codec-for-suggestions))
     config)))

(defn- ^IndexWriter create-index-writer
  "IndexWriter instance."
  ([^Directory directory]
   (create-index-writer directory (create-index-writer-config)))
  ([^Directory directory
    ^IndexWriterConfig index-writer-config]
   (IndexWriter. directory index-writer-config)))

(defn- ^IndexReader create-index-reader
  "An IndexReader instance."
  [^Directory directory]
  (DirectoryReader/open directory))

(defmulti delete-all! class)

(defmethod delete-all! Store
  [^Store store]
  (let [directory (:directory store)
        iw (create-index-writer directory)]
    (delete-all! iw)
    (.commit iw)))

(defmethod delete-all! IndexWriter
  [^IndexWriter iw]
  (.deleteAll iw))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- new-store [directory analyzer]
  (->Store directory analyzer))

(defn- ^Directory create-memory-index
  "Lucene Directory for transient indexes"
  []
  (let [temp-path (utils/temp-path)
        d         (MMapDirectory. temp-path)]
    (utils/delete-on-exit! d)
    d))

(defn- ^Directory create-disk-index
  "Persistent index on disk"
  [^String dir-path & {:keys [re-create?] :or {re-create? false}}]
  (let [path (.toPath ^File (io/as-file dir-path))
        dir  (FSDirectory/open path)]
    (when re-create?
      (delete-all! dir))
    dir))

(defn ^Store create-store
  "Create an appropriate index - where path is either the keyword :memory, or
  a string representing the path on disk where the index is created."
  [path & {:keys [analyzer]
           :as   opts}]
  (let [index (if (= path :memory)
                (create-memory-index)
                (create-disk-index opts))]
    (new-store index analyzer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti index! (fn [store & _] (class store)))

(defmethod index! Store
  [^Store store doc-maps opts]
  (let [analyzer            (:analyzer store)
        directory (:directory store)
        index-writer-config (create-index-writer-config analyzer)
        index-writer        (create-index-writer directory index-writer-config)]
    (try
      (index! index-writer doc-maps (dissoc opts :analyzer))
      (.commit index-writer)
      (catch Exception e
        (.log logger Level/SEVERE
          (str *ns* " - Error in IO with index writer - " (.getMessage e))))
      (finally
        (.close index-writer)))))

(defmethod index! IndexWriter
  [^IndexWriter index-writer doc-maps
   {:keys [stored-fields indexed-fields suggest-fields string-fields context-fn]
    :as   doc-opts}]
  (let [doc-maps (if (map? doc-maps) [doc-maps] doc-maps)
        doc-fn   (fn [doc-map] (d/map->document doc-map doc-opts))]
    (doseq [document (map doc-fn doc-maps)]
      (.addDocument index-writer document))))

(defn create-fuzzy-query [fld ^String val]
  (let [term (Term. ^String (name fld) val)]
    (FuzzyQuery. term)))

(defn combine-fuzzy-queries [m]
  (let [b (BooleanQuery$Builder.)]
    (doseq [[k v] m]
      (.add b (create-fuzzy-query k v) BooleanClause$Occur/SHOULD))
    (.build b)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti ^:private search* #(class (first %&)))

(defmethod search* Directory
  [^Directory index-store query-form opts]
  (with-open [reader (create-index-reader index-store)]
    (search* reader query-form opts)))

(defmethod search* IndexReader
  [^IndexReader index-store query-form
   {:keys [field-name results-per-page max-results analyzer hit->doc page fuzzy?]
    :or   {results-per-page 10
           max-results      results-per-page
           page             0
           hit->doc         identity
           fuzzy?           false}}]
  (let [^IndexSearcher searcher (IndexSearcher. index-store)
        field-name              (if field-name (name field-name))
        ^Query query            (if fuzzy?
                                  (let [term (Term. ^String field-name ^String (str query-form))]
                                    (println (str "Here with " term))
                                    (combine-fuzzy-queries query-form))
                                  (query/parse query-form {:analyzer analyzer :field-name field-name}))
        ^TopDocs hits           (.search searcher query (int max-results))
        start                   (* page results-per-page)
        end                     (min (+ start results-per-page) max-results (.totalHits hits))]
    (vec
      (for [^ScoreDoc hit (map (partial aget (.scoreDocs hits))
                            (range start end))]
        (let [doc-id (.doc hit)
              doc    (.doc searcher doc-id)
              score  (.score hit)]
          {:hit (hit->doc doc) :score score :doc-id doc-id})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn search
  ([^Store store query-form]
   (search store query-form {}))
  ([^Store store query-form opts]
   (let [{:keys [directory analyzer]} store]
     (search* directory query-form (assoc opts :analyzer analyzer)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti suggest
  "Return suggestions for prefix-queries. The index should have been created with
appropriate configuration for which fields should be analyzed for creating the suggestions
infrastructure."
  #(class (first %&)))

(defmethod suggest Store

  ([store field-name prefix-query]
   (suggest store field-name prefix-query {}))

  ([store field-name prefix-query opts]
   (let [{:keys [directory analyzer]} store]
     (with-open [index-reader (create-index-reader directory)]
       (suggest index-reader field-name prefix-query (assoc opts :analyzer analyzer))))))

(defmethod suggest IndexReader

  ([index-reader field-name ^String prefix-query]
   (suggest index-reader field-name prefix-query {}))

  ([index-reader field-name ^String prefix-query
    {:keys [analyzer max-results hit->doc fuzzy? skip-duplicates? contexts]}]
   (let [opts {:fuzzy?           (or fuzzy? false)
               :skip-duplicates? (or skip-duplicates? false)
               :analyzer         (or analyzer default-analyzer)
               :max-results      (or max-results 10)
               :hit->doc (or hit->doc identity)
               :contexts         contexts}]
     (su/suggest index-reader (name field-name) prefix-query opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-infix-suggester-index [path ^InputIterator doc-maps-iterator & {:keys [analyzer suggester-class]
                                                                             :or         {suggester-class :infix}}]
  (let [index     (create-store path :re-create? true)
        suggester (case suggester-class
                    :infix (AnalyzingInfixSuggester. index analyzer)
                    :blended-infix (BlendedInfixSuggester. index analyzer))]
    (.build suggester doc-maps-iterator)
    suggester))

(defn lookup
  "lookup - because using suggest feels wrong after looking at the underlying implementation,
  which uses lookup."
  [^Lookup suggester prefix & {:keys [^Set contexts
                                      ^int max-results
                                      result-xformer
                                      ^boolean match-all?]
                               :or   {result-xformer identity
                                      match-all?     false
                                      max-results    10}}]
  (let [results (if contexts
                  (.lookup suggester prefix contexts match-all? max-results)
                  (.lookup suggester prefix max-results match-all? false))]
    (map result-xformer results)))