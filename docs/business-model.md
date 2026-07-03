# Business Model: Community Building Construction

## Classification
- Repository: `cloud-itonami-4211`
- ISIC Rev.5: `4211` — building construction — structural, envelope and finishing work for community-scale projects
- Domain: `:construction/building`
- License: `AGPL-3.0-or-later`
- Social impact: `:housing-access` `:worker-safety` `:local-economy`

## Customer
- community builders, housing cooperatives and small contractors leaving closed project-management SaaS

## Offer
- design and permit intake, structural and envelope work, inspection records, handover and defect audit

## Revenue
- setup fee per project, monthly operations subscription, inspection and handover services

## The `:construction-governor` decision rule

`blueprint.edn` names `:itonami.blueprint/governor :construction-governor` and marks
`:itonami.blueprint/robotics true`. In this business the governor is the one checkpoint every
robot dispatch and every phase sign-off must clear — it is not a generic "approval step", it is
the mechanism that turns the three social-impact tags into enforceable rules:

- **`:housing-access`** → the governor rejects any dispatch or phase completion that is not tied
  to a registered project/permit record. Work that would advance a build outside the intake
  record it was scoped against (wrong unit, unscoped change order) is refused — this is what
  keeps unit costs and timelines predictable enough for housing-cooperative and small-contractor
  customers to budget against, per the "Customer" section above.
- **`:worker-safety`** → the governor is the single point that separates a *proposed* robot
  action (e.g. structural/envelope work near the public or at height) from *dispatch to
  hardware*. Per `docs/operator-guide.md`'s Minimum Production Controls, `:high`/`:safety-critical`
  actions require human sign-off before the governor will pass them through; a robot action the
  governor refuses is never sent to hardware, full stop.
- **`:local-economy`** → inspection and defect evidence the governor requires (see Trust
  Controls below) is what lets handover and defect-audit revenue (the "Revenue" section) be sold
  as a trustworthy service to local contractors, rather than something a departing SaaS vendor
  could dispute or withhold.

Concretely, the governor's rule is: **no work-phase is marked complete, and no robot action is
dispatched, without a matching sign-off record for that specific unit of work.** A sign-off is
scoped to one phase — completing a phase consumes it, so the next phase needs its own governor
check, never a standing blanket permit. This "one sign-off per phase" shape is literal enough
that it has a playable model: see `docs/operator-guide.md` for the `building-site` prototype,
where clearing a "phase" job without first checking in with the governor is scored as a safety
violation.

## Required technologies (`blueprint.edn` `:itonami.blueprint/required-technologies`)

| Technology | What it is FOR in this business |
|---|---|
| `:robotics` | Structural/envelope work robots on a physical build site (framing, envelope panel placement, material handling). Every action they take is subject to the `:construction-governor` gate before dispatch — this is the field-work half of the "Offer" (structural and envelope work). |
| `:identity` | Distinguishes operator, site personnel, robots and inspectors so that a sign-off, a dispatch, or a disclosure can be attributed to a specific actor — required for the human sign-off path on `:high`/`:safety-critical` robot actions and for defect-history accountability. |
| `:forms` | Captures the design/permit intake record and inspection records referenced in "Offer" — the structured input the governor checks dispatches and phase completions against (the housing-access rule above). |
| `:dmn` | Encodes the governor's decision rule itself (permit/design match, safety-class checks, sign-off requirements) as reviewable decision tables rather than opaque code — this is what makes `:construction-governor` an auditable rule instead of a black box. |
| `:bpmn` | Models the operating-state sequence `intake : design : permit : build : inspect : handover : audit` (see `docs/operator-guide.md`) as an explicit process, so the order those states must happen in — permit before build, inspect before handover — is enforced, not just documented. |
| `:audit-ledger` | Immutable log of every dispatch, hold, approval and disclosure. This is what backs the Trust Controls below ("defect history is immutable") and is the evidence base sold as part of inspection/handover services. |

## Trust Controls
- work outside permit or design is blocked; inspection evidence is required; defect history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive operating and personal data stays outside Git
