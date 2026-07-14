(ns construction.robotics
  "Robot-executed pre-placement verification -- the concrete, actor-
  level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) for THIS actor's own
  `:build/dispatch-placement` robot-dispatch actuation (`docs/adr/0002-
  robot-dispatch-slice.md`'s physical robot-placement slice) -- not
  merely a self-reported checklist string.

  A robot mission (`kotoba.robotics/mission`) walks the site's
  build-target element through three :sense/:actuate steps -- rebar-
  placement scan, robotic total-station as-built survey, concrete-cure
  compressive-strength test-cylinder press -- built with `kotoba.
  robotics/action` + `kotoba.robotics/telemetry-proof`, and reports an
  overall :passed? verdict.

  ADR-2607152000 (extending ADR-2607151600's automotive pilot to this
  vertical) upgrades the THIRD step -- concrete-cure test-cylinder
  press -- from a symbolic placeholder to a REAL, time-stepped
  `physics-2d` rigid-body press-collision simulation (`construction.
  simphysics`, this actor's own ns since no `kami-engine-vehicle-
  designer`-style design-repo sibling exists for construction -- see
  that ns's docstring for the full physical model, real citations
  (ASTM C39/EN 12390-3, ACI 318-19, Eurocode 2) and disclosed
  simplifications). The FIRST two steps -- rebar-placement scan and the
  total-station as-built survey -- remain the EXISTING symbolic ground-
  truth check (`as-built-tolerance-out-of-range?`): these are
  measurement/inspection steps on the stationary build-target, not a
  destructive/force test, so real rigid-body physics is deliberately
  NOT force-fit onto them (ADR-2607152000 Decision).

  `simulation-out-of-tolerance?` (as-built) and `press-out-of-
  tolerance?` (the real simulated press reading) independently re-
  derive their own verdicts from the site's OWN recorded fields, never
  from the mission's self-reported result -- the SAME 'ground truth,
  not self-report' discipline `construction.facts/weather-threshold-
  exceeded?` established for weather (this fleet's two-sided range-
  check family also includes `automotive.robotics/crash-simulation-
  out-of-tolerance?` and siblings). `construction.governor`'s
  `robotics-simulation-violations` calls BOTH independent rechecks,
  never the stored :passed? value, before any `:build/dispatch-
  placement` proposal may commit -- the two checks are NOT mutually
  exclusive and may co-fire (ADR-2607152000).

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; `construction.
  simphysics`'s own `world-step` is a pure fixed-timestep integrator, no
  wall-clock/IO -- so this stays exactly as offline/deterministic as
  every other sibling namespace in this actor."
  (:require [kotoba.robotics :as robotics]
            [construction.simphysics :as simphysics]))

(def mission-actions
  "The three-step pre-placement robot verification mission every
  build-target site walks through before `:build/dispatch-placement`
  is proposable. All :sense/:actuate at :none/:low safety --
  verification/QA sensing on the stationary build-target and a
  destructive press on a separate test cylinder, not the robot
  panel-placement actuation itself, which is `:build/dispatch-
  placement` (always :safety-critical -- see `construction.governor`)."
  [{:step :rebar-placement-scan              :kind :sense   :safety :none}
   {:step :as-built-total-station-survey     :kind :sense   :safety :none}
   {:step :concrete-cure-test-cylinder-press :kind :actuate :safety :low}])

(defn as-built-tolerance-out-of-range?
  "Ground-truth check: does `site`'s own recorded
  :as-built-deviation-actual (mm, from the robotic total-station
  survey) fall outside its own recorded [:as-built-deviation-min
  :as-built-deviation-max] bounds? Needs no mission run or proposal
  inspection -- its inputs are permanent fields already on the site,
  the same shape `automotive.robotics/crash-simulation-out-of-
  tolerance?` uses for a vehicle's crash telemetry. Covers the rebar-
  placement-scan / total-station-survey steps only -- the concrete-cure
  press step has its OWN real, simphysics-derived check below
  (`press-out-of-tolerance?`)."
  [{:keys [as-built-deviation-actual as-built-deviation-min as-built-deviation-max]}]
  (and (number? as-built-deviation-actual) (number? as-built-deviation-min) (number? as-built-deviation-max)
       (or (< as-built-deviation-actual as-built-deviation-min)
           (> as-built-deviation-actual as-built-deviation-max))))

(defn press-telemetry-for
  "Runs the REAL `construction.simphysics` press-collision simulation
  for `site`'s own recorded `:design-mix-rated-strength-mpa` /
  `:cylinder-height-actual-mm` / `:cylinder-diameter-actual-mm` --
  exactly the fields `simphysics/press-telemetry` reads (see that ns's
  docstring). Returns `{:sim-compressive-strength-mpa n ...}`. Pure,
  deterministic -- no IO; the same three fields always reproduce the
  same telemetry."
  [site]
  (simphysics/press-telemetry
   (select-keys site [:design-mix-rated-strength-mpa
                       :cylinder-height-actual-mm
                       :cylinder-diameter-actual-mm])))

(defn press-out-of-tolerance?
  "Ground-truth check: does `site`'s own recorded
  `:sim-compressive-strength-mpa` (the REAL `construction.simphysics`
  press-collision telemetry already on file for this site -- see
  `press-telemetry-for`) fall outside the real tolerance band derived
  from its own `:design-mix-rated-strength-mpa` -- ACI 318-19 Sec.
  26.12.3.1(b)'s real single-test floor (`simphysics/strength-floor-
  mpa`) and `simphysics`'s own disclosed sanity ceiling (`simphysics/
  strength-ceiling-mpa`)? Needs no mission run -- its inputs are
  permanent fields already on the site, the same shape `automotive.
  robotics/crash-simulation-out-of-tolerance?` uses for `:sim-decel-g`."
  [{:keys [sim-compressive-strength-mpa design-mix-rated-strength-mpa]}]
  (and (number? sim-compressive-strength-mpa) (number? design-mix-rated-strength-mpa)
       (or (< sim-compressive-strength-mpa (simphysics/strength-floor-mpa design-mix-rated-strength-mpa))
           (> sim-compressive-strength-mpa (simphysics/strength-ceiling-mpa design-mix-rated-strength-mpa)))))

(defn press-floor-mpa
  "Thin re-export of `simphysics/strength-floor-mpa` so `construction.
  governor` needs only require this ns, not `construction.simphysics`
  directly (mirrors how `automotive.governor` reads `automotive.
  robotics/decel-ceiling-g` rather than requiring `vdesign.simverify`)."
  [fc-mpa]
  (simphysics/strength-floor-mpa fc-mpa))

(defn press-ceiling-mpa
  "See `press-floor-mpa`."
  [fc-mpa]
  (simphysics/strength-ceiling-mpa fc-mpa))

(defn simulate-placement-verification
  "Run the robot pre-placement verification mission for `site-id`
  (`site` is the full site record, incl. as-built-deviation-* fields
  from a prior robotic total-station survey AND design-mix-rated-
  strength-mpa/cylinder-height-actual-mm/cylinder-diameter-actual-mm
  fields for the concrete-cure press). Actually runs the REAL press
  simulation (`press-telemetry-for`) for the `:concrete-cure-test-
  cylinder-press` step, and the existing symbolic `as-built-tolerance-
  out-of-range?` check for the other two steps. Returns {:mission ..
  :actions [{:action .. :proof ..} ..] :passed? bool
  :sim-compressive-strength-mpa n ...(rest of the press telemetry)}.
  Deterministic: :passed? is derived from the site's OWN recorded
  fields via `as-built-tolerance-out-of-range?`/`press-out-of-
  tolerance?`, never invented or randomized -- `kotoba.robotics`
  mandates no network/IO, and a repeatable simulation is what makes the
  governor's independent recheck meaningful."
  [site-id site]
  (let [as-built-bad? (as-built-tolerance-out-of-range? site)
        press (press-telemetry-for site)
        press-bad? (press-out-of-tolerance? (merge site press))
        mission (robotics/mission (str "mission-" site-id "-placement-verify")
                                   :robot/total-station-survey-cell-1
                                   :placement-verification
                                   :boundaries {:zone "build-target-zone"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [press-step? (= step :concrete-cure-test-cylinder-press)
                              bad? (if press-step? press-bad? as-built-bad?)
                              reading (if bad? :out-of-tolerance :nominal)
                              a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :site-id site-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance (if press-step?
                                                                           :simulated-physics-2d
                                                                           :simulated))}))
                      mission-actions)]
    (merge {:mission mission
            :actions actions
            :passed? (not (or as-built-bad? press-bad?))}
           press)))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `site`'s OWN
  current as-built-deviation fields fall out of range right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `construction.facts/weather-threshold-
  exceeded?`'s refusal to trust a stored verdict over a fresh
  recompute. Covers the rebar-scan/total-station-survey steps only --
  see `press-out-of-tolerance?` for the concrete-cure press's own
  independent recheck."
  [site]
  (as-built-tolerance-out-of-range? site))
