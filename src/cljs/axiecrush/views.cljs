(ns axiecrush.views
  (:require
   [re-frame.core :as rf]
   [axiecrush.subs :as subs]
   [axiecrush.views.game :as game]
   ))

(defn home-panel
  []
  [:div.container
   [:div.row
    [:div.col-xs-12.center-xs
     [:h1 "Axie Crush"]]]
   [:div.row
    [:div.col-xs-12.center-xs
     [:a {:href "/game/1970"}
      "Start"]]]])

(defn get-panel
  [panel]
  (case panel
    :game-panel [game/panel]
    [home-panel]))

(defn show-panel
  [panel]
  [get-panel panel])

(defn main-panel []
  (let [active-panel @(rf/subscribe [::subs/active-panel])]
    [show-panel active-panel]))
