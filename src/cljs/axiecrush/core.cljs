(ns axiecrush.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [re-pressed.core :as rp]
   [axiecrush.events :as events]
   [axiecrush.views :as views]
   [axiecrush.config :as config]
   [axiecrush.routes :as routes]
   ))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn setup-keys
  []
  (re-frame/dispatch-sync
    [::rp/set-keydown-rules
     {:event-keys [[[:move/left]
                    [{:keyCode 37}]
                    [{:keyCode 65}]]
                   [[:move/right]
                    [{:keyCode 39}]
                    [{:keyCode 68}]]
                   [[:move/up]
                    [{:keyCode 38}]
                    [{:keyCode 87}]]
                   [[:move/down]
                    [{:keyCode 40}]
                    [{:keyCode 83}]]
                   [[:move/left-up]
                    [{:keyCode 37} {:keyCode 38}]
                    [{:keyCode 65} {:keyCode 87}]]
                   [[:move/left-down]
                    [{:keyCode 37} {:keyCode 40}]
                    [{:keyCode 65} {:keyCode 83}]]
                   [[:move/right-up]
                    [{:keyCode 39} {:keyCode 38}]
                    [{:keyCode 68} {:keyCode 87}]]
                   [[:move/right-down]
                    [{:keyCode 39} {:keyCode 40}]
                    [{:keyCode 68} {:keyCode 83}]]
                   [[:game/space]
                    [{:keyCode 32}]]]
      :always-listen-keys [{:keyCode 37}
                           {:keyCode 39}]}]))

(defn ^:export init []
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (re-frame/dispatch-sync [::rp/add-keyboard-event-listener "keydown"])
  (setup-keys)
  (dev-setup)
  (mount-root))
