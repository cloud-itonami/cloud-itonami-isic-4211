(ns construction.scene
  "Bridge from `construction.cad`'s tessellated test-cylinder-specimen
  envelope mesh + `construction.simphysics/actual-run-for-site`'s
  per-tick physics trajectory into the vertex/index/per-frame-transform
  data shape `kotoba-lang/webgpu`'s `kami.webgpu.mesh` executor's REAL,
  working `upload-mesh!`/`render-frame!` functions already consume
  (ADR-2607997500 -- direct port of `fab.scene`'s/`autoparts.scene`'s/
  `quarryops.scene`'s reference pattern, itself a port of
  `kami-engine-vehicle-designer`'s `vdesign.scene`, ADR-2607151600; see
  `construction.cad`/`construction.simphysics` docstrings for that same
  porting rationale).

  `:positions`/`:normals`/`:indices` in `scene-for`'s result are
  DIRECTLY the shape `kami.webgpu.mesh/upload-mesh!` destructures
  (`{:keys [positions normals indices uvs morph-target-deltas joints
  weights]}`, all but `:positions`/`:normals`/`:indices` optional) --
  `(select-keys (scene-for site) [:positions :normals :indices])` is a
  drop-in `geometry` argument for that function today. `:frames`'s
  per-entry `:transform` map (`{:translation [x y z] :rotation [rx ry
  rz] :scale [sx sy sz]}`) is DIRECTLY the shape `kami.webgpu.mesh/
  model-matrix` (and `render-frame!`'s optional trailing `transform`
  arg, handed straight to `model-matrix`) expects.

  A GENUINE, VERIFIED DIFFERENCE FROM EVERY PRIOR VERTICAL'S SCENE NS
  (checked against `construction.simphysics`'s own placement algebra and
  `run-press`'s new `:specimen-position` return value -- see that ns's
  docstring's ADR-2607997500 section, third finding -- not assumed):
  in `autoparts.scene`/`fab.scene`/`quarryops.scene`, the tessellated
  CAD mesh and the per-tick animated body are the SAME `physics-2d`
  body, because in those verticals' pull/drop tests the specimen IS the
  body that moves. Here `construction.cad`'s envelope models the
  STATIC test-cylinder (see that ns's docstring point 1) -- and a
  STATIC, mass-0 `physics-2d` body's position PROVABLY never changes
  (`resolve-contact`'s positional correction/impulse resolution only
  ever adjusts inverse-mass-weighted bodies, and a mass-0 body's inverse
  mass is exactly 0.0). So `scene-for`'s `:frames` below are NOT an
  animation of genuine motion -- every `:transform`'s `:translation` is
  IDENTICAL, `run-press`'s own real `:specimen-position` for this run
  (VERIFIED, not merely asserted, in `construction.simphysics`'s own
  `simphysics_test.clj`, and re-checked directly here in
  `scene_test.clj`). This is an honest, disclosed limitation, not a
  bug: the press-platen genuinely DOES move in this simulation, but it
  has no CAD envelope of its own (only the test-cylinder specimen does,
  per `construction.cad`'s docstring point 1 -- the platen is test-rig
  equipment, not the specimen), so there is no platen mesh for this ns
  to attach the platen's own real trajectory to. `construction.
  simphysics/actual-run-for-site`'s full `:trajectory` (the platen's own
  motion) remains available as raw data to any caller that wants to
  render the platen separately (e.g. as a placeholder box) -- rendering
  it is NOT attempted here.

  A SECOND real, disclosed difference worth noting on its own (unlike
  `fab.cad`'s/`quarryops.cad`'s own box-envelope approximations,
  `construction.cad/envelope-mesh` tessellates a REAL cylinder --
  `construction.cad`'s own docstring point 3): the cylindrical side
  wall's tessellated triangles are PIECEWISE-FLAT facets of a genuinely
  curved surface (`brep.config/cylinder-segments` segments), so
  `face-normals` below (identical technique to `fab.scene`'s/
  `autoparts.scene`'s own flat per-triangle normal) is only an
  APPROXIMATION of the true continuous cylinder-surface normal, accurate
  to within the tessellation's angular resolution -- unlike the box-
  envelope verticals, where a flat per-triangle normal is EXACTLY
  correct for each planar box face. A genuine, disclosed trade-off of
  using a real curved primitive instead of a box approximation, not an
  oversight.

  Two REAL, disclosed unit/shape gaps close this from being a byte-for-
  byte drop-in -- the SAME two `fab.scene`/`autoparts.scene`/
  `quarryops.scene` close, ported here verbatim because the underlying
  mismatch is identical:

  1. `construction.cad/envelope-mesh` produces `{:positions :indices}`
     only -- no `:normals`. `kami.webgpu.mesh/upload-mesh!` requires
     `:normals` (the same length as `:positions`). `face-normals` below
     computes REAL per-triangle flat normals (cross product of each
     triangle's own two edges) to close this gap.
  2. `construction.cad/envelope-mesh`'s positions are in MILLIMETERS
     while `construction.simphysics`'s trajectory/`:specimen-position`
     are in METERS. `mesh->m` below converts the tessellated positions
     to meters.

  Axis-convention note (mirrors `quarryops.cad`'s own disclosed Z-up-
  CAD-vs-physics-fall-axis mismatch, applied here to a translation-only
  static mesh rather than a moving one): `construction.cad/envelope-
  solid`'s revolve puts the cylinder's own axial (real height) dimension
  along CAD-local Z, while `construction.simphysics`'s 2D world places
  the press's travel axis along X. Since the specimen mesh here is
  rendered at a single FIXED translation (see above, no rotation is
  ever applied to reconcile the two conventions), this mismatch has no
  visible consequence for `:frames`' translations -- disclosed for
  completeness, not because it changes this ns's actual output.

  Remaining, honest limitation (same as `vdesign.scene`'s/`autoparts.
  scene`'s/`fab.scene`'s/`quarryops.scene`'s): `kami.webgpu.mesh` itself
  is a `.cljs`-only WebGPU executor (`js/Float32Array`, real GPU
  device/buffer calls) -- actually calling `upload-mesh!`/
  `render-frame!` needs a ClojureScript/browser host loading this
  namespace's output and iterating `:frames`, which this JVM-`.cljc`
  actor repo (no browser here) cannot execute, and `kotoba-lang/webgpu`
  is deliberately NOT a runtime dependency of this repo (see
  `deps.edn`). The DATA SHAPE this namespace produces is genuinely,
  verifiably compatible with that function's real input contract (see
  `scene_test.clj`); wiring it into a live canvas is the small host-side
  step that remains, and is NOT claimed to be done here.

  Also disclosed: every frame's `:rotation` is `[0 0 0]` and `:scale` is
  `[1 1 1]` -- `physics-2d`'s `Body2D` carries NO orientation/angular
  state at all (translation-only rigid body), a real property of the
  underlying solver, not something simplified away by this bridge."
  (:require [construction.cad :as cad]
            [construction.simphysics :as simphysics]))

(defn- v-sub [[ax ay az] [bx by bz]] [(- bx ax) (- by ay) (- bz az)])

(defn- v-cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn- v-length [[x y z]]
  #?(:clj  (Math/sqrt (double (+ (* x x) (* y y) (* z z))))
     :cljs (js/Math.sqrt (+ (* x x) (* y y) (* z z)))))

(defn- flat-normal
  "Real geometric face normal for triangle `a b c` -- cross product of
  two edges, normalized. Falls back to `[0 0 1]` only for a degenerate
  (zero-area) triangle."
  [a b c]
  (let [n (v-cross (v-sub a b) (v-sub a c))
        len (v-length n)]
    (if (pos? len)
      (mapv #(/ % len) n)
      [0.0 0.0 1.0])))

(defn face-normals
  "Per-vertex flat (face) normals for `positions`/`indices`
  (`construction.cad/envelope-mesh`'s output shape) -- REAL geometric
  normals computed from each triangle's own 3 vertices, not a
  placeholder or constant. Safe to assign one flat normal per triangle-
  vertex here because `brep.tessellate/tessellate-solid` gives every
  BREP face (this envelope's two cap faces + one cylindrical wall face)
  its own PRIVATE vertex range -- no vertex index is shared between two
  faces with different normals, the SAME reasoning `fab.scene/face-
  normals`/`autoparts.scene/face-normals`/`vdesign.scene/face-normals`
  document, checked here against `brep.tessellate`'s cylinder-wall
  tessellation too (it appends fresh vertex positions per ring, never
  reusing another face's indices) -- see ns docstring's second
  disclosed difference for the resulting curved-surface approximation
  this technique implies for the wall face specifically."
  [positions indices]
  (let [tris (partition 3 indices)]
    (reduce
     (fn [normals [ia ib ic]]
       (let [n (flat-normal (nth positions ia) (nth positions ib) (nth positions ic))]
         (-> normals (assoc ia n) (assoc ib n) (assoc ic n))))
     (vec (repeat (count positions) [0.0 0.0 1.0]))
     tris)))

(defn- mesh->m
  "Converts `construction.cad`'s millimeter-scale tessellated positions
  to meters, matching `construction.simphysics`'s meter-scale trajectory
  positions -- see namespace docstring, gap 2."
  [positions]
  (mapv (fn [[x y z]] [(/ x 1000.0) (/ y 1000.0) (/ z 1000.0)]) positions))

(defn scene-for
  "Builds
    {:positions [...] :normals [...] :indices [...]
     :frames [{:tick n :transform {:translation [x y 0.0]
                                   :rotation [0.0 0.0 0.0]
                                   :scale [1.0 1.0 1.0]}} ...]
     :vertex-count n :index-count n :dims {...}}
  for `site` -- the real tessellated test-cylinder envelope
  (`construction.cad/envelope-solid`/`envelope-mesh`), unit-converted to
  meters and given real face normals, plus one `:frames` entry per
  `construction.simphysics/actual-run-for-site` trajectory tick. Unlike
  `fab.scene/scene-for`'s/`autoparts.scene/scene-for`'s own trailing
  `sim-opts` argument (a generic tuning-override map their own
  `simulate` fns accept, e.g. `:pull-mps`/`:travel-m`), this ns takes
  `site` alone: `construction.simphysics/actual-run-for-site` has no
  such generic override mechanism (its only inputs are `site`'s own
  `:design-mix-rated-strength-mpa`/`:cylinder-height-actual-mm`/
  `:cylinder-diameter-actual-mm`, via `resolve-press-inputs`) -- a
  disclosed signature difference, not a decorative unused parameter
  kept only for parity. Every `:transform`'s `:translation` is the SAME
  real, simulated `:specimen-position` (a STATIC mass-0 body's position
  never changes -- see ns docstring's first disclosed difference) --
  NOT an animation of the press-platen's own real motion, which this ns
  does not render (no CAD envelope exists for the platen). `:dims` is
  `construction.cad`'s own millimeter-scale `:dims`
  (`:length-mm`/`:width-mm`/`:height-mm`), kept as informational
  metadata, NOT the unit `:positions` is in."
  [site]
  (let [solid (cad/envelope-solid site)
        {:keys [positions indices]} (cad/envelope-mesh solid)
        positions (mesh->m positions)
        normals (face-normals positions indices)
        {:keys [ticks specimen-position]} (simphysics/actual-run-for-site site)
        [sx sy] specimen-position
        frames (mapv (fn [tick]
                       {:tick tick
                        :transform {:translation [sx sy 0.0]
                                    :rotation [0.0 0.0 0.0]
                                    :scale [1.0 1.0 1.0]}})
                     (range ticks))]
    {:positions positions
     :normals normals
     :indices indices
     :frames frames
     :vertex-count (count positions)
     :index-count (count indices)
     :dims (:dims solid)}))
