(ns construction.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: `docs/samples/
  operator-console.html` previously shipped a SHARED, hand-described
  robotics-safety template (`kotoba.robotics.ui` output -- two made-up
  missions `M1`/`M2` and four made-up actions `A1`..`A4`) that named
  none of this actor's own sites, ops, governor rules or ledger facts.
  This namespace replaces it by driving the REAL actor stack
  (`construction.operation` -> `construction.governor` ->
  `construction.phase` -> `construction.store` -> `construction.
  registry`) at build time and rendering whatever that run actually
  produced.

  EVERY id, number, verdict, rule name and hold reason on the page is
  read back out of the store and the append-only audit ledger after the
  scenario has run -- there is no hand-typed row anywhere below except
  the `action-gate-rows` block, which documents this actor's own FIXED
  op contract (`construction.phase/phases`, `construction.governor/
  high-stakes`) and is labelled as such in place.

  The scenario is adapted from this repo's own `construction.sim` demo
  driver (`clojure -M:dev:run`, run BEFORE writing this file to confirm
  it produces a sensible ledger). `construction.sim`'s site ids
  (`site-1`..`site-6`) were cross-checked against `construction.store/
  demo-data` and all six really exist there, so reusing the scenario
  shape was safe; it is trimmed here to a representative subset (two
  full clean lifecycles and six distinct HARD-hold reasons).

  DETERMINISTIC: the seeded store, the governor and the registry are
  all pure/derived -- no timestamps, no randomness, no wall-clock in
  the page content. Two consecutive runs against the same seed produce
  byte-identical output (verify by diffing them).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [construction.store :as store]
            [construction.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :safety-officer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every
  disposition this actor can reach.

  Clean lifecycle A -- site-1 (JPN, disaster-safety slice): intake
  (auto-commits at phase 3), a severe-weather assessment (phase-gated,
  human approves, recommendation `:stop-work`), a post-severe-weather
  hazard screening (human approves, verdict `:resolved`), a worker
  alert dispatch (AUTO-commits -- the one deliberate actuation
  exception in `construction.phase`, mail+phone actually sent through
  the injected notifier), a work-resume authorization attempted WHILE
  the site's own recorded wind is still over Japan's 10 m/s statutory
  trigger (HARD hold, never reaches a human), the storm passing
  (intake patch, auto-commits), the resume retried (human approves),
  an injury recorded (intake patch), an accident report filed (human
  approves) and a periodic report filed (human approves).

  Clean lifecycle B -- site-4 (JPN, robot build/handover slice): the
  building permit recorded via intake, a robot panel-placement
  dispatch attempted BEFORE the pre-placement verification mission
  ever ran (HARD hold), the mission run (rebar scan / total-station
  as-built survey / a REAL `physics-2d`-stepped concrete-cure
  test-cylinder press, human approves), the placement dispatched
  (human approves -- a physical act, never auto at any phase), the
  completion inspection recorded via intake, and the structure handed
  over (human approves, handover certificate rendered).

  HARD holds that never reach a human: site-2's weather assessment
  cites no official spec-basis for its unregistered jurisdiction;
  site-3's hazard screening finds an unresolved hazard; site-6's
  placement dispatch has a verification mission on file but BOTH its
  as-built deviation AND its independently re-simulated concrete-cure
  press reading recheck out of tolerance (two rules co-fire); site-5's
  handover has neither a permit nor a passed completion inspection on
  file (two rules co-fire).

  Returns the resulting store -- every field `render` reads below is
  real governor/store/registry output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    ;; --- site-1: full disaster-safety lifecycle -----------------------
    (exec! actor "s1-intake" {:op :site/intake :subject "site-1"
                              :patch {:id "site-1"
                                      :name "Sakura Community Housing Block C"}})

    (exec! actor "s1-assess" {:op :weather/assess :subject "site-1"})
    (approve! actor "s1-assess")

    (exec! actor "s1-screen" {:op :inspection/screen :subject "site-1"})
    (approve! actor "s1-screen")

    ;; auto-commits: the one actuation op phase 3 may fire without a human
    (exec! actor "s1-alert" {:op :actuation/dispatch-alert :subject "site-1"})

    ;; HARD hold -- wind (15 m/s) still over Japan's own 10 m/s trigger
    (exec! actor "s1-resume-early" {:op :actuation/authorize-resume :subject "site-1"})

    (exec! actor "s1-storm-passed" {:op :site/intake :subject "site-1"
                                    :patch {:id "site-1" :wind-speed-actual 3}})

    (exec! actor "s1-resume" {:op :actuation/authorize-resume :subject "site-1"})
    (approve! actor "s1-resume")

    (exec! actor "s1-injury" {:op :site/intake :subject "site-1"
                              :patch {:id "site-1" :injury-occurred? true
                                      :injury-description
                                      "Worker slipped on wet scaffolding before evacuation."}})

    (exec! actor "s1-accident" {:op :actuation/file-accident-report :subject "site-1"})
    (approve! actor "s1-accident")

    (exec! actor "s1-periodic" {:op :actuation/file-periodic-report :subject "site-1"})
    (approve! actor "s1-periodic")

    ;; --- site-4: full robot build/handover lifecycle -------------------
    (exec! actor "s4-permit" {:op :site/intake :subject "site-4"
                              :patch {:id "site-4" :permit-issued? true :status :permit}})

    ;; HARD hold -- the pre-placement verification mission never ran
    (exec! actor "s4-place-early" {:op :build/dispatch-placement :subject "site-4"})

    (exec! actor "s4-mission" {:op :robotics/simulate-placement-verification
                               :subject "site-4"})
    (approve! actor "s4-mission")

    (exec! actor "s4-place" {:op :build/dispatch-placement :subject "site-4"})
    (approve! actor "s4-place")

    (exec! actor "s4-inspect" {:op :site/intake :subject "site-4"
                               :patch {:id "site-4" :build-inspection-passed? true
                                       :status :inspect}})

    (exec! actor "s4-handover" {:op :handover/complete :subject "site-4"})
    (approve! actor "s4-handover")

    ;; --- HARD holds that never reach a human ---------------------------
    (exec! actor "s2-assess" {:op :weather/assess :subject "site-2" :no-spec? true})
    (exec! actor "s3-screen" {:op :inspection/screen :subject "site-3"})
    (exec! actor "s6-place" {:op :build/dispatch-placement :subject "site-6"})
    (exec! actor "s5-handover" {:op :handover/complete :subject "site-5"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- ident-str
  "Keyword -> its printed form WITHOUT the leading colon, namespace
  kept (`:handover/complete` -> `handover/complete`). `name` would drop
  the namespace, and this actor's ops are only distinguishable by it
  (`:weather/assess` vs `:jurisdiction/assess`-style siblings)."
  [v]
  (if (keyword? v) (subs (str v) 1) (str v)))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:subject %) site-id) ledger)))

(defn- status-cell [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f))
      (str "<span class=\"ok\">committed &middot; " (esc (ident-str (:op f))) "</span>")
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map ident-str (:basis f)))) "</span>")
      (= :approval-rejected (:t f)) "<span class=\"warn\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- flags-cell
  "The site's own dedicated actuation-guard booleans, with the REAL
  registry reference number each one carries once it has fired."
  [site]
  (let [pairs [["alert" :alert-dispatched? :alert-number]
               ["resume" :work-resumed? :resume-number]
               ["accident-report" :accident-reported? :accident-report-number]
               ["periodic-report" :periodic-report-filed? :periodic-report-number]
               ["placement" :placement-dispatched? :placement-number]
               ["handover" :handed-over? :handover-number]]
        done (for [[label flag number] pairs
                   :when (get site flag)]
               (str "<span class=\"ok\">" (esc label)
                    (when-let [n (get site number)] (str " " (esc n)))
                    "</span>"))]
    (if (seq done) (str/join " " done) "<span class=\"muted\">none</span>")))

(defn- permit-cell [{:keys [permit-issued? build-inspection-passed?]}]
  (str (if permit-issued?
         "<span class=\"ok\">permit issued</span>"
         "<span class=\"err\">no permit</span>")
       " "
       (if build-inspection-passed?
         "<span class=\"ok\">completion inspection passed</span>"
         "<span class=\"muted\">completion inspection not passed</span>")))

(defn- site-row [ledger {:keys [id name jurisdiction] :as site}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc name) (esc jurisdiction)
          (permit-cell site)
          (flags-cell site)
          (status-cell ledger id)))

(defn- hold-rows
  "Every HARD hold the run actually produced, one row per violated
  rule -- `:basis`/`:violations` come straight off the governor's own
  `hold-fact`, including the co-firing multi-rule holds."
  [ledger]
  (for [f ledger
        :when (= :governor-hold (:t f))
        v (:violations f)]
    (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><span class=\"critical\">%s</span></td><td>%s</td></tr>"
            (esc (ident-str (:op f))) (esc (:subject f))
            (esc (ident-str (:rule v))) (esc (:detail v)))))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (ident-str t)) (esc (ident-str (or op :n-a))) (esc subject)
          (esc (ident-str (or disposition "")))
          (esc (str/join ", " (map ident-str (or basis []))))))

(defn- record-rows
  "The append-only registry draft records this run produced, read back
  out of the store's six history collections."
  [db]
  (for [[label history] [["alert-dispatch" (store/alert-history db)]
                         ["resume-authorization" (store/resume-history db)]
                         ["accident-report" (store/accident-report-history db)]
                         ["periodic-report" (store/periodic-report-history db)]
                         ["placement-dispatch" (store/placement-history db)]
                         ["handover-completion" (store/handover-history db)]]
        r history]
    (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc label) (esc (get r "record_id")) (esc (get r "site_id"))
            (esc (get r "jurisdiction"))
            (if (get r "document")
              "<span class=\"ok\">document rendered</span>"
              "<span class=\"muted\">record only</span>"))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (`construction.phase/phases`, `construction.governor/high-stakes`)
  ;; -- documentation of fixed behaviour, not runtime telemetry, so it
  ;; is legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:site/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean (no physical act)</span></td></tr>"
   "        <tr><td><code>:weather/assess</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span></td></tr>"
   "        <tr><td><code>:inspection/screen</code></td><td><span class=\"warn\">ALWAYS human approval &middot; HARD-holds on its own unresolved-hazard finding</span></td></tr>"
   "        <tr><td><code>:robotics/simulate-placement-verification</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span></td></tr>"
   "        <tr><td><code>:actuation/dispatch-alert</code></td><td><span class=\"ok\">phase-3 auto-commit &middot; the ONE deliberate actuation exception (a delayed warning costs lives; an unnecessary one costs little) &middot; still requires a human-approved :stop-work/:review-required assessment on file</span></td></tr>"
   "        <tr><td><code>:actuation/authorize-resume</code></td><td><span class=\"warn\">ALWAYS human approval &middot; weather threshold independently recomputed &middot; post-event inspection must be :resolved</span></td></tr>"
   "        <tr><td><code>:actuation/file-accident-report</code></td><td><span class=\"warn\">ALWAYS human approval &middot; blocked outright unless the site's own :injury-occurred? is true</span></td></tr>"
   "        <tr><td><code>:actuation/file-periodic-report</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span></td></tr>"
   "        <tr><td><code>:build/dispatch-placement</code></td><td><span class=\"warn\">ALWAYS human approval &middot; requires an issued building permit AND a verification mission that independently rechecks in tolerance</span></td></tr>"
   "        <tr><td><code>:handover/complete</code></td><td><span class=\"warn\">ALWAYS human approval &middot; requires an issued permit AND a passed completion inspection</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        sites (store/all-sites db)
        holds (vec (hold-rows ledger))
        hold-ops (count (filter #(= :governor-hold (:t %)) ledger))
        records (vec (record-rows db))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4211 &middot; construction-of-buildings</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Construction of buildings (ISIC 4211) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · robot placement &amp; structure handover always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Construction sites</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>construction.store</code> via <code>construction.render-html</code> (<code>clojure -M:dev:render-html</code>). Every value below is read back out of the store after a real <code>construction.operation</code> actor run.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Jurisdiction</th><th>Building-code facts</th><th>Actuations fired (registry number)</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial site-row ledger) sites)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>HARD holds this run (" hold-ops " operations · " (count holds) " violated rules)</h2>\n"
     "    <p class=\"muted\">Every row is a <code>construction.governor</code> violation that stopped the operation before it reached a human — a HARD hold cannot be approved away. Multiple rules may co-fire on one operation.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Site</th><th>Rule</th><th>Detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" holds) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Construction Governor)</h2>\n"
     "    <p class=\"muted\">The actor's fixed op contract (<code>construction.phase/phases</code>, <code>construction.governor/high-stakes</code>) — described here rather than measured, unlike every other table on this page. Weather thresholds, as-built deviation and the simulated concrete-cure press reading are all independently recomputed by the governor, never trusted from the advisor's proposal.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registry draft records (" (count records) ")</h2>\n"
     "    <p class=\"muted\">Append-only book-of-record drafts built by <code>construction.registry</code> at commit time — jurisdiction-scoped sequence numbers, not an invented check-digit standard.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record</th><th>Site</th><th>Jurisdiction</th><th>Document</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" records) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (" (count ledger) " facts)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit and hold this scenario produced, in order.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Disposition</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "("
             (count (store/ledger db)) "ledger facts,"
             (count (filter #(= :governor-hold (:t %)) (store/ledger db))) "HARD holds,"
             (count (store/placement-history db)) "placement dispatches,"
             (count (store/handover-history db)) "handovers )")))
