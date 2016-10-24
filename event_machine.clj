#!/usr/local/bin/lein exec

(use '[clojure.string :only (join split)])
(use 'pallet.algo.fsm.event-machine 'pallet.algo.fsm.fsm-dsl)

(println "Ready...")
(def out (agent *out*))
(def mode-timeout 1000) ; in msec
(def close-enough 150) ; in msec
(def inputs [16 17 18 19 20 21 22 23 24 25 26 27])
(def orig_inputs inputs)
(def outputs             [ 2     3     4     5    6     7     8     9     10    11    12    13    14    15])
(def outputs-start-state ["off" "off" "on"  "on" "off" "on"  "off" "off" "off" "off" "off" "off" "off" "off"])
                         ; power plugs start on
(def allowed-inputs (set inputs))
(def allowed-outputs (set outputs))
(def input-output-mapping {:16 2,  ; sofa SW 1 (right) -> sofa main light
                           :17 3,  ; sofa SW 2 -> sofa reading light
                           :18 15, ; sofa SW 3 -> sofa LED
                           :19 14, ; sofa SW 4 -> table LED
                           :20 11, ; entrance right SW -> kitchen main light
                           :21 12, ; entrance left  SW -> table main light
                           :22 11, ; sink SW left -> kitchen main light
                           :23 10, ; sink SW right -> kitchen LED
                           :24 8,  ;
                           :25 4,  ;
                           :26 5,  ;
                           :27 9   ;
                          })
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
     #{16,17} #{2,3,10,11,12,14,15} ; advanced using sofa SW 1+2
     #{20,21} #{2,3,10,11,12,14,15} ; advanced using entrance SW L+R
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

(defn default-outputs
 "set all outputs to default state"
 []
 (doseq [n (range (count outputs))]
  (write-output (str (now) "," (nth outputs n) "," (nth outputs-start-state n)))
 )
)

(defn action-per-set
 "for each special code - preform action or generate pre programmed outputs"
 [s]
 (case s
  [18 17 16] (gen-outputs-from-set [2 3 "500" -2 -3 "500" 2 3]) ; turn on 2 and 3, sleep 0.5 sec, off and back on
  [16 17 18] (gen-outputs-from-set [-2 -3 -11 -12 "500" 2 "500" -2 3 "500" -3 11 "500" -11 12 "500" -12 "500" 2 3 11 12]) ; runing lights
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



; start with all in default states
(default-outputs)


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
               (let [
                   history (first (get state :history))
                   prev-gpio (history :state-data)
                   prev-state-kw (history :state-kw)
                   state-kw (state :state-kw)
                   gpio (state :state-data)
               ]
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


