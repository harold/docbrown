(ns docbrown.core
  (:require [clojure.pprint]
            [clojure.string :as str]
            [clojure.java.shell :as shell]
            [clojure.set :as clojure-set]
            [docbrown.util :refer [mapmap] :as util]
            [docbrown.clojure-reader :as clojure-reader]
            [crux.api :as crux])
  (:import [java.util Date UUID]))

(defonce ^:dynamic *system* nil)

(def blank-line (->> "\r?\n"
                     (repeat 2)
                     (apply str)
                     (re-pattern)))

(defn parse-commit
  [sha]
  (let [[header message] (str/split (util/git-cat sha) blank-line)]
    (->> header
         (util/lines)
         (map util/columns)
         (mapmap (fn [[k :as line]]
                   (condp = k
                     "tree" [:commit/files (util/git-tree->files (second line))]
                     "author" [:commit/inst
                               (->> line reverse second Integer/parseInt (* 1000) Date.)]
                     nil)))
         (merge {:commit/sha sha
                 :commit/message (str/trim message)}))))

(defn- decorate-file
  [{:keys [file/path file/sha] :as file}]
  (let [content (util/git-cat sha)]
    (cond-> file
      true (assoc :crux.db/id (util/item->rid file)
                  :file/content content)
      (re-find #"\.clj.?$" path) (assoc :data
                                        (try (clojure-reader/content->data content)
                                             (catch Exception _
                                               (println "ERROR: Failed to parse clj:" path)))))))

(defn- file->file-tx
  [inst {:keys [crux.db/id file/path file/sha file/content]}]
  [:crux.tx/put id
   {:crux.db/id id
    :resource/type :resource.type/file
    :file/path path
    :file/sha sha
    :file/content content}
   inst])

(defn- db->existing-files
  [db]
  (->> (crux/q db
               '{:find [path sha rid]
                 :where [[rid :file/sha sha]
                         [rid :file/path path]]})
       (reduce (fn [eax [path sha rid]]
                 (assoc eax path {:crux.db/id rid
                                  :file/path path
                                  :file/sha sha}))
               {})))


(defn- submit-parsed-commit
  [{:keys [commit/sha commit/message commit/files commit/inst]}]
  (let [db (crux/db *system* inst)
        file->data-rid-set (fn [{:keys [crux.db/id] :as file}]
                             (when-not id
                               (println "========================================")
                               (clojure.pprint/pprint file))
                             (->> (crux/q db
                                          {:find ['rid]
                                           :where [['rid :loc/file id]
                                                   ['rid :docbrown/tag :tag/data]]})
                                  (map first)
                                  (set)))
        commit-rid (util/string->rid sha)
        commit-tx [:crux.tx/put commit-rid
                   {:crux.db/id commit-rid
                    :resource/type :resource.type/commit
                    :commit/sha sha
                    :commit/message message
                    :commit/inst inst}
                   inst]
        existing-files (db->existing-files db)
        incoming-path-set (set (map :file/path files))
        deleted-files (remove (fn [[path _]] (incoming-path-set path)) existing-files)
        deleted-file-txs (->> deleted-files
                              (map (fn [[_ {:keys [crux.db/id]}]] [:crux.tx/delete id inst])))
        deleted-file-data-txs (->> deleted-files
                                   (vals)
                                   (mapcat file->data-rid-set)
                                   (map (fn [rid] [:crux.tx/delete rid inst])))
        changed-files (->> files
                           (remove (fn [{:keys [file/path file/sha]}]
                                     (= sha (get-in existing-files [path :file/sha]))))
                           (map decorate-file))
        changed-file-data-txs
        (->> changed-files
             (mapcat (fn [{:keys [crux.db/id data] :as file}]
                       (let [existing-data-rid-set (file->data-rid-set file)
                             incoming-data (for [d data]
                                             (merge d
                                                    {:crux.db/id (util/item->rid d)
                                                     :docbrown/tag :tag/data
                                                     :loc/file id}))
                             incoming-data-rid-set (->> incoming-data
                                                        (map :crux.db/id)
                                                        (set))]
                         (concat (->> (clojure-set/difference existing-data-rid-set
                                                              incoming-data-rid-set)
                                      (map (fn [rid] [:crux.tx/delete rid inst])))
                                 (->> incoming-data
                                      (map (fn [d] [:crux.tx/put (:crux.db/id d) d inst]))))))))]
    (->> (concat [commit-tx]
                 (map (partial file->file-tx inst) changed-files)
                 changed-file-data-txs
                 deleted-file-txs
                 deleted-file-data-txs)
         (vec)
         ((fn [x]
            (println "Submitting" sha "at" inst "with" (count x) "items.")
            x))
         (crux/submit-tx *system*))))

(defn ingest
  [repo-path]
  (shell/with-sh-dir repo-path
    (->> (util/git-commit-shas)
         (pmap parse-commit)
         (map submit-parsed-commit)
         (doall)))
  :done)

(defn items-by-resource-type
  [resource-type & {:keys [t]}]
  (let [db (if t
             (crux/db *system* t)
             (crux/db *system*))]
    (->> (crux/q db
                 {:find '[rid]
                  :where [['e :crux.db/id 'rid]
                          ['e :resource/type resource-type]]})
         (map #(crux/entity db (first %))))))

(defn rid->valid-times
  [rid]
  (->> (crux/history *system* rid)
       (map :crux.db/valid-time)))

(defn rid+time->data
  [rid t]
  (crux/entity (crux/db *system* t) rid))

;; (defn- diff
;;   [system rid t1 t2]
;;   (spit "/tmp/f1" (:file/content (crux/entity (crux/db system t1) rid)))
;;   (spit "/tmp/f2" (:file/content (crux/entity (crux/db system t2) rid)))
;;   (println (util/sh "diff" "/tmp/f1" "/tmp/f2")))
