(ns msync.lucene.query
  (:import
    [org.apache.lucene.search Query BooleanQuery$Builder BooleanClause$Occur BooleanClause]
    [clojure.lang Sequential IPersistentSet IPersistentMap IMapEntry]
    [org.apache.lucene.util QueryBuilder]
    [org.apache.lucene.analysis Analyzer]))

;; Unabashedly based on https://github.com/federkasten/clucie/blob/master/src/clucie/query.clj

(defprotocol QueryExpression
  (parse-expression [expr opts]))

(defn- query-subexp-meta-process
  "If the sub-expression is a map-entry, pick the field-name from the key.
  Otherwise, use the sub-expression as-is. It is assumed in the non map-entry case, the field-name
  is part of the input parameters - opts"
  [subexp opts]
  (if (instance? IMapEntry subexp)
    (let [[k v] subexp]
      [v (assoc opts :field-name (name k))])
    [subexp opts]))

(defn- pred-combine-subexps [subexps opts ^BooleanClause$Occur occur-condition]
  (let [qb (BooleanQuery$Builder.)]
    (doseq [q (keep (fn [e]
                      (let [[updated-e updated-opts] (query-subexp-meta-process e opts)]
                        (parse-expression updated-e updated-opts)))
                    subexps)]
      (.add qb q occur-condition))
    (.build qb)))

(extend-protocol QueryExpression

  Query
  (parse-expression [query _] query)

  Sequential
  (parse-expression [coll opts]
    (pred-combine-subexps coll opts BooleanClause$Occur/MUST))

  IPersistentSet
  (parse-expression [s opts]
    (pred-combine-subexps s opts BooleanClause$Occur/SHOULD))

  IPersistentMap
  (parse-expression [m opts]
    (pred-combine-subexps m opts BooleanClause$Occur/MUST))

  String
  (parse-expression [str-query {:keys [^Analyzer analyzer field-name query-type]}]
    {:pre [(not-empty field-name) (not (nil? analyzer))]}
    (let [builder    (QueryBuilder. analyzer)
          query-type (or query-type (if (re-find #"\s" str-query)
                                      :phrase-query
                                      :query))]
      (println (str "Query type is " query-type))
      (case query-type
        :query (.createBooleanQuery builder (name field-name) str-query)
        :phrase-query (.createPhraseQuery builder (name field-name) str-query)
        (throw (ex-info (str "Unsupported query type - " (name query-type)) {:query-type query-type}))))))