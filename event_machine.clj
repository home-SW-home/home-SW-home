#!/usr/local/bin/lein exec

(use '[clojure.string :only (join split)])
(use 'pallet.algo.fsm.event-machine 'pallet.algo.fsm.fsm-dsl)

(println "Ready...")
(def out (agent *out*))
(def mode-timeout 5000) ; in msec
(def close-enough 150) ; in msec
(def inputs [16 17 18 19 20 21 22 23 24 25 26 27])
(def orig_inputs inputs)
(def outputs [2 3 4 5 6 7 8 9 10 11 12 13 14 15])
(def allowed-inputs (set inputs))
(def allowed-outputs (set outputs))
; note: more outputs than inputs
(def input-output-mapping (into {} (for [ n (range (count inputs)) ] [(keyword (str(nth inputs n))),(nth outputs n)])))
; start with all gpio states off
(def gpio-states (into {} (for [ n (range (count outputs))] [(keyword (str (nth outputs n))),"unknown"])))

;(println gpio-states)
;(println input-output-mapping)

(def advanced-sets)

; general util funcitions

(defprotocol PStack
  "A stack protocol"
  (stack-push [this val] "Push element into the stack")
  (stack-pop [this] "Pop element from stack")
  (stack-top [this] "Get top element from stack")
  (stack-all [this] "Get all the element from stack")
)

(defrecord Stack
  [coll]
  PStack
  (stack-push [_ val]
	(swap! coll conj val))
  (stack-pop [_]
       (let [ret (first @coll)]
	 (swap! coll rest)
	 ret))
  (stack-top [_]
       (first @coll))
  (stack-all [_]
       @coll))

(defn update-output-state
 "keeps the local gpio states updated"
 [line]
 (def gpio-kw (keyword (second (split line #","))))
 (def action (nth (split line #",") 2))
 (def gpio-states (update-in gpio-states [gpio-kw] (fn [x] action)))
 ;(println gpio-states)
)

(defn write-output
  "writes to the outpus file in sync"
  [line]
  (update-output-state line)
  (with-open [w (clojure.java.io/writer  "/var/log/raw_outputs.csv" :append true)]
    (.write w (str line "\n"))
    (flush)))

(defn parse-int [s]
   (Integer. (re-find  #"\d+" s )))

(defn now []
 (.getTime (java.util.Date.))
)

; local service functions

(defn output-per-input
  "determines which outputs are controlled by specific inputs"
  ([input]
   (input-output-mapping (keyword (str input))))
  ([i1 i2]
   (def t (sorted-set i1 i2))
   ;(println t)
   (case t
     #{16,17} #{2,3,4,5,6,7}
     #{}
   )
  )
)

(defn gen-outputs-from-set
 "translates a local switch programming language to timed file outputs"
 [commands-set]
 (doseq [c commands-set]
  ;(println (str (type c)))
  (case (str (type c))
   ; plus number for on and minus number for off
   "class java.lang.Long" (if (> c 0)
                     (write-output (str (now) "," c ",on"))
                     (write-output (str (now) "," (- c) ",off"))
                    )
   ; sleep value in msec
   "class java.lang.String" (Thread/sleep (parse-int c))
   ; default
   (println (str "Wrong command in set:" c))
  )
 )
)

(defn all-outputs
 "set all outputs to one state"
 [action]
 ; action is "on" or "off"
 (doseq [n (range (count outputs))]
  (write-output (str (now) "," (nth outputs n) "," action))
 )
)

(defn shuffle-mapping
   "shaffle the input to output mapping"
   []
   (def inputs (shuffle inputs))
   (def input-output-mapping (into {} (for [ n (range (count inputs)) ]
                                      [(keyword (str(nth inputs n))),(nth outputs n)])))
)

(defn un-shuffle-mapping
   "revert the shaffle of the input to output mapping"
   []
   (def inputs orig_inputs)
   (def input-output-mapping (into {} (for [ n (range (count inputs)) ]
                                      [(keyword (str(nth inputs n))),(nth outputs n)])))
)

(defn action-per-set
 "for each special code - preform action or generate pre programmed outputs"
 [s]
 (case s
  [18 17 16] (gen-outputs-from-set [2 3 "1000" -2 -3]) ; turn on 2 and 3, sleep 1 sec, turn off 2 and 3
  [16 17 18] (gen-outputs-from-set [4 "500" -4 "1000" 4 "500" -4]) ; blink on 4
  [16] (shuffle-mapping) ; shuffle
  [17] (un-shuffle-mapping) ; cancel shuffle
  (println (str "Unknown special command:" s))                  ; default is empty program
 )
)

(defn is-gpio-off
 "checks if a specific gpio is off"
 [gpio]
 (def gpio-kw (keyword (str gpio)))
 (def last-gpio-state (gpio-states gpio-kw))
 (= last-gpio-state "off")
)

(defn get-toggled-action
 "gets a single opposite state for gpio set"
 ([gpio]
  (if (is-gpio-off gpio)
   "on"
   "off"
  )
 )
 ([gpio & rest]
  ; if all are off, then turn all on
  ; otherwise, turn all off
  (if (and (is-gpio-off gpio) (every? is-gpio-off rest))
   "on"
   "off"
  )
 )
)


(defn advanced-action
 "preforms the required action in advanced mode according to the last two inputs"
 [gpio prev-gpio]
  (def advanced-outputs-set (output-per-input gpio prev-gpio))
  (def action (apply get-toggled-action advanced-outputs-set))
  (doseq [x advanced-outputs-set] (write-output (str (now) "," x "," action)))
)



; start with all off
(all-outputs "off")


; state machine configuration

(let [
      listen-mode1 (fn [state event event-data]
                    (if (= (state :state-kw) :timed-out)
                     (if (= event :button)
                      (assoc state :state-kw :button-pressed :state-data event-data :timeout {:ms close-enough})
                     state)
                    )
                   )

      listen-mode2 (fn [state event event-data]
                    (if (= (state :state-kw) :button-pressed)
                     (if (= event :button)
                      (assoc state :state-kw :advanced :state-data event-data :timeout {:ms close-enough})
                      state
                     )
                    )
                   )

      listen-mode3 (fn [state event event-data]
                    (def inputs-stack (Stack. (atom '())))
;                    (stack-push inputs-stack event-data)
                    (if (= (state :state-kw) :advanced)
                     (if (= event :button)
                       (assoc state :state-kw :special :state-data event-data :timeout {:ms mode-timeout})
                      state
                     )
                    )
                   )

      listen-mode4 (fn [state event event-data]
                    (stack-push inputs-stack event-data)
                    (if (= (state :state-kw) :special)
                     (if (= event :button)
                       (assoc state :state-kw :special :state-data event-data :timeout {:ms mode-timeout})
                      state
                     )
                    )
                   )

      output (fn [state]
               (def history (first (get state :history)))
               (def prev-gpio (history :state-data))
               (def prev-state-kw (history :state-kw))
               (def state-kw (state :state-kw))
               (def gpio (state :state-data))
               ;(println prev-state-kw)
               ;(println (state :state-data))
               (if (or (= prev-state-kw :advanced) (= prev-state-kw :special))
                ; special mode
                (action-per-set (stack-all inputs-stack))
                (if (= prev-state-kw :button-pressed)
                 ; advanced mode
                  (advanced-action gpio prev-gpio)
                  ; timed-out (basic) mode
                  (if (= prev-state-kw :timed-out)
                    (if-not (= nil (allowed-inputs gpio))
                      (write-output (str (now) "," (output-per-input gpio) "," (get-toggled-action (output-per-input gpio))))
                      (println (str "not allowed inputs:" gpio))
                    )
                    (println prev-state-kw "WTF?")
                    )
                  )
               )
              )

      {:keys [state event event-data] :as sm}
      (event-machine
        (event-machine-config
          (initial-state :timed-out)
          (using-fsm-features :on-enter-exit)
          (using-stateful-fsm-features :timeout :history)
          (state :timed-out
            (valid-transitions :timed-out :button-pressed)
            (event-handler listen-mode1)
            (on-enter output))
          (state :advanced
            (event-handler listen-mode3)
            (valid-transitions :special :timed-out))
          (state :special
            (event-handler listen-mode4)
            (valid-transitions :special :timed-out))
          (state :button-pressed
            (event-handler listen-mode2)
            (valid-transitions :button-pressed :advanced :timed-out))))]


(defn process-line
 "generates button actions according to line input"
 [line]
 (def epoch (bigdec (first (split line #","))))
 (def gpio (parse-int (second (split line #","))))
 (def action (nth (split line #",") 2))
  (if (= action "pressed")
   (event :button gpio)
  )
)


; file reading loop

(loop []
   (let [line (read-line)]
      (when line
            (process-line line)) ;generates :button events
              ;(println (state))
              (recur)))
)


 ; (event :trying nil) ; => {:state-kw :ready}


