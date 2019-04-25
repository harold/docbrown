(ns docbrown.core-test
  (:require [clojure.pprint]
            [clojure.test :refer [is deftest use-fixtures]]
            [crux.api :as crux]
            [docbrown.core :as docbrown]
            [docbrown.test-utils :as test-utils]))

(use-fixtures :each test-utils/test-system-fixture)

(deftest basic-ingest-test
  (let [entries (docbrown/entries)]
    (clojure.pprint/pprint entries)
    (is (not-empty entries))))

(deftest valid-times-test
  (let [test-file "README.md"
        valid-times (->> (docbrown/path->rid test-file)
                         (docbrown/rid->valid-times))]
    (is (every? inst? valid-times))))

(deftest file-data-seq-test
  (let [test-file "src/docbrown/core.clj"
        test-file-rid (docbrown/path->rid test-file)
        datas (for [t (docbrown/rid->valid-times test-file-rid)]
                (:data (docbrown/rid+time->data test-file-rid t)))]
    (is (pos? (count datas)))))
