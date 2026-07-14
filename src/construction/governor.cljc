(ns construction.governor
  "Construction Governor -- the independent compliance layer named in
  `blueprint.edn` (`:itonami.blueprint/governor :construction-
  governor`) that earns the Construction Advisor the right to commit.
  The LLM has no notion of labor-safety/disaster law, whether a site's
  own measured weather figures actually still exceed its own
  jurisdiction's regulatory trigger, whether a mandatory post-severe-
  weather inspection has actually resolved its own reported hazard, or
  when an act stops being a draft and becomes a real-world alert
  dispatch, work-resume authorization, or legal report filing, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the construction-safety analog of `cloud-itonami-isic-
  3030`'s Aerospace Manufacturing Governor.

  Nine checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, a disaster alert with no
  human-approved weather determination behind it, a site still
  measurably over its own legal weather threshold, an incomplete/
  unresolved mandatory inspection, a build/handover with no building-
  code permit/inspection on file, a robot placement-verification
  mission that never ran or that independently re-checks out-of-
  tolerance, or a double dispatch/authorization/report/placement/
  handover). The confidence/high-stakes gate is SOFT: it asks a human
  to look, and the human may approve.

    1. Legal-basis missing       -- did the `:weather/assess`/actuation
                                     proposal cite an OFFICIAL source
                                     (`construction.facts`), or invent
                                     one?
    2. No approved stop-work
       assessment                -- for `:actuation/dispatch-alert`,
                                     has a `:weather/assess` proposal
                                     for this site actually been
                                     committed (which itself always
                                     required a human approval -- see
                                     `construction.phase`, `:weather/
                                     assess` is never in any phase's
                                     `:auto` set) with a `:stop-work`/
                                     `:review-required` recommendation
                                     on file? This is what makes
                                     `:actuation/dispatch-alert`'s
                                     auto-commit exception (see check 7
                                     / `construction.phase` ns
                                     docstring) SAFE: the alert can
                                     fire fast, but only on a
                                     determination a human already
                                     signed off on, never on the
                                     advisor's say-so alone.
    3. Weather still exceeds
       threshold                 -- for `:actuation/authorize-resume`
                                     in a `:quantitative`-threshold
                                     jurisdiction (Japan), INDEPENDENTLY
                                     recompute whether the site's own
                                     recorded weather figures still
                                     exceed its own jurisdiction's
                                     regulatory trigger
                                     (`construction.facts/weather-
                                     threshold-exceeded?`) -- needs no
                                     proposal inspection at all. This
                                     is the SAME two-sided/threshold-
                                     check family `aerospace.registry/
                                     assembly-tolerance-out-of-range?`
                                     established, applied here to a
                                     jurisdiction-scoped legal trigger
                                     instead of a per-unit spec bound.
                                     DELIBERATELY does not fire for
                                     `:qualitative` jurisdictions (USA,
                                     DEU/EU) -- there is no numeric
                                     bright line to independently
                                     re-check there; the risk-
                                     assessment judgment is the human
                                     safety officer's, which the
                                     permanent high-stakes gate below
                                     (check 7) already routes to every
                                     time. Never invent a threshold to
                                     force those jurisdictions through
                                     a HARD rule.
    4. Inspection incomplete     -- for `:actuation/authorize-resume`,
                                     has the mandatory post-severe-
                                     weather/earthquake site inspection
                                     actually been committed with a
                                     `:resolved` verdict on file?
    5. Unresolved hazard         -- reported by THIS proposal itself
                                     (an `:inspection/screen` that just
                                     found one), or already on file for
                                     the site (`:inspection/screen`/
                                     `:actuation/authorize-resume`).
                                     Evaluated UNCONDITIONALLY, the SAME
                                     discipline `aerospace.governor/
                                     ndt-defect-unresolved-violations`
                                     established for this fleet's NDT-
                                     defect concept, applied here to a
                                     site hazard.
    6. Fabricated accident report -- `:actuation/file-accident-report`
                                     for a site whose own
                                     `:injury-occurred?` is false --
                                     never file a report for a non-
                                     event.
    7. Confidence floor /
       high-stakes gate           -- LLM confidence below threshold, OR
                                     the op is `:actuation/authorize-
                                     resume`/`:actuation/file-accident-
                                     report`/`:actuation/file-periodic-
                                     report`/`:build/dispatch-placement`/
                                     `:handover/complete` (REAL legal/
                                     safety-critical / physical acts) ->
                                     escalate to a human.
                                     `:actuation/dispatch-alert` is
                                     DELIBERATELY EXCLUDED from this
                                     set -- see `construction.phase` ns
                                     docstring 'Actuation' section for
                                     why alert dispatch, uniquely among
                                     this actor's actuation events, MAY
                                     auto-commit when the governor is
                                     clean.

    8. Permit + completion
       inspection required        -- the GENUINELY-NEW HARD check for the
                                     physical robot-dispatch (build) slice
                                     (grep-verified UNIQUE fleet-wide).
                                     A `:build/dispatch-placement` or
                                     `:handover/complete` proposal on a
                                     site with NO ISSUED BUILDING PERMIT
                                     on file is HARD-held, and
                                     `:handover/complete` ADDITIONALLY
                                     requires a PASSED COMPLETION
                                     INSPECTION. Grounded in 建築基準法
                                     第6条（建築確認）/第7条（完了検査）,
                                     IBC §105（permit）/§111（CO + final
                                     inspection）, Landesbauordnung
                                     Baugenehmigung/Abnahme + EU CPR
                                     305/2011 -- cited per-jurisdiction in
                                     `construction.facts`. Fires ONLY for
                                     the two build ops, so the existing
                                     safety-slice checks (incl.
                                     `:inspection/screen`'s own
                                     `:unresolved-hazard` hold) are
                                     untouched.

    9. Robot placement-verification
       mission missing or
       independently out-of-
       tolerance                  -- for `:build/dispatch-placement`,
                                     has the robot pre-placement
                                     verification mission
                                     (`construction.robotics`) actually
                                     run and been recorded on the site
                                     (`:robotics-sim-verified?`)? AND
                                     INDEPENDENTLY recompute whether the
                                     site's own recorded as-built-
                                     deviation reading falls out of its
                                     own recorded tolerance bounds
                                     (`construction.robotics/
                                     simulation-out-of-tolerance?`),
                                     ignoring whatever :passed? verdict
                                     the mission run itself stored --
                                     the same 'ground truth, not self-
                                     report' discipline check 3
                                     (weather-still-exceeds-threshold)
                                     above uses for weather. Fires ONLY
                                     for `:build/dispatch-placement`,
                                     not `:handover/complete` -- the
                                     robot mission verifies the panel-
                                     placement dispatch itself, exactly
                                     as `automotive.governor`'s sibling
                                     check gates only `:actuation/
                                     dispatch-vehicle`, never
                                     certificate issuance
                                     (ADR-2607142800).

  Six more guards, double-dispatch/double-authorization/double-report/
  double-placement/double-handover prevention, are enforced but NOT
  listed as numbered HARD checks above because they need no upstream
  comparison at all -- `already-dispatched-violations`/`already-
  resumed-violations`/`already-accident-reported-violations`/
  `already-periodic-reported-violations`/`already-placement-dispatched-
  violations`/`already-handed-over-violations` refuse to repeat the
  SAME actuation for the SAME site twice, off dedicated boolean facts
  (never a `:status` value) -- the same 'check a dedicated boolean,
  not status' discipline every prior sibling governor's guards
  establish."
  (:require [construction.facts :as facts]
            [construction.robotics :as robotics]
            [construction.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Authorizing work-resume after a severe-weather/disaster hold, filing
  a legal accident report, filing a legal periodic-inspection report,
  dispatching a robot to physically place a building element, and
  handing over a completed structure are the five real-world actuation
  events this actor performs that ALWAYS need a human. `:actuation/
  dispatch-alert` (warning site workers) is intentionally NOT a member
  -- see `construction.phase` ns docstring."
  #{:actuation/authorize-resume :actuation/file-accident-report :actuation/file-periodic-report
    :build/dispatch-placement :handover/complete})

;; ----------------------------- checks -----------------------------

(defn- legal-basis-missing-violations
  "A `:weather/assess` (or actuation) proposal with no legal-basis
  citation is a HARD violation -- never invent a jurisdiction's
  work-stoppage/inspection/reporting requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:weather/assess :actuation/dispatch-alert :actuation/authorize-resume
                     :actuation/file-accident-report :actuation/file-periodic-report
                     :build/dispatch-placement :handover/complete} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-legal-basis
          :detail "公式legal-basisの引用が無い提案は災害安全対応として扱えない"}]))))

(defn- no-approved-stop-work-assessment-violations
  "For `:actuation/dispatch-alert`, a `:weather/assess` for this site
  must actually be COMMITTED (i.e. already human-approved, since
  `:weather/assess` is never auto-eligible) with a `:stop-work`/
  `:review-required` recommendation on file. This is what makes
  `:actuation/dispatch-alert`'s own auto-commit exception safe: fast,
  but only ever on a determination a human already signed off on."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-alert)
    (let [wa (store/weather-assessment-of st subject)]
      (when-not (contains? #{:stop-work :review-required} (:recommendation wa))
        [{:rule :no-approved-stop-work-assessment
          :detail (str subject " には人手承認済みの:stop-work/:review-required気象判定が無い状態での警報配信提案")}]))))

(defn- weather-still-exceeds-threshold-violations
  "For `:actuation/authorize-resume`, INDEPENDENTLY recompute whether
  the site's own recorded weather figures still exceed its own
  jurisdiction's regulatory trigger via `construction.facts/weather-
  threshold-exceeded?`. Fires ONLY when that returns `true` (a
  :quantitative jurisdiction confirmed still over threshold) --
  `:qualitative`/`nil` never trip this HARD check (see ns docstring)."
  [{:keys [op subject]} st]
  (when (= op :actuation/authorize-resume)
    (let [a (store/site st subject)]
      (when (true? (facts/weather-threshold-exceeded? (:jurisdiction a) a))
        [{:rule :weather-still-exceeds-threshold
          :detail (str subject " の現在の気象実測値が法定基準を依然超過（wind=" (:wind-speed-actual a)
                      " rain=" (:rainfall-actual a) " snow=" (:snowfall-actual a) "）")}]))))

(defn- inspection-incomplete-violations
  "For `:actuation/authorize-resume`, the mandatory post-severe-
  weather/earthquake inspection must actually be committed with a
  `:resolved` verdict -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (= op :actuation/authorize-resume)
    (let [insp (store/inspection-of st subject)]
      (when-not (= :resolved (:verdict insp))
        [{:rule :inspection-incomplete
          :detail "義務付けられた悪天候後点検が未実施または未解決の状態での作業再開authorize提案"}]))))

(defn- unresolved-hazard-violations
  "An unresolved hazard -- reported by THIS proposal (e.g. an
  `:inspection/screen` that itself just found one), or already on file
  in the store for the site (`:inspection/screen`/`:actuation/
  authorize-resume`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY so the screening op itself can HARD-hold on its own
  finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        site-id (when (contains? #{:inspection/screen :actuation/authorize-resume} op) subject)
        hit-on-file? (and site-id (= :unresolved (:verdict (store/inspection-of st site-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :unresolved-hazard
        :detail "未解決のハザードがある状態での点検確定/作業再開提案は進められない"}])))

(defn- fabricated-accident-report-violations
  "For `:actuation/file-accident-report`, the site's own
  `:injury-occurred?` must be true -- never file a report for a
  non-event."
  [{:keys [op subject]} st]
  (when (= op :actuation/file-accident-report)
    (let [a (store/site st subject)]
      (when-not (true? (:injury-occurred? a))
        [{:rule :fabricated-accident-report
          :detail (str subject " は負傷等の発生記録が無く、労働者死傷病報告相当の提出提案は成立しない")}]))))

(defn- already-dispatched-violations
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-alert)
    (when (store/site-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に警報配信済み")}])))

(defn- already-resumed-violations
  [{:keys [op subject]} st]
  (when (= op :actuation/authorize-resume)
    (when (store/site-already-resumed? st subject)
      [{:rule :already-resumed
        :detail (str subject " は既に作業再開authorize済み")}])))

(defn- already-accident-reported-violations
  [{:keys [op subject]} st]
  (when (= op :actuation/file-accident-report)
    (when (store/site-already-accident-reported? st subject)
      [{:rule :already-accident-reported
        :detail (str subject " は既に労働者死傷病報告相当を提出済み")}])))

(defn- already-periodic-reported-violations
  [{:keys [op subject]} st]
  (when (= op :actuation/file-periodic-report)
    (when (store/site-already-periodic-reported? st subject)
      [{:rule :already-periodic-reported
        :detail (str subject " は既に定期報告相当を提出済み")}])))

(defn- permit-and-inspection-required-violations
  "GENUINELY-NEW HARD check for the physical robot-dispatch (build)
  slice -- grep-verified UNIQUE fleet-wide (no prior sibling governor
  gates on a building permit). A physical build/handover op must have
  an ISSUED BUILDING PERMIT on file for the site, and `:handover/
  complete` must ADDITIONALLY have a PASSED COMPLETION INSPECTION on
  file. You cannot dispatch a robot to place a building element, nor
  hand over a structure, without the building-code prerequisites the
  jurisdiction's own permit/inspection regime requires. CONDITIONAL --
  fires ONLY for `:build/dispatch-placement` and `:handover/complete`,
  so the existing safety-slice ops (incl. `:inspection/screen`, which
  HARD-holds on its own `:unresolved-hazard` finding) are untouched.

  Grounded in REAL construction/building-code law, cited per
  jurisdiction in `construction.facts` (`:permit-basis` /
  `:completion-inspection-basis`):
    - JPN 建築基準法 第6条（建築確認 = permit before construction）/
      第7条（完了検査 = completion inspection before occupancy/handover）
    - USA IBC §105（Permits, F105.1 permit required before
      construction）/ §111（Certificate of Occupancy, issued only after
      a passed final inspection）-- ICC model code adopted as state/local
      law by the AHJ; honestly cited as a model code, not federal statute
    - DEU Landesbauordnung Baugenehmigung（permit）/ Abnahme（acceptance/
      final inspection）+ EU Construction Products Regulation (EU)
      No 305/2011（CE marking of construction products）; state-level
      BauO honestly layered with the EU product regulation

  Distinct from every existing check: this is about the BUILDING-CODE
  permit/inspection regime (a construction-site's right to build/occupy
  at all), not the disaster-safety weather/threshold/hazard/injury
  regime checks 1-7 cover. The site's own `:permit-issued?` /
  `:build-inspection-passed?` booleans (set via `:site/intake` patches,
  the same intake-as-fact-source pattern `:injury-occurred?` uses) are
  the independent fact the governor re-checks -- it never trusts the
  advisor's self-reported confidence that a permit exists."
  [{:keys [op subject]} st]
  (when (contains? #{:build/dispatch-placement :handover/complete} op)
    (let [a (store/site st subject)]
      (cond-> []
        (not (:permit-issued? a))
        (conj {:rule :permit-not-issued
               :detail (str subject " に建築確認/建築許可（permit）の記録が無い状態での建築作業/引渡し提案 -- "
                            (:permit-basis (facts/spec-basis (:jurisdiction a)) "no jurisdiction spec-basis"))})
        (and (= op :handover/complete) (not (:build-inspection-passed? a)))
        (conj {:rule :completion-inspection-not-passed
               :detail (str subject " に完了検査/completion inspection 合格の記録が無い状態での引渡し提案 -- "
                            (:completion-inspection-basis (facts/spec-basis (:jurisdiction a)) "no jurisdiction spec-basis"))})))))

(defn- robotics-simulation-violations
  "For `:build/dispatch-placement`: HARD hold if the robot pre-
  placement verification mission (`construction.robotics`) never ran
  and was recorded on the site (`:robotics-sim-verified?`), OR if it
  did but an INDEPENDENT recompute of the site's own as-built-deviation
  fields (`construction.robotics/simulation-out-of-tolerance?`) says
  out-of-tolerance right now -- never trusts the mission's own stored
  :passed? verdict alone, the same discipline `weather-still-exceeds-
  threshold-violations` above uses for weather. Fires ONLY for
  `:build/dispatch-placement`, not `:handover/complete` -- the robot
  mission verifies the panel-placement dispatch itself, not the later
  handover."
  [{:keys [op subject]} st]
  (when (= op :build/dispatch-placement)
    (let [a (store/site st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のロボット部材配置前検証ミッション（配筋スキャン/トータルステーション出来形測量/供試体圧縮強度試験）が未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の出来形（as-built）偏差実測値("
                       (:as-built-deviation-actual a) ")が独立再検証で許容範囲["
                       (:as-built-deviation-min a) "," (:as-built-deviation-max a) "]を逸脱")}]))))

(defn- already-placement-dispatched-violations
  [{:keys [op subject]} st]
  (when (= op :build/dispatch-placement)
    (when (store/site-already-placement-dispatched? st subject)
      [{:rule :already-placement-dispatched
        :detail (str subject " は既にロボット部材配置 dispatch 済み")}])))

(defn- already-handed-over-violations
  [{:keys [op subject]} st]
  (when (= op :handover/complete)
    (when (store/site-already-handed-over? st subject)
      [{:rule :already-handed-over
        :detail (str subject " は既に引渡し（handover）完了済み")}])))

(defn check
  "Censors a Construction Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (legal-basis-missing-violations request proposal)
                           (no-approved-stop-work-assessment-violations request st)
                           (weather-still-exceeds-threshold-violations request st)
                           (inspection-incomplete-violations request st)
                           (unresolved-hazard-violations request proposal st)
                           (fabricated-accident-report-violations request st)
                           (permit-and-inspection-required-violations request st)
                           (robotics-simulation-violations request st)
                           (already-dispatched-violations request st)
                           (already-resumed-violations request st)
                           (already-accident-reported-violations request st)
                           (already-periodic-reported-violations request st)
                           (already-placement-dispatched-violations request st)
                           (already-handed-over-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
