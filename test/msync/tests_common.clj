(ns msync.tests-common
  (:require [clojure.test :refer :all]
            [msync.lucene.utils :as utils]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defonce sample-data (-> "sample-data.csv"
                         io/resource
                         slurp
                         csv/read-csv
                         utils/docfields-vecs-to-maps))