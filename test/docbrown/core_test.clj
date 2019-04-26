(ns docbrown.core-test
  (:require [clojure.pprint]
            [clojure.test :refer [is deftest use-fixtures]]
            [crux.api :as crux]
            [docbrown.core :as docbrown]
            [docbrown.util :as util]
            [docbrown.test-utils :as test-utils]))

(use-fixtures :each test-utils/test-system-fixture)

(deftest basic-ingest-test
  (let [commits (docbrown/commits)
        files (docbrown/files)
        namespaces (docbrown/namespaces)
        defs (docbrown/defs)]
    (is (not-empty commits))
    (is (not-empty files))
    (is (not-empty namespaces))
    (is (not-empty defs))))

(deftest valid-times-test
  (let [valid-times (->> (docbrown/lookup-rid {:resource/type :resource.type/file
                                               :file/path "README.md"})
                         (docbrown/rid->valid-times))]
    (is (every? inst? valid-times))))

(deftest file-data-seq-test
  (let [test-file-rid (docbrown/lookup-rid {:resource/type :resource.type/file
                                            :file/path "src/docbrown/core.clj"})
        datas (for [t (docbrown/rid->valid-times test-file-rid)]
                (:data (docbrown/rid+time->data test-file-rid t)))]
    (is (pos? (count datas)))))

(deftest def-history-test
  (let [d (rand-nth (docbrown/defs))
        ts (docbrown/rid->valid-times (:crux.db/id d))
        snip (fn [start end content]
               (as-> content content
                 (util/lines content)
                 (drop (dec start) content)
                 (take (inc (- end start)) content)))
        history (for [inst ts]
                  {:inst inst
                   :content (->> (docbrown/rid+time->data (:loc/file d) inst)
                                 (:file/content)
                                 (snip (:loc/line d) (:loc/endline d))
                                 (clojure.string/join "\n"))})]
    (is (every? map? history))))
