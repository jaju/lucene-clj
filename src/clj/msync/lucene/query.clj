(ns msync.lucene.query
  (:require [msync.lucene
             [schema :as schema]
             [values :as values]])
  (:import
    [org.apache.lucene.search Query BooleanQuery$Builder BooleanClause$Occur FuzzyQuery]
    [clojure.lang Named Sequential IPersistentSet IPersistentMap IMapEntry]
    [org.apache.lucene.util QueryBuilder]
    [org.apache.lucene.analysis Analyzer]
    [org.apache.lucene.queryparser.classic QueryParser]
    [org.apache.lucene.index Term]
    [org.apache.lucene.document KeywordField LongField]))

;; Unabashedly based on https://github.com/federkasten/clucie/blob/master/src/clucie/query.clj

(defprotocol QueryExpression
  (parse [expr] [expr opts]))

(defn- field-spec
  [{:keys [field-name field-specs]}]
  (schema/-field-spec field-specs field-name))

(defn- exact-query
  [field-name field-spec value]
  (case (:type field-spec)
    :text    nil
    :keyword (KeywordField/newExactQuery (name field-name)
                                         (values/-normalize-text-value field-name value))
    :boolean (KeywordField/newExactQuery (name field-name)
                                         (str (values/-normalize-boolean-value field-name value)))
    :long    (LongField/newExactQuery (name field-name)
                                      (values/-normalize-long-value field-name value))
    nil      nil
    nil))

(defn- typed-query-required!
  [message data]
  (throw (ex-info message data)))

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
  (parse [str-query {:keys [^Analyzer analyzer field-name query-type] :as opts}]
    (let [builder        (QueryBuilder. analyzer)
          resolved-query (or (exact-query field-name (field-spec opts) str-query)
                             (let [query-type (or query-type (if (re-find #"\s" str-query)
                                                               :phrase-query
                                                               :query))]
                               (case query-type
                                 :query (.createBooleanQuery builder (name field-name) str-query)
                                 :phrase-query (.createPhraseQuery builder (name field-name) str-query)
                                 (throw (ex-info (str "Unsupported query type - " (name query-type)) {:query-type query-type})))))]
      resolved-query))

  Named
  (parse [named-query opts]
    (parse (name named-query) opts))

  Number
  (parse [numeric-query {:keys [field-name] :as opts}]
    (or (exact-query field-name (field-spec opts) numeric-query)
        (typed-query-required! "Numeric query values require a :long field definition"
                               {:field-name field-name
                                :value      numeric-query
                                :field-spec (field-spec opts)})))

  Boolean
  (parse [boolean-query {:keys [field-name] :as opts}]
    (or (exact-query field-name (field-spec opts) boolean-query)
        (typed-query-required! "Boolean query values require a :boolean field definition"
                               {:field-name field-name
                                :value      boolean-query
                                :field-spec (field-spec opts)}))))

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

(defn -create-fuzzy-query
  "Build a fuzzy Lucene query for a single field/value pair."
  [fld ^String val]
  (let [term (Term. ^String (name fld) val)]
    (FuzzyQuery. term)))

(defn -combine-fuzzy-queries
  "Combine a field-to-term map into a SHOULD-based fuzzy query."
  [m field-specs]
  (let [b (BooleanQuery$Builder.)]
    (doseq [[k v] m]
      (let [resolved-field-spec (schema/-field-spec field-specs k)]
        (when (and resolved-field-spec
                   (not (#{:text :keyword} (:type resolved-field-spec))))
          (typed-query-required! "Fuzzy queries are only supported for :text and :keyword fields"
                                 {:field-name k
                                  :field-spec resolved-field-spec
                                  :value      v}))
        (.add b
              (-create-fuzzy-query k (values/-normalize-text-value k v))
              BooleanClause$Occur/SHOULD)))
    (.build b)))

(def create-fuzzy-query -create-fuzzy-query)
(def combine-fuzzy-queries -combine-fuzzy-queries)
