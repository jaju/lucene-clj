(ns msync.lucene.utils
  (:require [clojure.java.io])
  (:import [org.apache.lucene.util BytesRef]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.apache.lucene.store AlreadyClosedException FSDirectory]))

(defn- lucene-dir-deleter
  [directory-path]
  (when (Files/exists directory-path (make-array java.nio.file.LinkOption 0))
    (with-open [directory (FSDirectory/open directory-path)]
      (doseq [^String f (.listAll directory)]
        (.deleteFile directory f)))
    (Files/deleteIfExists directory-path)))

(defonce ^:private deletable-directory-list (atom #{}))

(defn delete-on-exit!
  [^FSDirectory directory]
  (swap! deletable-directory-list conj (.getDirectory directory)))

(defn- delete-marked-lucene-directories! []
  (doseq [directory-path @deletable-directory-list]
    (try
      (lucene-dir-deleter directory-path)
      (catch AlreadyClosedException _
        nil)))
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
