(ns msync.lucene.utils
  (:require [clojure.java.io])
  (:import [org.apache.lucene.util BytesRef]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.apache.lucene.store FSDirectory]))

(def !nil? (comp nil? not))

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

(.addShutdownHook (Runtime/getRuntime)
                  (proxy [Thread] []
                    (run [] (delete-marked-lucene-directories!))))

(defn temp-path [& {:keys [prefix]
                    :or   {prefix "msync-lucene"}}]
  (let [path (Files/createTempDirectory prefix (make-array FileAttribute 0))]
    path))

(defn string->bytes-ref [^String s]
  (BytesRef. (.getBytes s "UTF-8")))

(defn bytes-ref->string [^BytesRef b]
  (.utf8ToString b))
