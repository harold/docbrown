(ns docbrown.core
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]
            [docbrown.util :refer [mapmap] :as util]
            [docbrown.clojure-reader :as clojure-reader]
            [crux.api :as crux])
  (:import [java.util Date UUID]))

(defonce ^:dynamic *system* nil)
(defn system [] *system*)

(def blank-line (->> "\r?\n"
                     (repeat 2)
                     (apply str)
                     (re-pattern)))

(defn- k+v->rid
  [k v]
  (->> (crux/q (crux/db *system*)
               {:find ['e]
                :where [['e k v]]})
       (ffirst)))

(defn lookup-rid
  [item]
  (let [k (util/unique-key item)]
    (k+v->rid k (get item k))))

(defn- commit->data
  [sha]
  (let [[header message] (str/split (util/git-cat sha) blank-line)
        m (->> header
               (util/lines)
               (map util/columns)
               (mapmap (fn [[k :as line]]
                         (condp = k
                           "tree" [:commit/tree (nth line 1)]
                           "author" [:commit/inst
                                     (->> line reverse second Integer/parseInt (* 1000) Date.)]
                           nil))))]
    (assoc m
           :commit/sha sha
           :commit/message (str/trim message)
           :commit/paths (util/tree->paths (:commit/tree m)))))

(defn- submit-data
  [system {:keys [commit/sha commit/message commit/inst commit/paths]}]
  (let [commit-rid (UUID/randomUUID)
        commit-tx [:crux.tx/put commit-rid
                   {:crux.db/id commit-rid
                    :resource/type :resource.type/commit
                    :commit/sha sha
                    :commit/message message
                    :commit/inst inst}
                   inst]]
    (->> paths
         (reduce (fn [eax {:keys [file/sha file/path] :as file}]
                   (if (empty? (crux/q (crux/db system)
                                       {:find ['e]
                                        :where [['e :file/sha sha]
                                                ['e :file/path path]]}))
                     (let [content (util/git-cat sha)
                           data (cond
                                  (re-find #"\.clj.?$" path) (clojure-reader/content->data content)
                                  :else nil)
                           file-rid (or (lookup-rid file) (UUID/randomUUID))
                           file-tx [:crux.tx/put file-rid
                                    {:crux.db/id file-rid
                                     :resource/type :resource.type/file
                                     :file/path path
                                     :file/sha sha
                                     :file/content content}
                                    inst]]
                       (-> eax
                           (concat [file-tx]
                                   (for [d data]
                                     (let [data-rid (or (lookup-rid d) (UUID/randomUUID))]
                                       [:crux.tx/put data-rid
                                        (merge {:crux.db/id data-rid
                                                :loc/file file-rid}
                                               d)
                                        inst])))
                           (vec)))
                     eax))
                 [])
         (remove nil?)
         (concat [commit-tx])
         (vec)
         ((fn [x]
            (println "Submitting" sha "at" inst "with" (count x) "items.")
            x))
         (crux/submit-tx system))))

(defn ingest
  [repo-path]
  (shell/with-sh-dir repo-path
    (->> (util/sh "git" "log" "--pretty=format:%h" "--date-order" "--reverse")
         (util/lines)
         (map commit->data)
         (map (partial submit-data *system*))
         (doall))))

(defn items-by-resource-type
  [resource-type & {:keys [t]}]
  (let [db (if t
             (crux/db (system) t)
             (crux/db (system)))]
    (->> (crux/q db
                 {:find '[rid]
                  :where [['e :crux.db/id 'rid]
                          ['e :resource/type resource-type]]})
         (map #(crux/entity db (first %))))))

(defn commits
  []
  (items-by-resource-type :resource.type/commit))

(defn files
  []
  (items-by-resource-type :resource.type/file))

(defn namespaces
  [& {:keys [t]}]
  (if t
    (items-by-resource-type :resource.type/namespace :t t)
    (items-by-resource-type :resource.type/namespace)))

(defn defs
  [& {:keys [namespace-name t]}]
  (cond->> (if t
             (items-by-resource-type :resource.type/def :t t)
             (items-by-resource-type :resource.type/def))
    namespace-name (filter #(= namespace-name (:def/namespace %)))))

(defn rid->valid-times
  [rid]
  (->> (crux/history (system) rid)
       (map :crux.db/valid-time)))

(defn rid+time->data
  [rid t]
  (crux/entity (crux/db (system) t) rid))

;; (defn- diff
;;   [system rid t1 t2]
;;   (spit "/tmp/f1" (:file/content (crux/entity (crux/db system t1) rid)))
;;   (spit "/tmp/f2" (:file/content (crux/entity (crux/db system t2) rid)))
;;   (println (util/sh "diff" "/tmp/f1" "/tmp/f2")))
