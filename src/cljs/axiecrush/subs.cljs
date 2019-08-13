(ns axiecrush.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
  ::active-panel
  (fn [{:keys [active-panel]}]
    active-panel))

(rf/reg-sub
  :running?
 (fn [db]
   (:running? db)))

(rf/reg-sub
  :board
  (fn [db]
    (:board db)))

(rf/reg-sub
  :player
  (fn [db]
    (:player db)))

(rf/reg-sub
  :player/pos
  (fn [_]
    [(rf/subscribe [:player])])
  (fn [[player]]
    {:x (:x player)
     :y (:y player)
     :dir (:dir player)}))

(rf/reg-sub
  :player/size
  (fn [_]
    [(rf/subscribe [:player])])
  (fn [[player]]
    {:width (:width player)
     :height (:height player)}))

(rf/reg-sub
  :player/image
  (fn [_]
    [(rf/subscribe [:player])])
  (fn [[player]]
    (:image player)))

(rf/reg-sub
  :player/score
  (fn [_]
    [(rf/subscribe [:player])])
  (fn [[player]]
    (:score player)))

(rf/reg-sub
  :player/hp
  (fn [_]
    [(rf/subscribe [:player])])
  (fn [[player]]
    {:hp (:current-hp player)
     :max (:max-hp player)}))

(rf/reg-sub
  :player/dead?
  (fn [db]
    (:dead? db)))

(rf/reg-sub
  :tokens
  (fn [db]
    (:tokens db)))

(rf/reg-sub
  :rocks
  (fn [db]
    (:rocks db)))

(rf/reg-sub
  :dodges
  (fn [db]
    (:dodges db)))

(rf/reg-sub
  :items
  (fn [db]
    (:items db)))
