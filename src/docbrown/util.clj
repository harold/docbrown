(ns docbrown.util
  (:require [clojure.java.shell :as shell]))

(defn mapmap
  "Applies mapv f to args, filters out nils and returns the result as a map.
  f should return a two element vector or nil."
  [f & args]
  (->> (apply mapv f args)
       (remove nil?)
       (into {})))

(defn sh
  [& args]
  (:out (apply shell/sh args)))

(defn lines
  [s]
  (clojure.string/split-lines s))

(defn columns
  [s]
  (clojure.string/split s #"\s"))

(defmulti unique-key :resource/type)

(defmethod unique-key :resource.type/commit [_] :commit/sha)

(defmethod unique-key :resource.type/file [_] :file/path)

(defn git-cat
  [sha]
  (sh "git" "cat-file" "-p" sha))

(defn tree->paths
  [sha & {:keys [path]}]
  (->> (git-cat sha)
       (lines)
       (map columns)
       (mapv (fn [[_ k child-sha n]]
               (condp = k
                 "blob" {:resource/type :resource.type/file
                         :file/sha child-sha
                         :file/path (clojure.string/join "/" (conj path n))}
                 "tree" (tree->paths child-sha :path (conj (or path []) n))
                 nil)))
       (flatten)))
