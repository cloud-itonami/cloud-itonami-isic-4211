(ns construction.registry-test
  (:require [clojure.test :refer [deftest is]]
            [construction.registry :as r]))

;; ----------------------------- register-alert-dispatch -----------------------------

(deftest alert-dispatch-assigns-alert-number
  (let [result (r/register-alert-dispatch "site-1" "JPN" 7)]
    (is (= (get result "alert_number") "JPN-ALT-000007"))
    (is (= (get-in result ["record" "site_id"]) "site-1"))
    (is (= (get-in result ["record" "kind"]) "alert-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest alert-dispatch-validation-rules
  (is (thrown? Exception (r/register-alert-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-alert-dispatch "site-1" "" 0)))
  (is (thrown? Exception (r/register-alert-dispatch "site-1" "JPN" -1))))

;; ----------------------------- register-resume-authorization -----------------------------

(deftest resume-authorization-assigns-resume-number
  (let [result (r/register-resume-authorization "site-1" "JPN" 3)]
    (is (= (get result "resume_number") "JPN-RSM-000003"))
    (is (= (get-in result ["record" "kind"]) "resume-authorization-draft"))))

(deftest resume-authorization-validation-rules
  (is (thrown? Exception (r/register-resume-authorization "" "JPN" 0)))
  (is (thrown? Exception (r/register-resume-authorization "site-1" "" 0)))
  (is (thrown? Exception (r/register-resume-authorization "site-1" "JPN" -1))))

;; ----------------------------- register-accident-report / register-periodic-report -----------------------------

(deftest accident-report-assigns-report-number
  (let [result (r/register-accident-report "site-1" "JPN" 0)]
    (is (= (get result "report_number") "JPN-ACR-000000"))
    (is (= (get-in result ["record" "kind"]) "accident-report-draft"))))

(deftest periodic-report-assigns-report-number
  (let [result (r/register-periodic-report "site-1" "JPN" 0)]
    (is (= (get result "report_number") "JPN-PDR-000000"))
    (is (= (get-in result ["record" "kind"]) "periodic-report-draft"))))

(deftest report-validation-rules
  (is (thrown? Exception (r/register-accident-report "" "JPN" 0)))
  (is (thrown? Exception (r/register-periodic-report "site-1" "" 0)))
  (is (thrown? Exception (r/register-accident-report "site-1" "JPN" -1))))

;; ----------------------------- render-accident-report / render-periodic-report -----------------------------

(def sample-site
  {:id "site-1" :name "Sakura Community Housing Block C" :jurisdiction "JPN"
   :injury-description "Worker slipped on wet scaffolding before evacuation."})

(deftest accident-report-document-cites-legal-basis-inline
  (let [doc (r/render-accident-report sample-site "JPN-ACR-000000")]
    (is (re-find #"JPN-ACR-000000" doc))
    (is (re-find #"労働安全衛生規則 第97条" doc))
    (is (re-find #"laws\.e-gov\.go\.jp" doc))
    (is (re-find #"Worker slipped" doc))))

(deftest periodic-report-document-cites-legal-basis-inline
  (let [doc (r/render-periodic-report sample-site "JPN-PDR-000000")]
    (is (re-find #"JPN-PDR-000000" doc))
    (is (re-find #"建築基準法 第12条" doc))
    (is (re-find #"mlit\.go\.jp" doc))))

(deftest periodic-report-document-is-honest-about-uncovered-basis
  (let [doc (r/render-periodic-report (assoc sample-site :jurisdiction "USA") "USA-PDR-000000")]
    (is (re-find #"NOT COVERED" doc))))

(deftest history-is-append-only
  (let [c1 (r/register-alert-dispatch "site-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-alert-dispatch "site-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-ALT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-ALT-000001" (get-in hist2 [1 "record_id"])))))
