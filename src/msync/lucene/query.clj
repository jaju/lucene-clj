(ns msync.lucene.query
  (:import
    [org.apache.lucene.search Query BooleanQuery BooleanQuery$Builder BooleanClause$Occur BooleanClause]
    [clojure.lang Sequential IPersistentSet IPersistentMap]
    [org.apache.lucene.util QueryBuilder]
    [org.apache.lucene.analysis Analyzer]))

;; Unabashedly based on https://github.com/federkasten/clucie/blob/master/src/clucie/query.clj

(defprotocol QueryExpression
  (parse-expression [expr opts]))

(extend-protocol QueryExpression

  Query
  (parse-expression [query _] query)

  Sequential
  (parse-expression [coll opts]
    (let [qb (BooleanQuery$Builder.)]
      (doseq [q (keep #(parse-expression % opts) coll)]
        (.add qb q BooleanClause$Occur/MUST))
      (.build qb)))

  IPersistentSet
  (parse-expression [s opts]
    (let [qb (BooleanQuery$Builder.)]
      (doseq [q (keep #(parse-expression % opts) s)]
        (.add qb q BooleanClause$Occur/SHOULD))
      (.build qb)))

  IPersistentMap
  (parse-expression [m opts]
    (let [qb (BooleanQuery$Builder.)]
      (doseq [q (keep (fn [[k v]] (parse-expression v (assoc opts :key k))) m)]
        (.add qb q BooleanClause$Occur/MUST))))

  String
  (parse-expression [str-query {:keys [^Analyzer analyzer field-name query-type] :as opts}]
    (let [builder (QueryBuilder. analyzer)]
      (case query-type
        :query (.createBooleanQuery builder (name field-name) str-query)
        :phrase-query (.createPhraseQuery builder (name field-name) str-query)
        (throw (ex-info (str "Unsupported query type - " (name query-type)) {:query-type query-type}))))))