(ns construction.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `aerospace.store-contract-test` for the
  same pattern on the sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Community Housing Block C" (:name (store/site s "site-1"))))
      (is (= "JPN" (:jurisdiction (store/site s "site-1"))))
      (is (= 15 (:wind-speed-actual (store/site s "site-1"))))
      (is (false? (:hazard-unresolved? (store/site s "site-1"))))
      (is (false? (:injury-occurred? (store/site s "site-1"))))
      (is (true? (:hazard-unresolved? (store/site s "site-3"))))
      (is (false? (:alert-dispatched? (store/site s "site-1"))))
      (is (false? (:work-resumed? (store/site s "site-1"))))
      (is (= ["site-1" "site-2" "site-3" "site-4" "site-5" "site-6"] (mapv :id (store/all-sites s))))
      (is (nil? (store/weather-assessment-of s "site-1")))
      (is (nil? (store/inspection-of s "site-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/alert-history s)))
      (is (= [] (store/resume-history s)))
      (is (= [] (store/accident-report-history s)))
      (is (= [] (store/periodic-report-history s)))
      (is (= [] (store/placement-history s)))
      (is (= [] (store/handover-history s)))
      (is (zero? (store/next-alert-sequence s "JPN")))
      (is (zero? (store/next-placement-sequence s "JPN")))
      (is (zero? (store/next-handover-sequence s "JPN")))
      (is (false? (store/site-already-dispatched? s "site-1")))
      (is (false? (store/site-already-resumed? s "site-1")))
      (is (false? (store/site-already-accident-reported? s "site-1")))
      (is (false? (store/site-already-periodic-reported? s "site-1")))
      (is (false? (store/site-already-placement-dispatched? s "site-1")))
      (is (false? (store/site-already-handed-over? s "site-1")))
      (is (false? (:permit-issued? (store/site s "site-1"))) "build-slice permit fact starts false")
      (is (false? (:build-inspection-passed? (store/site s "site-1"))) "build-slice completion-inspection fact starts false")
      (is (some? (:build-target (store/site s "site-4"))) "site-4 carries a build-target for the panel-placement walkthrough")
      (is (false? (:robotics-sim-verified? (store/site s "site-1"))) "no robotics mission has run yet")
      (is (true? (:robotics-sim-verified? (store/site s "site-6"))) "seeded as already-on-file")
      (is (= 40 (:as-built-deviation-actual (store/site s "site-6")))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :site/upsert
                                 :value {:id "site-1" :wind-speed-actual 3}})
        (is (= 3 (:wind-speed-actual (store/site s "site-1"))))
        (is (= "Sakura Community Housing Block C" (:name (store/site s "site-1"))) "unrelated field preserved"))
      (testing "robotics-sim result commits via :site/upsert and reads back"
        (store/commit-record! s {:effect :site/upsert
                                 :value {:id "site-1" :robotics-sim-verified? true
                                        :robotics-sim-record {:mission-id "m-1" :passed? true}}})
        (is (true? (:robotics-sim-verified? (store/site s "site-1"))))
        (is (= {:mission-id "m-1" :passed? true} (:robotics-sim-record (store/site s "site-1"))))
        (is (= "Sakura Community Housing Block C" (:name (store/site s "site-1"))) "unrelated field still preserved"))
      (testing "weather-assessment / inspection payloads commit and read back"
        (store/commit-record! s {:effect :weather-assessment/set :path ["site-1"]
                                 :payload {:jurisdiction "JPN" :recommendation :monitor}})
        (is (= {:jurisdiction "JPN" :recommendation :monitor} (store/weather-assessment-of s "site-1")))
        (store/commit-record! s {:effect :inspection/set :path ["site-1"]
                                 :payload {:site-id "site-1" :verdict :resolved}})
        (is (= {:site-id "site-1" :verdict :resolved} (store/inspection-of s "site-1"))))
      (testing "alert dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :site/mark-alert-dispatched :path ["site-1"]})
        (is (= "JPN-ALT-000000" (get (first (store/alert-history s)) "record_id")))
        (is (true? (:alert-dispatched? (store/site s "site-1"))))
        (is (= 1 (store/next-alert-sequence s "JPN")))
        (is (true? (store/site-already-dispatched? s "site-1")))
        (is (false? (store/site-already-dispatched? s "site-2"))))
      (testing "resume authorization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :site/mark-resumed :path ["site-1"]})
        (is (= "JPN-RSM-000000" (get (first (store/resume-history s)) "record_id")))
        (is (true? (:work-resumed? (store/site s "site-1"))))
        (is (true? (store/site-already-resumed? s "site-1"))))
      (testing "accident report drafts a record + document, and advances the sequence"
        (store/commit-record! s {:effect :site/upsert :value {:id "site-1" :injury-occurred? true}})
        (store/commit-record! s {:effect :site/mark-accident-reported :path ["site-1"]})
        (is (= "JPN-ACR-000000" (get (first (store/accident-report-history s)) "record_id")))
        (is (some? (get (first (store/accident-report-history s)) "document")))
        (is (true? (:accident-reported? (store/site s "site-1"))))
        (is (true? (store/site-already-accident-reported? s "site-1"))))
      (testing "periodic report drafts a record + document, and advances the sequence"
        (store/commit-record! s {:effect :site/mark-periodic-reported :path ["site-1"]})
        (is (= "JPN-PDR-000000" (get (first (store/periodic-report-history s)) "record_id")))
        (is (some? (get (first (store/periodic-report-history s)) "document")))
        (is (true? (:periodic-report-filed? (store/site s "site-1"))))
        (is (true? (store/site-already-periodic-reported? s "site-1"))))
      (testing "placement dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :site/mark-placement-dispatched :path ["site-1"]})
        (is (= "JPN-PLC-000000" (get (first (store/placement-history s)) "record_id")))
        (is (= "placement-dispatch-draft" (get (first (store/placement-history s)) "kind")))
        (is (true? (:placement-dispatched? (store/site s "site-1"))))
        (is (= 1 (store/next-placement-sequence s "JPN")))
        (is (true? (store/site-already-placement-dispatched? s "site-1")))
        (is (false? (store/site-already-placement-dispatched? s "site-2"))))
      (testing "handover completion drafts a record + certificate document, and advances the sequence"
        (store/commit-record! s {:effect :site/mark-handed-over :path ["site-1"]})
        (is (= "JPN-HDO-000000" (get (first (store/handover-history s)) "record_id")))
        (is (= "handover-completion-draft" (get (first (store/handover-history s)) "kind")))
        (is (some? (get (first (store/handover-history s)) "document")) "rendered handover certificate present")
        (is (true? (:handed-over? (store/site s "site-1"))))
        (is (= 1 (store/next-handover-sequence s "JPN")))
        (is (true? (store/site-already-handed-over? s "site-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/site s "nope")))
    (is (= [] (store/all-sites s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/alert-history s)))
    (is (zero? (store/next-alert-sequence s "JPN")))
    (store/with-sites s {"x" {:id "x" :name "n" :jurisdiction "JPN"
                              :wind-speed-actual 3 :rainfall-actual 0 :snowfall-actual 0
                              :hazard-unresolved? false :injury-occurred? false
                              :alert-dispatched? false :work-resumed? false
                              :accident-reported? false :periodic-report-filed? false :status :intake}})
    (is (= "n" (:name (store/site s "x"))))))
