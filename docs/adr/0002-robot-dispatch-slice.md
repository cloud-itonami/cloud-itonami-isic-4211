# ADR-0002: Construction Advisor ⊣ Construction Governor -- robot-dispatch (build) slice

## Status

Accepted. `cloud-itonami-isic-4211` promoted from `:partially-implemented`
to `:implemented` -- see `blueprint.edn` `:itonami.blueprint/maturity`
and the updated `:itonami.blueprint/implemented-slice`. This ADR closes
ADR-0001 Decision 1's explicit follow-up ("Robot-dispatch ... remains
unimplemented -- a separate follow-up slice of the same
`:construction-governor`").

## Context

ADR-0001 implemented the disaster/severe-weather SAFETY slice of this
blueprint (assess → alert → inspect → resume, plus accident/periodic
reporting) and deliberately left the physical robot-dispatch (panel
placement etc.) example from `docs/operator-guide.md`'s "Day in the
life" walkthrough unimplemented. That operator-guide example -- a crew
lead closing out an exterior-envelope-panel installation phase with a
robot doing the placement, gated by `:construction-governor` requiring
a matching permit/design scope and a safety-class human sign-off
before the action is ever dispatched to hardware -- is the field-work
half of this blueprint's `:robotics`-required-technology and the
`:housing-access` social-impact tag.

This ADR records the SECOND governed-actor slice for the SAME
`:construction-governor`, reusing ADR-0001's Store / Phase / Governor /
Advisor / StateGraph scaffolding verbatim. It adds the physical build
actuation that was missing: a robot physically placing a building
element, and handing over the completed, inspected structure.

## Decision

### Decision 1: two new build/handover actuation ops, same entity, same scaffolding

The construction operating-states are
`intake : design : permit : build : inspect : handover : audit`. This
ADR implements the physical actuation at the `build` and `handover`
states as two new ops:

- `:build/dispatch-placement` -- a construction robot physically places
  a building element (the Operator Guide's exterior-envelope-panel
  example). The proposal names the robot, the panel and the wall
  location it reads off the site's own `:build-target` record (set via
  `:site/intake` -- the advisor normalizes, it does not invent the
  target), exactly the shape the Operator Guide's "propose" step
  describes.
- `:handover/complete` -- hand over the completed, inspected structure
  (引渡し).

Both reuse the existing `site` entity, `Store` protocol, `Phase`
scaffolding, Advisor proposal shape and `operation` StateGraph wiring.
Each gets its OWN dedicated double-actuation-guard boolean
(`:placement-dispatched?` / `:handed-over?`, never a `:status` value),
its own history collection (`placement-history` / `handover-history`)
and sequence counter (`next-placement-sequence` /
`next-handover-sequence`), exactly like the four safety-slice
actuation events. Six actuation events on one entity in total.

### Decision 2: a genuinely-new HARD governor check -- permit + completion-inspection required

The new physical slice gets a HARD check the safety slice does not
have: `permit-and-inspection-required-violations` (governor check 8).
This is grep-verified UNIQUE fleet-wide -- no prior sibling governor
gates on a building permit. It encodes the real-world construction-
law invariant the Operator Guide's "approve" step names ("does the
action match the Phase 3 permit/design scope?"):

- A `:build/dispatch-placement` proposal on a site with NO ISSUED
  BUILDING PERMIT on file is HARD-held -- you cannot dispatch a robot
  to place a building element without the building-code permit.
- A `:handover/complete` proposal ADDITIONALLY requires a PASSED
  COMPLETION INSPECTION on file -- you cannot hand over a structure
  without it.

Two new double-actuation guards (`already-placement-dispatched` /
`already-handed-over`) prevent repeating the same physical act for the
same site, off dedicated boolean facts -- the same discipline as the
safety slice's four.

The check is CONDITIONAL -- it fires ONLY for the two build ops, so the
existing safety-slice checks (including `:inspection/screen`'s own
`:unresolved-hazard` HARD hold on its own finding) are untouched.

### Decision 3: the permit/inspection regime is DATA, cited honestly per jurisdiction

Following ADR-0001 Decision 2's "legal basis is DATA, not code"
discipline, `construction.facts`'s per-jurisdiction catalog is extended
additively with `:permit-basis` / `:permit-provenance` /
`:completion-inspection-basis` / `:completion-inspection-provenance`:

- **JPN** -- 建築基準法 第6条（建築確認 = permit before construction）/
  第7条（完了検査 = completion inspection before occupancy/handover),
  sourced from e-Gov (`325AC0000000201`).
- **USA** -- IBC §105（Permits, F105.1 permit required before
  construction）/ §111（Certificate of Occupancy, issued only after a
  passed final inspection）. The IBC is honestly labeled a model code
  published by ICC and adopted as state/local law by the AHJ -- NOT
  dressed up as federal statute (`:permit-note` says so). No federal US
  building-permit statute is fabricated.
- **DEU** (EU proxy, same convention as ADR-0001) --
  Landesbauordnung Baugenehmigung（permit）/ Abnahme（acceptance/final
  inspection）+ EU Construction Products Regulation (EU) No 305/2011
  (CE marking of construction products). State-level BauO is honestly
  layered with the EU product regulation; neither is misreported.

Coverage is reported HONESTLY: the three seeded jurisdictions all have
a real permit + completion-inspection basis (unlike ADR-0001's
periodic-report, where the USA honestly had no federal analog). The
site's own `:permit-issued?` / `:build-inspection-passed?` booleans --
set via `:site/intake` patches, the same intake-as-fact-source pattern
`:injury-occurred?` / `:hazard-unresolved?` use -- are the independent
fact the governor re-checks; it never trusts the advisor's
self-reported confidence that a permit exists.

### Decision 4: build/handover are NEVER auto-eligible -- always a human's call

`construction.phase` adds both ops to `write-ops` (governor-gated like
any write) and to NONE of its `:auto` sets -- a permanent structural
fact, not a rollout milestone. Dispatching a robot to physically place
a building element and handing over a completed structure are real
physical acts (work at height, near the public); both are always a
human safety officer's call, exactly like `:actuation/authorize-resume`
and the report filings. `construction.governor`'s `high-stakes` gate
agrees independently (both ops are members) -- two layers, not one,
same as the safety slice. The deliberate `:actuation/dispatch-alert`
auto-commit exception (ADR-0001 Decision 5) is unchanged.

### Decision 5: handover renders a certificate document, not just a reference number

Following ADR-0001 Decision 6, `construction.registry/render-handover-
certificate` produces the human-readable completion/handover certificate
text, citing the jurisdiction's completion-inspection basis inline
(which article, which authority, which source URL) -- the concrete
引渡し deliverable, the build-slice analog of the safety slice's
`render-accident-report` / `render-periodic-report`.

## Alternatives considered

- **Reusing `:inspection/screen` as the build completion inspection.**
  Rejected -- that op is the post-severe-weather/earthquake hazard
  screen (a different legal regime and a different point in the
  operating-state sequence). Conflating it with the build completion
  inspection would misrepresent both; a dedicated `:build-inspection-
  passed?` fact (and the completion-inspection legal citations) keeps
  the two slices honest and independently auditable.
- **A uniform "permit + inspection required for BOTH ops" rule (the
  task's high-level phrasing) instead of the staged rule (permit gates
  build; permit + completion-inspection gates handover).** The staged
  rule is the legally accurate reading of the building-code permit/
  inspection regime in all three jurisdictions (建築確認 before
  construction; 完了検査 before occupancy; IBC §105 permit then §111
  CO after final inspection; BauO Baugenehmigung then Abnahme) and is
  MORE honest than requiring a passed inspection before a panel is even
  placed. Both prerequisites still gate the actuation; only the staging
  reflects the law.
- **Modeling the permit/inspection as their own commit effects (new
  ops) rather than site facts set via intake.** Rejected -- the
  Operator Guide states the permit/design record "already exists
  (registered during First Deployment / import)", i.e. it is a
  pre-condition fact about the site, not an actuation the actor
  performs. Modeling it as an intake-set boolean (`:permit-issued?`,
  same shape as `:injury-occurred?`) is both faithful to the guide and
  the smallest change to the Store.

## Consequences

- `cloud-itonami-isic-4211` is now `:implemented`: both the disaster/
  severe-weather safety slice (ADR-0001) AND the physical robot-
  dispatch build slice (this ADR) are real, tested, governed-actor
  code. The Operator Guide's Day-in-the-life panel-placement example
  is now backed by `:build/dispatch-placement` + the permit+inspection
  governor check, not just narrative.
- The genuinely-new `permit-and-inspection-required` HARD check is
  grep-verified UNIQUE fleet-wide (no sibling governor gates on a
  building permit); the two new double-actuation guards and the
  `:build-target` site fact are likewise additive and non-colliding.
- The existing safety slice is unchanged and green -- its 59 tests /
  238 assertions still pass verbatim; this ADR only adds (19 new tests
  / 106 new assertions, 78 / 344 total).
- The `:construction-governor` now enforces EIGHT HARD checks + SIX
  double-actuation guards across six actuation events, all behind one
  advisor → govern → decide → commit | approval graph and one swap-
  able Store (MemStore ‖ DatomicStore, contract-proven equal).

## Follow-ups (not done in this ADR)

- `kotoba-lang/industry`'s registry entry for `"4211"` is promoted to
  `:implemented` in the SAME fleet change (the parent agent's west-pin
  ADR records it). That registry is three-tier
  (`:spec`/`:blueprint`/`:implemented`) with no `:partially-implemented`
  slot, so completing this slice closes the partial-impl phase cleanly
  with no library tier-model change.
- A multi-episode "incident" entity distinct from the long-lived
  `site` (ADR-0001 follow-up) remains a reasonable future extension;
  the build slice keeps the same one-`site`-carries-everything shape.
