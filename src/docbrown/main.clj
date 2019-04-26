(ns docbrown.main
  (:require [org.httpkit.server :as http]
            [hiccup.page :as hiccup]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.util.response :as response]
            [bidi.ring :as bidi-ring]
            [muuntaja.middleware :refer [wrap-format]]
            [crux.api :as crux]
            [docbrown.core :as docbrown])
  (:import [java.util UUID])
  (:gen-class))

(defonce stop-web-server-fn* (atom nil))

(defn- home-page
  [request]
  (-> (hiccup/html5
       [:head
        [:meta {:charset "utf-8"}]
        [:title "Docbrown"]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
        [:link {:rel "stylesheet" :href "/css/normalize.css"}]
        [:link {:rel "stylesheet" :href "/css/site.css"}]]
       [:body
        [:div#app]
        [:script {:src "/js/app.js" :type "text/javascript"}]])
      (response/response)
      (response/header "Content-Type" "text/html")))

(defn- get-commits
  [req]
  (response/response (docbrown/commits)))

(defn- get-defs
  [req]
  (let [rid (UUID/fromString (-> req :params :id))]
    (->> rid
         (crux/entity (crux/db docbrown/*system*))
         (:commit/inst)
         (docbrown/defs :t)
         (response/response))))

(def routes ["/" {:get [["commits" #'get-commits]
                        [["defs/" :id] #'get-defs]
                        [true #'home-page]]}])

(defn handler
  []
  (-> (bidi-ring/make-handler routes)
      (wrap-format)
      (wrap-cookies)
      (wrap-params)
      (wrap-multipart-params)
      (wrap-resource "public")
      (wrap-content-type)))

(defn restart-web-server!
  []
  (when-let [stop-web-server-fn @stop-web-server-fn*]
    (stop-web-server-fn))
  (let [port 1922]
    (reset! stop-web-server-fn* (http/run-server (handler) {:port port}))
    (println "Server started on port" port)))

(defn -main
  [& args]
  (let [repo-full-path (or (first args) ".")
        ;; system (crux/start-standalone-system {:kv-backend "crux.kv.rocksdb.RocksKv"
        ;;                                       :db-dir "data/db-dir-1"})
        system (crux/start-standalone-system {:kv-backend "crux.kv.memdb.MemKv"
                                              :db-dir (str (UUID/randomUUID))})]
    (alter-var-root #'docbrown/*system* (constantly system))
    (docbrown/ingest repo-full-path)
    (restart-web-server!)))
