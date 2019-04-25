(ns docbrown.main
  (:require [crux.api :as crux]
            [docbrown.core :as docbrown])
  (:gen-class))

(defn -main
  [& args]
  (let [repo-full-path (first args)
        system (crux/start-standalone-system {:kv-backend "crux.kv.rocksdb.RocksKv"
                                              :db-dir "data/db-dir-1"})]
    (with-bindings {#'docbrown/*system* system}
      (docbrown/ingest repo-full-path)
      (shutdown-agents))))

