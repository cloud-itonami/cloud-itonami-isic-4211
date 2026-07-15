(ns construction.cad
  "CAD bridge -- turns a construction site's own recorded ASTM C39/EN
  12390-3 test-cylinder specimen dimensions
  (`:cylinder-height-actual-mm`/`:cylinder-diameter-actual-mm`, fields
  that ALREADY exist and are ALREADY persisted on every site -- see point
  2 below) into a real BREP test-cylinder envelope via `kotoba-lang/
  org-iso-10303`'s `brep.feature` parametric feature tree, then
  tessellates it (`brep.tessellate`) for `construction.simphysics`'s
  `:test-cylinder` AABB placement and `construction.scene`'s render
  bridge (ADR-2607997500, extending ADR-2607995500's isic-0810 /
  ADR-2607992500's isic-2610 / ADR-2607160000's isic-2930 digital-twin
  pattern -- and ADR-2607152000/ADR-2607151600's real-engineering-
  simulation pattern before it -- to THIS vertical: a direct port of
  `autoparts.cad`/`fab.cad`/`quarryops.cad` to this actor's own
  no-sibling-design-library case -- this ns lives directly in
  `construction.*`, same reasoning ADR-2607152000 already used for
  putting the physics module directly in `construction.simphysics`).

  HONEST DESIGN CHOICES, GENUINELY DIFFERENT FROM EVERY PRIOR VERTICAL'S
  DEFAULT SHAPE (verified against THIS vertical's own actual pre-existing
  code, not assumed to carry over unchanged -- ADR-2607997500 explicitly
  calls for checking, not copying, mirroring `quarryops.cad`'s own
  'genuinely different' disclosure):

  1. WHICH BODY GETS THE ENVELOPE. In every prior vertical (autoparts's
     pull-jaw, fab's wire-bond anchor, quarryops's falling fragment), the
     CAD envelope sizes the body that is BOTH 'the test specimen' AND
     the MOVING `physics-2d` body -- a coincidence of those three
     verticals' shared pull/drop-test shape, not a fixed rule. THIS
     vertical's `construction.simphysics` -- like the cementmill sibling
     ADR-2607997500 cites (isic-2394, a genuinely analogous press-closes-
     onto-a-static-specimen shape) -- has the roles SPLIT: the
     press-platen is the MOVING body (a rigid test-machine FIXTURE,
     analogous to `fab.simphysics`'s fixed, non-specimen
     `tension-limit-wall`), and the test-cylinder is the STATIC (mass 0)
     body -- and it is the test-cylinder that is genuinely 'the
     specimen' this ns's envelope models. Checked directly against
     `construction.simphysics/run-press` (platen: `:velocity [v0 0.0]`;
     specimen: `:velocity [0.0 0.0]`, `:mass 0.0`), not assumed from the
     other three verticals' shape. So THIS ns's envelope attaches to the
     STATIC body, and the press-platen stays sized by its own fixed
     `platen-half-w-m`/`platen-half-h-m` constants -- the reverse of
     autoparts/fab/quarryops's moving-body assignment, but the SAME
     underlying rule those three actually follow once stated generally:
     the CAD envelope always models THE SPECIMEN, whichever `physics-2d`
     body that happens to be, never the test-rig fixture.

  2. NO NEW OPT-IN FIELD NEEDED. `autoparts.cad`/`fab.cad`/
     `quarryops.cad` each introduced a BRAND NEW, opt-in `:*-length-mm`
     etc. field because their specimen's pre-ADR AABB was a bare FIXED
     CONSTANT with no real per-record dimension field to read at all.
     THIS vertical is different: `construction.simphysics/run-press`
     (pre-ADR-2607997500) was ALREADY reading real per-site
     `:cylinder-height-actual-mm`/`:cylinder-diameter-actual-mm` fields
     directly (falling back to the ASTM C39 Sec. 6.2 / EN 12390-3
     standard reference dims, `nominal-height-mm`/`nominal-diameter-mm`
     = 300/150mm, when absent) -- and `construction.store`'s schema
     (both `MemStore` and `DatomicStore`) ALREADY declares and persists
     both fields. So there is no persistence gap to disclose here
     (unlike `autoparts.cad`'s/`fab.cad`'s/`quarryops.cad`'s own
     disclosed 'DatomicStore does not yet persist this' gaps) --
     `envelope-dims-mm` below reads the SAME two already-real, already-
     persisted fields, formalizing their existing direct use through a
     genuine BREP feature-tree + tessellation instead of introducing new
     ones. A direct, verifiable consequence (see `construction.
     simphysics`'s own docstring, and `simphysics_test.clj`): routing
     these SAME two fields through this ns produces BIT-IDENTICAL
     results to `construction.simphysics`'s pre-ADR-2607997500 inline
     arithmetic, for every site (not merely for the no-real-data default
     case, as in autoparts/fab/quarryops's own epsilon-close default-
     matching) -- a stronger invariance than any prior vertical achieved,
     because this ns adds no new dimension-sourcing capability the
     physics module lacked; it only formalizes an existing one.

  3. A REAL CYLINDER, NOT A BOX APPROXIMATION -- A KERNEL-MATURITY
     FINDING CHECKED FRESH FOR THIS ADR, NOT CARRIED OVER FROM
     `vdesign.cad`'S/`autoparts.cad`'S/`fab.cad`'S/`quarryops.cad`'S OWN
     DISCLOSURES. Those four namespaces' docstrings each state
     'revolve/fillet/chamfer/boolean are documented not-yet-implemented
     in org-iso-10303', and so each works around that gap with a fixed
     +/-0.5-unit-square `:extrude` cross-section, non-uniformly scaled to
     the target dims. Checked directly against the CURRENT
     `org-iso-10303` `brep.feature`/`brep.tessellate` source (commit
     `a1de3b075bcdcfbfb75c781eb88e3d07195831cf`, this ns's own pinned
     `deps.edn` SHA) rather than assumed unchanged: `brep.feature/
     evaluate` NOW handles a real `:revolve` of a full 2pi turn for a
     single axis-parallel `sketch-line` profile, producing an ACTUAL
     `brep.kernel/make-cylinder` solid (two polygonal cap faces + one
     genuine `:cylinder`-surface side-wall face) -- see
     `org-iso-10303`'s own `revolve-axis-parallel-profile-produces-a-
     cylinder` test. `brep.tessellate/tessellate-solid` correspondingly
     dispatches a `:cylinder`-surface face to a real `tessellate-
     cylinder-face` (a genuine N-segment triangulated side wall, not a
     planar-face fallback). A test-cylinder specimen's true cross-
     section IS a circle, so `envelope-solid` below uses this real
     revolve primitive directly -- a single axis-parallel sketch-line at
     `x = radius-mm` from `y = 0` to `y = length-mm`, revolved a full 2pi
     around `[0 0 1]` -- producing an ACTUAL cylindrical solid at the
     site's own real dimensions, with NO unit-cross-section-then-
     non-uniform-scale work-around needed at all (`envelope-solid`'s
     sketch/revolve params ARE the real mm dimensions directly). This is
     a genuinely more accurate CAD representation than every prior
     vertical's box envelope, made possible by real, verified kernel
     capability gained since the automotive pilot -- not a technique
     invented for this ns, and not assumed available without checking
     `org-iso-10303`'s actual current source. Partial-angle revolves and
     non-axis-parallel (cone/frustum) profiles remain genuinely
     unimplemented in `org-iso-10303` (`evaluate` returns `[:error ...]`
     for both, per that repo's own docstring/tests) -- irrelevant here,
     since a right-circular test cylinder is exactly a full-2pi
     axis-parallel-profile revolve, the ONE case the kernel supports.

  Envelope axis mapping (checked against `construction.simphysics/
  run-press`'s own placement algebra, not assumed): `:length-mm` = the
  cylinder's own AXIAL height (`cylinder-height-actual-mm` -- the
  press's travel axis, i.e. the dimension the platen closes across) --
  `run-press` computes `specimen-half-w` (the specimen's travel-axis
  AABB half-extent) as `height-m / 2`, so length maps to HEIGHT, not
  diameter. `:width-mm` = the cylinder's own DIAMETER
  (`cylinder-diameter-actual-mm`) -- `run-press` uses `radius-m` (=
  diameter/2) directly as the specimen's lateral AABB half-extent.
  `:height-mm` -- UNLIKE `fab.cad`'s/`quarryops.cad`'s own `:height-mm`
  (an inert third box-edge figure kept only so the box envelope is
  genuinely 3D, disconnected from the physical quantity the name
  suggests) -- is here the SAME real figure as `:length-mm`: a standing
  ASTM C39 cylinder's own real height IS its axial dimension, so no
  separate 'third dimension' invention is needed once the envelope is a
  real cylinder rather than a box (a genuine simplification the real-
  cylinder primitive affords, not a coincidence); `construction.
  motionplan`'s working-height derivation reads this same real figure.

  Disclosed persistence note: `construction.store/MemStore`'s
  `:site/upsert` merges arbitrary keys and `DatomicStore`'s schema/
  `site->tx`/`site-pull`/`pull->site` ALREADY declare
  `:cylinder-height-actual-mm`/`:cylinder-diameter-actual-mm` (see point
  2 above) -- both backends round-trip this ns's inputs fine already,
  with no persistence gap to disclose here."
  (:require [brep.feature :as feat]
            [brep.tessellate :as tess]))

(def ^:const default-cylinder-height-mm
  "Fallback specimen-envelope length (mm, along the press's travel axis)
  when a site carries no real `:cylinder-height-actual-mm` -- reproduces
  `construction.simphysics`'s own `nominal-height-mm` (300.0, the ASTM
  C39 Sec. 6.2 / EN 12390-3 standard cylinder height), the SAME default
  `construction.simphysics/press-telemetry` already fell back to before
  ADR-2607997500 (`(or cylinder-height-actual-mm nominal-height-mm)`).
  Redefined here (not required from `construction.simphysics`) to avoid
  a circular require (`construction.simphysics` -> `construction.cad`,
  mirroring every sibling `cad` ns's own redefinition of a physics-ns
  constant, e.g. `fab.cad/default-specimen-height-mm` /
  `quarryops.cad/rock-density-kg-per-m3`)."
  300.0)

(def ^:const default-cylinder-diameter-mm
  "Fallback specimen-envelope width (mm, lateral) -- see
  `default-cylinder-height-mm`; reproduces `construction.simphysics`'s
  own `nominal-diameter-mm` (150.0, ASTM C39 Sec. 6.2 / EN 12390-3
  standard cylinder diameter)."
  150.0)

(defn envelope-dims-mm
  "{:length-mm :width-mm :height-mm} for `site`: its OWN recorded
  `:cylinder-height-actual-mm` / `:cylinder-diameter-actual-mm` (real,
  already-persisted per-site ASTM C39/EN 12390-3 specimen-prep
  measurements -- see ns docstring point 2) when present, or the SAME
  300mm/150mm ASTM/EN standard reference dims `construction.simphysics`
  itself already falls back to when absent. `:length-mm` = height (the
  press's travel axis); `:width-mm` = diameter (lateral); `:height-mm`
  (the specimen's real standing height) reuses `:length-mm` -- see ns
  docstring's 'Envelope axis mapping'. `site` may be `nil`/`{}` (every
  field then falls back to the standard reference dims)."
  [site]
  (let [{:keys [cylinder-height-actual-mm cylinder-diameter-actual-mm]} site
        height-mm (double (or cylinder-height-actual-mm default-cylinder-height-mm))
        diameter-mm (double (or cylinder-diameter-actual-mm default-cylinder-diameter-mm))]
    {:length-mm height-mm
     :width-mm diameter-mm
     :height-mm height-mm}))

(defn envelope-solid
  "Build+evaluate a single-sketch/revolve BREP feature tree sized to
  `site`'s envelope dims (`envelope-dims-mm`) -- a REAL cylindrical
  solid (`brep.kernel/make-cylinder`, via `brep.feature/evaluate`'s
  `:revolve` support), not a box approximation -- see ns docstring point
  3. Returns {:solid :edges :vertices :dims}. Unlike `autoparts.cad`/
  `fab.cad`/`quarryops.cad`/`vdesign.cad`'s own `envelope-solid`, there
  is no unit-scale-then-non-uniform-scale step here: the sketch-line's
  own [radius-mm 0] -> [radius-mm length-mm] coordinates ARE the real
  target dimensions directly, because `org-iso-10303`'s revolve support
  takes real profile coordinates (unlike its `:extrude` support, which
  is still hardcoded to a fixed +/-0.5-unit-square cross-section).
  Throws `ex-info` if evaluation fails (e.g. a future `org-iso-10303`
  regression that reintroduces the 'revolve not implemented' error this
  ns's docstring documents having checked past)."
  [site]
  (let [{:keys [length-mm width-mm] :as dims} (envelope-dims-mm site)
        radius-mm (/ width-mm 2.0)
        ;; sketch on XY, a single axis-parallel line at x = radius-mm
        ;; from y = 0 (axial origin) to y = length-mm (the cylinder's
        ;; own real axial height) -- the exact profile shape
        ;; `org-iso-10303`'s own `revolve-axis-parallel-profile-
        ;; produces-a-cylinder` test exercises, revolved a full 2pi
        ;; around [0 0 1] (mirrors that test's own convention).
        sketch  (feat/sketch-feature 1 (feat/sketch-plane-xy)
                                      [(feat/sketch-line [radius-mm 0.0] [radius-mm length-mm])])
        revolve (feat/revolve-feature 2 1 [0.0 0.0 1.0] (* 2.0 Math/PI) :new)
        tree    (-> (feat/feature-tree)
                    (feat/add-feature sketch)
                    (feat/add-feature revolve))
        [status result] (feat/evaluate tree)]
    (when (not= status :ok)
      (throw (ex-info "brep envelope evaluation failed" {:result result :site site})))
    (let [[solid edges vertices] result]
      {:solid solid :edges edges :vertices vertices :dims dims})))

(defn envelope-mesh
  "Tessellate an `envelope-solid` result into {:positions [[x y z] ...]
  :indices [i0 i1 i2 ...]} -- the shape `construction.scene/scene-for`
  consumes. `brep.tessellate/tessellate-solid` dispatches the cylinder's
  two planar cap faces to its planar-face triangulation and its one
  `:cylinder`-surface side-wall face to a real N-segment triangulated
  wall (`brep.config/cylinder-segments`) -- both real geometry, not a
  degenerate/placeholder mesh."
  [{:keys [solid edges vertices]}]
  (let [[positions indices] (tess/tessellate-solid solid edges vertices)]
    {:positions positions :indices indices}))
