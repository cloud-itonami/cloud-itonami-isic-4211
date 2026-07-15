(ns construction.cad-test
  "construction.cad's real BREP test-cylinder envelope bridge
  (ADR-2607997500) -- envelope-dims-mm's real-vs-default fallback
  discipline, and envelope-solid/envelope-mesh's genuine REVOLVE-based
  cylindrical tessellation output (NOT a box approximation -- see
  construction.cad's own docstring point 3). Adapted from fab.cad-test's/
  quarryops.cad-test's assertions to this vertical's own already-real
  :cylinder-height-actual-mm/:cylinder-diameter-actual-mm fields."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.cad :as cad]
            [construction.simphysics :as simphysics]))

(deftest envelope-dims-mm-falls-back-to-astm-en-standard-defaults-when-absent
  (testing "a site with no :cylinder-height-actual-mm/:cylinder-diameter-actual-mm
            gets the ASTM C39/EN 12390-3 standard 300x150mm reference dims"
    (is (= {:length-mm 300.0 :width-mm 150.0 :height-mm 300.0}
           (cad/envelope-dims-mm {:id "site-x"}))))
  (testing "nil site also falls back cleanly"
    (is (= {:length-mm 300.0 :width-mm 150.0 :height-mm 300.0}
           (cad/envelope-dims-mm nil)))))

(deftest envelope-dims-mm-uses-a-sites-own-real-measurement-when-present
  (testing "an explicit :cylinder-height-actual-mm/:cylinder-diameter-actual-mm
            pair overrides the defaults"
    (is (= {:length-mm 450.0 :width-mm 150.0 :height-mm 450.0}
           (cad/envelope-dims-mm {:cylinder-height-actual-mm 450.0
                                   :cylinder-diameter-actual-mm 150.0}))))
  (testing "a partial pair only overrides the field actually given"
    (is (= {:length-mm 150.0 :width-mm cad/default-cylinder-diameter-mm :height-mm 150.0}
           (cad/envelope-dims-mm {:cylinder-height-actual-mm 150.0})))))

(deftest default-cylinder-dims-reproduce-simphysics-prior-fixed-defaults
  (testing "the disclosed fallback defaults are DEFINED to reproduce
            construction.simphysics's own nominal-height-mm/nominal-diameter-mm
            EXACTLY (bit-identical, not merely epsilon-close -- see
            construction.cad's own docstring point 2, a stronger guarantee than
            fab.cad's/quarryops.cad's own epsilon-close default-matching)"
    (is (= simphysics/nominal-height-mm cad/default-cylinder-height-mm))
    (is (= simphysics/nominal-diameter-mm cad/default-cylinder-diameter-mm))))

(deftest envelope-solid-produces-a-real-cylinder-not-a-box
  (let [{:keys [dims solid] :as result} (cad/envelope-solid {:cylinder-height-actual-mm 300.0
                                                               :cylinder-diameter-actual-mm 150.0})]
    (is (= {:length-mm 300.0 :width-mm 150.0 :height-mm 300.0} dims))
    (is (seq (:vertices result)))
    (is (seq (:edges result)))
    (testing "a real revolve produces 3 faces: two circular caps + one cylindrical wall"
      (is (= 3 (count (mapcat :faces (:shells solid))))))
    (testing "the wall face is a genuine :cylinder surface, not a plane -- proof the
              revolve primitive was actually used, not the legacy box-extrude path"
      (is (some #(= :cylinder (:kind (:surface %))) (mapcat :faces (:shells solid)))))
    (testing "the tessellated bounding extent matches the requested dims (mm)"
      (let [{:keys [positions]} (cad/envelope-mesh result)
            extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                  (apply min (map #(nth % axis) positions))))]
        ;; radius 75mm -> diameter 150mm across X and Y; axial height 300mm on Z.
        (is (< (Math/abs (- (extent 0) 150.0)) 1e-6))
        (is (< (Math/abs (- (extent 1) 150.0)) 1e-6))
        (is (< (Math/abs (- (extent 2) 300.0)) 1e-6))))))

(deftest envelope-mesh-is-well-formed
  (let [solid (cad/envelope-solid {:cylinder-height-actual-mm 300.0
                                    :cylinder-diameter-actual-mm 150.0})
        {:keys [positions indices]} (cad/envelope-mesh solid)]
    (is (pos? (count positions)))
    (is (pos? (count indices)))
    (is (zero? (mod (count indices) 3)) "indices are complete triangles")
    (is (every? #(<= 0 % (dec (count positions))) indices)
        "every index references a valid vertex")
    (is (every? #(= 3 (count %)) positions) "positions are [x y z]")))

(deftest envelope-dims-mm-vary-per-site
  (testing "two sites with different real specimen-prep measurements get genuinely
            different envelopes -- this is not a fixed constant dressed up as
            per-site data"
    (is (not= (cad/envelope-dims-mm {:cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0})
              (cad/envelope-dims-mm {:cylinder-height-actual-mm 150.0 :cylinder-diameter-actual-mm 150.0})))))
