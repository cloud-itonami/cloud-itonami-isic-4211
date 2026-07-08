(ns construction.facts-test
  (:require [clojure.test :refer [deftest is]]
            [construction.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN"))))
  (is (= :quantitative (:threshold-model (facts/spec-basis "JPN")))))

(deftest usa-and-deu-are-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA"))))
  (is (nil? (:thresholds (facts/spec-basis "USA"))))
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU"))))
  (is (nil? (:thresholds (facts/spec-basis "DEU")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

;; ----------------------------- weather-threshold-exceeded? -----------------------------

(deftest jpn-threshold-is-a-real-numeric-recheck
  (is (true? (facts/weather-threshold-exceeded? "JPN" {:wind-speed-actual 15 :rainfall-actual 0 :snowfall-actual 0})))
  (is (true? (facts/weather-threshold-exceeded? "JPN" {:wind-speed-actual 0 :rainfall-actual 60 :snowfall-actual 0})))
  (is (true? (facts/weather-threshold-exceeded? "JPN" {:wind-speed-actual 0 :rainfall-actual 0 :snowfall-actual 30})))
  (is (false? (facts/weather-threshold-exceeded? "JPN" {:wind-speed-actual 3 :rainfall-actual 10 :snowfall-actual 0}))))

(deftest usa-and-deu-never-get-a-fabricated-true-false
  (is (= :qualitative (facts/weather-threshold-exceeded? "USA" {:wind-speed-actual 40 :rainfall-actual 100 :snowfall-actual 0})))
  (is (= :qualitative (facts/weather-threshold-exceeded? "DEU" {:wind-speed-actual 40 :rainfall-actual 100 :snowfall-actual 0}))))

(deftest unknown-jurisdiction-returns-nil-not-a-guess
  (is (nil? (facts/weather-threshold-exceeded? "ATL" {:wind-speed-actual 40 :rainfall-actual 100 :snowfall-actual 0}))))

(deftest inspection-checklist-is-empty-for-uncovered-jurisdiction
  (is (seq (facts/inspection-checklist "JPN")))
  (is (= [] (facts/inspection-checklist "ATL"))))
