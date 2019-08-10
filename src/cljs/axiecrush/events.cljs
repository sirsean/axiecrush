(ns axiecrush.events
  (:require
   [re-frame.core :as rf]
   [cuerdas.core :refer [format]]
   [camel-snake-kebab.core :refer [->kebab-case-keyword]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [district0x.re-frame.interval-fx]
   [cljs-time.core :as tc]
   [cljs-time.coerce :as tco]
   [ajax.core :as ajax]
   [secretary.core :as secretary]
   [accountant.core :as accountant]
   ))

(rf/reg-fx
  :http-get
  (fn [{:keys [url handler err-handler response-format transform? headers]
        :or {response-format :json
             transform? true}}]
    (println :http-get response-format url)
    (ajax/GET
      url
      {:response-format response-format
       :keywords? true
       :headers headers
       :error-handler (fn [err]
                        (if err-handler
                          (rf/dispatch (conj err-handler err))
                          (println "error" err)))
       :handler (fn [result]
                  (rf/dispatch
                    (conj handler
                          (cond->> result
                            transform? (transform-keys ->kebab-case-keyword)))))})))

(defmulti set-active-panel
  (fn [cofx [_ panel :as event]]
    panel))

(defmethod set-active-panel :default
  [{:keys [db]} [_ panel]]
  (let [axie-id (int (inc (rand 80000)))]
    (accountant/navigate! (format "/game/%s" axie-id))))

(defmethod set-active-panel :game-panel
  [{:keys [db]} [_ panel axie-id]]
  {:db (assoc db :active-panel panel)
   :dispatch [:axie/set-id axie-id {:handler :game/reset}]})

(rf/reg-event-fx
  ::set-active-panel
  (fn [cofx [_ active-panel :as event]]
    (set-active-panel cofx event)))

(def new-game
  {:player {:x 500
            :y 80
            :width 120
            :height 100
            :dir :left
            :score 0
            :max-hp 10
            :current-hp 10
            :speed 10
            :morale 10
            :skill 10}
   :board {:width 1000
           :height 800}
   :dead? false
   :running? false
   :token-gen-time 100
   :rock-gen-time 2000
   :potion-gen-time 20000
   :tokens []
   :rocks []
   :dodges []
   :potions []})

(defn new-game-with-axie
  [axie]
  (-> new-game
      (update :player merge (select-keys (:stats axie) [:speed :morale :skill]))
      (update :player merge {:max-hp (:hp (:stats axie))
                             :current-hp (:hp (:stats axie))})
      (update :player merge (case (:stage axie)
                              3 {:width 60 :height 50}
                              {:width 120 :height 100}))
      (update :player assoc :image (:image axie))))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   {}))

(rf/reg-event-fx
  :axie/set-id
  (fn [{:keys [db]} [_ axie-id {:keys [handler]}]]
    {:db (-> db
             (assoc-in [:axie :loading?] true)
             (assoc-in [:axie :id] (str axie-id)))
     :dispatch [::fetch-axie axie-id {:handler handler}]}))

(rf/reg-event-fx
  ::fetch-axie
  (fn [{:keys [db]} [_ axie-id {:keys [handler]}]]
    {:http-get {:url (format "https://axieinfinity.com/api/v2/axies/%s?lang=en" axie-id)
                :handler [:axie/got handler]}}))


(rf/reg-event-fx
  :axie/got
  (fn [{:keys [db]} [_ handler axie]]
    (cond->
      {:db (-> db
               (assoc-in [:axie :loading?] false)
               (assoc-in [:axie] axie))}
      (some? handler)
      (merge {:dispatch [handler axie]}))))

;; here's the game!

(rf/reg-event-fx
  :game/reset
  (fn [{:keys [db]} _]
    {:db (merge db (new-game-with-axie (:axie db)))
     :dispatch [:game/start (:axie db)]}))

(rf/reg-event-fx
  :game/start
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc :now (tco/to-long (tc/now)))
             (assoc :running? true))
     :dispatch-interval {:id :ticker
                         :dispatch [:tick]
                         :ms 15}}))

(rf/reg-event-fx
  :game/stop
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc :running? false))
     :clear-interval {:id :ticker}}))

(rf/reg-event-fx
  :game/space
  (fn [{:keys [db]} _]
    (cond
      (:dead? db) {:dispatch [:game/reset]}
      (:running? db) {:dispatch [:game/stop]}
      :else {:dispatch [:game/start]})))

(rf/reg-event-fx
  :tick
  (fn [{:keys [db]} _]
    (let [now (tco/to-long (tc/now))
          dt (- now (:now db))]
      (cond-> {:db (-> db
                       (assoc :now now))}
        (:running? db) (merge {:dispatch-n [[:token/generate dt]
                                            [:token/movement dt]
                                            [:token/collision dt]
                                            [:token/bottom dt]
                                            [:rock/generate dt]
                                            [:rock/movement dt]
                                            [:rock/collision dt]
                                            [:rock/bottom dt]
                                            [:dodge/decrement dt]
                                            [:dodge/clear dt]
                                            [:potion/generate dt]
                                            [:potion/movement dt]
                                            [:potion/collision dt]
                                            [:potion/bottom dt]
                                            [:player/death dt]]})))))

(rf/reg-event-db
  :move/left
  (fn [db _]
    (cond-> db
      (:running? db)
      (->
        (assoc-in [:player :dir] :left)
        (update-in [:player :x] - (:speed (:player db)))
        (update-in [:player :x] max (/ (:width (:player db)) 2))))))

(rf/reg-event-db
  :move/right
  (fn [db _]
    (cond-> db
      (:running? db)
      (->
        (assoc-in [:player :dir] :right)
        (update-in [:player :x] + (:speed (:player db)))
        (update-in [:player :x] min (- (:width (:board db))
                                       (/ (:width (:player db)) 2)))))))

(rf/reg-event-db
  :move/down
  (fn [db _]
    (cond-> db
      (:running? db)
      (->
        (update-in [:player :y] - (:speed (:player db)))
        (update-in [:player :y] max (/ (:height (:player db)) 2))))))

(rf/reg-event-db
  :move/up
  (fn [db _]
    (cond-> db
      (:running? db)
      (->
        (update-in [:player :y] + (:speed (:player db)))
        (update-in [:player :y] min (- (:height (:board db))
                                       (/ (:height (:player db)) 2)))))))

(rf/reg-event-fx
  :move/left-up
  (fn [_ _]
    {:dispatch-n [[:move/left]
                  [:move/up]]}))

(rf/reg-event-fx
  :move/left-down
  (fn [_ _]
    {:dispatch-n [[:move/left]
                  [:move/up]]}))

(rf/reg-event-fx
  :move/right-up
  (fn [_ _]
    {:dispatch-n [[:move/right]
                  [:move/up]]}))

(rf/reg-event-fx
  :move/right-down
  (fn [_ _]
    {:dispatch-n [[:move/right]
                  [:move/down]]}))

(rf/reg-event-fx
  :player/death
  (fn [{:keys [db]} _]
    (if (>= 0 (:current-hp (:player db)))
      {:db (assoc db :dead? true)
       :dispatch-n [[:game/stop]]}
      {})))

(defn process-bottom
  [items]
  (remove
    (fn [{:keys [y]}]
      (<= y 0))
    items))

(defn distance
  [a b]
  (Math/sqrt
    (+ (Math/pow (- (:x a) (:x b)) 2)
       (Math/pow (- (:y a) (:y b)) 2))))

(defn radius
  [{:keys [width]}]
  (/ width 2))

(defn collision?
  [a b]
  (let [d (distance a b)
        r1 (radius a)
        r2 (radius b)]
    (< d (+ r1 r2))))

;; tokens

(defn new-token
  [{:keys [board]}]
  {:id (gensym)
   :speed (+ 80 (rand 80))
   :x (+ 60 (rand (- (:width board) 100)))
   :y (:height board)
   :width 40
   :height 50})

(rf/reg-event-fx
  :token/generate
  (fn [{:keys [db]} [_ dt]]
    ;(if (zero? (count (:tokens db)))
    (if (>= dt (rand (:token-gen-time db)))
      {:db (-> db
               (update :token-gen-time + 50)
               (update :token-gen-time min 3000)
               (update :tokens conj (new-token db)))}
      {})))

(defn process-token-movement
  [token dt]
  (update token :y - (/ (* (:speed token) dt) 1000)))

(defn process-tokens-movement
  [tokens dt]
  (map #(process-token-movement % dt) tokens))

(rf/reg-event-db
  :token/movement
  (fn [db [_ dt]]
    (-> db
        (update :tokens process-tokens-movement dt))))

(rf/reg-event-fx
  :token/collision
  (fn [{:keys [db]} [_ dt]]
    (let [{collisions true
           remaining false} (group-by (partial collision? (:player db)) (:tokens db))]
      {:db (assoc db :tokens remaining)
       :dispatch-n (map (fn [t] [:token/collide t]) collisions)})))

(rf/reg-event-db
  :token/collide
  (fn [db [_ token]]
    (-> db
        (update :dodges conj {:id (gensym)
                              :x (:x token)
                              :y (:y token)
                              :time 800
                              :msg (get-in db [:player :skill])
                              :color "green"})
        (update-in [:player :score] + (get-in db [:player :skill])))))

(rf/reg-event-db
  :token/bottom
  (fn [db [_ dt]]
    (update db :tokens process-bottom)))

;; rocks

(defn new-rock
  [{:keys [board]}]
  {:id (gensym)
   :speed (+ 70 (rand 140))
   ;:dmg (+ 12 (rand 18))
   :dmg 10
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 30})

(rf/reg-event-db
  :rock/generate
  (fn [db [_ dt]]
    (if (>= dt (rand (:rock-gen-time db)))
      (-> db
          (update :rock-gen-time - 50)
          (update :rock-gen-time max 500)
          (update :rocks conj (new-rock db)))
      db)))

(defn process-rock-movement
  [rock dt]
  (update rock :y - (/ (* (:speed rock) dt) 1000)))

(defn process-rocks-movement
  [rocks dt]
  (map #(process-rock-movement % dt) rocks))

(rf/reg-event-db
  :rock/movement
  (fn [db [_ dt]]
    (update db :rocks process-rocks-movement dt)))

;; higher morale gives a better chance at dodging
(defn dodge?
  [{:keys [morale]}]
  (>= morale (rand 100)))

(rf/reg-event-fx
  :rock/collision
  (fn [{:keys [db]} [_ dt]]
    (let [{collisions true
           remaining false} (group-by (partial collision? (:player db)) (:rocks db))
          {hits false
           dodged true} (group-by #(dodge? (:player db)) collisions)]
      {:db (assoc db :rocks remaining)
       :dispatch-n (concat
                     (map (fn [r] [:rock/collide r]) hits)
                     (map (fn [r] [:rock/dodged r]) dodged))})))

(rf/reg-event-db
  :rock/collide
  (fn [db [_ rock]]
    (-> db
        (update :dodges conj {:id (gensym)
                              :x (:x rock)
                              :y (:y rock)
                              :time 800
                              :msg (int (:dmg rock))
                              :color "red"})
        (update-in [:player :current-hp] - (:dmg rock))
        (update-in [:player :current-hp] max 0))))

(rf/reg-event-db
  :rock/dodged
  (fn [db [_ rock]]
    (update db :dodges conj {:id (gensym)
                             :x (:x rock)
                             :y (:y rock)
                             :time 700
                             :msg "dodged!"
                             :color "red"})))

(rf/reg-event-db
  :rock/bottom
  (fn [db [_ dt]]
    (update db :rocks process-bottom)))

;; dodges

(rf/reg-event-db
  :dodge/decrement
  (fn [db [_ dt]]
    (update db :dodges (fn [dodges]
                         (map (fn [d]
                                (update d :time - dt))
                              dodges)))))

(rf/reg-event-db
  :dodge/clear
  (fn [db [_ dt]]
    (update db :dodges (fn [dodges]
                         (filter (fn [d]
                                   (<= 0 (:time d)))
                                 dodges)))))

;; health potions

(defn new-potion
  [{:keys [board]}]
  {:id (gensym)
   :speed (+ 25 (rand 40))
   ;:hp (+ 5 (rand 10))
   :hp 20
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(rf/reg-event-db
  :potion/generate
  (fn [db [_ dt]]
    (if (>= dt (rand (:potion-gen-time db)))
      (-> db
          (update :potions conj (new-potion db)))
      db)))

;; token gen-time boosters

(rf/reg-event-db
  :potion/movement
  (fn [db [_ dt]]
    (update db :potions process-rocks-movement dt)))

(rf/reg-event-fx
  :potion/collision
  (fn [{:keys [db]} [_ dt]]
    (let [{collisions true
           remaining false} (group-by (partial collision? (:player db)) (:potions db))]
      {:db (assoc db :potions remaining)
       :dispatch-n (map (fn [p] [:potion/collide p]) collisions)})))

(rf/reg-event-db
  :potion/collide
  (fn [db [_ potion]]
    (-> db
        (update :dodges conj {:id (gensym)
                              :x (:x potion)
                              :y (:y potion)
                              :time 800
                              :msg (int (:hp potion))
                              :color "purple"})
        (update-in [:player :current-hp] + (:hp potion))
        (update-in [:player :current-hp] min (get-in db [:player :max-hp])))))

(rf/reg-event-db
  :potion/bottom
  (fn [db [_ dt]]
    (update db :potions process-bottom)))
