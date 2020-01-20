(ns msync.lucene.query
  (:import
    [org.apache.lucene.search Query BooleanQuery$Builder BooleanClause$Occur FuzzyQuery]
    [clojure.lang Sequential IPersistentSet IPersistentMap IMapEntry]
    [org.apache.lucene.util QueryBuilder]
    [org.apache.lucene.analysis Analyzer]
    [org.apache.lucene.queryparser.classic QueryParser]
    [org.apache.lucene.index Term]))

;; Unabashedly based on https://github.com/federkasten/clucie/blob/master/src/clucie/query.clj

(defprotocol QueryExpression
  (parse [expr] [expr opts]))

(defn- query-subexp-meta-process
  "If the sub-expression is a map-entry, pick the field-name from the key.
  Otherwise, use the sub-expression as-is. It is assumed in the non map-entry case, the field-name
  is part of the input parameters - opts"
  [subexp opts]
  (if (instance? IMapEntry subexp)
    (let [[k v] subexp]
      [v (assoc opts :field-name (name k))])
    [subexp opts]))

(defn- combine-subexps [subexps opts ^BooleanClause$Occur occur-condition]
  (let [qb (BooleanQuery$Builder.)]
    (doseq [q (keep (fn [e]
                      (let [[updated-e updated-opts] (query-subexp-meta-process e opts)]
                        (parse updated-e updated-opts)))
                    subexps)]
      (.add qb q occur-condition))
    (.build qb)))

(extend-protocol QueryExpression

  Query
  (parse [query] query)
  (parse [query _] query)

  Sequential
  (parse [subexps-coll opts]
    (combine-subexps subexps-coll opts BooleanClause$Occur/MUST))

  IPersistentSet
  (parse [subexps-set opts]
    (combine-subexps subexps-set opts BooleanClause$Occur/SHOULD))

  IPersistentMap
  (parse [field-wise-supexps opts]
    (combine-subexps field-wise-supexps opts BooleanClause$Occur/MUST))

  String
  (parse [str-query {:keys [^Analyzer analyzer field-name query-type]}]
    (let [builder    (QueryBuilder. analyzer)
          query-type (or query-type (if (re-find #"\s" str-query)
                                      :phrase-query
                                      :query))]
      (case query-type
        :query (.createBooleanQuery builder (name field-name) str-query)
        :phrase-query (.createPhraseQuery builder (name field-name) str-query)
        (throw (ex-info (str "Unsupported query type - " (name query-type)) {:query-type query-type}))))))

(defn ^Query parse-dsl
  ([^String dsl ^Analyzer analyzer]
   (parse-dsl dsl "" analyzer))
  ([^String dsl ^String default-field-name ^Analyzer analyzer]
   (let [default-field-name (name default-field-name)
         ^QueryParser qp   (QueryParser. default-field-name analyzer)]
     (doto qp
       (.setSplitOnWhitespace true)
       (.setAutoGeneratePhraseQueries true))
     (.parse qp dsl))))

(defn create-fuzzy-query [fld ^String val]
  (let [term (Term. ^String (name fld) val)]
    (FuzzyQuery. term)))

(defn combine-fuzzy-queries [m]
  (let [b (BooleanQuery$Builder.)]
    (doseq [[k v] m]
      (.add b (create-fuzzy-query k v) BooleanClause$Occur/SHOULD))
    (.build b)))
