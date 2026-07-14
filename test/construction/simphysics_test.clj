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
  automotive, ADR-2607151600)."
  (:require [clojure.test :refer [deftest is testing]]
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
