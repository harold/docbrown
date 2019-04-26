(ns docbrown.clojure-reader
  (:require [clojure.string :as str]
            [docbrown.util :as util]))

(defmethod util/unique-key :resource.type/namespace [_] :namespace/name)

(defmethod util/unique-key :resource.type/def [_] :def/name)

(defn- form->data
  [namespace-name form]
  (let [{:keys [line column]} (meta form)
        s (str (first form))
        n (str (second form))
        data (cond
               (= "ns" s) {:resource/type :resource.type/namespace
                           :namespace/name n}
               (str/starts-with? s "def") (merge {:resource/type :resource.type/def
                                                  :def/type s}
                                                 (if namespace-name
                                                   {:def/namespace namespace-name
                                                    :def/name (str namespace-name "/" n)}
                                                   {:def/name n}))
               :default nil)]
    (when (:resource/type data)
      (merge {:loc/line line
              :loc/column column}
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
                                      :loc/endline (.getLineNumber r)
                                      :loc/endcolumn (.getColumnNumber r)))
                     (read r false eof) namespace-name)
              (recur out (read r false eof) namespace-name))))))))
