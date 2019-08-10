(ns axiecrush.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require
   [secretary.core :as secretary]
   [accountant.core :as accountant]
   [re-frame.core :as rf]
   [axiecrush.events :as events]
   ))

(defn app-routes []
  (defroute "/" []
    (rf/dispatch [::events/set-active-panel :home-panel]))

  (defroute "/game/:axie-id" {:keys [axie-id]}
    (rf/dispatch [::events/set-active-panel :game-panel axie-id]))

  (accountant/configure-navigation!
    {:nav-handler secretary/dispatch!
     :path-exists? secretary/locate-route
     :reload-same-path? false})
  (accountant/dispatch-current!))
