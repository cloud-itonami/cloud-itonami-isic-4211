(ns construction.simphysics
  "Time-stepped rigid-body simulation of the concrete test-cylinder
  compressive-strength press (ASTM C39/C39M \"Standard Test Method for
  Compressive Strength of Cylindrical Concrete Specimens\" / EN 12390-3
  \"Testing hardened concrete -- Compressive strength of test
  specimens\"), built directly on `kotoba-lang/physics-2d`'s real,
  tested impulse-based `world-step` solver (ADR-2607152000, extending
  ADR-2607151600's automotive pilot -- `kami-engine-vehicle-designer`'s
  `vdesign.simphysics` -- to this vertical).

  Unlike automotive (which took a real dependency on a SIBLING design-
  library repo, `kami-engine-vehicle-designer`, because that pairing
  already existed via `vehicle-design-link`, ADR-2607083500), no
  cloud-itonami design-repo sibling exists for construction, so this
  physics module is built DIRECTLY inside this actor (ADR-2607152000
  Context) and depends on `kotoba-lang/physics-2d` alone -- no BREP/CAM/
  webgpu-scene bridge (those were specific to automotive's existing
  design-review tooling).

  What is REAL here: a `press-platen` rigid body and a `test-cylinder`
  rigid body are actual `physics-2d` `Body2D`/AABB `Collider2D`
  entities; `physics-2d/world-step` actually integrates velocity/
  position and actually runs its brute-force collision detection +
  impulse resolution + positional correction over N discrete ticks --
  `:trajectory` inside `run-press`'s return value is the ACTUAL per-tick
  output of that solver, read back tick by tick, exactly the same
  technique `vdesign.simphysics/simulate` uses for automotive's crash-
  dispatch event (see that namespace's docstring, ADR-2607151600).

  ## The physical picture

  A real ASTM C39/EN 12390-3 test presses a rigid platen down onto a
  standing cylindrical concrete specimen (nominally 150 mm diameter x
  300 mm height, L/D = 2.0 -- ASTM C39 Sec. 6.2 / EN 12390-3) at a
  controlled, standard-specified loading rate until failure. This ns
  models that as: the platen is the MOVING `physics-2d` body (travel
  axis = the cylinder's HEIGHT axis, i.e. the direction the platen
  actually presses); the specimen is a second, STATIC (mass 0) AABB --
  `physics-2d` treats a mass-0 body as having zero inverse mass, i.e. an
  immovable anchor, matching how a real specimen sits on the fixed
  lower platen of the test machine (`resolve-contact` never special-
  cases this; it falls out of `inv-mass-b` being exactly `0.0`, the
  SAME technique `vdesign.simphysics` uses for automotive's immovable
  barrier).

  ## Real, citable inputs

  - `nominal-diameter-mm` / `nominal-height-mm` -- 150 mm x 300 mm,
    L/D = 2.0, the ASTM C39 Sec. 6.2 / EN 12390-3 standard cylinder.
  - `peak-strain` -- 0.002 (EN 1992-1-1, Eurocode 2, Table 3.1's nominal
    strain at peak stress, `eps-c2`, for concrete grades <= C50/60) --
    a real material constant, largely independent of the mix's own
    rated strength (unlike the elastic modulus below), used here as the
    (height-scaled) axial deformation the platen has closed by when the
    specimen reaches its peak stress.
  - `loading-stress-rate-mpa-per-s` -- 0.25 MPa/s (ASTM C39/C39M Sec.
    7.5: 0.25 +/- 0.05 MPa/s, i.e. 35 +/- 7 psi/s -- the PRIMARY real
    convention used here, since ASTM C39 is named first in
    ADR-2607152000's scope). EN 12390-3 Sec. 7 specifies a different,
    real convention (0.6 +/- 0.2 MPa/s) -- NOT used as the primary value
    here because it drives the derived closing velocity/dt below (see
    `closing-velocity-mps`) to a shorter total time-to-peak than ASTM's
    slower rate; ASTM's own rate was chosen so the derived total time-
    to-peak (~3-4 minutes at a 30 MPa design mix) lands in the same
    ballpark as real reported ASTM C39 test durations. This is a
    disclosed modeling choice between two equally-real conventions, not
    a fabricated number.
  - `elastic-modulus-mpa` -- ACI 318-19 Eq. 19.2.2.1.a (metric form):
    `Ec = 4700 * sqrt(f'c)` MPa, normal-weight concrete.
  - `aci-strength-floor-margin-mpa` -- 3.4 MPa (500 psi), ACI 318-19
    Sec. 26.12.3.1(b): no single strength test result may fall more than
    500 psi below the design mix's own rated `f'c`. This is the ONLY
    HARD, code-cited bound `construction.robotics/press-out-of-
    tolerance?` uses (a floor); see `sanity-ceiling-multiple` below for
    the disclosed non-citation on the other side.

  ## How `dt`/closing-velocity are derived (mirrors `vdesign.simphysics`
  exactly -- read that namespace's docstring for the full reasoning)

  `physics-2d`'s impulse resolver has no progressive stiffness/force-
  deflection model: whatever tick first detects ANY AABB overlap fully
  zeroes the closing velocity in that ONE tick (given `restitution` 0)
  -- a discrete 'boxcar' stop, not a continuous force ramp, EXACTLY the
  same disclosed limitation `vdesign.simphysics` documents for
  automotive's crash pulse. Left at an arbitrary `dt`, the resulting
  peak deceleration would be dominated by whatever `dt` happened to be
  chosen, not a meaningful physical reading. So `dt` here is
  deliberately derived from THIS run's own axial deformation-at-peak-
  stress (`peak-strain * height-m`) divided by the closing velocity
  (`closing-velocity-mps`, itself derived from the design mix's own
  rated strength via `elastic-modulus-mpa` and the real ASTM stress
  rate) -- a principled, not arbitrary, choice, coupling `dt` through a
  real, disclosed material-property assumption.

  ## The genuine per-specimen discriminant: L/D ratio, not mass

  Colliding with an immovable (mass-0) anchor, `physics-2d`'s impulse is
  ALWAYS a full stop to zero velocity in one tick regardless of the
  moving body's own mass -- mass cancels algebraically in `resolve-
  contact`, the SAME 'mass does not matter, only the kinematic
  parameters do' finding `vdesign.simphysics` disclosed for automotive
  (that ns's docstring: 'MASS DOES NOT change :sim-decel-g'). So the
  platen's `physics-2d` mass here is left at an ARBITRARY unit value
  (`1.0`) for every run -- not a calibrated real platen mass -- because
  `press-telemetry` below only ever reports a RATIO between two runs at
  the SAME mass/closing-velocity (this specimen's own actual dimensions
  vs the ASTM/EN standard reference dimensions), and mass provably
  cancels out of that ratio (asserted in `simphysics_test.clj`).

  The genuine, real-standard-grounded per-specimen discriminant is
  instead the specimen's own ACTUAL height (`cylinder-height-actual-mm`
  on the site record) vs the standard 300 mm: a shorter-than-nominal
  specimen has a smaller axial deformation-at-peak-stress for the SAME
  closing velocity, hence a smaller `dt`, hence a LARGER peak simulated
  deceleration/reading -- and a genuinely SHORTER specimen (a non-
  standard, out-of-range L/D ratio) is EXACTLY the real defect mode
  ASTM C39's own L/D correction-factor table exists to address: ASTM
  C39 documents that specimens with a LOWER L/D ratio read an
  ARTIFICIALLY INFLATED apparent strength (platen-restraint/confinement
  effects from the loading faces dominate a shorter specimen), which is
  why the standard applies a correction factor < 1.0 to L/D ratios below
  2.0, and REJECTS specimens with L/D outside its acceptable range
  (~1.75-2.10) as invalid for testing at all. This ns's 'shorter height
  -> higher simulated reading' direction is a genuine, correctly-
  directioned qualitative match to that real, documented phenomenon --
  it is NOT independently claimed as a quantitatively accurate stand-in
  for the ASTM correction-factor table itself (physics-2d has no
  material/confinement model at all -- see the disclosed simplifications
  below).

  ## Disclosed modeling simplifications (honest, not hidden)

  - 2D projection only (`physics-2d` has no 3D solver) -- the travel
    axis models the cylinder's height/load axis; the lateral axis is
    one horizontal diameter; world gravity is `[0 0]` (this is a
    press-axis projection of a vertical compression event, not a
    top-down one -- there is no meaningful lateral drop to model).
  - `physics-2d` has NO material/stress-strain/confinement model
    whatsoever -- it is a pure kinematic rigid-body impulse solver. The
    'compressive strength' this ns reports is NOT read off a simulated
    stress-strain curve; it is a RATIO of two kinematic `|delta-v|/dt`
    readings (this specimen's actual dimensions vs the standard
    reference dimensions, at the same design-mix-derived closing
    velocity), scaled onto the design mix's own rated strength. This is
    a proxy for a real, quasi-static hydraulic loading process using a
    kinematic-impulse solver -- an explicit analogy, not a claim that
    `physics-2d` is simulating concrete material failure.
  - The platen's `physics-2d` mass (`1.0`) is an arbitrary unit value,
    not a real platen mass -- see above; it provably cancels out of the
    reported ratio.
  - `sanity-ceiling-multiple` (1.5x the design mix's own rated strength)
    is a DISCLOSED engineering-judgment sanity bound, NOT itself an ACI/
    EN citation -- concrete testing above its design strength is not
    itself a QA failure in reality; this ceiling exists only to catch
    an implausible over-reading (e.g. a specimen-dimension data-entry
    error) the way `automotive.robotics/decel-ceiling-g`'s own 2.2x
    factor layers a disclosed margin onto automotive's real 20g
    reference pulse."
  (:require [physics-2d :as p2d]))

;; ---------------------------------------------------------------------------
;; Platform shims
;; ---------------------------------------------------------------------------

(defn- sqrt* [x] #?(:clj (Math/sqrt (double x)) :cljs (js/Math.sqrt x)))
(defn- abs* [x] (if (neg? x) (- x) x))
(defn- mm->m [mm] (/ (double mm) 1000.0))

;; ---------------------------------------------------------------------------
;; Real, citable constants (see ns docstring for sources)
;; ---------------------------------------------------------------------------

(def nominal-diameter-mm
  "ASTM C39 Sec. 6.2 / EN 12390-3 standard test-cylinder diameter (mm)."
  150.0)

(def nominal-height-mm
  "ASTM C39 Sec. 6.2 / EN 12390-3 standard test-cylinder height (mm),
  L/D = 2.0 with `nominal-diameter-mm`."
  300.0)

(def peak-strain
  "EN 1992-1-1 (Eurocode 2) Table 3.1 nominal strain at peak
  compressive stress (`eps-c2`) for concrete grades <= C50/60."
  0.002)

(def loading-stress-rate-mpa-per-s
  "ASTM C39/C39M Sec. 7.5 stress-controlled loading rate: 0.25 +/- 0.05
  MPa/s (35 +/- 7 psi/s) -- see ns docstring for why this, and not EN
  12390-3's own real 0.6 +/- 0.2 MPa/s convention, is used here."
  0.25)

(def aci-strength-floor-margin-mpa
  "ACI 318-19 Sec. 26.12.3.1(b): no single concrete strength test result
  may fall more than 500 psi (3.4 MPa) below the design mix's own rated
  f'c."
  3.4)

(def sanity-ceiling-multiple
  "Disclosed engineering-judgment sanity ceiling (NOT an ACI/EN
  citation) -- see ns docstring."
  1.5)

(def approach-ticks
  "Ticks of pre-contact approach before the platen first overlaps the
  specimen, so the trajectory captures a real approach phase, not just
  the collision tick itself (mirrors `vdesign.simphysics/default-gap-m`,
  ADR-2607151600)."
  5)

(def settle-ticks
  "Extra ticks appended after contact, so the trajectory also captures
  post-contact positional-correction settling -- SAME constant/rationale
  `vdesign.simphysics` uses (`physics-2d`'s positional correction removes
  80% of remaining overlap per tick; 15 ticks converges to ~3e-11 of
  whatever it was at first contact)."
  15)

(def platen-half-w-m
  "Press-platen AABB half-width along the travel axis (m) -- a thin
  rigid platen face, arbitrary since `physics-2d` collision detection
  only needs it to be positive; does not affect the derived ratio."
  0.01)

(def platen-half-h-m
  "Press-platen AABB half-height (m), lateral -- wide enough that the
  platen always fully overlaps the specimen's cross-section (a real
  compression-machine platen must be at least as large as the specimen,
  ASTM C39 Sec. 5.1); no offset/eccentric loading is modeled."
  0.10)

;; ---------------------------------------------------------------------------
;; Derivation
;; ---------------------------------------------------------------------------

(defn elastic-modulus-mpa
  "ACI 318-19 Eq. 19.2.2.1.a (metric form): Ec = 4700 * sqrt(f'c) MPa,
  normal-weight concrete, `fc-mpa` = the design mix's own rated
  compressive strength."
  [fc-mpa]
  (* 4700.0 (sqrt* fc-mpa)))

(defn closing-velocity-mps
  "The press platen's controlled closing velocity (m/s): the real ASTM
  C39 stress-controlled loading rate (`loading-stress-rate-mpa-per-s`)
  converted to a displacement rate via `fc-mpa`'s own elastic modulus
  (strain-rate = stress-rate / Ec) times the nominal specimen height --
  a disclosed, principled derivation (not a measured rate), the SAME
  'derive a kinematic parameter from a real per-design property'
  technique `vdesign.simphysics` uses for automotive's dt (crush-length
  / impact-speed)."
  [fc-mpa]
  (let [ec (elastic-modulus-mpa fc-mpa)
        strain-rate (/ loading-stress-rate-mpa-per-s ec)]
    (* strain-rate (mm->m nominal-height-mm))))

(defn run-press
  "Runs ONE real `physics-2d` `world-step` simulation: a press-platen
  body closes at `(closing-velocity-mps fc-mpa)` onto a static (mass 0)
  test-cylinder collider sized `height-mm` x `diameter-mm`. Returns
  {:trajectory [{:tick :position :velocity} ...] (platen body only)
   :peak-decel-mps2 n :ticks n :dt n :closing-velocity-mps n}.

  `:peak-decel-mps2` is the PEAK magnitude of tick-to-tick velocity
  change (along the travel axis) divided by `dt` -- derived from the
  ACTUAL simulated velocity trajectory, not invented, the same
  `vdesign.simphysics/simulate` technique (ADR-2607151600)."
  [height-mm diameter-mm fc-mpa]
  (let [height-m (mm->m height-mm)
        radius-m (/ (mm->m diameter-mm) 2.0)
        v0 (closing-velocity-mps fc-mpa)
        dt (/ (* peak-strain height-m) v0)
        gap-m (* approach-ticks v0 dt)
        specimen-half-w height-m
        specimen-half-w (/ specimen-half-w 2.0)
        specimen-x 0.0
        platen-x0 (- specimen-x specimen-half-w platen-half-w-m gap-m)
        platen (p2d/make-body {:position [platen-x0 0.0]
                                :velocity [v0 0.0]
                                :mass 1.0
                                :restitution 0.0
                                :friction 0.0
                                :collider (p2d/make-aabb-collider platen-half-w-m platen-half-h-m)
                                :user-data :press-platen})
        specimen (p2d/make-body {:position [specimen-x 0.0]
                                  :velocity [0.0 0.0]
                                  :mass 0.0
                                  :restitution 0.0
                                  :friction 0.0
                                  :collider (p2d/make-aabb-collider specimen-half-w radius-m)
                                  :user-data :test-cylinder})
        w0 (p2d/world-new [0.0 0.0])
        [w1 pid] (p2d/world-add w0 platen)
        [w2 _sid] (p2d/world-add w1 specimen)
        ticks (+ approach-ticks settle-ticks)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w2 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) pid)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel (->> (map (fn [va vb] (abs* (/ (- vb va) dt))) vxs (rest vxs))
                         (reduce max 0.0))]
    {:trajectory trajectory
     :peak-decel-mps2 peak-decel
     :ticks (count trajectory)
     :dt dt
     :closing-velocity-mps v0}))

(defn press-telemetry
  "Runs TWO real `physics-2d` press-collision simulations for `design`
  (a map of {:design-mix-rated-strength-mpa :cylinder-height-actual-mm
  :cylinder-diameter-actual-mm}, defaulting to a clean 30 MPa / standard
  150x300mm specimen when a key is missing): one at THIS specimen's own
  actual recorded dimensions, one at the ASTM/EN standard reference
  dimensions -- both real `run-press` invocations, same design-mix
  strength/closing-velocity. Returns:

    {:sim-compressive-strength-mpa n
     :sim-press-peak-decel-actual-mps2 n
     :sim-press-peak-decel-nominal-mps2 n
     :sim-press-ticks n :sim-press-dt-s n :sim-press-closing-velocity-mps n}

  `:sim-compressive-strength-mpa` = design-mix-rated-strength-mpa *
  (actual-peak-decel / nominal-peak-decel) -- the REAL simulated-
  impulse ratio between this specimen's actual geometry and the
  standard reference geometry, scaled onto the design mix's own rated
  strength (MPa) so the result is directly comparable to a real,
  citable acceptance criterion (ACI 318-19 Sec. 26.12.3.1(b), see
  `construction.robotics/press-out-of-tolerance?`)."
  [{:keys [design-mix-rated-strength-mpa cylinder-height-actual-mm cylinder-diameter-actual-mm]}]
  (let [fc (double (or design-mix-rated-strength-mpa 30.0))
        h  (double (or cylinder-height-actual-mm nominal-height-mm))
        d  (double (or cylinder-diameter-actual-mm nominal-diameter-mm))
        actual (run-press h d fc)
        nominal (run-press nominal-height-mm nominal-diameter-mm fc)
        ratio (/ (:peak-decel-mps2 actual) (:peak-decel-mps2 nominal))]
    {:sim-compressive-strength-mpa (* fc ratio)
     :sim-press-peak-decel-actual-mps2 (:peak-decel-mps2 actual)
     :sim-press-peak-decel-nominal-mps2 (:peak-decel-mps2 nominal)
     :sim-press-ticks (:ticks actual)
     :sim-press-dt-s (:dt actual)
     :sim-press-closing-velocity-mps (:closing-velocity-mps actual)}))

(defn strength-floor-mpa
  "ACI 318-19 Sec. 26.12.3.1(b): `fc-mpa` (the design mix's own rated
  strength) minus the real 3.4 MPa (500 psi) single-test floor margin."
  [fc-mpa]
  (- fc-mpa aci-strength-floor-margin-mpa))

(defn strength-ceiling-mpa
  "Disclosed sanity ceiling (NOT an ACI/EN citation) -- see ns
  docstring's `sanity-ceiling-multiple`."
  [fc-mpa]
  (* fc-mpa sanity-ceiling-multiple))
