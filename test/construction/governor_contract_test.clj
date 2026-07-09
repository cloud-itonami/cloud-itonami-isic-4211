(ns construction.governor-contract-test
  "The governor contract as executable tests -- the construction-safety
  analog of `aerospace.governor-contract-test`. The single invariant
  under test:

    Construction Advisor never dispatches a disaster alert, authorizes
    work-resume, or files a report the Construction Governor would
    reject; `:actuation/authorize-resume`/`:actuation/file-accident-
    report`/`:actuation/file-periodic-report` NEVER auto-commit at any
    phase; `:site/intake` (no direct capital risk) MAY auto-commit
    when clean; `:actuation/dispatch-alert` MAY auto-commit when clean
    AND a human-approved stop-work assessment is on file (the
    deliberate safety-first exception); and every decision (commit OR
    hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [construction.store :as store]
            [construction.operation :as op]
            [construction.phase :as phase]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :safety-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through weather/assess -> approve, leaving a weather
  assessment on file."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :weather/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through hazard screening -> approve, leaving a
  screening on file. Only safe to call for a site whose hazard status
  has already resolved -- an unresolved hazard HARD-holds the screen
  itself."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :inspection/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :site/intake :subject "site-1"
                   :patch {:id "site-1" :name "Sakura Community Housing Block C"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Community Housing Block C" (:name (store/site db "site-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest weather-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :weather/assess :subject "site-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :stop-work (:recommendation (store/weather-assessment-of db "site-1"))))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a weather/assess proposal for an uncovered jurisdiction -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3" {:op :weather/assess :subject "site-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-legal-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/weather-assessment-of db "site-2")) "no assessment written"))))

(deftest dispatch-alert-without-approved-assessment-is-held
  (testing "no weather/assess on file yet -> HOLD, never auto-fires on the advisor's say-so alone"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-alert :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-approved-stop-work-assessment} (-> (store/ledger db) first :basis))))))

(deftest dispatch-alert-auto-commits-when-clean-the-deliberate-exception
  (testing "unlike every other actuation op in this fleet, a clean dispatch-alert commits WITHOUT interrupting for approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "site-1")
          res (exec-op actor "t5" {:op :actuation/dispatch-alert :subject "site-1"} operator)]
      (is (not= :interrupted (:status res)) "never pauses -- the safety-first auto-commit exception")
      (is (= :commit (get-in res [:state :disposition])))
      (is (true? (:alert-dispatched? (store/site db "site-1"))))
      (is (= 1 (count (store/alert-history db)))))))

(deftest resume-while-still-over-threshold-is-held
  (testing "site-1's wind (15 m/s) still exceeds JPN's 10 m/s threshold -> HARD hold, independent of proposal confidence"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre1" "site-1")
          _ (screen! actor "t6pre2" "site-1")
          res (exec-op actor "t6" {:op :actuation/authorize-resume :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:weather-still-exceeds-threshold} (-> (store/ledger db) last :basis)))
      (is (false? (:work-resumed? (store/site db "site-1")))))))

(deftest resume-without-inspection-is-held
  (testing "no inspection screen on file at all -> HOLD (inspection-incomplete)"
    (let [[db actor] (fresh)]
      (store/commit-record! db {:effect :site/upsert :value {:id "site-1" :wind-speed-actual 3}})
      (let [res (exec-op actor "t7" {:op :actuation/authorize-resume :subject "site-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:inspection-incomplete} (-> (store/ledger db) first :basis)))))))

(deftest unresolved-hazard-is-held-and-unoverridable
  (testing "site-3 has hazard-unresolved? true -> HOLD, settles immediately, never reaches a human -- exercised via :inspection/screen DIRECTLY"
    (let [[db actor] (fresh)
          res (exec-op actor "t8" {:op :inspection/screen :subject "site-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:unresolved-hazard} (-> (store/ledger db) first :basis)))
      (is (nil? (store/inspection-of db "site-3")) "no clearance written"))))

(deftest fabricated-accident-report-is-held
  (testing "site-4 has injury-occurred? false -> HOLD, never file a report for a non-event"
    (let [[db actor] (fresh)
          res (exec-op actor "t9" {:op :actuation/file-accident-report :subject "site-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:fabricated-accident-report} (-> (store/ledger db) first :basis)))
      (is (false? (:accident-reported? (store/site db "site-4")))))))

(deftest authorize-resume-always-escalates-then-human-decides
  (testing "a clean, fully-inspected, weather-cleared site still ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre1" "site-1")
          _ (screen! actor "t10pre2" "site-1")
          _ (store/commit-record! db {:effect :site/upsert :value {:id "site-1" :wind-speed-actual 3}})
          r1 (exec-op actor "t10" {:op :actuation/authorize-resume :subject "site-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (let [r2 (approve! actor "t10")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:work-resumed? (store/site db "site-1"))))
        (is (= 1 (count (store/resume-history db))))))))

(deftest file-accident-report-always-escalates-then-human-decides
  (let [[db actor] (fresh)
        _ (store/commit-record! db {:effect :site/upsert :value {:id "site-1" :injury-occurred? true}})
        r1 (exec-op actor "t11" {:op :actuation/file-accident-report :subject "site-1"} operator)]
    (is (= :interrupted (:status r1)))
    (let [r2 (approve! actor "t11")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (true? (:accident-reported? (store/site db "site-1"))))
      (is (some? (get (first (store/accident-report-history db)) "document")) "rendered report document present"))))

(deftest file-periodic-report-always-escalates-then-human-decides
  (let [[db actor] (fresh)
        r1 (exec-op actor "t12" {:op :actuation/file-periodic-report :subject "site-1"} operator)]
    (is (= :interrupted (:status r1)))
    (let [r2 (approve! actor "t12")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (true? (:periodic-report-filed? (store/site db "site-1")))))))

(deftest double-dispatch-alert-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t13pre" "site-1")
        _ (exec-op actor "t13a" {:op :actuation/dispatch-alert :subject "site-1"} operator)
        res (exec-op actor "t13" {:op :actuation/dispatch-alert :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/alert-history db))))))

(deftest double-resume-is-held
  (let [[db actor] (fresh)
        _ (assess! actor "t14pre1" "site-1")
        _ (screen! actor "t14pre2" "site-1")
        _ (store/commit-record! db {:effect :site/upsert :value {:id "site-1" :wind-speed-actual 3}})
        _ (exec-op actor "t14a" {:op :actuation/authorize-resume :subject "site-1"} operator)
        _ (approve! actor "t14a")
        res (exec-op actor "t14" {:op :actuation/authorize-resume :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-resumed} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/resume-history db))))))

(deftest double-accident-report-is-held
  (let [[db actor] (fresh)
        _ (store/commit-record! db {:effect :site/upsert :value {:id "site-1" :injury-occurred? true}})
        _ (exec-op actor "t15a" {:op :actuation/file-accident-report :subject "site-1"} operator)
        _ (approve! actor "t15a")
        res (exec-op actor "t15" {:op :actuation/file-accident-report :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-accident-reported} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/accident-report-history db))))))

(deftest double-periodic-report-is-held
  (let [[db actor] (fresh)
        _ (exec-op actor "t16a" {:op :actuation/file-periodic-report :subject "site-1"} operator)
        _ (approve! actor "t16a")
        res (exec-op actor "t16" {:op :actuation/file-periodic-report :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-periodic-reported} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/periodic-report-history db))))))

(deftest qualitative-jurisdiction-never-fabricates-a-numeric-hard-hold
  (testing "USA (qualitative) -- weather/assess and authorize-resume never invent a numeric threshold, but resume still always needs a human"
    (let [[db actor] (fresh)
          _ (assess! actor "t17pre1" "site-5")
          _ (screen! actor "t17pre2" "site-5")
          r1 (exec-op actor "t17" {:op :actuation/authorize-resume :subject "site-5"} operator)]
      (is (= :interrupted (:status r1)) "no numeric bright-line to auto-clear on -- still always a human's call")
      (let [r2 (approve! actor "t17")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:work-resumed? (store/site db "site-5"))))))))

;; ----------------------------- robot-dispatch (build/handover) slice -----------------------------

(defn- permit!
  "Record an ISSUED BUILDING PERMIT on `subject` via an intake patch
  (auto-commits). Mirrors how the safety slice sets `:injury-occurred?`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-permit") {:op :site/intake :subject subject
                                             :patch {:id subject :permit-issued? true}} operator))

(defn- build-inspection-passed!
  "Record a PASSED COMPLETION INSPECTION on `subject` via an intake patch
  (auto-commits)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-insp") {:op :site/intake :subject subject
                                           :patch {:id subject :build-inspection-passed? true}} operator))

(deftest placement-without-permit-is-held
  (testing "site-1 has no building permit on file -> HARD hold, the physical dispatch never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "b1" {:op :build/dispatch-placement :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:permit-not-issued} (-> (store/ledger db) first :basis)))
      (is (false? (:placement-dispatched? (store/site db "site-1")))))))

(deftest handover-without-permit-and-inspection-is-held-with-both-violations
  (testing "site-1 has neither permit nor a passed completion inspection -> HARD hold reporting BOTH violations"
    (let [[db actor] (fresh)
          res (exec-op actor "b2" {:op :handover/complete :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:permit-not-issued} (-> (store/ledger db) first :basis)))
      (is (some #{:completion-inspection-not-passed} (-> (store/ledger db) first :basis)))
      (is (false? (:handed-over? (store/site db "site-1")))))))

(deftest handover-with-permit-but-no-completion-inspection-is-held
  (testing "a permit alone is not enough for handover -- a passed completion inspection is separately required"
    (let [[db actor] (fresh)
          _ (permit! actor "b3pre" "site-1")
          res (exec-op actor "b3" {:op :handover/complete :subject "site-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:completion-inspection-not-passed} (-> (store/ledger db) last :basis)))
      (is (not (some #{:permit-not-issued} (-> (store/ledger db) last :basis))) "permit IS on file -- not the violation here"))))

(deftest placement-always-escalates-then-human-decides
  (testing "a permitted site's panel-placement STILL ALWAYS interrupts for human approval -- a physical act, never auto"
    (let [[db actor] (fresh)
          _ (permit! actor "b4pre" "site-1")
          r1 (exec-op actor "b4" {:op :build/dispatch-placement :subject "site-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (let [r2 (approve! actor "b4")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:placement-dispatched? (store/site db "site-1"))))
        (is (= "JPN-PLC-000000" (get (first (store/placement-history db)) "record_id")))
        (is (= 1 (count (store/placement-history db))))))))

(deftest handover-always-escalates-then-human-decides
  (testing "a permitted + completion-inspected site's handover STILL ALWAYS interrupts for human approval"
    (let [[db actor] (fresh)
          _ (permit! actor "b5pre" "site-1")
          _ (build-inspection-passed! actor "b5pre" "site-1")
          r1 (exec-op actor "b5" {:op :handover/complete :subject "site-1"} operator)]
      (is (= :interrupted (:status r1)))
      (let [r2 (approve! actor "b5")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:handed-over? (store/site db "site-1"))))
        (is (some? (get (first (store/handover-history db)) "document")) "rendered handover certificate present")
        (is (= "JPN-HDO-000000" (get (first (store/handover-history db)) "record_id")))))))

(deftest double-placement-is-held
  (let [[db actor] (fresh)
        _ (permit! actor "b6pre" "site-1")
        _ (exec-op actor "b6a" {:op :build/dispatch-placement :subject "site-1"} operator)
        _ (approve! actor "b6a")
        res (exec-op actor "b6" {:op :build/dispatch-placement :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-placement-dispatched} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/placement-history db))))))

(deftest double-handover-is-held
  (let [[db actor] (fresh)
        _ (permit! actor "b7pre" "site-1")
        _ (build-inspection-passed! actor "b7pre" "site-1")
        _ (exec-op actor "b7a" {:op :handover/complete :subject "site-1"} operator)
        _ (approve! actor "b7a")
        res (exec-op actor "b7" {:op :handover/complete :subject "site-1"} operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:already-handed-over} (-> (store/ledger db) last :basis)))
    (is (= 1 (count (store/handover-history db))))))

(deftest placement-and-handover-never-auto-at-any-phase
  (testing "structural invariant: a physical placement / handover is never auto-eligible at any phase, even when clean"
    (doseq [op [:build/dispatch-placement :handover/complete]]
      (is (= :escalate (:disposition (phase/gate 3 {:op op} :commit)))
          (str op " must escalate to a human even when the governor is clean at phase 3")))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :site/intake :subject "site-1"
                          :patch {:id "site-1" :name "Sakura Community Housing Block C"}} operator)
      (exec-op actor "b" {:op :weather/assess :subject "site-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
