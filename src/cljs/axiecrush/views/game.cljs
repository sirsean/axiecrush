(ns axiecrush.views.game
  (:require
    [clojure.string :as string]
    [cuerdas.core :refer [format]]
    [re-frame.core :as rf]
    [axiecrush.subs :as subs]
    ))

(defn board
  []
  (let [board @(rf/subscribe [:board])
        running? @(rf/subscribe [:running?])
        dead? @(rf/subscribe [:player/dead?])
        pos @(rf/subscribe [:player/pos])
        size @(rf/subscribe [:player/size])
        player-image @(rf/subscribe [:player/image])
        score @(rf/subscribe [:player/score])
        hp @(rf/subscribe [:player/hp])
        tokens @(rf/subscribe [:tokens])
        rocks @(rf/subscribe [:rocks])
        dodges @(rf/subscribe [:dodges])
        items @(rf/subscribe [:items])]
    [:div {:style {:background-image "url(/img/savannah-bg.png)"
                   :background-repeat "no-repeat"
                   :background-position "center"
                   :background-size "cover"
                   :background-color "#000000"
                   :border-radius "1em"
                   :margin "0 auto"
                   :position "relative"
                   :font-family "Lucida Grande, Lucida Sans Unicode, Lucida Sans, Geneva, Verdana, sans-serif"
                   :width (str (:width board) "px")
                   :height (str (:height board) "px")}}

     ;; i want the background to be slightly darkened
     [:div {:style {:position "absolute"
                    :width "100%"
                    :height "100%"
                    :background-color "rgba(0,0,0, 0.4)"}}]

     (when (and (not running?)
                (not dead?))
       [:div {:style {:position "absolute"
                      :width "100%"
                      :height "100%"
                      :z-index "1000"
                      :background-color "rgba(0, 0, 0, 0.5)"}}
        [:div {:style {:position "relative"
                       :top "20%"
                       :text-align "center"
                       :font-size "80px"
                       :color "white"}}
         "PAUSED"]
        [:div {:style {:position "relative"
                       :top "70%"
                       :text-align "center"
                       :font-size "40px"
                       :color "white"}}
         "(Press spacebar to continue)"]])

     (when dead?
       [:div {:style {:position "absolute"
                      :width "100%"
                      :height "100%"
                      :z-index "1000"
                      :background-color "rgba(0, 0, 0, 0.5)"}}
        [:div {:style {:position "relative"
                       :top "20%"
                       :text-align "center"
                       :font-size "80px"
                       :color "white"}}
         "GAME OVER"]
        [:div {:style {:position "relative"
                       :top "40%"
                       :text-align "center"
                       :font-size "60px"
                       :color "white"}}
         (str "You scored: " score)]
        [:div {:style {:position "relative"
                       :top "60%"
                       :text-align "center"
                       :font-size "40px"
                       :color "white"}}
         "(Press spacebar to play again)"]])

     ; score
     [:div {:style {:position "absolute"
                    :top "20px"
                    :right "20px"
                    :color "white"
                    :font-size "40px"}}
      [:div score]
      [:div (str (int (:hp hp)) "/" (:max hp))]]

     ; show the tokens
     (for [{:keys [id x y width height]} tokens]
       [:div {:key id
              :style {:position "absolute"
                      :bottom (str (- y (/ height 2)) "px")
                      :left (str (- x (/ width 2)) "px")
                      :background-image "url(https://cdn.axieinfinity.com/terrarium-items/s3b.png)"
                      :background-repeat "no-repeat"
                      :background-position "center"
                      :background-size (str width "px " height "px")
                      :width (str width "px")
                      :height (str height "px")}}])

     ; show the rocks
     (let [rock-image #(case %
                         :barrage "https://cdn.axieinfinity.com/terrarium-items/s11c.png"
                         "https://cdn.axieinfinity.com/terrarium-items/s11b.png")]

       (for [{:keys [id x y width height kind]} rocks]
         [:div {:key id
                :style {:position "absolute"
                        :bottom (str (- y (/ height 2)) "px")
                        :left (str (- x (/ width 2)) "px")
                        :background-image (format "url(%s)" (rock-image kind))
                        :background-repeat "no-repeat"
                        :background-position "center"
                        :background-size (str width "px " height "px")
                        :width (str width "px")
                        :height (str height "px")}}]))

     ;; show the items
     (let [item-image #(case %
                         :potion "https://cdn.axieinfinity.com/terrarium-items/f17a.png"
                         :clear-rocks "https://cdn.axieinfinity.com/terrarium-items/p3c.png"
                         :freeze-rocks "https://cdn.axieinfinity.com/terrarium-items/p3b.png"
                         :token-boost "https://cdn.axieinfinity.com/terrarium-items/a3a.png")]
       (for [{:keys [id kind x y width height]} items]
         [:div {:key id
                :style {:position "absolute"
                        :bottom (str (- y (/ height 2)) "px")
                        :left (str (- x (/ width 2)) "px")
                        :background-image (format "url(%s)" (item-image kind))
                        :background-repeat "no-repeat"
                        :background-position "center"
                        :background-size (str width "px " height "px")
                        :width (str width "px")
                        :height (str height "px")}}]))

     ; the axie/player
     [:div {:style (cond->
                     {:position "absolute"
                      :bottom (str (- (:y pos) (/ (:height size) 2)) "px")
                      :left (str (- (:x pos) (/ (:width size) 2)) "px")
                      :background-image (format "url(%s)" player-image)
                      :background-repeat "no-repeat"
                      :background-position "center"
                      :background-size "220px 165px"
                      :width (str (+ 10 (:width size)) "px")
                      :height (str (:height size) "px")}

                     (= :right (:dir pos))
                     (assoc :transform "rotateY(180deg)")

                     )}]

     ; show the dodges
     (for [{:keys [id x y color msg]} dodges]
       [:div {:key id
              :style {:position "absolute"
                      :bottom (str y "px")
                      :left (str (- x 15) "px")
                      :z-index 500
                      :font-size "20px"
                      :color color}}
        msg])
     ]))

(defn panel
  []
  [:div
   [board]])
