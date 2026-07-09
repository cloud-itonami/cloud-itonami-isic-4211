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
  ever fabricated there.

  THEN the ROBOT-DISPATCH (build) slice on site-4 (田中ビル外墙改修,
  exterior-wall renovation -- the Operator Guide's exterior-envelope-
  panel Day-in-the-life example): the permit/design record is
  registered via intake (auto-commits) -> a robot panel-placement is
  dispatched (human approves -- a physical act, never auto) -> the
  completion inspection is recorded via intake (auto-commits) -> the
  structure is handed over (human approves, handover certificate
  rendered) -> a USA (IBC §105/§111) build walkthrough -> then four
  HARD holds (a placement with NO permit on file, a handover with no
  permit+completion-inspection, a double placement, a double handover)
  that never reach a human. Finally prints the audit ledger + the
  draft records + the rendered report/certificate documents."
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

    (println)
    (println "== ROBOT-DISPATCH (build) slice -- site-4 (田中ビル外墙改修, exterior-wall renovation) ==")
    (println "== site/intake site-4: permit/design record registered (建築確認 issued) (auto-commits) ==")
    (println (exec! actor "t19" {:op :site/intake :subject "site-4"
                                 :patch {:id "site-4" :permit-issued? true :status :permit}} operator))

    (println "== build/dispatch-placement site-4 (robot places exterior-envelope-panel @ north-wall-unit-4; escalates -- human approves, a physical act never auto) ==")
    (let [r (exec! actor "t20" {:op :build/dispatch-placement :subject "site-4"} operator)]
      (println r)
      (println "-- human safety officer approves the robot placement dispatch --")
      (println (approve! actor "t20")))

    (println "== site/intake site-4: completion inspection passed (完了検査/IBC §111 final inspection) (auto-commits) ==")
    (println (exec! actor "t21" {:op :site/intake :subject "site-4"
                                 :patch {:id "site-4" :build-inspection-passed? true :status :inspect}} operator))

    (println "== handover/complete site-4 (escalates -- human approves, handover certificate rendered) ==")
    (let [r (exec! actor "t22" {:op :handover/complete :subject "site-4"} operator)]
      (println r)
      (println "-- human safety officer approves the structure handover --")
      (println (approve! actor "t22")))

    (println "== USA (IBC §105/§111) build walkthrough -- site-5 permit + completion inspection recorded, then handed over ==")
    (println (exec! actor "t23" {:op :site/intake :subject "site-5"
                                 :patch {:id "site-5" :permit-issued? true :build-inspection-passed? true}} operator))
    (let [r (exec! actor "t24" {:op :handover/complete :subject "site-5"} operator)]
      (println r)
      (println (approve! actor "t24")))

    (println "== build/dispatch-placement site-1 (JPN, NO permit on file -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t25" {:op :build/dispatch-placement :subject "site-1"} operator))

    (println "== handover/complete site-1 (NO permit AND NO completion inspection -> HARD hold with BOTH violations) ==")
    (println (exec! actor "t26" {:op :handover/complete :subject "site-1"} operator))

    (println "== build/dispatch-placement site-4 AGAIN (double-placement -> HARD hold) ==")
    (println (exec! actor "t27" {:op :build/dispatch-placement :subject "site-4"} operator))

    (println "== handover/complete site-4 AGAIN (double-handover -> HARD hold) ==")
    (println (exec! actor "t28" {:op :handover/complete :subject "site-4"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft alert-dispatch records ==")
    (doseq [r (store/alert-history db)] (println r))

    (println "== draft resume-authorization records ==")
    (doseq [r (store/resume-history db)] (println r))

    (println "== accident-report document (site-1) ==")
    (doseq [r (store/accident-report-history db)] (println (get r "document")))

    (println "== periodic-report document (site-1) ==")
    (doseq [r (store/periodic-report-history db)] (println (get r "document")))

    (println "== draft placement-dispatch records ==")
    (doseq [r (store/placement-history db)] (println r))

    (println "== draft handover-completion records ==")
    (doseq [r (store/handover-history db)] (println r))

    (println "== handover-certificate document (site-4) ==")
    (doseq [r (store/handover-history db)] (println (get r "document")))))
