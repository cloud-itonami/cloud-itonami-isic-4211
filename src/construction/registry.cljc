(ns construction.registry
  "Pure-function alert-dispatch / work-resume-authorization / accident-
  report / periodic-report record construction -- an append-only
  construction-site book-of-record draft. Also builds the ROBOT-
  DISPATCH (build/handover) records: a robot panel-placement dispatch
  and a structure handover completion, the physical-build half of this
  actor's actuation surface.

  Like every sibling actor's registry, there is no single international
  check-digit standard for an alert-dispatch, resume-authorization,
  report, placement or handover reference number -- every
  operator/jurisdiction assigns its own reference format. This
  namespace does NOT invent one; it builds a jurisdiction-scoped
  sequence number and validates the record's required fields, the same
  honest, non-fabricating discipline `construction.facts` uses.

  `render-accident-report` / `render-periodic-report` produce the
  actual human-readable report DOCUMENT text (not just an internal EDN
  record) -- this is the concrete 報告書作成 (report generation)
  deliverable, citing the jurisdiction's legal basis inline so the
  document is self-evidencing about which law it is filed under.
  `render-handover-certificate` produces the analogous completion/
  handover certificate document, citing the jurisdiction's building-
  code completion-inspection basis.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real labor-bureau/building-authority filing system, no
  mail/phone send. It builds the RECORD/DOCUMENT an operator would
  keep and submit, not the act of submitting it or notifying anyone
  (that is `construction.operation`'s `:actuation/*`/:build/*/:handover/*
  ops + `construction.notify`, always human-gated except
  `:actuation/dispatch-alert` -- see README `Actuation`)."
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

(defn register-placement-dispatch
  "Validate + construct the ROBOT PLACEMENT-DISPATCH registration DRAFT
  -- the operator's own act of dispatching a construction robot to
  physically place a building element (e.g. an exterior-envelope
  panel), the real-world build actuation. Pure function -- does not
  move any real hardware; it builds the RECORD an operator would keep.
  `construction.governor` independently re-verifies an ISSUED BUILDING
  PERMIT is on file for this site before this is ever allowed to commit
  (you cannot place a building element without 建築確認 / IBC §105
  permit / BauO Baugenehmigung)."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "placement-dispatch: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "placement-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "placement-dispatch: sequence must be >= 0" {})))
  (let [placement-number (str (str/upper-case jurisdiction) "-PLC-" (zero-pad sequence 6))]
    {"record" {"record_id" placement-number "kind" "placement-dispatch-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "placement_number" placement-number}))

(defn register-handover-completion
  "Validate + construct the STRUCTURE HANDOVER-COMPLETION registration
  DRAFT -- the operator's own act of handing over the completed,
  inspected structure (引渡し), the real-world handover actuation. Pure
  function -- `construction.governor` independently re-verifies BOTH an
  ISSUED PERMIT and a PASSED COMPLETION INSPECTION are on file before
  this is ever allowed to commit (you cannot hand over a structure
  without 建築基準法 第7条 完了検査 / IBC §111 CO / BauO Abnahme)."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "handover-completion: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "handover-completion: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "handover-completion: sequence must be >= 0" {})))
  (let [handover-number (str (str/upper-case jurisdiction) "-HDO-" (zero-pad sequence 6))]
    {"record" {"record_id" handover-number "kind" "handover-completion-draft"
               "site_id" site-id "jurisdiction" jurisdiction "immutable" true}
     "handover_number" handover-number}))

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

(defn render-handover-certificate
  "Human-readable HANDOVER-COMPLETION certificate document text, citing
  the jurisdiction's building-code completion-inspection basis inline
  -- the concrete 引渡し (handover) deliverable, the build-slice
  analog of `render-periodic-report`. `site` is the site record at
  handover time; `handover-number` is from `register-handover-
  completion`."
  [{:keys [id name jurisdiction]} handover-number]
  (let [{:keys [completion-inspection-basis completion-inspection-provenance
                permit-basis owner-authority]} (facts/spec-basis jurisdiction)]
    (str "# Structure Handover / Completion Certificate (引渡証 相当)\n\n"
         "Handover number: " handover-number "\n"
         "Site: " name " (" id ")\n"
         "Jurisdiction: " jurisdiction "\n"
         "Filed with: " owner-authority "\n"
         "Completion-inspection basis: " completion-inspection-basis "\n"
         "Permit basis on record: " (or permit-basis "n/a") "\n"
         "Source: " completion-inspection-provenance "\n\n"
         "## Status\nDraft -- pending human safety-officer sign-off and final acceptance.\n")))

(defn append [history result]
  (conj (vec history) (get result "record")))
