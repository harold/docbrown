(ns docbrown.core
  (:require [clojure.java.shell :as shell]
            [crux.api :as crux])
  (:import [java.util Date UUID]
           [crux.api ICruxAPI])
  (:gen-class))

(defn mapmap
  "Applies mapv f to args, filters out nils and returns the result as a map.
  f should return a two element vector or nil."
  [f & args]
  (->> (apply mapv f args)
       (remove nil?)
       (into {})))

(defn- sh
  [& args]
  (:out (apply shell/sh args)))

(defn- lines
  [s]
  (clojure.string/split-lines s))

(defn- columns
  [s]
  (clojure.string/split s #"\s"))

(defn- git-cat
  [sha]
  (sh "git" "cat-file" "-p" sha))

(defn- tree->paths
  [sha & {:keys [path]}]
  (->> (git-cat sha)
       (lines)
       (map columns)
       (mapv (fn [[_ k child-sha n]]
               (condp = k
                 "blob" (let [content (git-cat child-sha)]
                          {:sha child-sha
                           :path (clojure.string/join "/" (conj path n))
                           :content content
                           :hash child-sha})
                 "tree" (tree->paths child-sha :path (conj (or path []) n))
                 nil)))
       (flatten)))

(defn- commit->data
  [sha]
  (let [m (->> (git-cat sha)
               (lines)
               (map columns)
               (mapmap (fn [[k :as line]]
                         (condp = k
                           "tree" [:tree (nth line 1)]
                           "author" [:inst (->> line reverse second Integer/parseInt (* 1000) Date.)]
                           nil))))]
    (assoc m :sha sha
           :paths (tree->paths (:tree m)))))

(defonce path-uuids* (atom {}))

(defn- path->uuid
  [path]
  (or (get @path-uuids* path)
      (let [uuid (UUID/randomUUID)]
        (swap! path-uuids* assoc path uuid)
        uuid)))

(defn- submit-data
  [system {:keys [sha inst paths]}]
  (->> (for [{:keys [sha path content]} paths]
         (when (empty? (crux/q (crux/db system)
                               {:find ['e]
                                :where [['e :path path]
                                        ['e :hash sha]]}))
           (let [uuid (path->uuid path)]
             [:crux.tx/put uuid
              {:crux.db/id uuid
               :path path
               :hash sha
               :content content}
              inst])))
       (remove nil?)
       (vec)
       ((fn [x]
          (println "Submitting" sha "at" inst "with" (count x) "paths.")
          x))
       (crux/submit-tx system)))

(defn ingest
  [system repo-path]
  (shell/with-sh-dir repo-path
    (->> (sh "git" "log" "--pretty=format:%h" "--date-order" "--reverse")
         (lines)
         (pmap commit->data)
         (map (partial submit-data system))
         (doall))))

(defn -main
  [& args]
  (let [repo-full-path (first args)
        system (crux/start-standalone-system {:kv-backend "crux.kv.rocksdb.RocksKv"
                                              :db-dir "data/db-dir-1"})]
    (ingest system repo-full-path)
    (shutdown-agents)))

(comment

  (def ^crux.api.ICruxAPI system
    (crux/start-standalone-system {:kv-backend "crux.kv.rocksdb.RocksKv"
                                   :db-dir "data/db-dir-1"}))

  (->> (crux/q (crux/db system)
               '{:find [rid p]
                 :where [[e :crux.db/id rid]
                         [e :path p]]})
       (vec)
       (rand-nth)
       (first)
       ((fn [x] (println x) x))
       (crux/history system)
       (clojure.pprint/pprint))

  )

(defn- diff
  [system rid t1 t2]
  (spit "/tmp/f1" (:content (crux/entity (crux/db system t1) rid)))
  (spit "/tmp/f2" (:content (crux/entity (crux/db system t2) rid)))
  (println (sh "diff" "/tmp/f1" "/tmp/f2")))
