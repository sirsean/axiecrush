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

(rf/reg-cofx
  :window-size
  (fn [cofx]
    (assoc cofx
           :window-size
           {:width (.-innerWidth js/window)
            :height (.-innerHeight js/window)})))

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
            :level 1
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
   :item-gen-time {:potion 20000
                   :freeze-player 7000}
   :rocks-frozen 0
   :player-frozen 0
   :buffs []
   :tokens []
   :rocks []
   :dodges []
   :items []})

(defn level->item-gen-time
  [level]
  (case level
    2 {:token-boost 10000
       :freeze-rocks 15000
       :freeze-player 6000}
    3 {:token-boost 9000
       :clear-rocks 30000
       :freeze-player 5000}
    4 {:token-boost 8000
       :freeze-rocks 10000
       :freeze-player 4000}
    5 {:token-boost 7000
       :freeze-player 3000}
    {}))

(defn ->with-board-size
  [db board]
  (update db :board merge (select-keys board [:width :height])))

(defn ->with-axie
  [db axie]
  (-> db
      (assoc-in [:player :x] (/ (:width (:board db)) 2))
      (update :player merge (select-keys (:stats axie) [:speed :morale :skill]))
      (update :player merge {:max-hp (:hp (:stats axie))
                             :current-hp (:hp (:stats axie))})
      (update :player merge (case (:stage axie)
                              3 {:width 60 :height 50}
                              {:width 120 :height 100}))
      (update :player assoc :image (:image axie))))

(defn rocks-frozen?
  [db]
  (pos? (:rocks-frozen db)))

(defn player-frozen?
  [db]
  (pos? (:player-frozen db)))

(defn has-part?
  [axie part-id]
  (let [parts (->> axie :parts (map :id) set)]
    (contains? parts part-id)))

(defn buff->slow-rocks
  [{:keys [buffs]}]
  (->> buffs
       (filter #(= :slow-rocks (:kind %)))
       (map :by)
       (reduce + 0)))

(defn buff->weak-rocks
  [{:keys [buffs]}]
  (->> buffs
       (filter #(= :weak-rocks (:kind %)))
       (map :multiple)
       (reduce * 1)))

(defn buff->boost-morale
  [{:keys [buffs]}]
  (->> buffs
       (filter #(= :boost-morale (:kind %)))
       (map :by)
       (reduce + 0)))

(defn buff->boost-token-rate
  [{:keys [buffs]}]
  (->> buffs
       (filter #(= :boost-token-rate (:kind %)))
       (map :by)
       (reduce + 0)))

(defn buff->boost-speed
  [{:keys [buffs]}]
  (->> buffs
       (filter #(= :boost-speed (:kind %)))
       (map :by)
       (reduce + 0)))

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
  [(rf/inject-cofx :window-size)]
  (fn [{:keys [db window-size]} _]
    {:db (-> db
             (merge new-game)
             (->with-board-size (-> window-size
                                    (update :width - 30)
                                    (update :height - 40)))
             (->with-axie (:axie db)))
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
        (:running? db) (merge {:dispatch-n [[:token/events dt]
                                            [:rock/events dt]
                                            [:dodge/events dt]
                                            [:item/events dt]
                                            [:level/events dt]
                                            [:buff/events dt]
                                            [:player/death dt]]})))))

(defn player-speed
  [db]
  (+ (:speed (:player db)) (buff->boost-speed db)))

(rf/reg-event-db
  :move/left
  (fn [db _]
    (cond-> db
      (and (not (player-frozen? db))
           (:running? db))
      (->
        (assoc-in [:player :dir] :left)
        (update-in [:player :x] - (player-speed db))
        (update-in [:player :x] max (/ (:width (:player db)) 2))))))

(rf/reg-event-db
  :move/right
  (fn [db _]
    (cond-> db
      (and (not (player-frozen? db))
           (:running? db))
      (->
        (assoc-in [:player :dir] :right)
        (update-in [:player :x] + (player-speed db))
        (update-in [:player :x] min (- (:width (:board db))
                                       (/ (:width (:player db)) 2)))))))

(rf/reg-event-db
  :move/down
  (fn [db _]
    (cond-> db
      (and (not (player-frozen? db))
           (:running? db))
      (->
        (update-in [:player :y] - (player-speed db))
        (update-in [:player :y] max (/ (:height (:player db)) 2))))))

(rf/reg-event-db
  :move/up
  (fn [db _]
    (cond-> db
      (and (not (player-frozen? db))
           (:running? db))
      (->
        (update-in [:player :y] + (player-speed db))
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

(rf/reg-event-fx
  :buff/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:buff/countdown dt]]}))

(defn countdown-buff
  [buff dt]
  (update buff :for - dt))

(defn countdown-buffs
  [buffs dt]
  (map #(countdown-buff % dt) buffs))

(defn remove-empty-buffs
  [buffs]
  (filter #(pos? (:for %)) buffs))

(rf/reg-event-db
  :buff/countdown
  (fn [db [_ dt]]
    (-> db
        (update :buffs countdown-buffs dt)
        (update :buffs remove-empty-buffs))))

(rf/reg-event-db
  :buff/hit
  (fn [{:keys [axie] :as db} [_ rock]]
    (cond-> db
      (has-part? axie "horn-unko")
      (update :buffs conj {:id (gensym)
                           :kind :slow-rocks
                           :by 30
                           :for 3000})

      (has-part? axie "horn-pinku-unko")
      (update :buffs conj {:id (gensym)
                           :kind :slow-rocks
                           :by 30
                           :for 3000})

      (has-part? axie "mouth-kotaro")
      (update :buffs conj {:id (gensym)
                           :kind :slow-rocks
                           :by 30
                           :for 3000})

      (has-part? axie "horn-incisor")
      (update :buffs conj {:id (gensym)
                           :kind :slow-rocks
                           :by 30
                           :for 3000})

      (has-part? axie "mouth-pincer")
      (update :buffs conj {:id (gensym)
                           :kind :weak-rocks
                           :multiple 0.5
                           :for 3000})

      (has-part? axie "mouth-confident")
      (update :buffs conj {:id (gensym)
                           :kind :boost-morale
                           :by 6
                           :for 6000})

      (has-part? axie "tail-shiba")
      (update :buffs conj {:id (gensym)
                           :kind :boost-morale
                           :by 3
                           :for 3000})

      (has-part? axie "back-goldfish")
      (update :buffs conj {:id (gensym)
                           :kind :boost-speed
                           :by 3
                           :for 3000}))))

(rf/reg-event-db
  :buff/dodge
  (fn [{:keys [axie] :as db} [_ rock]]
    (cond-> db
      (has-part? axie "horn-arco")
      (update :buffs conj {:id (gensym)
                           :kind :boost-token-rate
                           :by 500
                           :for 3000})

      (has-part? axie "mouth-axie-kiss")
      (update :buffs conj {:id (gensym)
                           :kind :boost-morale
                           :by 3
                           :for 6000}))))

(defn process-item-movement
  [{:keys [speed] :as item} dt {:keys [slow-down]}]
  (let [speed (max (- speed slow-down) 0)]
    (update item :y - (/ (* speed dt) 1000))))

(defn process-items-movement
  [items dt buffs]
  (map #(process-item-movement % dt buffs) items))

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

;; levels

(rf/reg-event-fx
  :level/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:level/check-level dt]]}))

(rf/reg-event-fx
  :level/check-level
  (fn [{:keys [db]} _]
    (let [level (:level (:player db))
          current-level (inc (quot (:score (:player db)) 1000))]
      (cond-> {:db (assoc-in db [:player :level] current-level)}
        (not= level current-level)
        (merge {:dispatch [:level/up]})))))

(rf/reg-event-fx
  :level/up
  (fn [{:keys [db]} _]
    {:db (-> db
             (update :token-gen-time / 2)
             (update :item-gen-time merge (level->item-gen-time (:level (:player db)))))
     :dispatch [:rock/create
                (* 5 (:level (:player db)))
                :barrage]}))

;; tokens

(rf/reg-event-fx
  :token/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:token/generate dt]
                  [:token/movement dt]
                  [:token/collision dt]
                  [:token/bottom dt]]}))

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
    (if (>= dt (rand (- (:token-gen-time db)
                        (buff->boost-token-rate db))))
      {:db (-> db
               (update :token-gen-time + 50)
               (update :token-gen-time min 2000)
               (update :tokens conj (new-token db)))}
      {})))

(rf/reg-event-db
  :token/movement
  (fn [db [_ dt]]
    (-> db
        (update :tokens process-items-movement dt))))

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

(rf/reg-event-fx
  :rock/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:rock/generate dt]
                  [:rock/movement dt]
                  [:rock/collision dt]
                  [:rock/bottom dt]]}))

(defn new-rock
  [{:keys [board player]} {:keys [kind]}]
  {:id (gensym)
   :kind kind
   :speed (+ 50 (* 10 (:level player)) (rand 140))
   :dmg (* 4 (:level player))
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 30})

(rf/reg-event-db
  :rock/create
  (fn [db [_ n kind]]
    (update db :rocks into (repeatedly n (partial new-rock db {:kind kind})))))

(rf/reg-event-fx
  :rock/generate
  (fn [{:keys [db]} [_ dt]]
    (if (and (not (rocks-frozen? db))
             (>= dt (rand (:rock-gen-time db))))
      {:db (-> db
               (update :rock-gen-time - 50)
               (update :rock-gen-time max 500))
       :dispatch [:rock/create 1 :default]}
      {})))

(rf/reg-event-db
  :rock/movement
  (fn [db [_ dt]]
    (cond-> db
      (not (rocks-frozen? db))
      (update :rocks process-items-movement dt {:slow-down (buff->slow-rocks db)}))))

;; higher morale gives a better chance at dodging
(defn dodge?
  [{:keys [player] :as db}]
  (>= (+ (:morale player) (buff->boost-morale db)) (rand 100)))

(rf/reg-event-fx
  :rock/collision
  (fn [{:keys [db]} [_ dt]]
    (let [{collisions true
           remaining false} (group-by (partial collision? (:player db)) (:rocks db))
          {hits false
           dodged true} (group-by #(dodge? db) collisions)]
      {:db (assoc db :rocks remaining)
       :dispatch-n (concat
                     (map (fn [r] [:rock/collide r]) hits)
                     (map (fn [r] [:rock/dodged r]) dodged))})))

(rf/reg-event-fx
  :rock/collide
  (fn [{:keys [db]} [_ rock]]
    {:db (-> db
             (update :dodges conj {:id (gensym)
                                   :x (:x rock)
                                   :y (:y rock)
                                   :time 800
                                   :msg (int (* (:dmg rock)
                                                (buff->weak-rocks db)))
                                   :color "red"})
             (update-in [:player :current-hp] - (:dmg rock))
             (update-in [:player :current-hp] max 0))
     :dispatch [:buff/hit rock]}))

(rf/reg-event-fx
  :rock/dodged
  (fn [{:keys [db]} [_ rock]]
    {:db (update db :dodges conj {:id (gensym)
                                  :x (:x rock)
                                  :y (:y rock)
                                  :time 700
                                  :msg "dodged!"
                                  :color "red"})
     :dispatch [:buff/dodge rock]}))

(rf/reg-event-db
  :rock/bottom
  (fn [db [_ dt]]
    (update db :rocks process-bottom)))

;; dodges

(rf/reg-event-fx
  :dodge/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:dodge/decrement dt]
                  [:dodge/clear dt]]}))

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

;; items

(rf/reg-event-fx
  :item/events
  (fn [_ [_ dt]]
    {:dispatch-n [[:item/generate dt]
                  [:item/movement dt]
                  [:item/collision dt]
                  [:item/effects dt]
                  [:item/bottom dt]]}))

(defn new-potion
  [{:keys [board]}]
  {:id (gensym)
   :kind :potion
   :speed (+ 25 (rand 40))
   :hp 20
   :msg 20
   :color "purple"
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(defn new-clear-rocks
  [{:keys [board]}]
  {:id (gensym)
   :kind :clear-rocks
   :speed (+ 95 (rand 60))
   :msg "clear rocks!"
   :color "purple"
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(defn new-freeze-rocks
  [{:keys [board]}]
  {:id (gensym)
   :kind :freeze-rocks
   :speed (+ 65 (rand 80))
   :freeze-ms 1500
   :msg "freeze rocks!"
   :color "purple"
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(defn new-freeze-player
  [{:keys [board]}]
  {:id (gensym)
   :kind :freeze-player
   :speed (+ 65 (rand 80))
   :freeze-ms 1000
   :msg "freeze!"
   :color "red"
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(defn new-token-boost
  [{:keys [board]}]
  {:id (gensym)
   :kind :token-boost
   :speed (+ 25 (rand 40))
   :boost 500
   :msg "token boost!"
   :color "purple"
   :x (+ 60 (rand (- (:width board) 100)))
   :y (- (:height board) 30)
   :width 30
   :height 40})

(defn collide-fn
  [db item]
  (case (:kind item)
    :potion (-> db
                (update-in [:player :current-hp] + (:hp item))
                (update-in [:player :current-hp] min (get-in db [:player :max-hp])))
    :clear-rocks (-> db
                     (assoc :rocks []))
    :freeze-rocks (-> db
                      (update :rocks-frozen + (:freeze-ms item)))
    :freeze-player (-> db
                       (update :player-frozen + (:freeze-ms item)))
    :token-boost (-> db
                     (update :token-gen-time - (:boost item))
                     (update :token-gen-time max 100))))

(rf/reg-event-db
  :item/generate
  (fn [db [_ dt]]
    (update db :items into
            (->> (:item-gen-time db)
                 (keep (fn [[kind gen-time]]
                         (when (>= dt (rand gen-time))
                           kind)))
                 (map (fn [kind]
                        (case kind
                          :potion (new-potion db)
                          :clear-rocks (new-clear-rocks db)
                          :freeze-rocks (new-freeze-rocks db)
                          :freeze-player (new-freeze-player db)
                          :token-boost (new-token-boost db))))))))

(rf/reg-event-db
  :item/movement
  (fn [db [_ dt]]
    (update db :items process-items-movement dt)))

(rf/reg-event-fx
  :item/collision
  (fn [{:keys [db]} [_ dt]]
    (let [{collisions true
           remaining false} (group-by (partial collision? (:player db)) (:items db))]
      {:db (assoc db :items remaining)
       :dispatch-n (map (fn [p] [:item/collide p]) collisions)})))

(rf/reg-event-db
  :item/collide
  (fn [db [_ item]]
    (-> db
        (update :dodges conj {:id (gensym)
                              :x (:x item)
                              :y (:y item)
                              :time 800
                              :msg (:msg item)
                              :color (:color item)})
        (collide-fn item))))

(rf/reg-event-db
  :item/effects
  (fn [db [_ dt]]
    (-> db
        (update :rocks-frozen - dt)
        (update :rocks-frozen max 0)
        (update :player-frozen - dt)
        (update :player-frozen max 0))))

(rf/reg-event-db
  :item/bottom
  (fn [db [_ dt]]
    (update db :items process-bottom)))
