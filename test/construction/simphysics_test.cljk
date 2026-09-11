(ns construction.simphysics-test
  "Unit tests for `construction.simphysics`'s real `physics-2d`-backed
  concrete test-cylinder press simulation (ADR-2607152000). Asserts the
  disclosed properties that ns's own docstring documents: a standard
  (nominal-dimension) specimen self-consistently reproduces its design
  mix's own rated strength; a specimen prepared at a non-standard
  (shorter) height reads a HIGHER simulated strength (the same
  direction as ASTM C39's real L/D-ratio correction-factor phenomenon,
  not backwards); the ratio is invariant to which design-mix strength
  is used (mass/velocity cancel out, mirroring `vdesign.simphysics`'s
  own disclosed 'mass does not change the reading' finding for
  automotive, ADR-2607151600). Also covers `construction.cad`'s real
  BREP bridge into the STATIC `:test-cylinder` body's AABB
  (ADR-2607997500), incl. the construction-specific findings disclosed
  in this ns's own docstring's ADR-2607997500 section: unlike every
  prior vertical, CAD-derived geometry reproduces this ns's pre-ADR
  behavior BIT-IDENTICALLY (not merely epsilon-close), and the STATIC
  specimen's own position is verified never to move."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.cad :as cad]
            [construction.simphysics :as sp]))

(defn- close? [a b eps] (< (Math/abs (- (double a) (double b))) eps))

(deftest nominal-specimen-reproduces-rated-strength
  (testing "actual dims == standard ASTM C39/EN 12390-3 dims -> the ratio is exactly 1.0"
    (let [t (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                 :cylinder-height-actual-mm 300.0
                                 :cylinder-diameter-actual-mm 150.0})]
      (is (close? 30.0 (:sim-compressive-strength-mpa t) 1.0e-6))
      (is (close? (:sim-press-peak-decel-actual-mps2 t) (:sim-press-peak-decel-nominal-mps2 t) 1.0e-12)))))

(deftest short-specimen-reads-higher-the-real-ld-ratio-direction
  (testing "a specimen prepared shorter than the 300mm standard (L/D < 2.0) reads a HIGHER
            simulated strength -- the SAME direction ASTM C39's own L/D correction-factor
            table documents for real, non-standard-L/D specimens (correction < 1.0 applied
            to a raw reading that is itself inflated), not an invented/backwards direction"
    (let [nominal (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                       :cylinder-height-actual-mm 300.0
                                       :cylinder-diameter-actual-mm 150.0})
          short   (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                       :cylinder-height-actual-mm 150.0
                                       :cylinder-diameter-actual-mm 150.0})]
      (is (> (:sim-compressive-strength-mpa short) (:sim-compressive-strength-mpa nominal)))
      (is (close? 60.0 (:sim-compressive-strength-mpa short) 1.0e-6)
          "height halved -> dt halved -> peak-decel doubled -> reading exactly doubles"))))

(deftest tall-specimen-reads-lower
  (testing "a specimen prepared TALLER than standard reads a LOWER simulated strength (the
            opposite-direction extrapolation of the same height/dt relationship)"
    (let [nominal (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                       :cylinder-height-actual-mm 300.0
                                       :cylinder-diameter-actual-mm 150.0})
          tall    (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                       :cylinder-height-actual-mm 450.0
                                       :cylinder-diameter-actual-mm 150.0})]
      (is (< (:sim-compressive-strength-mpa tall) (:sim-compressive-strength-mpa nominal))))))

(deftest height-ratio-invariant-to-design-mix-strength
  (testing "the SAME height ratio (150/300) produces the SAME 2.0x multiple regardless of
            which design-mix rated strength is simulated -- mass (an arbitrary unit value)
            and the derived closing-velocity/dt both cancel out of the ratio, mirroring
            vdesign.simphysics's own disclosed mass-invariance finding for automotive"
    (let [fc30 (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                    :cylinder-height-actual-mm 150.0
                                    :cylinder-diameter-actual-mm 150.0})
          fc45 (sp/press-telemetry {:design-mix-rated-strength-mpa 45.0
                                    :cylinder-height-actual-mm 150.0
                                    :cylinder-diameter-actual-mm 150.0})]
      (is (close? 2.0 (/ (:sim-compressive-strength-mpa fc30) 30.0) 1.0e-6))
      (is (close? 2.0 (/ (:sim-compressive-strength-mpa fc45) 45.0) 1.0e-6)))))

(deftest strength-floor-and-ceiling-are-derived-from-the-design-mix
  (testing "the ACI 318-19 Sec. 26.12.3.1(b) floor (fc - 3.4 MPa) and the disclosed sanity
            ceiling (1.5x fc) both scale with the site's OWN design-mix rated strength"
    (is (close? 26.6 (sp/strength-floor-mpa 30.0) 1.0e-9))
    (is (close? 45.0 (sp/strength-ceiling-mpa 30.0) 1.0e-9))))

(deftest defaults-are-the-standard-clean-specimen
  (testing "a design map missing all keys defaults to a clean, standard 30 MPa / 150x300mm
            specimen -- self-consistent (ratio 1.0)"
    (let [t (sp/press-telemetry {})]
      (is (close? 30.0 (:sim-compressive-strength-mpa t) 1.0e-6)))))

;; ----------------------- ADR-2607997500 CAD-derived STATIC geometry -----------------------

(deftest specimen-half-extents-m-reads-construction-cads-real-dims
  (testing "specimen-half-extents-m (public -- see its own docstring for why) agrees
            with construction.cad/envelope-dims-mm for the same height/diameter,
            confirming the CAD bridge is genuinely wired in, not a private/parallel
            implementation"
    (let [{:keys [length-mm width-mm]} (cad/envelope-dims-mm {:cylinder-height-actual-mm 450.0
                                                                :cylinder-diameter-actual-mm 200.0})]
      (is (= {:half-w (/ length-mm 2000.0) :half-h (/ width-mm 2000.0)}
             (sp/specimen-half-extents-m 450.0 200.0))))))

(deftest cad-refactor-is-bit-identical-to-pre-adr-2607997500-behavior
  (testing "a GENUINE, STRONGER finding than every prior vertical's own epsilon-close
            default-matching (see construction.cad's own docstring point 2 and this
            ns's docstring's ADR-2607997500 section, second finding): because
            run-press was ALREADY reading real per-site height/diameter directly
            before this ADR, routing the SAME values through construction.cad
            produces results BIT-IDENTICAL (not merely close) to the pre-ADR inline
            arithmetic, for a REAL (non-default) specimen geometry, not just the
            no-data default case"
    (let [{:keys [half-w half-h]} (sp/specimen-half-extents-m 450.0 200.0)]
      ;; the exact pre-ADR-2607997500 formula: specimen-half-w = height-m/2,
      ;; specimen-half-h = radius-m = diameter-m/2.
      (is (= (/ (/ 450.0 1000.0) 2.0) half-w))
      (is (= (/ (/ 200.0 1000.0) 2.0) half-h))))
  (testing "press-telemetry's own output is unaffected by the refactor -- same
            fixture values as nominal-specimen-reproduces-rated-strength above"
    (let [t (sp/press-telemetry {:design-mix-rated-strength-mpa 30.0
                                 :cylinder-height-actual-mm 300.0
                                 :cylinder-diameter-actual-mm 150.0})]
      (is (close? 30.0 (:sim-compressive-strength-mpa t) 1.0e-6)))))

(deftest test-cylinder-specimen-never-moves-a-real-physics-2d-verified-finding
  (testing "the STATIC (mass 0) test-cylinder specimen's own position is IDENTICAL
            to its starting position after the press-platen has closed on it and
            settled -- physics-2d's positional correction/impulse resolution never
            moves a mass-0 (infinite-inverse-mass) body -- checked here against the
            ACTUAL simulated world-step output (run-press's own real
            :specimen-position), not merely re-asserting this ns's own docstring
            prose. This is why construction.scene renders the specimen mesh at a
            single fixed position across every frame, unlike every prior vertical's
            specimen-as-moving-body scene bridge"
    (let [{:keys [specimen-position]} (sp/run-press 300.0 150.0 30.0)]
      (is (= [0.0 0.0] specimen-position))))
  (testing "holds regardless of specimen geometry or design-mix strength -- a genuine
            property of a mass-0 physics-2d body, not a coincidence of one fixture"
    (doseq [[h d fc] [[150.0 150.0 30.0] [450.0 150.0 45.0] [300.0 300.0 20.0]]]
      (is (= [0.0 0.0] (:specimen-position (sp/run-press h d fc)))
          (str "h=" h " d=" d " fc=" fc)))))

(deftest resolve-press-inputs-matches-press-telemetrys-own-defaulting
  (testing "resolve-press-inputs (extracted for construction.scene's benefit) applies
            the SAME defaults press-telemetry always used"
    (is (= {:fc-mpa 30.0 :height-mm sp/nominal-height-mm :diameter-mm sp/nominal-diameter-mm}
           (sp/resolve-press-inputs {})))
    (is (= {:fc-mpa 45.0 :height-mm 450.0 :diameter-mm 200.0}
           (sp/resolve-press-inputs {:design-mix-rated-strength-mpa 45.0
                                      :cylinder-height-actual-mm 450.0
                                      :cylinder-diameter-actual-mm 200.0})))))

(deftest actual-run-for-site-exposes-the-real-trajectory-press-telemetry-hides
  (testing "actual-run-for-site returns the FULL run-press result (incl.
            :trajectory/:specimen-position) for the site's own actual dimensions --
            press-telemetry itself only keeps summary fields"
    (let [site {:design-mix-rated-strength-mpa 30.0
                :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0}
          run (sp/actual-run-for-site site)]
      (is (contains? run :trajectory))
      (is (pos? (count (:trajectory run))))
      (is (contains? run :specimen-position))
      ;; peak-decel-mps2 in the full run matches the actual-dimension run's own
      ;; reading press-telemetry's ratio is computed from.
      (is (close? (:peak-decel-mps2 run)
                  (:sim-press-peak-decel-actual-mps2 (sp/press-telemetry site))
                  1.0e-12)))))
