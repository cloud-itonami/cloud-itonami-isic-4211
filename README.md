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

## Implemented slice: disaster / severe-weather safety (`src/construction`)

`blueprint.edn` names the governor `:construction-governor`. This repo
implements ONE vertical slice of it end-to-end -- **Construction
Advisor ⊣ Construction Governor** for typhoon/severe-weather safety at
a site, following the SAME `.cljc` actor pattern (langgraph-clj
StateGraph, mock-by-default advisor, dual MemStore/Datomic backend,
0→3 phase rollout) every prior `cloud-itonami-isic-*` actor in this
fleet uses (see e.g.
[`cloud-itonami-isic-3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030)).
Physical robot-dispatch ops (panel placement etc., see the Operator
Guide's Day-in-the-life example) are a SEPARATE, not-yet-implemented
slice of the same governor -- see `blueprint.edn`
`:itonami.blueprint/implemented-slice`.

| Ask | Implementation |
|---|---|
| 台風など災害の呼びかけ (disaster/typhoon outreach) | `:weather/assess` (per-jurisdiction legal work-stoppage determination, human-approved) → `:actuation/dispatch-alert` (mail + phone to every site worker contact, `construction.notify`) |
| 点検 (inspection) | `:inspection/screen` -- mandatory post-severe-weather/earthquake hazard screening gating `:actuation/authorize-resume` |
| 予防 (prevention) | The whole assess → stop-work → mandatory-inspect → authorize-resume loop -- work cannot resume while the site's own weather figures still exceed the legal threshold or its hazard is unresolved |
| 共有 (sharing) | `construction.notify/dispatch-alert!` fans the alert out to every contact in a site's `:worker-contacts` roster, mail AND phone, with per-contact/per-channel failure isolation |
| 報告書作成 (report generation) | `:actuation/file-accident-report` / `:actuation/file-periodic-report` -- `construction.registry/render-accident-report` / `render-periodic-report` produce the actual document text, citing the jurisdiction's legal basis inline |

**Legal basis is data, not code** -- `src/construction/facts.cljc`'s
`catalog` is the per-jurisdiction EDN source-of-truth the governor
checks every proposal against (JPN/USA/DEU seeded; DEU stands in for
the EU, the same convention `aerospace.facts` uses for EASA):

| Jurisdiction | Legal basis | Source |
|---|---|---|
| 🇯🇵 Japan | 労働安全衛生規則 第522条（悪天候時の作業中止）・第655条（悪天候後の点検義務）・第97条（労働者死傷病報告）／建築基準法 第12条（定期報告） | [e-Gov 労働安全衛生規則](https://laws.e-gov.go.jp/law/347M50002000032) / [国土交通省 定期報告制度](https://www.mlit.go.jp/jutakukentiku/build/jutakukentiku_house_tk_000039.html) |
| 🇺🇸 USA | OSH Act §5(a)(1) General Duty Clause / 29 CFR 1926.20 / 29 CFR 1904 | [OSHA 1926.20](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.20) / [OSHA Recordkeeping](https://www.osha.gov/recordkeeping) |
| 🇪🇺 EU (DEU proxy) | Framework Directive 89/391/EEC / Construction Sites Directive 92/57/EEC / Baustellenverordnung | [EUR-Lex 92/57/EEC](https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=celex:31992L0057) / [EU-OSHA](https://osha.europa.eu/en/legislation/directives/15) |

Japan has a real numeric trigger (10 m/s wind / 50 mm rain / 25 cm
snow, per 10-min/event); the USA and EU deliberately do NOT --
`construction.facts/weather-threshold-exceeded?` reports `:qualitative`
there rather than fabricating a number, and the Construction Governor
routes those jurisdictions' work-resume decision to a human safety
officer every time instead of a hard numeric rule. See
`construction.facts` ns docstring for the full honesty discipline and
`construction.governor` for how each check maps to a specific legal
citation.

**Actuation.** Four real-world acts this actor performs each get their
own governor hard-gates + double-actuation guard:
`:actuation/dispatch-alert` (mail+phone warning),
`:actuation/authorize-resume` (work-resume authorization),
`:actuation/file-accident-report`, `:actuation/file-periodic-report`
(legal report filings). `:actuation/dispatch-alert` is the ONE
deliberate exception to this fleet's "actuation always needs a human"
norm -- it MAY auto-commit at phase 3 when the governor is clean
(a real legal-basis citation on file AND a human-approved `:stop-work`
weather assessment already committed), because for a disaster warning
dispatch SPEED is itself the safety property. The other three never
auto-commit at any phase -- see `construction.phase` ns docstring
'Actuation' section and `construction.governor` checks 2/7.

```bash
clojure -M:dev:run    # demo: full typhoon episode + every HARD hold
clojure -M:dev:test   # 59 tests / 238 assertions
clojure -M:lint       # clj-kondo, errors fail
```

## License

AGPL-3.0-or-later.
