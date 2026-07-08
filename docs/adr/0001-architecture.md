# ADR-0001: Construction Advisor ⊣ Construction Governor -- disaster/severe-weather safety slice

## Status

Accepted. `cloud-itonami-isic-4211` promoted from `:blueprint` (docs
only) to `:partially-implemented` -- see `blueprint.edn`
`:itonami.blueprint/implemented-slice`.

## Context

`cloud-itonami-isic-4211` publishes an OSS business blueprint for
community building construction. Before this change, the repository
had `blueprint.edn` + `docs/business-model.md` + `docs/operator-
guide.md` only -- no `src/`, no code, no tests. Those docs already
name the governor `:construction-governor` and describe an `intake :
design : permit : build : inspect : handover : audit` operating-state
sequence, but implement none of it.

This ADR records the FIRST governed-actor implementation for this
blueprint, following the same langgraph-clj StateGraph + independent
Governor + mock-advisor-by-default + Phase 0→3 rollout + dual
MemStore/DatomicStore pattern `cloud-itonami-isic-3030` (aerospace
manufacturing) uses. It deliberately implements ONE vertical slice --
typhoon/severe-weather disaster safety (assess → alert → inspect →
resume, plus legal accident/periodic reporting) -- rather than the
physical robot-dispatch (panel placement etc.) example in the
Operator Guide's "Day in the life" walkthrough, which remains
unimplemented and is a separate follow-up under the same governor.

## Decision

### Decision 1: scope this slice to disaster/severe-weather safety, not robot dispatch

The Operator Guide's own worked example is an exterior-envelope-panel
placement robot action. This ADR does NOT implement that op. Instead
it implements the disaster/severe-weather safety concern named in the
blueprint's `:worker-safety` social-impact tag and required by real
labor-safety law in every surveyed jurisdiction (see Decision 2) --
weather-triggered work-stoppage, mandatory post-event inspection,
worker alerting, work-resume authorization, and legal report filing.
Both are legitimate slices of the same `:construction-governor`;
robot-dispatch is left for a follow-up that can reuse this ADR's
Store/Phase/Governor scaffolding.

### Decision 2: legal basis is DATA (`construction.facts`), not code, with an honest quantitative/qualitative split

`src/construction/facts.cljc`'s `catalog` seeds three jurisdictions
with real official-source citations:

- **JPN** -- 労働安全衛生規則 第522条 (work-stoppage) / 第655条
  (post-severe-weather inspection) / 第97条 (労働者死傷病報告) and
  建築基準法 第12条 (12条点検), sourced from e-Gov and MLIT.
- **USA** -- OSH Act §5(a)(1) General Duty Clause / 29 CFR 1926.20 /
  29 CFR 1904, sourced from osha.gov.
- **DEU** (EU jurisdiction proxy -- the SAME convention
  `aerospace.facts` established for EASA) -- Framework Directive
  89/391/EEC / Construction Sites Directive 92/57/EEC /
  Baustellenverordnung, sourced from EUR-Lex and EU-OSHA.

Japan's law states a real numeric trigger (10 m/s wind / 50 mm rain /
25 cm snow); the USA and EU deliberately do NOT -- `:threshold-model`
is `:qualitative` for both, and `weather-threshold-exceeded?` returns
the keyword `:qualitative` rather than fabricating a true/false
answer. This is a genuine, defensible legal difference (risk-
assessment duty vs. bright-line numeric rule), not an implementation
gap -- see `construction.facts` ns docstring.

### Decision 3: the qualitative/quantitative split changes WHERE a rule is HARD vs. SOFT

Because a HARD governor violation is, by this fleet's convention,
un-overridable by a human, it would be wrong to hard-hold a USA/EU
work-resume decision against a fabricated numeric threshold. Instead:
`weather-still-exceeds-threshold-violations` (a HARD check) fires only
when `weather-threshold-exceeded?` returns `true` (Japan-style,
independently confirmed); for `:qualitative` jurisdictions, the SAME
decision instead always reaches a human via the permanent `high-
stakes` gate on `:actuation/authorize-resume` (SOFT, but
unconditional). Two different jurisdictions, two different
enforcement layers, same never-fabricate discipline.

### Decision 4: entity and op shape -- one `site`, four actuation events

The primary entity is a `site` (a construction site), carrying weather
actuals, a hazard-screening flag, an injury flag, and a worker-contact
roster alongside four dedicated double-actuation-guard booleans
(`:alert-dispatched?` / `:work-resumed?` / `:accident-reported?` /
`:periodic-report-filed?`, never a single `:status` value -- the same
discipline every governor guard in this fleet uses). Seven ops:
`:site/intake` (no capital risk), `:weather/assess` and `:inspection/
screen` (never auto, per-jurisdiction/per-site determinations), and
four actuation ops (`:actuation/dispatch-alert`, `:actuation/
authorize-resume`, `:actuation/file-accident-report`, `:actuation/
file-periodic-report`).

### Decision 5: `:actuation/dispatch-alert` is a deliberate, reasoned exception to "actuation always needs a human"

Every actuation op in this fleet (aerospace's assembly-dispatch/
airworthiness-evidence, and every prior sibling) is permanently
excluded from every phase's `:auto` set, because an ERRONEOUS
auto-commit is the harm being guarded against. A disaster alert has
the OPPOSITE risk shape: the primary harm is a DELAYED warning (a
human approver asleep, unreachable, or simply slow during the
disaster itself), while an unnecessary warning costs almost nothing.
`construction.phase` therefore puts `:actuation/dispatch-alert` in
phase 3's `:auto` set -- but ONLY when `construction.governor` is
clean, which requires (a) a real legal-basis citation for the
jurisdiction, (b) the site not already dispatched, AND (c),
critically, a HUMAN-APPROVED `:weather/assess` determination
(`:stop-work`/`:review-required`) already committed for this site
(`no-approved-stop-work-assessment-violations`, governor check 2).
Because `:weather/assess` is itself never auto-eligible, a human is
always in the loop for the underlying weather determination -- the
alert dispatch is fast, but never on the advisor's say-so alone.

### Decision 6: report generation renders an actual document, not just a reference number

`construction.registry/render-accident-report` /
`render-periodic-report` produce human-readable report TEXT citing the
jurisdiction's legal basis inline (which article, which authority,
which source URL) -- the concrete 報告書作成 deliverable, not merely
an internal EDN record. `render-periodic-report` is honest when a
jurisdiction has no periodic-report basis on file (USA has none at
federal level): it prints "NOT COVERED" rather than inventing one.

### Decision 7: mail + phone dispatch as a dedicated `construction.notify` namespace

Following `cloud-itonami.mail`'s (gftdcojp/cloud-itonami) real
Resend-via-`java.net.http` convention exactly (`{:url :method
:headers :body} -> {:status :body}` injectable `:http-fn`), this repo
adds `construction.notify` with `mock-notifier` (deterministic,
default everywhere) and real `resend-mail-notifier` /
`twilio-voice-notifier` (JVM-only, `#?(:clj ...)`-gated). Twilio Voice
is used for phone (電話) specifically, not SMS, per the request: an
outbound call speaking inline TwiML `<Say>` text, needing no separate
webhook host for a simple spoken alert. `dispatch-alert!` fans one
alert out to every contact in a site's `:worker-contacts` roster over
BOTH channels with per-contact/per-channel failure isolation -- one
bad phone number must never suppress the mail, or the next worker's
call. This repo adds no JSON-library dependency (a hand-rolled minimal
encoder in `construction.notify`, scoped to the one known small
request shape it sends) to keep this actor standalone-forkable per
`deps.edn`'s existing convention.

### Decision 8: Store protocol, MemStore + DatomicStore parity

`construction.store/Store` is implemented by both `MemStore` (atom-
backed default) and `DatomicStore` (`langchain.db`-backed), proven to
satisfy the same contract in `test/construction/store_contract_test.clj`
-- the same seam every sibling actor uses.

### Decision 9: `blueprint.edn` merge with the concurrent network-isekai game reference (PR #1)

Between this branch's work starting and its push, `origin/main`
advanced two commits (`ed6caa0`/`1d2633c`, PR #1) adding
`:itonami.blueprint/game` (a reference to the `building-site`
minigame in `gftdcojp/network-isekai`, ADR-2607031000) to the same
`blueprint.edn` this ADR's build also edits (`:required-technologies`
`:notifications`, `:maturity`, `:implemented-slice`). Per this
repo's git-sync discipline (fetch + `--ff-only`, never rebase), the
two edits were reconciled by hand -- both additive, non-overlapping
keys in the same top-level map, so the merge is a straight union with
no semantic conflict: `blueprint.edn` now carries `:game` (PR #1) AND
`:maturity`/`:implemented-slice`/the extended `:required-technologies`
(this ADR) together. No content from either branch was dropped.

## Alternatives considered

- **A single "site" that is ALSO the storm-episode record, re-used
  across multiple typhoons over the site's lifetime.** Rejected for
  this slice: each of the four actuation booleans is a one-time,
  permanent guard (matching every sibling's dual-actuation shape), so
  a second storm at the same site would need its own record. A
  multi-episode "incident" entity distinct from the long-lived `site`
  is a reasonable future extension, not implemented here.
- **Treating `:actuation/dispatch-alert` the same as every other
  actuation op (never auto).** Rejected -- see Decision 5. The risk
  asymmetry is real and jurisdiction-independent (a delayed warning
  during an active disaster is a distinct, worse failure mode than an
  over-cautious one), and the exception is gated by an additional HARD
  check (Decision 5c) rather than left ungated.
- **Fabricating numeric wind/rain/snow thresholds for the USA and EU
  to make `weather-threshold-exceeded?` uniformly `true`/`false`.**
  Rejected outright -- this would misrepresent USA/EU law (a
  risk-assessment duty, not a bright-line rule) and violate this
  fleet's "never fabricate a jurisdiction's requirements" discipline
  every `facts` namespace holds to.
- **SMS instead of / in addition to a real phone call for the "電話"
  channel.** Rejected as the primary implementation -- the request was
  specifically mail and phone (call), and Twilio Voice's inline-TwiML
  `<Say>` param supports a spoken alert without extra infrastructure.
  SMS remains a reasonable future channel addition via the same
  `Notifier` protocol.

## Consequences

- `cloud-itonami-isic-4211` now has a real, tested governed-actor
  implementation for its `:worker-safety` social-impact tag, with a
  legal-basis catalog that is data (EDN, cited, sourced) rather than
  logic buried in code.
- Robot-dispatch (the Operator Guide's panel-placement example) is
  explicitly NOT covered by this ADR -- `blueprint.edn`'s
  `:itonami.blueprint/maturity` is `:partially-implemented`, not
  `:implemented`, until that slice lands too.
- `construction.notify` is the only namespace in this repo that
  touches the network or reads `RESEND_API_KEY`/`TWILIO_*` -- every
  other namespace stays pure per its own contract, matching
  `cloud-itonami.mail`'s isolation discipline in the sibling
  gftdcojp/cloud-itonami repo.
- `blueprint.edn` cleanly absorbed PR #1's concurrent `:game` key
  (Decision 9) -- confirms the "additive keys in a flat top-level EDN
  map" shape this fleet's blueprints use stays merge-friendly even
  under genuinely concurrent edits from independent sessions.

## Follow-ups (not done in this ADR)

- `kotoba-lang/industry`'s `resources/kotoba/industry/registry.edn`
  entry for `"4211"` is stale: `:repo` points at
  `https://github.com/gftdcojp/cloud-itonami-4211` (wrong org, missing
  the `isic-` infix -- the real repo is `cloud-itonami/cloud-itonami-
  isic-4211`) and `:business-id` has the same missing-infix bug; the
  entry also has no `:maturity` field at all (most of that registry's
  642 other entries do). Left unfixed here because promoting a
  registry entry to `:implemented` in that fleet's tracking system
  (`docs/cloud-itonami.md`'s running tally, `test/kotoba/
  industry_test.clj`'s hardcoded count assertions) is that repo's own
  workflow and this build is only `:partially-implemented` (robot-
  dispatch is out of scope, see Decision 1) -- a follow-up PR there
  should fix the stale fields and decide how (or whether) a
  `:partially-implemented` tier fits that registry's existing
  three-tier (`:spec`/`:blueprint`/`:implemented`) counting invariant
  before touching it.
- Robot-dispatch (panel placement etc., the Operator Guide's Day-in-
  the-life example) remains unimplemented -- a separate follow-up
  slice of the same `:construction-governor`, reusing this build's
  Store/Phase scaffolding.
