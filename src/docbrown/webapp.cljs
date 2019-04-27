(ns docbrown.webapp
  (:require [cljs.pprint]
            [reagent.core :as r]
            [ajax.core :refer [GET]]))

(defonce state* (r/atom {}))

(defn- fetch-commits!
  []
  (GET "/commits"
       {:handler (fn [resp]
                   (swap! state* assoc :commits resp))}))

(defn- fetch-namespaces!
  [commit-id]
  (GET (str"/namespaces/" commit-id)
       {:handler (fn [resp]
                   (swap! state* assoc :namespaces resp))}))

(defn- fetch-defs!
  [commit-id namespace-name]
  (GET (str"/defs/" commit-id "/" namespace-name)
       {:handler (fn [resp]
                   (swap! state* assoc :defs resp))}))

(defn- fetch-def-history!
  [def-id]
  (GET (str"/def-history/" def-id)
       {:handler (fn [resp]
                   (swap! state* assoc :def-history resp))}))

(defn- selectable-list
  [header items display-fn on-select]
  (let [local-state* (atom {})]
    (fn [header items display-fn on-select]
      [:div.commit-list-area {:style {:display :inline-block
                                      :vertical-align :top
                                      :margin-left "25px"}}
       [:h3 header]
       (into [:ol {:style {:margin 0
                           :padding 0
                           :list-style :none}}]
             (for [[i item] (map-indexed vector items)]
               [:li {:style (merge {:padding "2px"
                                    :cursor :pointer}
                                   (when (= i (:selected-index @local-state*))
                                     {:background :#222
                                      :color :#f8f8f8}))
                     :on-click (fn [e]
                                 (swap! local-state* assoc :selected-index i)
                                 (on-select item))}
                (display-fn item)]))])))

(defn- commit-list
  []
  [selectable-list "Commits"
   (sort-by :commit/inst (:commits @state*))
   (fn [{:keys [commit/inst]}] (.toLocaleString inst))
   (fn [{:keys [crux.db/id]}]
     (swap! state* assoc :selected-commit-id id)
     (swap! state* dissoc :namespaces :defs :def-history)
     (fetch-namespaces! id))])

(defn- namespace-list
  []
  [selectable-list "Namespaces"
   (sort-by :namespace/name (:namespaces @state*))
   :namespace/name
   (fn [{:keys [namespace/name]}]
     (swap! state* dissoc :defs :def-history)
     (fetch-defs! (:selected-commit-id @state*) name))])

(defn- def-table
  []
  (let [local-state* (atom {})]
    (fn []
      [:div.def-table-area {:style {:display :inline-block
                                    :vertical-align :top
                                    :margin-left "25px"}}
       [:h3 "Defs"]
       [:table {:style {:border-collapse :collapse
                        :font-family "monospace"}}
        [:thead #_[:tr [:th "Type"] [:th "Name"]]]
        (into [:tbody]
              (for [[i {:keys [crux.db/id def/name def/type]}] (->> (:defs @state*)
                                                                    (sort-by :def/name)
                                                                    (map-indexed vector))]
                [:tr {:style (merge {:cursor :pointer}
                                    (when (= i (:selected-index @local-state*))
                                      {:background :#222
                                       :color :#f8f8f8}))
                      :on-click (fn [e]
                                  (swap! local-state* assoc :selected-index i)
                                  (fetch-def-history! id))}
                 [:td {:style {:opacity 0.5}} type] [:td name]]))]])))

(defn- def-history
  []
  (fn []
    [:div.def-history-area
     [:h3 "History"]
     (into [:div]
           (for [{:keys [inst content]} (sort-by :inst (:def-history @state*))]
             [:div.history
              [:div.inst {:style {:opacity 0.5}} (.toLocaleString inst)]
              [:pre.content content]]))]))

(defn- page
  []
  (fetch-commits!)
  (fn []
    [:div.page {:style {:font-family :sans-serif}}
     [:h2 "DOCBROWN"]
     [commit-list]
     (when-not (empty? (:namespaces @state*))
       [namespace-list])
     (when-not (empty? (:defs @state*))
       [def-table])
     (when-not (empty? (:def-history @state*))
       [def-history])]))

(r/render [page] (js/document.getElementById "app"))
