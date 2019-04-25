(ns docbrown.clojure-reader
  (:require [clojure.string :as str]))

(defn- form->data
  [form]
  (let [{:keys [line column]} (meta form)
        form-name (cond
                    (= 'ns (first form)) (second form)
                    (str/starts-with? (str (first form)) "def") (second form)
                    :default :unknown)]
    {:src form
     :line line
     :column column
     :name form-name}))

(defn content->data
   [content]
   (with-open [r (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. content))]
     (let [eof (Object.)]
       (loop [out []
              form (read r false eof)]
         (if (= eof form)
           out
           (let [out (conj out (assoc (form->data form)
                                      :endline (.getLineNumber r)
                                      :endcol (.getColumnNumber r)))]
             (recur out (read r false eof))))))))


