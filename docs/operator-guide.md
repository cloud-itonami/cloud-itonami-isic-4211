# Operator Guide

> **Implementation status**: the disaster/severe-weather safety slice
> of this guide (weather assessment, mandatory post-event inspection,
> mail+phone alert dispatch, work-resume authorization, accident/
> periodic report filing) is implemented in `src/construction` -- see
> the README's "Implemented slice" section and `docs/adr/0001-
> architecture.md`. The robot-dispatch example below (Day in the life)
> is not yet implemented.

## First Deployment
1. Register operator, sites, assets, personnel and robots.
2. Import historical records.
3. Run read-only validation and robot mission dry-runs.
4. Configure safety-class allowed sets and human sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for :high/:safety-critical robot actions (e.g. operating on site, near the public or at height)
- audit export for every dispatch, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : design : permit : build : inspect : handover : audit

## Day in the life: an exterior envelope panel phase

This walks the `intake → propose → approve → execute → audit` loop for one concrete unit of
work in this business — a community housing project's `4211` scope covers structural and
envelope work, so the example is a crew lead closing out one **exterior envelope panel
installation phase** with a robot doing the panel placement.

1. **intake** — The permit and design record for the project already exists (registered during
   First Deployment / import). This morning's work item is Phase 3: install the exterior
   envelope panel on the north wall of Unit 4. The crew lead opens the phase record; it is
   scoped to that permit, that unit, that wall — the `:forms` record the `:construction-governor`
   will check everything else against.

2. **propose** — The crew lead queues the panel-placement robot action against Phase 3. This
   is a proposal only: the action names the robot, the panel, the wall location and the permit
   scope it's operating under. Nothing is dispatched yet.

3. **approve** — Because panel placement on an occupied community site is `:safety-critical`
   (work at height, near the public — see Minimum Production Controls), the
   `:construction-governor` requires a human sign-off before it will pass the action through.
   The governor checks: does the action match the Phase 3 permit/design scope
   (`:housing-access` rule)? Is the safety-class sign-off present (`:worker-safety` rule)? If
   either check fails — wrong unit, unscoped change, missing sign-off — the governor holds the
   action and it is never sent to hardware. Once the crew lead signs off, the hold clears for
   **this phase only**; the sign-off does not carry over to Phase 4.

4. **execute** — The governor dispatches the panel-placement action. The robot installs the
   panel; the dispatch, and every parameter it ran with, is what gets logged next.

5. **audit** — The dispatch, the sign-off, and the resulting inspection evidence are written to
   the immutable `:audit-ledger`. Phase 3 is marked complete. If a defect is found later, the
   defect-history record traces back to this exact sign-off and dispatch — this is the evidence
   base the handover and defect-audit revenue line (see `docs/business-model.md`) is sold on.

Skip step 3 — dispatch or clear the phase without the governor sign-off — and the action is a
safety-rule violation, not a shortcut: it is the one thing this business's Trust Controls
guarantee never happens.

### Feel the approval-gate loop hands-on

The `intake → propose → approve → execute` shape above is small enough to be a game. The
network-isekai companion prototype at `network-isekai/public/games/itonami/building-site`
(`ITONAMI: Building Site`) turns this exact blueprint into a playable round: touch the
"site-office" depot to get this round's `:construction-governor` sign-off (the `equipped` flag
in `logic.cljc`), then clear a "phase" job while signed off to complete it. A rarer "inspection"
job is worth 3x a regular phase — same sign-off applies. Clear a phase (or the inspection)
**without** checking in first and it's scored as a governor violation and costs a life, mirroring
step 3 above exactly: an unsigned dispatch never goes through. Clearing all 8 phases tops the
site out clean. It's a useful five-minute way for a new operator to internalize why the sign-off
step exists before working the real intake/propose/approve/execute/audit loop above.
