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
  overall :passed? verdict. `simulation-out-of-tolerance?` independently
  re-derives that verdict from the site's OWN recorded as-built-
  deviation fields, never from the mission's self-reported result --
  the SAME 'ground truth, not self-report' discipline `construction.
  facts/weather-threshold-exceeded?` established for weather (this
  fleet's two-sided range-check family also includes `automotive.
  robotics/structural-tolerance-out-of-range?` and siblings).
  `construction.governor`'s `robotics-simulation-violations` calls this
  ns's independent recheck, never the stored :passed? value, before any
  `:build/dispatch-placement` proposal may commit.

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; this namespace
  simulates what a real robot cell would report, deterministically,
  from the site's own recorded fields, so tests and the demo run
  offline exactly like every other sibling namespace in this actor."
  (:require [kotoba.robotics :as robotics]))

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
  the same shape `automotive.robotics/structural-tolerance-out-of-
  range?` uses for a vehicle's structural deviation."
  [{:keys [as-built-deviation-actual as-built-deviation-min as-built-deviation-max]}]
  (and (number? as-built-deviation-actual) (number? as-built-deviation-min) (number? as-built-deviation-max)
       (or (< as-built-deviation-actual as-built-deviation-min)
           (> as-built-deviation-actual as-built-deviation-max))))

(defn simulate-placement-verification
  "Run the robot pre-placement verification mission for `site-id`
  (`site` is the full site record, incl. as-built-deviation-* fields
  from a prior robotic total-station survey). Returns {:mission ..
  :actions [{:action .. :proof ..} ..] :passed? bool}. Deterministic:
  :passed? is derived from the site's OWN recorded as-built-deviation
  fields via `as-built-tolerance-out-of-range?`, never invented or
  randomized -- `kotoba.robotics` mandates no network/IO, and a
  repeatable simulation is what makes the governor's independent
  recheck (`simulation-out-of-tolerance?`) meaningful."
  [site-id site]
  (let [out-of-range? (as-built-tolerance-out-of-range? site)
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" site-id "-placement-verify")
                                   :robot/total-station-survey-cell-1
                                   :placement-verification
                                   :boundaries {:zone "build-target-zone"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :site-id site-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `site`'s OWN
  current as-built-deviation fields fall out of range right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `construction.facts/weather-threshold-
  exceeded?`'s refusal to trust a stored verdict over a fresh
  recompute."
  [site]
  (as-built-tolerance-out-of-range? site))
