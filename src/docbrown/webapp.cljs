(ns docbrown.webapp
  (:require [cljs.pprint]
            [reagent.core :as r]
            [ajax.core :refer [GET]]))

(defonce state* (r/atom {}))

(defn- page
  []
  (GET "/files"
       {:handler (fn [resp]
                   (swap! state* assoc :files resp))})
  (fn []
    [:div.page
     [:h2 "DOCBROWN"]
     (into [:ul]
           (for [{:keys [path]} (->> (:files @state*)
                                     (vals)
                                     (sort-by :file/path))]
             [:li path]))]))

(r/render [page] (js/document.getElementById "app"))
