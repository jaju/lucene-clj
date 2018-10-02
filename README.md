# org.msync/lucene-clj

A Clojure wrapper for Apache Lucene.

Note: **UNSTABLE** API. No releases yet.

## What?

Inspired by other example wrappers I've come across.
Notably
 - https://github.com/federkasten/clucie
 - https://github.com/weavejester/clucy

## Why?

When I found out the above inspirations, there were a few aspects that I wanted different.
* Up-to-date with the latest Lucene as on date
* Stick to _core_ Lucene. No script/language specific dependencies part of the core language. Except, maybe, English.
* Support for _suggestions_ - a feature of Lucene I found quite undocumented, as well as lacking good examples for.

I honestly am thankful to the above library authors for their liberal licensing. I used them for inspiration and ideas.
Bad implementation ideas are my own, of course.

## Usage

_To be completed_. 

Some samples to look at in the tests.

_Given_
```clojure
(require '[msync.lucene :as lucene])
(require '[msync.lucene.query :as query])
```

### A simple but complete example

* Create an index
```clojure
(def idx (lucene/>memory-index)) ; In-memory index
; OR
(def idx (lucene/>disk-index "/path/to/index/directory")) ; On disk
```

* Index a document. Use Clojure maps
```clojure
(lucene/index-all! idx
                   [{:name "Ram" :description "A just king. Ethical. Read more in Ramayan."}
                    {:name "Ravan" :description "A scary king. Ethical villain. Read more in Ramayan."}
                    {:name "Krishna" :description "An adorable king. Pragmatic. Read about him in the Mahabharat."}]
                   {:suggest-fields {:name 5}})
```

* Search
```clojure
(lucene/search idx "Ram" {:field-name :name})
; The same as
(lucene/search idx {:name "Ram"} {}) ;; Empty last options argument

;; Search for either Ram or Krishna
(lucene/search idx {:name #{"Krishna" "Ram"}} {})
```

* Phrase search
```clojure
;; Space(s) in the query string are inferred to mean a phrase search operation
(lucene/search idx "read more" {:field-name "description"})
;; The same as
(lucene/search idx {:description "read more"} {})
```

* Suggestion
```clojure
(lucene/suggest idx :name "Ra" {})
```

## License

Copyright © 2018 Ravindra R. Jaju

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
