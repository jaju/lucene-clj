(ns msync.lucene.analyzers
  (:import [org.apache.lucene.analysis.miscellaneous PerFieldAnalyzerWrapper]
           [org.apache.lucene.analysis Analyzer CharArraySet]
           [org.apache.lucene.analysis.core KeywordAnalyzer]
           [java.util Collection]
           [org.apache.lucene.analysis.standard StandardAnalyzer]))

(defn ^Analyzer create-standard-analyzer
  "StandardAnalyzer, with configurable stop-words and case-ignore behavior."
  [^Collection stop-words & [^Boolean ignore-case?]]
  (StandardAnalyzer. (CharArraySet. stop-words
                       (or ignore-case? true))))

(defn ^PerFieldAnalyzerWrapper create-per-field-analyzer
  [^Analyzer analyzer field->analyzer]
  (PerFieldAnalyzerWrapper.
    analyzer
    (clojure.walk/stringify-keys field->analyzer)))

(defn ^Analyzer create-keyword-analyzer []
  (KeywordAnalyzer.))

(defn create-default-analyzer [] (create-standard-analyzer []))
