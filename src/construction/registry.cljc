(ns construction.registry
  "Pure-function alert-dispatch / work-resume-authorization / accident-
  report / periodic-report record construction -- an append-only
  construction-site disaster-safety book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for an alert-dispatch, resume-authorization or
  report reference number -- every operator/jurisdiction assigns its
  own reference format. This namespace does NOT invent one; it builds
  a jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `construction.facts` uses.

  `render-accident-report` / `render-periodic-report` produce the
  actual human-readable report DOCUMENT text (not just an internal EDN
  record) -- this is the concrete 報告書作成 (report generation)
  deliverable, citing the jurisdiction's legal basis inline so the
  document is self-evidencing about which law it is filed under.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real labor-bureau/building-authority filing system, no
  mail/phone send. It builds the RECORD/DOCUMENT an operator would
  keep and submit, not the act of submitting it or notifying anyone
  (that is `construction.operation`'s `:actuation/*` ops + `construction.
  notify`, always human-gated except `:actuation/dispatch-alert` -- see
  README `Actuation`)."
  (:require [clojure.string :as str]
            [construction.facts :as facts]))

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-alert-dispatch
  "Validate + construct the ALERT-DISPATCH registration DRAFT -- the
  operator's own act of warning site workers/foremen of an imminent or
  ongoing severe-weather/disaster condition by mail + phone. Pure
  function -- does not touch any real mail/phone transport; it builds
  the RECORD an operator would keep. `construction.governor`
  independently re-verifies the site is not already dispatched before
  this is ever allowed to commit."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "alert-dispatch: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "alert-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "alert-dispatch: sequence must be >= 0" {})))
  (let [alert-number (str (str/upper-case jurisdiction) "-ALT-" (zero-pad sequence 6))]
    {"record" {"record_id" alert-number "kind" "alert-dispatch-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "alert_number" alert-number}))

(defn register-resume-authorization
  "Validate + construct the WORK-RESUME-AUTHORIZATION registration
  DRAFT -- the operator's own act of authorizing work to resume at a
  site after a severe-weather/disaster hold. Pure function --
  `construction.governor` independently re-verifies the mandatory
  post-event inspection is on file and resolved, and that the site's
  own weather figures no longer exceed its jurisdiction's threshold,
  before this is ever allowed to commit."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "resume-authorization: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "resume-authorization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "resume-authorization: sequence must be >= 0" {})))
  (let [resume-number (str (str/upper-case jurisdiction) "-RSM-" (zero-pad sequence 6))]
    {"record" {"record_id" resume-number "kind" "resume-authorization-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "resume_number" resume-number}))

(defn register-accident-report
  "Validate + construct the ACCIDENT-REPORT registration DRAFT (labor
  injury/accident report, e.g. Japan's 労働者死傷病報告). Pure function
  -- `construction.governor` independently re-verifies the site
  actually has `:injury-occurred?` true before this is ever allowed to
  commit (never file a report for a non-event)."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "accident-report: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "accident-report: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "accident-report: sequence must be >= 0" {})))
  (let [report-number (str (str/upper-case jurisdiction) "-ACR-" (zero-pad sequence 6))]
    {"record" {"record_id" report-number "kind" "accident-report-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "report_number" report-number}))

(defn register-periodic-report
  "Validate + construct the PERIODIC-REPORT registration DRAFT (routine
  building/equipment inspection report, e.g. Japan's Building Standards
  Act Art.12 12条点検). Pure function."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "periodic-report: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "periodic-report: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "periodic-report: sequence must be >= 0" {})))
  (let [report-number (str (str/upper-case jurisdiction) "-PDR-" (zero-pad sequence 6))]
    {"record" {"record_id" report-number "kind" "periodic-report-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "report_number" report-number}))

;; ----------------------------- report documents -----------------------------

(defn render-accident-report
  "Human-readable ACCIDENT-REPORT document text, citing the
  jurisdiction's legal basis inline -- the concrete 報告書作成
  deliverable. `site` is the site record at filing time; `report-
  number` is from `register-accident-report`."
  [{:keys [id name jurisdiction injury-description]} report-number]
  (let [{:keys [accident-report-basis accident-report-provenance owner-authority]} (facts/spec-basis jurisdiction)]
    (str "# Accident / Occupational Injury Report (労働者死傷病報告 相当)\n\n"
         "Report number: " report-number "\n"
         "Site: " name " (" id ")\n"
         "Jurisdiction: " jurisdiction "\n"
         "Filed with: " owner-authority "\n"
         "Legal basis: " accident-report-basis "\n"
         "Source: " accident-report-provenance "\n\n"
         "## Incident description\n" (or injury-description "(not recorded)") "\n\n"
         "## Status\nDraft -- pending human safety-officer review and submission.\n")))

(defn render-periodic-report
  "Human-readable PERIODIC-REPORT document text (routine building/
  equipment inspection filing), citing the jurisdiction's legal basis
  inline."
  [{:keys [id name jurisdiction]} report-number]
  (let [{:keys [periodic-report-basis periodic-report-provenance periodic-report-note owner-authority]}
        (facts/spec-basis jurisdiction)]
    (str "# Periodic Inspection Report (12条点検 相当)\n\n"
         "Report number: " report-number "\n"
         "Site: " name " (" id ")\n"
         "Jurisdiction: " jurisdiction "\n"
         "Filed with: " owner-authority "\n"
         "Legal basis: " (or periodic-report-basis
                             (str "NOT COVERED -- " (or periodic-report-note
                                                        "no jurisdiction-level basis on file; do not fabricate one"))) "\n"
         "Source: " (or periodic-report-provenance "n/a") "\n\n"
         "## Status\nDraft -- pending human safety-officer review and submission.\n")))

(defn append [history result]
  (conj (vec history) (get result "record")))
