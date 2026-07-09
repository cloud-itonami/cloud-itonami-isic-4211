(ns construction.facts
  "Per-jurisdiction construction-site severe-weather/disaster-safety
  regulatory catalog -- the spec-basis table the Construction Governor
  checks every `:weather/assess` and actuation proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  work-stoppage/inspection/reporting requirements, or did it invent
  one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official labor-safety
  and building-safety authorities (see `:provenance` / `:accident-
  report-provenance` / `:periodic-report-provenance`); this is a
  STARTING catalog (JPN/USA/DEU), not a from-scratch survey of all
  ~194 jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  `:threshold-model` is deliberately honest about a real difference
  between jurisdictions:
    :quantitative -- the law itself states a numeric trigger (Japan's
                     10 m/s / 50 mm / 25 cm). `weather-threshold-
                     exceeded?` can independently recompute a HARD
                     hold from this.
    :qualitative  -- the law imposes a general risk-assessment duty
                     with NO fixed numeric trigger (USA, DEU/EU). This
                     actor does NOT invent a threshold to make these
                     jurisdictions look automatable -- `weather-
                     threshold-exceeded?` returns `:qualitative` and
                     the Construction Governor's high-stakes gate
                     (never a HARD rule) routes the decision to a
                     human safety officer instead, every time. See
                     `construction.governor` ns docstring.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `aerospace.facts` established (Germany/EASA for aircraft
  certification) -- there is no ISO-3166 alpha-3 code for the EU
  itself, and the Construction Sites Directive 92/57/EEC is
  transposed into national law per member state (here: Germany's
  Baustellenverordnung), so the citation lists BOTH the EU directive
  and its German transposition rather than inventing an EU country
  code.")

(def catalog
  "iso3 -> requirement map. `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2-style citation the governor requires before
  any `:weather/assess` or actuation proposal can commit.
  `:accident-report-basis` / `:accident-report-provenance` back
  `:actuation/file-accident-report`; `:periodic-report-basis` /
  `:periodic-report-provenance` back `:actuation/file-periodic-
  report` (nil where no federal/EU-wide single equivalent exists --
  reported honestly, not fabricated). `:inspection-evidence` mirrors
  the mandatory post-severe-weather/earthquake site-inspection
  checklist `:inspection/screen` drafts against.

  `:permit-basis` / `:permit-provenance` /
  `:completion-inspection-basis` / `:completion-inspection-provenance`
  back the ROBOT-DISPATCH (build/handover) slice -- the per-jurisdiction
  BUILDING-CODE permit + completion-inspection regime the Construction
  Governor HARD-requires before a physical robot placement can be
  dispatched or a structure handed over (建築基準法 第6条 建築確認 /
  第7条 完了検査; IBC §105 permit / §111 certificate of occupancy;
  Landesbauordnung Baugenehmigung / Abnahme + EU Construction Products
  Regulation 305/2011). Same G2 citation shape as the labor-safety
  entries above; reported HONESTLY (the USA's IBC is a model code, not
  federal statute, and German BauO is state-level -- neither is dressed
  up as a federal law it isn't)."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (労働基準監督署) / 国土交通省"
          :legal-basis "労働安全衛生規則 第522条（強風・大雨・大雪等の悪天候時の高所作業中止）・第655条（悪天候・中震以上の地震後の足場等点検義務）"
          :provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :threshold-model :quantitative
          :thresholds {:wind-speed-ms 10 :rainfall-mm 50 :snowfall-cm 25}
          :threshold-note "10分間平均風速10m/s以上・1回の降雨量50mm以上・1回の降雪量25cm以上、または気象警報発表時を含む（安衛則522条の解釈）"
          :inspection-evidence ["足場・作業構台点検記録 (scaffold/working-platform inspection record)"
                                "建設物・仮設物点検記録 (structure/temporary-work inspection record)"
                                "地盤・法面点検記録 (ground/slope inspection record)"
                                "電気設備点検記録 (electrical-equipment inspection record)"]
          :accident-report-basis "労働安全衛生規則 第97条（労働者死傷病報告）"
          :accident-report-provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :periodic-report-basis "建築基準法 第12条（特定建築物等定期報告制度、通称12条点検）"
          :periodic-report-provenance "https://www.mlit.go.jp/jutakukentiku/build/jutakukentiku_house_tk_000039.html"
          :permit-basis "建築基準法 第6条（建築確認 -- 建築主事等の確認申請・確認済証の交付を受けるまで工事に着工できない）"
          :permit-provenance "https://laws.e-gov.go.jp/law/325AC0000000201"
          :completion-inspection-basis "建築基準法 第7条（完了検査 -- 工事完了後、建築主事等の完了検査に合格し検査済証の交付を受けなければ用途変更・引渡しができない）"
          :completion-inspection-provenance "https://laws.e-gov.go.jp/law/325AC0000000201"}
   "USA" {:name "United States"
          :owner-authority "Occupational Safety and Health Administration (OSHA), U.S. Department of Labor"
          :legal-basis "OSH Act §5(a)(1) General Duty Clause / 29 CFR 1926.20 (General Safety and Health Provisions, competent-person inspections)"
          :provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.20"
          :threshold-model :qualitative
          :thresholds nil
          :threshold-note "No fixed federal numeric wind/rain/snow trigger -- employer risk assessment under the General Duty Clause. Non-binding operational guidance: OSHA Hurricane Preparedness and Response."
          :guidance-provenance "https://www.osha.gov/hurricane/preparedness"
          :inspection-evidence ["Competent-person site inspection record (29 CFR 1926.20)"
                                "Fall-protection inspection record (29 CFR 1926.501, post-storm roof/scaffold work)"
                                "Excavation/trench competent-person inspection record (29 CFR 1926 Subpart P)"]
          :accident-report-basis "29 CFR 1904 (Recording and Reporting Occupational Injuries and Illnesses)"
          :accident-report-provenance "https://www.osha.gov/recordkeeping"
          :periodic-report-basis nil
          :periodic-report-provenance nil
          :periodic-report-note "No single federal periodic-report analog to Japan's Building Standards Act Art.12 -- building/equipment periodic inspection is set by state/local Authority Having Jurisdiction (AHJ) codes, not surveyed here."
          :permit-basis "International Building Code (IBC) §105 Permits (F105.1: permit required before construction/enlargement/alteration) -- ICC model code adopted, with amendments, as state/local law by the Authority Having Jurisdiction"
          :permit-provenance "https://codes.iccsafe.org/s/IBC2021P1/chapter-1-scope-and-administration/IBC2021P1-Ch01-Sec105"
          :permit-note "The IBC is a model code published by the International Code Council (ICC), not federal statute; it becomes law only via state/local AHJ adoption. No federal US building-permit statute is fabricated here -- the IBC is the de facto national regime, cited honestly as the model-code basis it is."
          :completion-inspection-basis "International Building Code (IBC) §111 Certificate of Occupancy (a CO is issued only after a passed final inspection confirms code compliance; a building may not be occupied or handed over without it)"
          :completion-inspection-provenance "https://codes.iccsafe.org/s/IBC2021P1/chapter-1-scope-and-administration/IBC2021P1-Ch01-Sec111"}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Bundesministerium für Arbeit und Soziales (BMAS) / Berufsgenossenschaft der Bauwirtschaft (BG BAU); EU level: European Agency for Safety and Health at Work (EU-OSHA)"
          :legal-basis "Baustellenverordnung (BaustellV, transposing Council Directive 92/57/EEC) / Arbeitsschutzgesetz (ArbSchG, transposing Framework Directive 89/391/EEC Art.5 general risk-assessment duty)"
          :provenance "https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=celex:31992L0057"
          :framework-provenance "https://en.wikipedia.org/wiki/Directive_89/391/EEC"
          :threshold-model :qualitative
          :thresholds nil
          :threshold-note "EU directives set a general risk-assessment duty (Framework Directive 89/391/EEC Art.5), not a fixed EU-wide numeric wind/rain/snow trigger. National/trade-association technical rules (e.g. German DGUV rules) may set quantitative limits but are not surveyed here -- never fabricated."
          :inspection-evidence ["Health & safety plan / coordinator record (Directive 92/57/EEC Art.3, BaustellV §3)"
                                "Site inspection record per national transposition"]
          :accident-report-basis "Framework Directive 89/391/EEC Art.9(1)(c) (employer must record and report occupational accidents to the competent authority; nationally transposed, e.g. German SGB VII / DGUV Unfallanzeige)"
          :accident-report-provenance "https://en.wikipedia.org/wiki/Directive_89/391/EEC"
          :periodic-report-basis "Directive 92/57/EEC health & safety plan obligations (BaustellV §2-§3); no single EU-wide numeric periodic-report analog to Japan's Building Standards Act Art.12"
          :periodic-report-provenance "https://osha.europa.eu/en/legislation/directives/15"
          :permit-basis "Landesbauordnung (state building code, e.g. Musterbauordnung MBO §68 ff.) -- Baugenehmigung (building permit) required before construction; building law is Länder (state) competence, not federal"
          :permit-provenance "https://www.dibt.de/en"
          :permit-note "German building permits/inspections are governed by the state Landesbauordnungen (MBO as the ARGEBAU model), not federal law -- the same honest state-vs-federal layering as the labor-safety entries above. DIBt (Deutsches Institut für Bautechnik) is cited as the central building-authority reference."
          :completion-inspection-basis "Landesbauordnung Abnahme (acceptance/final inspection of the completed structure) + EU Construction Products Regulation (EU) No 305/2011 (CE marking / Declaration of Performance for construction products placed on the EU market)"
          :completion-inspection-provenance "https://eur-lex.europa.eu/legal-content/EN/ALL/?uri=CELEX:32011R0305"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch an
  alert, authorize work-resume, or file a report on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4211 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `construction.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn weather-threshold-exceeded?
  "Independently recompute whether `actuals` ({:wind-speed-actual ..
  :rainfall-actual .. :snowfall-actual ..}, the site's own permanent
  recorded fields) exceeds `iso3`'s regulatory trigger.

  Three-valued, deliberately:
    true        -- a :quantitative jurisdiction (Japan) whose own
                   numeric threshold is independently confirmed
                   exceeded -- a bright-line legal violation. The
                   Construction Governor turns this into a HARD,
                   un-overridable hold on `:actuation/authorize-
                   resume`.
    false       -- a :quantitative jurisdiction confirmed clear.
    :qualitative -- a jurisdiction with NO fixed numeric trigger (USA,
                   DEU/EU). This actor cannot independently confirm
                   'clear' or 'exceeded' by arithmetic alone -- the
                   law itself requires a human risk-assessment
                   judgment call. Never fabricate a threshold to force
                   a true/false answer here. The Construction Governor
                   relies on its permanent high-stakes gate for
                   `:actuation/authorize-resume` (always escalates to
                   a human safety officer) rather than a HARD rule in
                   this case -- see `construction.governor` ns
                   docstring.
    nil         -- no spec-basis at all for `iso3` (a jurisdiction not
                   in `catalog`)."
  [iso3 {:keys [wind-speed-actual rainfall-actual snowfall-actual]}]
  (when-let [{:keys [threshold-model thresholds]} (spec-basis iso3)]
    (case threshold-model
      :quantitative
      (boolean
       (or (and (number? wind-speed-actual) (>= wind-speed-actual (:wind-speed-ms thresholds)))
           (and (number? rainfall-actual) (>= rainfall-actual (:rainfall-mm thresholds)))
           (and (number? snowfall-actual) (>= snowfall-actual (:snowfall-cm thresholds)))))
      :qualitative
      :qualitative
      nil)))

(defn inspection-checklist [iso3]
  (:inspection-evidence (spec-basis iso3) []))
