# org.msync/lucene-clj [![Build Status](https://secure.travis-ci.org/jaju/lucene-clj.png)](http://travis-ci.org/jaju/lucene-clj)

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

I am sincerely thankful to the above library authors for their liberal licensing. I used them for inspiration and ideas.
Bad implementation ideas are my own, of course.

# Dependency
    [org.msync/lucene-clj "0.1.0-SNAPSHOT"]
(Available via [clojars](https://clojars.org/search?q=lucene-clj))

## Usage

_To be completed_. 

Note: There are some samples to look at in the tests as well. A sample data-set exists in [here](test-resources/sample-data.csv),
which is used in the tests.

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

* A brief note about the search and suggest results
These function calls return a sequence of maps with the following structure for one map (may change!)
```clojure
{:hit ^org.apache.lucene.document.Document Object
 :score 'float
 :doc-id 'number}
```

There's a convenience function to convert the Lucene _Document_ object to a Clojure map.
```clojure
(lucene/document->map (:hit 'one-response))
;; In bulk
(->> (lucene/search idx "query-string" {:field-name "field-name-to-search-in"})
     (map :hit)
     (map lucene/document->map))
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
