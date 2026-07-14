# cloud-itonami-4211

Open Business Blueprint for **ISIC Rev.5 4211**: building construction — structural, envelope and finishing work for community-scale projects.

This repository designs a forkable OSS business for community building construction:
run by a qualified operator so a community keeps its own operating records
instead of renting a closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a construction robot performs bricklaying, printing, lifting and inspection on site under an actor that proposes
actions and an independent **Construction Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating on site, near the public or at height) require human sign-off.

**Robot process simulation is concrete, not just a flag** (ADR-2607150700,
extending ADR-2607142800 / ADR-2607011000): `construction.robotics` walks
every build-target site through a robot-executed pre-placement
verification mission (`kotoba.robotics` mission/action/telemetry-proof
contracts) -- rebar-placement scan, robotic total-station as-built
survey, concrete-cure compressive-strength test-cylinder press --
before `:build/dispatch-placement` is proposable. The Construction
Governor independently re-derives the site's own as-built-deviation
tolerance from ground-truth fields, never trusting the mission's
self-reported verdict alone.

## Core Contract

```text
intake + identity + identity records
        |
        v
Advisor -> Construction Governor -> proceed, hold, or human approval
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `4211`). Required capabilities:

- `:robotics`
- `:identity`
- `:forms`
- `:dmn`
- `:bpmn`
- `:audit-ledger`
- `:notifications`

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Implemented slices (`src/construction`)

`blueprint.edn` names the governor `:construction-governor` and is now
`:implemented`. This repo implements BOTH vertical slices of that
governor end-to-end -- **Construction Advisor ⊣ Construction Governor**
-- following the SAME `.cljc` actor pattern (langgraph-clj StateGraph,
mock-by-default advisor, dual MemStore/Datomic backend, 0→3 phase
rollout) every prior `cloud-itonami-isic-*` actor in this fleet uses
(see e.g.
[`cloud-itonami-isic-3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030)).

### Slice 1 — disaster / severe-weather safety

| Ask | Implementation |
|---|---|
| 台風など災害の呼びかけ (disaster/typhoon outreach) | `:weather/assess` (per-jurisdiction legal work-stoppage determination, human-approved) → `:actuation/dispatch-alert` (mail + phone to every site worker contact, `construction.notify`) |
| 点検 (inspection) | `:inspection/screen` -- mandatory post-severe-weather/earthquake hazard screening gating `:actuation/authorize-resume` |
| 予防 (prevention) | The whole assess → stop-work → mandatory-inspect → authorize-resume loop -- work cannot resume while the site's own weather figures still exceed the legal threshold or its hazard is unresolved |
| 共有 (sharing) | `construction.notify/dispatch-alert!` fans the alert out to every contact in a site's `:worker-contacts` roster, mail AND phone, with per-contact/per-channel failure isolation |
| 報告書作成 (report generation) | `:actuation/file-accident-report` / `:actuation/file-periodic-report` -- `construction.registry/render-accident-report` / `render-periodic-report` produce the actual document text, citing the jurisdiction's legal basis inline |

### Slice 2 — physical robot-dispatch (build)

The Operator Guide's Day-in-the-life exterior-envelope-panel example is
now real code. Two physical actuation ops on the same `site` entity,
gated by a concrete robot pre-placement verification mission:

| Ask | Implementation |
|---|---|
| ロボット検証 (robot pre-placement verification) | `:robotics/simulate-placement-verification` -- runs the robot mission (`construction.robotics`: rebar-placement scan / total-station as-built survey / concrete-cure test-cylinder press) and records `:robotics-sim-verified?` + `:robotics-sim-record` on the site (always human approval, never auto) |
| 施工 (build / robot placement) | `:build/dispatch-placement` -- a construction robot physically places a building element (panel @ wall by robot, read off the site's `:build-target`). Governor check 8 HARD-requires an ISSUED BUILDING PERMIT on file, and check 9 HARD-requires the robot pre-placement verification mission to have actually run and independently recheck in-tolerance, before this can ever commit |
| 引渡し (handover) | `:handover/complete` -- hand over the completed, inspected structure. Governor check 8 ADDITIONALLY HARD-requires a PASSED COMPLETION INSPECTION on file. `construction.registry/render-handover-certificate` produces the completion certificate, citing the jurisdiction's completion-inspection basis inline |

**Legal basis is data, not code** -- `src/construction/facts.cljc`'s
`catalog` is the per-jurisdiction EDN source-of-truth the governor
checks every proposal against (JPN/USA/DEU seeded; DEU stands in for
the EU, the same convention `aerospace.facts` uses for EASA):

| Jurisdiction | Safety-slice legal basis | Build-slice legal basis |
|---|---|---|
| 🇯🇵 Japan | 労働安全衛生規則 第522条・第655条・第97条 ／建築基準法 第12条 | 建築基準法 第6条（建築確認）・第7条（完了検査） — [e-Gov 建築基準法](https://laws.e-gov.go.jp/law/325AC0000000201) |
| 🇺🇸 USA | OSH Act §5(a)(1) / 29 CFR 1926.20 / 29 CFR 1904 | IBC §105（Permits）・§111（Certificate of Occupancy）— [ICC IBC 2021](https://codes.iccsafe.org/s/IBC2021P1/chapter-1-scope-and-administration/IBC2021P1-Ch01-Sec105) (model code, honestly labeled) |
| 🇪🇺 EU (DEU proxy) | Framework Directive 89/391/EEC / Construction Sites Directive 92/57/EEC / Baustellenverordnung | Landesbauordnung Baugenehmigung・Abnahme + EU Construction Products Regulation 305/2011 — [EUR-Lex 305/2011](https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX:32011R0305) |

Japan has a real numeric trigger (10 m/s wind / 50 mm rain / 25 cm
snow, per 10-min/event); the USA and EU deliberately do NOT --
`construction.facts/weather-threshold-exceeded?` reports `:qualitative`
there rather than fabricating a number, and the Construction Governor
routes those jurisdictions' work-resume decision to a human safety
officer every time instead of a hard numeric rule. The build-slice
permit+inspection regime is honestly per-jurisdiction too: the USA's
IBC is cited as the ICC model code it is (not federal statute) and
Germany's BauO is cited as the state law it is (layered with the EU
CPR). See `construction.facts` ns docstring for the full honesty
discipline and `construction.governor` for how each check maps to a
specific legal citation.

**Actuation.** Six real-world acts this actor performs each get their
own governor hard-gates + double-actuation guard:
`:actuation/dispatch-alert` (mail+phone warning),
`:actuation/authorize-resume` (work-resume authorization),
`:actuation/file-accident-report`, `:actuation/file-periodic-report`
(legal report filings), `:build/dispatch-placement` (robot placement)
and `:handover/complete` (structure handover). `:actuation/dispatch-
alert` is the ONE deliberate exception to this fleet's "actuation
always needs a human" norm -- it MAY auto-commit at phase 3 when the
governor is clean (a real legal-basis citation on file AND a
human-approved `:stop-work` weather assessment already committed),
because for a disaster warning dispatch SPEED is itself the safety
property. The other five never auto-commit at any phase -- see
`construction.phase` ns docstring 'Actuation' section,
`construction.governor` checks 2/7/8/9, and `docs/adr/0002-robot-
dispatch-slice.md` for the build-slice decisions.
`:robotics/simulate-placement-verification` is not itself an
actuation event (`:stake nil`), but like `:inspection/screen` it is
never auto-eligible at any phase either -- see
`90-docs/adr/2607150700-cloud-itonami-isic-4211-robotics-retrofit.md`
(extending ADR-2607142800 / ADR-2607011000) for the concrete robot
pre-placement verification mission gating `:build/dispatch-placement`.

```bash
clojure -M:dev:run    # demo: full typhoon episode + robot-dispatch build slice + robot pre-placement verification + every HARD hold
clojure -M:dev:test   # 83 tests / 372 assertions
clojure -M:lint       # clj-kondo, errors fail
```

## License

AGPL-3.0-or-later.
