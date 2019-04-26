(ns docbrown.clojure-reader
  (:require [clojure.string :as str]
            [docbrown.util :as util]))

(defmethod util/unique-key :resource.type/namespace [_] :namespace/name)

(defmethod util/unique-key :resource.type/def [_] :def/name)

(defn- form->data
  [namespace-name form]
  (let [{:keys [line column]} (meta form)
        s (str (first form))
        data (cond
               (= "ns" s) {:resource/type :resource.type/namespace
                           :namespace/name (str (second form))}
               (str/starts-with? s "def") {:resource/type :resource.type/def
                                           :def/type (str (first form))
                                           :def/name (if namespace-name
                                                       (str namespace-name "/" (second form))
                                                       (str (second form)))}
               :default nil)]
    (when (:resource/type data)
      (merge {:line line
              :column column}
             data))))

(defn content->data
  [content]
  (with-open [r (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. content))]
    (let [eof (Object.)]
      (loop [out []
             form (read r false eof)
             namespace-name nil]
        (if (= eof form)
          out
          (let [maybe-data (form->data namespace-name form)
                namespace-name (or (:namespace/name maybe-data) namespace-name)]
            (if maybe-data
              (recur (conj out (assoc maybe-data
                                      :endline (.getLineNumber r)
                                      :endcolumn (.getColumnNumber r)))
                     (read r false eof) namespace-name)
              (recur out (read r false eof) namespace-name))))))))
