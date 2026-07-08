(ns construction.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a site through a full
  typhoon/disaster episode: intake (severe weather already measured) ->
  weather assessment (stop-work, human approves) -> hazard-inspection
  screening (resolved, human approves) -> alert dispatch (AUTO-commits
  -- the deliberate exception, mail+phone actually 'sent' via the mock
  notifier) -> resume authorization attempt WHILE still over threshold
  (HARD hold) -> weather clears (intake patch, auto-commits) -> resume
  authorization retried (human approves) -> an injury is recorded ->
  accident-report filed (human approves) -> periodic-report filed
  (human approves), then five HARD holds (an uncovered jurisdiction, an
  unresolved hazard screened directly, a fabricated accident report
  for a site with no injury, a double alert-dispatch, a double
  resume-authorization) that never reach a human at all, then a
  qualitative-jurisdiction (USA) walkthrough showing no threshold is
  ever fabricated there, and finally prints the audit ledger + the
  draft records + the rendered report documents."
  (:require [langgraph.graph :as g]
            [construction.store :as store]
            [construction.notify :as notify]
            [construction.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :safety-officer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        notifier (notify/mock-notifier)
        actor (op/build db {:notifier notifier})]
    (println "== site/intake site-1 (JPN, wind=15m/s already over 10m/s threshold) ==")
    (println (exec! actor "t1" {:op :site/intake :subject "site-1"
                                :patch {:id "site-1" :name "Sakura Community Housing Block C"}} operator))

    (println "== weather/assess site-1 (escalates -- human approves; recommendation=:stop-work) ==")
    (println (exec! actor "t2" {:op :weather/assess :subject "site-1"} operator))
    (println (approve! actor "t2"))

    (println "== inspection/screen site-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :inspection/screen :subject "site-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-alert site-1 (AUTO-COMMITS -- the deliberate safety-first exception; mail+phone actually sent via mock notifier) ==")
    (println (exec! actor "t4" {:op :actuation/dispatch-alert :subject "site-1"} operator))
    (println "-- sent log --")
    (println (notify/sent-log notifier))

    (println "== actuation/authorize-resume site-1 WHILE still over threshold (HARD hold, never reaches a human) ==")
    (println (exec! actor "t5" {:op :actuation/authorize-resume :subject "site-1"} operator))

    (println "== site/intake site-1: storm has passed (wind 15 -> 3) (auto-commits) ==")
    (println (exec! actor "t6" {:op :site/intake :subject "site-1" :patch {:id "site-1" :wind-speed-actual 3}} operator))

    (println "== actuation/authorize-resume site-1 AGAIN (escalates -- human approves) ==")
    (let [r (exec! actor "t7" {:op :actuation/authorize-resume :subject "site-1"} operator)]
      (println r)
      (println "-- human safety officer approves --")
      (println (approve! actor "t7")))

    (println "== site/intake site-1: an injury occurred during the storm (auto-commits) ==")
    (println (exec! actor "t8" {:op :site/intake :subject "site-1"
                                :patch {:id "site-1" :injury-occurred? true
                                       :injury-description "Worker slipped on wet scaffolding before evacuation."}} operator))

    (println "== actuation/file-accident-report site-1 (escalates -- human approves) ==")
    (let [r (exec! actor "t9" {:op :actuation/file-accident-report :subject "site-1"} operator)]
      (println r)
      (println "-- human safety officer approves --")
      (println (approve! actor "t9")))

    (println "== actuation/file-periodic-report site-1 (escalates -- human approves) ==")
    (let [r (exec! actor "t10" {:op :actuation/file-periodic-report :subject "site-1"} operator)]
      (println r)
      (println "-- human safety officer approves --")
      (println (approve! actor "t10")))

    (println "== weather/assess site-2 (ATL, no spec-basis -> HARD hold) ==")
    (println (exec! actor "t11" {:op :weather/assess :subject "site-2" :no-spec? true} operator))

    (println "== inspection/screen site-3 (unresolved hazard -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t12" {:op :inspection/screen :subject "site-3"} operator))

    (println "== actuation/file-accident-report site-4 (no injury on file -> HARD hold, never fabricated) ==")
    (println (exec! actor "t13" {:op :actuation/file-accident-report :subject "site-4"} operator))

    (println "== actuation/dispatch-alert site-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t14" {:op :actuation/dispatch-alert :subject "site-1"} operator))

    (println "== actuation/authorize-resume site-1 AGAIN (double-resume -> HARD hold) ==")
    (println (exec! actor "t15" {:op :actuation/authorize-resume :subject "site-1"} operator))

    (println "== USA (qualitative jurisdiction) -- weather/assess site-5 never fabricates a numeric threshold ==")
    (println (exec! actor "t16" {:op :weather/assess :subject "site-5"} operator))
    (println (approve! actor "t16"))
    (println (exec! actor "t17" {:op :inspection/screen :subject "site-5"} operator))
    (println (approve! actor "t17"))
    (println "-- authorize-resume site-5: no numeric bright-line to recheck (qualitative) -- still always escalates to a human --")
    (let [r (exec! actor "t18" {:op :actuation/authorize-resume :subject "site-5"} operator)]
      (println r)
      (println (approve! actor "t18")))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft alert-dispatch records ==")
    (doseq [r (store/alert-history db)] (println r))

    (println "== draft resume-authorization records ==")
    (doseq [r (store/resume-history db)] (println r))

    (println "== accident-report document (site-1) ==")
    (doseq [r (store/accident-report-history db)] (println (get r "document")))

    (println "== periodic-report document (site-1) ==")
    (doseq [r (store/periodic-report-history db)] (println (get r "document")))))
