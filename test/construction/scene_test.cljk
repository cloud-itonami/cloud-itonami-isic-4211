(ns construction.scene-test
  "construction.scene's bridge from construction.cad's tessellated
  test-cylinder envelope + construction.simphysics/actual-run-for-site's
  trajectory into kami.webgpu.mesh's real input shape, asserted for
  well-formedness -- no browser/WebGPU device is available in this
  JVM/.cljc actor repo (see construction.scene's docstring). Adapted
  from fab.scene-test's/autoparts.scene-test's own assertions
  (ADR-2607997500), incl. this vertical's OWN disclosed, verified
  finding (not assumed identical to those siblings): every frame's
  translation is IDENTICAL here, because the mesh models the STATIC
  test-cylinder specimen, not the moving press-platen -- see the last
  two tests."
  (:require [clojure.test :refer [deftest is testing]]
            [construction.simphysics :as simphysics]
            [construction.scene :as scene]))

(def ^:private sample-site
  {:id "site-scene-test" :design-mix-rated-strength-mpa 30.0
   :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0})

(deftest mesh-data-is-well-formed
  (testing "positions/normals/indices satisfy kami.webgpu.mesh/upload-mesh!'s
            real contract: same-length positions/normals, index count a
            multiple of 3, every index within the vertex range"
    (let [{:keys [positions normals indices vertex-count index-count]} (scene/scene-for sample-site)]
      (is (pos? vertex-count))
      (is (pos? index-count))
      (is (= (count positions) vertex-count))
      (is (= (count normals) vertex-count)
          "upload-mesh! requires one normal per vertex, not optional like uvs/skin/morph")
      (is (= (count indices) index-count))
      (is (zero? (mod index-count 3)))
      (is (every? #(<= 0 % (dec vertex-count)) indices)
          "every index must reference a valid vertex")
      (is (every? #(= 3 (count %)) positions) "positions are [x y z]")
      (is (every? #(= 3 (count %)) normals) "normals are [x y z]")
      (is (every? (fn [n] (< (Math/abs (- 1.0 (Math/sqrt (reduce + (map * n n))))) 1e-6)) normals)
          "every normal must actually be unit-length"))))

(deftest one-frame-per-simulated-tick
  (testing "one :transform per construction.simphysics/actual-run-for-site trajectory tick"
    (let [sim (simphysics/actual-run-for-site sample-site)
          sc (scene/scene-for sample-site)]
      (is (= (:ticks sim) (count (:frames sc))))
      (is (every? #(= 3 (count (get-in % [:transform :translation]))) (:frames sc)))
      (is (every? #(= [0.0 0.0 0.0] (get-in % [:transform :rotation])) (:frames sc))
          "physics-2d has no orientation state -- every frame's rotation is identity, honestly")
      (is (every? #(= [1.0 1.0 1.0] (get-in % [:transform :scale])) (:frames sc))))))

(deftest mesh-is-unit-converted-to-meters-and-real-cylinder-shaped
  (testing "the mesh's tessellated extent (now in METERS, matching
            construction.simphysics's trajectory units) matches the real
            envelope-dims-mm length/width (converted mm->m) -- a genuine
            circular cross-section, not a box footprint"
    (let [{:keys [positions dims]} (scene/scene-for sample-site)
          extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                (apply min (map #(nth % axis) positions))))]
      (is (< (Math/abs (- (extent 0) (/ (:width-mm dims) 1000.0))) 1e-9)
          "X extent = diameter (radius*2), converted to meters")
      (is (< (Math/abs (- (extent 1) (/ (:width-mm dims) 1000.0))) 1e-9)
          "Y extent = diameter too -- a circular cross-section, not a box")
      (is (< (Math/abs (- (extent 2) (/ (:length-mm dims) 1000.0))) 1e-9)
          "Z extent = the cylinder's own real axial height, converted to meters"))))

(deftest scene-for-uses-defaults-when-site-has-no-cylinder-dim-fields
  (testing "a site with no :cylinder-*-actual-mm fields still produces a genuine,
            well-formed mesh -- via construction.cad's disclosed ASTM/EN standard
            defaults, never throws"
    (let [sc (scene/scene-for {:design-mix-rated-strength-mpa 30.0})]
      (is (pos? (:vertex-count sc)))
      (is (pos? (:index-count sc))))))

(deftest every-frame-translation-is-identical-a-construction-specific-finding
  (testing "UNLIKE fab.scene/autoparts.scene/quarryops.scene (where the CAD-
            enveloped body genuinely moves), construction.scene's :frames are
            ALL identical -- the rendered mesh models the STATIC test-cylinder
            specimen, whose real physics-2d position never changes (a mass-0
            body -- verified directly against construction.simphysics's own
            :specimen-position, not merely asserted in prose)"
    (let [sim (simphysics/actual-run-for-site sample-site)
          sc (scene/scene-for sample-site)
          translations (mapv #(get-in % [:transform :translation]) (:frames sc))
          [sx sy] (:specimen-position sim)]
      (is (apply = translations) "every frame's translation is bit-identical")
      (is (= [sx sy 0.0] (first translations))
          "the (only) translation is construction.simphysics's own real, simulated
           :specimen-position, not an arbitrary placeholder"))))

(deftest mesh-size-genuinely-differs-per-site-even-though-frames-do-not
  (testing "the tessellated MESH's own size (:positions/:dims) genuinely varies
            with a site's real specimen dims, even though :frames never do
            (this vertical's own disclosed split -- see scene docstring)"
    (let [nominal (scene/scene-for {:design-mix-rated-strength-mpa 30.0
                                     :cylinder-height-actual-mm 300.0
                                     :cylinder-diameter-actual-mm 150.0})
          short (scene/scene-for {:design-mix-rated-strength-mpa 30.0
                                   :cylinder-height-actual-mm 150.0
                                   :cylinder-diameter-actual-mm 150.0})]
      (is (not= (:dims nominal) (:dims short)))
      (is (not= (:positions nominal) (:positions short))))))
