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
                 "blob" (let [content (git-cat child-sha)]
                          {:sha child-sha
                           :path (clojure.string/join "/" (conj path n))
                           :content content
                           :hash child-sha})
                 "tree" (tree->paths child-sha :path (conj (or path []) n))
                 nil)))
       (flatten)))
