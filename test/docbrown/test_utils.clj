(ns docbrown.test-utils
  (:require [crux.api :as crux]
            [docbrown.core :as docbrown])
  (:import [java.util UUID]))

(defn test-system-fixture
  [f]
  (let [blank-system (crux/start-standalone-system {:kv-backend "crux.kv.memdb.MemKv"
                                 :db-dir (str (UUID/randomUUID))})]
    (with-bindings {#'docbrown/*system* blank-system}
      (docbrown/ingest ".")
      (f))))
