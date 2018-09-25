(ns msync.lucene.query
  (:import
    [org.apache.lucene.search Query BooleanQuery BooleanQuery$Builder BooleanClause$Occur BooleanClause]
    [clojure.lang Sequential]
    [org.apache.lucene.util QueryBuilder]))

;; Unabashedly based on https://github.com/federkasten/clucie/blob/master/src/clucie/query.clj

(defmulti parse-query
          (fn [_ {:keys [query-type]}] query-type))

(defmethod parse-query :query
  [query-form {:keys [analyzer field-name]}]
  (let [^QueryBuilder q-builder (QueryBuilder. analyzer)]
    (.createBooleanQuery q-builder (name field-name) query-form)))

(defmethod parse-query :phrase-query
  [query-form {:keys [analyzer field-name]}]
  (let [^QueryBuilder q-builder (QueryBuilder. analyzer)]
    (.createPhraseQuery q-builder (name field-name) query-form)))

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
      (.build qb))))