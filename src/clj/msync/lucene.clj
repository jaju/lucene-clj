(ns msync.lucene
  (:require [clojure.java.io :as io]
            [msync.lucene
             [input-iterator]
             [document :as d]
             [query :as query]
             [suggestions :as su]
             [store :as store]
             [analyzers :as a]])
  (:import [java.util Set]
           [java.util.logging Logger]
           [org.apache.lucene.store Directory]
           [org.apache.lucene.index IndexWriter IndexReader Term]
           [org.apache.lucene.search IndexSearcher Query TopDocs ScoreDoc FuzzyQuery
                                     BooleanQuery$Builder BooleanClause$Occur]
           [org.apache.lucene.search.suggest.analyzing AnalyzingInfixSuggester BlendedInfixSuggester]
           [org.apache.lucene.search.suggest InputIterator Lookup]
           [msync.lucene.store Store]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defonce logger (Logger/getLogger "msync.lucene"))
(defonce default-analyzer (a/standard-analyzer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti index! (fn [o & _] (class o)))

(defmethod index! Store
  [^Store store doc-maps opts]
  (let [analyzer  (:analyzer store)
        directory (:directory store)
        iwc       (store/index-writer-config analyzer)]
    (with-open [iw (store/index-writer directory iwc)]
      (index! iw doc-maps (dissoc opts :analyzer)))))

(defmethod index! IndexWriter
  [^IndexWriter iw
   doc-maps
   {:keys [indexed-fields stored-fields keyword-fields suggest-fields context-fn] :as doc-opts}]
  (let [doc-maps (if (map? doc-maps) [doc-maps] doc-maps)
        doc-fn   (d/fn:map->document doc-opts)]
    (doseq [document (map doc-fn doc-maps)]
      (.addDocument iw document))))

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
  (with-open [reader (store/index-reader index-store)]
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
                                  (combine-fuzzy-queries query-form)
                                  (query/parse query-form {:analyzer analyzer :field-name field-name}))
        ^TopDocs hits           (.search searcher query (int max-results))
        start                   (* page results-per-page)
        end                     (min (+ start results-per-page) max-results (.value (.totalHits hits)))]
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
     (with-open [index-reader (store/index-reader directory)]
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
               :hit->doc         (or hit->doc identity)
               :contexts         contexts}]
     (su/suggest index-reader (name field-name) prefix-query opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-infix-suggester-index [path ^InputIterator doc-maps-iterator & {:keys [analyzer suggester-class]
                                                                             :or   {suggester-class :infix}}]
  (let [index     (store/store path :re-create? true)
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