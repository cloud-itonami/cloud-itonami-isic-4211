(ns construction.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/authorize-resume`/`:actuation/file-accident-
  report`/`:actuation/file-periodic-report` must NEVER be a member of
  any phase's `:auto` set. `:actuation/dispatch-alert` is the ONE
  deliberate exception -- see `construction.phase` ns docstring
  'Actuation' section."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.phase :as phase]))

(deftest authorize-resume-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real work-resume authorization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/authorize-resume))
          (str "phase " n " must not auto-commit :actuation/authorize-resume")))))

(deftest file-accident-report-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :actuation/file-accident-report))
        (str "phase " n " must not auto-commit :actuation/file-accident-report"))))

(deftest file-periodic-report-never-auto-at-any-phase
  (doseq [[n {:keys [auto]}] phase/phases]
    (is (not (contains? auto :actuation/file-periodic-report))
        (str "phase " n " must not auto-commit :actuation/file-periodic-report"))))

(deftest inspection-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :inspection/screen))
          (str "phase " n " must not auto-commit :inspection/screen")))))

(deftest dispatch-alert-IS-auto-eligible-at-phase-3-the-deliberate-exception
  (testing "the one actuation op in this actor that MAY auto-commit when the governor is clean -- see ns docstring"
    (is (contains? (:auto (get phase/phases 3)) :actuation/dispatch-alert))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-set-is-exactly-intake-and-dispatch-alert
  (is (= #{:site/intake :actuation/dispatch-alert} (:auto (get phase/phases 3)))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :site/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/authorize-resume} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :weather/assess} :commit)))))

(deftest gate-auto-commits-dispatch-alert-when-clean-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :actuation/dispatch-alert} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :site/intake} :commit)))))
