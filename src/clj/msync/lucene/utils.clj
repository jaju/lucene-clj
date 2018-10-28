(ns msync.lucene.utils
  (:require [clojure.java.io])
  (:import [org.apache.lucene.util BytesRef]
           [java.nio.file Files]
           [java.io File]
           [java.nio.file.attribute FileAttribute]
           [org.apache.lucene.store FSDirectory]))

(defn- lucene-dir-deleter [^FSDirectory directory]
  (doseq [^String f (.listAll directory)]
    (.deleteFile directory f))
  (Files/delete (.getDirectory directory)))

(defonce ^:private deletable-directory-list (atom #{}))

(defn delete-on-exit! [d]
  (swap! deletable-directory-list conj d))

(defn- delete-marked-lucene-directories! []
  (doseq [d @deletable-directory-list]
    (lucene-dir-deleter d))
  (reset! deletable-directory-list #{}))

(.addShutdownHook (Runtime/getRuntime) (proxy [Thread] [] (run [] (delete-marked-lucene-directories!))))

(defn temp-path [& {:keys [prefix]
                    :or {prefix "msync-lucene"}}]
  (let [path (Files/createTempDirectory prefix (make-array FileAttribute 0))]
    path))

(defn docfields-vecs-to-maps
  "Seq of documents, each a vector.
  Each element in a vector corresponds to a field.
  The first element in the seq is the `header`.
  Output is a map of each document, with each field keyed with its name.
  Use-case: A CSV, with a header-row, parsed and fed."
  ([doc-vecs-with-header]
   (docfields-vecs-to-maps (first doc-vecs-with-header) (rest doc-vecs-with-header)))
  ([header-vec doc-vecs]
   (map zipmap (->> header-vec
                    (map keyword)
                    repeat)
        doc-vecs)))

(defn string->bytes-ref [^String s]
  (BytesRef. (.getBytes s "UTF-8")))

(defn bytes-ref->string [^BytesRef b]
  (.utf8ToString b))
