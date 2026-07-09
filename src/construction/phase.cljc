(ns construction.phase
  "Phase 0->3 staged rollout -- the construction-safety analog of
  `aerospace.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- site intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds weather assessment + hazard
                                 screening writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:site/intake` (no capital risk yet)
                                 AND `:actuation/dispatch-alert` may
                                 auto-commit.

  ## Actuation

  `:actuation/authorize-resume`, `:actuation/file-accident-report`,
  `:actuation/file-periodic-report`, `:build/dispatch-placement` and
  `:handover/complete` are deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Authorizing work to resume after a
  disaster hold, filing a real legal report, dispatching a robot to
  physically place a building element, and handing over a completed
  structure are real-world acts this actor performs; all four are
  always a human safety officer's call. `construction.governor`'s
  high-stakes gate enforces the same invariant independently -- two
  layers, not one, agree on this.

  `:actuation/dispatch-alert` is the ONE actuation op that IS a member
  of phase 3's `:auto` set -- a deliberate, reasoned exception to the
  'actuation is always human' norm this fleet otherwise holds
  (`aerospace.phase`, and every prior sibling's actuation gate). The
  reasoning: dispatching a disaster/severe-weather warning to site
  workers by mail + phone is a SAFETY-POSITIVE action whose primary
  risk is asymmetric -- a delayed warning (waiting on a human who may
  be unavailable, asleep, or unreachable during the disaster itself)
  can cost lives, while an unnecessary warning costs almost nothing.
  This is the opposite risk shape from every other actuation event in
  this fleet (dispatching a robot action, issuing certification/
  airworthiness evidence, authorizing work-resume, filing a legal
  report), where an erroneous auto-commit is the harm to guard against.
  The Construction Governor still gates it: `:actuation/dispatch-alert`
  only auto-commits when `construction.governor/check` is clean (a
  real legal-basis citation on file, the site not already dispatched)
  -- see `construction.governor` high-stakes set and check 1
  (legal-basis-missing). `:inspection/screen` is never auto-eligible,
  at any phase -- the same posture every sibling's screening op has."
  )

(def read-ops  #{})
(def write-ops #{:site/intake :weather/assess :inspection/screen
                 :actuation/dispatch-alert :actuation/authorize-resume
                 :actuation/file-accident-report :actuation/file-periodic-report
                 :build/dispatch-placement :handover/complete})

;; NOTE the invariant: `:actuation/authorize-resume`/`:actuation/file-
;; accident-report`/`:actuation/file-periodic-report`/`:build/dispatch-
;; placement`/`:handover/complete` are members of `write-ops`
;; (governor-gated like any write) but are NEVER members of any phase's
;; `:auto` set below. Do not add them there -- dispatching a robot to
;; physically place a building element and handing over a completed
;; structure are real physical acts that ALWAYS need a human safety
;; officer, exactly like authorizing work-resume or filing a legal
;; report. `:actuation/dispatch-alert` IS the one deliberate exception
;; -- see ns docstring 'Actuation' section above before changing this.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:site/intake}                                              :auto #{}}
   2 {:label "assisted-verify"  :writes #{:site/intake :weather/assess :inspection/screen}            :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:site/intake :actuation/dispatch-alert}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/authorize-resume`/`:actuation/file-accident-report`/
    `:actuation/file-periodic-report`/`:build/dispatch-placement`/
    `:handover/complete` are never auto-eligible at any phase, so they
    always escalate once the governor clears them (or hold if the
    governor doesn't). `:actuation/dispatch-alert` MAY auto-commit at
    phase 3 -- see ns docstring."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Construction Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
