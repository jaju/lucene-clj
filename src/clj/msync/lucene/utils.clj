(ns msync.lucene.utils)

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
