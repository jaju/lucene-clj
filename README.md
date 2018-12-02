# org.msync/lucene-clj [![Build Status](https://secure.travis-ci.org/jaju/lucene-clj.png)](http://travis-ci.org/jaju/lucene-clj)

A Clojure wrapper for Apache Lucene.
The primary use-case is for in-process text search needs for *read-only* datasets that can be managed on single-instance deployments. Because for multi-instance deployments, keeping modifications of data in sync will be hard. In other words, when you need light-weight text-search support without the hassle of setting up something like Solr.

Both in-memory, and on-disk indexes can be used depending on the dataset size.

Note: **UNSTABLE** API. No releases yet, but coming soon.

# Dependency
    [org.msync/lucene-clj "0.2.0-SNAPSHOT"]
(Available via [clojars](https://clojars.org/search?q=lucene-clj))

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

## Usage

_To be completed_. 

Note: There are some samples to look at in the tests as well. A sample data-set exists in [here](test-resources/sample-data.csv) and [here](test-resources/albumlist.csv),
which is used in the tests.

_Given_
```clojure
(require '[msync.lucene :as lucene])
(require '[msync.lucene.query :as query])
```

### A simple but complete example

#### Create an index
```clojure
(def store (lucene/create-store :memory)) ; In-memory index
; OR
(def store (lucene/create-store "/path/to/index/directory")) ; On disk
```

#### Index a document. Use Clojure maps
```clojure
(lucene/index! store
                   [{:name "Ram" :description "A just king. Ethical. Read more in Ramayan."}
                    {:name "Ravan" :description "A scary king. Ethical villain. Read more in Ramayan."}
                    {:name "Krishna" :description "An adorable king. Pragmatic. Read about him in the Mahabharat."}]
                   {:suggest-fields {:name 5}})
```

#### A brief note about the search and suggest results
These function calls return a sequence of maps with the following structure for one map (may change!)
```clojure
{:hit ^org.apache.lucene.document.Document Object
 :score 'float
 :doc-id 'number}
```

There's a convenience function to convert the Lucene _Document_ object to a Clojure map.
```clojure
(require '[msync.lucene.document :as d])
(d/document->map (:hit 'one-response))
;; In bulk
(->> (lucene/search store "query-string" {:field-name "field-name-to-search-in"})
     (map :hit)
     (map d/document->map))
```

#### Search
```clojure
(lucene/search store {:name "Ram"})

;; Search for either Ram or Krishna
(lucene/search store {:name #{"Krishna" "Ram"}})
```

#### Phrase search
```clojure
;; Space(s) in the query string are inferred to mean a phrase search operation
(lucene/search idx {:description "read more"})
```

#### Suggestion
```clojure
(lucene/suggest store :name "Ra")
```

## Sample Datasets
1. [Albums - Kaggle](https://www.kaggle.com/notgibs/500-greatest-albums-of-all-time-rolling-stone) - [local](test-resources/albumlist.csv)
2. [Hand-created, real + fictional characters](test-resources/sample-data.csv)

## License

Copyright © 2018 Ravindra R. Jaju

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.