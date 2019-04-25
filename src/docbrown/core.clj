(ns docbrown.core
  (:require [clojure.java.shell :as shell]
            [docbrown.util :refer [mapmap] :as util]
            [docbrown.clojure-reader :as clojure-reader]
            [crux.api :as crux])
  (:import [java.util Date UUID]
           [crux.api ICruxAPI]))

(def ^:dynamic *system* nil)
(defn system [] *system*)

(defn- commit->data
  [sha]
  (let [m (->> (util/git-cat sha)
               (util/lines)
               (map util/columns)
               (mapmap (fn [[k :as line]]
                         (condp = k
                           "tree" [:tree (nth line 1)]
                           "author" [:inst (->> line reverse second Integer/parseInt (* 1000) Date.)]
                           nil))))]
    (assoc m :sha sha
           :paths (util/tree->paths (:tree m)))))

(defn path->rid
  [path]
  (->> (crux/q (crux/db *system*)
               {:find ['e]
                :where [['e :path path]]})
       (ffirst)))

(defn- submit-data
  [system {:keys [sha inst paths]}]
  (->> (for [{:keys [sha path content]} paths]
         (when (empty? (crux/q (crux/db system)
                               {:find ['e]
                                :where [['e :path path]
                                        ['e :hash sha]]}))
           (let [uuid (or (path->rid path) (UUID/randomUUID))
                 data (cond
                        (re-find #"\.clj.?$" path) (clojure-reader/content->data content)
                        :else nil)]
             [:crux.tx/put uuid
              (merge
                {:crux.db/id uuid
                 :path path
                 :hash sha
                 :content content}
                (when data {:data data}))
              inst])))
       (remove nil?)
       (vec)
       ((fn [x]
          (println "Submitting" sha "at" inst "with" (count x) "paths.")
          x))
       (crux/submit-tx system)))

(defn ingest
  [repo-path]
  (shell/with-sh-dir repo-path
    (->> (util/sh "git" "log" "--pretty=format:%h" "--date-order" "--reverse")
         (util/lines)
         (pmap commit->data)
         (map (partial submit-data *system*))
         (doall))))

(defn rid->valid-times
  [rid]
  (->> (crux/history (system) rid)
       (map :crux.db/valid-time)))

(defn rid+time->data
  [rid t]
  (crux/entity (crux/db (system) t) rid))

(defn- diff
  [system rid t1 t2]
  (spit "/tmp/f1" (:content (crux/entity (crux/db system t1) rid)))
  (spit "/tmp/f2" (:content (crux/entity (crux/db system t2) rid)))
  (println (util/sh "diff" "/tmp/f1" "/tmp/f2")))
