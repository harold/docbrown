(ns docbrown.webapp
  (:require [cljs.pprint]
            [reagent.core :as r]
            [ajax.core :refer [GET]]))

(defonce state* (r/atom {}))

(defn- commit-list
  []
  (let [local-state* (atom {})]
    (fn []
      [:div.commit-list-area {:style {:display :inline-block
                                      :vertical-align :top}}
       [:h3 "Commits"]
       (into [:ol {:style {:margin 0
                           :padding 0
                           :list-style :none}}]
             (for [[i {:keys [crux.db/id commit/inst]}] (->> (:commits @state*)
                                                             (sort-by :commit/inst)
                                                             (map-indexed vector))]
               [:li {:style (merge {:cursor :pointer}
                                   (when (= i (:selected-index @local-state*))
                                     {:background :#222
                                      :color :#f8f8f8}))
                     :on-click (fn [e]
                                 (swap! local-state* assoc :selected-index i)
                                 (GET (str"/defs/" id)
                                      {:handler (fn [resp]
                                                  (swap! state* assoc :defs resp))}))}
                (.toLocaleString inst)]))])))

(defn- def-table
  []
  (fn []
    [:div.def-table-area {:style {:display :inline-block
                                  :vertical-align :top
                                  :margin-left "25px"}}
     [:h3 "Defs"]
     [:table {:style {:font-family "monospace"}}
      [:thead
       [:tr [:th "Name"] [:th "Type"]]]
      (into [:tbody]
            (for [{:keys [def/name def/type]} (sort-by :def/name (:defs @state*))]
              [:tr [:td name] [:td type]]))]]))

(defn- page
  []
  (GET "/commits"
       {:handler (fn [resp]
                   (swap! state* assoc :commits resp))})
  (fn []
    [:div.page {:style {:font-family :sans-serif}}
     [:h2 "DOCBROWN"]
     [commit-list]
     [def-table]]))

(r/render [page] (js/document.getElementById "app"))
