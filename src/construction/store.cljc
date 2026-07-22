(ns construction.store
  "SSoT for the construction-site disaster-safety actor, behind a
  `Store` protocol so the backend is a swap, not a rewrite -- the same
  seam every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/construction/store_contract_test.clj), which is the whole point:
  the actor, the Construction Governor and the audit ledger never know
  which SSoT they run on.

  Unlike the dual-actuation-event sibling shape (`aerospace.store`,
  `telecom.store`), this actor has SIX actuation events acting on the
  SAME entity (a construction site). The disaster/severe-weather SAFETY
  slice has four: dispatching a disaster/severe-weather alert,
  authorizing work-resume, filing an accident report, filing a periodic
  inspection report. The physical ROBOT-DISPATCH (build) slice adds
  two: dispatching a robot to physically place a building element
  (`:build/dispatch-placement`, the Operator Guide's exterior-envelope-
  panel example) and handing over the completed, inspected structure
  (`:handover/complete`). Each of the six has its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:alert-dispatched?` / `:work-resumed?` / `:accident-
  reported?` / `:periodic-report-filed?` / `:placement-dispatched?` /
  `:handed-over?`, never a `:status` value), the same discipline every
  prior sibling governor's guards establish.

  The build-slice prerequisite facts -- an ISSUED PERMIT
  (`:permit-issued?`) and a PASSED COMPLETION INSPECTION
  (`:build-inspection-passed?`) -- are carried on the same `site`
  entity and set the same way the safety slice's `:hazard-unresolved?`
  / `:injury-occurred?` are set: via a `:site/intake` patch. The
  Construction Governor HARD-requires them before a placement can be
  dispatched or a structure handed over (see `construction.governor`,
  check 8).

  The ledger stays append-only on every backend: 'which site was
  screened for an unresolved hazard, which alert was dispatched to
  which mail/phone contacts, which work-resume was authorized, which
  accident/periodic report was filed, which robot placement was
  dispatched, which structure was handed over, on what jurisdictional
  basis, approved by whom' is always a query over an immutable log --
  the audit trail a community trusting a construction operator needs,
  and the evidence an operator needs if a stop-work or resume decision
  is later disputed."
  (:require [construction.registry :as registry]
            [construction.robotics :as robotics]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (site [s id])
  (all-sites [s])
  (weather-assessment-of [s site-id] "committed weather assessment for a site, or nil")
  (inspection-of [s site-id] "committed post-severe-weather inspection verdict for a site, or nil")
  (ledger [s])
  (alert-history [s] "the append-only alert-dispatch history (construction.registry drafts)")
  (resume-history [s] "the append-only work-resume-authorization history")
  (accident-report-history [s] "the append-only accident-report history")
  (periodic-report-history [s] "the append-only periodic-report history")
  (placement-history [s] "the append-only robot placement-dispatch history")
  (handover-history [s] "the append-only structure handover-completion history")
  (next-alert-sequence [s jurisdiction])
  (next-resume-sequence [s jurisdiction])
  (next-accident-report-sequence [s jurisdiction])
  (next-periodic-report-sequence [s jurisdiction])
  (next-placement-sequence [s jurisdiction])
  (next-handover-sequence [s jurisdiction])
  (site-already-dispatched? [s site-id])
  (site-already-resumed? [s site-id])
  (site-already-accident-reported? [s site-id])
  (site-already-periodic-reported? [s site-id])
  (site-already-placement-dispatched? [s site-id])
  (site-already-handed-over? [s site-id])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map id->site)"))

;; ----------------------------- demo data -----------------------------

(defn- with-press-telemetry
  "Merges REAL press-collision telemetry onto a demo site's base fields
  -- `construction.robotics/press-telemetry-for` actually runs
  `construction.simphysics`'s `physics-2d`-stepped press-collision
  simulation for this site's own `:design-mix-rated-strength-mpa`/
  `:cylinder-height-actual-mm`/`:cylinder-diameter-actual-mm`
  (ADR-2607152000), so even the 'already on file' seed data (as if from
  an earlier real ASTM C39 lab report) is genuinely simulation-derived,
  never a hand-typed double."
  [base]
  (merge base (select-keys (robotics/press-telemetry-for base) [:sim-compressive-strength-mpa])))

(defn demo-data
  "A small, self-contained site set covering all six actuation
  lifecycles (the four disaster/severe-weather safety events plus the
  two robot-dispatch build/handover events), the robot pre-placement
  verification mission (`:robotics-sim-verified?` / as-built-deviation-*
  ground-truth fields, `construction.robotics`), the REAL `physics-2d`-
  simulated concrete-cure test-cylinder press reading
  (`:design-mix-rated-strength-mpa`/`:cylinder-height-actual-mm`/
  `:cylinder-diameter-actual-mm` -> `:sim-compressive-strength-mpa`,
  `with-press-telemetry`, ADR-2607152000), and the uncovered-
  jurisdiction / unresolved-hazard / no-injury / no-permit / no-
  completion-inspection / no-robotics-sim / out-of-tolerance-robotics-
  sim failure modes, so the actor + tests run offline. site-6's
  `:cylinder-height-actual-mm` (150mm instead of the ASTM C39/EN
  12390-3 standard 300mm) is a genuine specimen-preparation defect (an
  L/D ratio of 1.0, outside ASTM C39's acceptable ~1.75-2.10 range) --
  its real re-simulated `:sim-compressive-strength-mpa` genuinely reads
  out of tolerance (see `construction.robotics/press-out-of-
  tolerance?`), CO-FIRING alongside its existing as-built-deviation
  violation, not a hand-set fake field."
  []
  {:sites
   (into {}
         (map (fn [s] [(:id s) (with-press-telemetry s)]))
         [{:id "site-1" :name "Sakura Community Housing Block C"
           :jurisdiction "JPN" :wind-speed-actual 15 :rainfall-actual 10 :snowfall-actual 0
           :hazard-unresolved? false :injury-occurred? false :injury-description nil
           :worker-contacts [{:name "Tanaka" :email "tanaka@example.com" :phone "+819000000001"}
                             {:name "Suzuki" :email "suzuki@example.com" :phone "+819000000002"}]
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? false :build-inspection-passed? false
           :as-built-deviation-actual 5 :as-built-deviation-min -15 :as-built-deviation-max 15
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? false :robotics-sim-record nil :status :intake}
          {:id "site-2" :name "Atlantis Waterfront Tower"
           :jurisdiction "ATL" :wind-speed-actual 12 :rainfall-actual 0 :snowfall-actual 0
           :hazard-unresolved? false :injury-occurred? false
           :worker-contacts []
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? false :build-inspection-passed? false
           :as-built-deviation-actual 5 :as-built-deviation-min -15 :as-built-deviation-max 15
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? false :robotics-sim-record nil :status :intake}
          {:id "site-3" :name "鈴木団地 改修工事"
           :jurisdiction "JPN" :wind-speed-actual 3 :rainfall-actual 0 :snowfall-actual 0
           :hazard-unresolved? true :injury-occurred? false
           :worker-contacts [{:name "Sato" :email "sato@example.com" :phone "+819000000003"}]
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? false :build-inspection-passed? false
           :as-built-deviation-actual 5 :as-built-deviation-min -15 :as-built-deviation-max 15
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? false :robotics-sim-record nil :status :intake}
          {:id "site-4" :name "田中ビル外壁改修"
           :jurisdiction "JPN" :wind-speed-actual 2 :rainfall-actual 0 :snowfall-actual 0
           :hazard-unresolved? false :injury-occurred? false
           :worker-contacts [{:name "Ito" :email "ito@example.com" :phone "+819000000004"}]
           :build-target {:panel "exterior-envelope-panel"
                          :wall-location "north-wall-unit-4"
                          :robot-id "robot-1"}
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? false :build-inspection-passed? false
           :as-built-deviation-actual 5 :as-built-deviation-min -15 :as-built-deviation-max 15
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? false :robotics-sim-record nil :status :intake}
          {:id "site-5" :name "Liberty Ave Apartments"
           :jurisdiction "USA" :wind-speed-actual 20 :rainfall-actual 60 :snowfall-actual 0
           :hazard-unresolved? false :injury-occurred? false
           :worker-contacts [{:name "Jordan" :email "jordan@example.com" :phone "+15550000001"}]
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? false :build-inspection-passed? false
           :as-built-deviation-actual 5 :as-built-deviation-min -15 :as-built-deviation-max 15
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 300.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? false :robotics-sim-record nil :status :intake}
          {:id "site-6" :name "ことぶき小学校増築棟"
           :jurisdiction "JPN" :wind-speed-actual 2 :rainfall-actual 0 :snowfall-actual 0
           :hazard-unresolved? false :injury-occurred? false
           :worker-contacts [{:name "Watanabe" :email "watanabe@example.com" :phone "+819000000005"}]
           :alert-dispatched? false :work-resumed? false
           :accident-reported? false :periodic-report-filed? false
           :placement-dispatched? false :handed-over? false
           :permit-issued? true :build-inspection-passed? false
           :as-built-deviation-actual 40 :as-built-deviation-min -15 :as-built-deviation-max 15
           ;; genuine ASTM C39 Sec. 6.2 specimen-prep defect: L/D = 1.0
           ;; (150mm/150mm), outside the ~1.75-2.10 acceptable range --
           ;; a damaged/mis-capped cylinder, not a hand-set fake sim
           ;; reading (see `with-press-telemetry` above).
           :design-mix-rated-strength-mpa 30.0 :cylinder-height-actual-mm 150.0 :cylinder-diameter-actual-mm 150.0
           :robotics-sim-verified? true :robotics-sim-record nil :status :build}])})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-alert!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-alert-sequence s (:jurisdiction a))
        result (registry/register-alert-dispatch site-id (:jurisdiction a) seq-n)]
    {:result result
     :site-patch {:alert-dispatched? true :alert-number (get result "alert_number")}}))

(defn- authorize-resume!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-resume-sequence s (:jurisdiction a))
        result (registry/register-resume-authorization site-id (:jurisdiction a) seq-n)]
    {:result result
     :site-patch {:work-resumed? true :resume-number (get result "resume_number")}}))

(defn- file-accident-report!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-accident-report-sequence s (:jurisdiction a))
        result (registry/register-accident-report site-id (:jurisdiction a) seq-n)
        report-number (get result "report_number")
        doc (registry/render-accident-report a report-number)]
    {:result (assoc-in result ["record" "document"] doc)
     :site-patch {:accident-reported? true :accident-report-number report-number}}))

(defn- file-periodic-report!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-periodic-report-sequence s (:jurisdiction a))
        result (registry/register-periodic-report site-id (:jurisdiction a) seq-n)
        report-number (get result "report_number")
        doc (registry/render-periodic-report a report-number)]
    {:result (assoc-in result ["record" "document"] doc)
     :site-patch {:periodic-report-filed? true :periodic-report-number report-number}}))

(defn- dispatch-placement!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-placement-sequence s (:jurisdiction a))
        result (registry/register-placement-dispatch site-id (:jurisdiction a) seq-n)]
    {:result result
     :site-patch {:placement-dispatched? true :placement-number (get result "placement_number")}}))

(defn- complete-handover!
  [s site-id]
  (let [a (site s site-id)
        seq-n (next-handover-sequence s (:jurisdiction a))
        result (registry/register-handover-completion site-id (:jurisdiction a) seq-n)
        handover-number (get result "handover_number")
        doc (registry/render-handover-certificate a handover-number)]
    {:result (assoc-in result ["record" "document"] doc)
     :site-patch {:handed-over? true :handover-number handover-number}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ id] (get-in @a [:sites id]))
  (all-sites [_] (sort-by :id (vals (:sites @a))))
  (weather-assessment-of [_ site-id] (get-in @a [:weather-assessments site-id]))
  (inspection-of [_ site-id] (get-in @a [:inspections site-id]))
  (ledger [_] (:ledger @a))
  (alert-history [_] (:alerts @a))
  (resume-history [_] (:resumes @a))
  (accident-report-history [_] (:accident-reports @a))
  (periodic-report-history [_] (:periodic-reports @a))
  (placement-history [_] (:placements @a))
  (handover-history [_] (:handovers @a))
  (next-alert-sequence [_ jurisdiction] (get-in @a [:alert-sequences jurisdiction] 0))
  (next-resume-sequence [_ jurisdiction] (get-in @a [:resume-sequences jurisdiction] 0))
  (next-accident-report-sequence [_ jurisdiction] (get-in @a [:accident-report-sequences jurisdiction] 0))
  (next-periodic-report-sequence [_ jurisdiction] (get-in @a [:periodic-report-sequences jurisdiction] 0))
  (next-placement-sequence [_ jurisdiction] (get-in @a [:placement-sequences jurisdiction] 0))
  (next-handover-sequence [_ jurisdiction] (get-in @a [:handover-sequences jurisdiction] 0))
  (site-already-dispatched? [_ site-id] (boolean (get-in @a [:sites site-id :alert-dispatched?])))
  (site-already-resumed? [_ site-id] (boolean (get-in @a [:sites site-id :work-resumed?])))
  (site-already-accident-reported? [_ site-id] (boolean (get-in @a [:sites site-id :accident-reported?])))
  (site-already-periodic-reported? [_ site-id] (boolean (get-in @a [:sites site-id :periodic-report-filed?])))
  (site-already-placement-dispatched? [_ site-id] (boolean (get-in @a [:sites site-id :placement-dispatched?])))
  (site-already-handed-over? [_ site-id] (boolean (get-in @a [:sites site-id :handed-over?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :site/upsert
      (swap! a update-in [:sites (:id value)] merge value)

      :weather-assessment/set
      (swap! a assoc-in [:weather-assessments (first path)] payload)

      :inspection/set
      (swap! a assoc-in [:inspections (first path)] payload)

      :site/mark-alert-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-alert! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:alert-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :alerts registry/append result))))
        result)

      :site/mark-resumed
      (let [site-id (first path)
            {:keys [result site-patch]} (authorize-resume! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:resume-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :resumes registry/append result))))
        result)

      :site/mark-accident-reported
      (let [site-id (first path)
            {:keys [result site-patch]} (file-accident-report! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:accident-report-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :accident-reports registry/append result))))
        result)

      :site/mark-periodic-reported
      (let [site-id (first path)
            {:keys [result site-patch]} (file-periodic-report! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:periodic-report-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :periodic-reports registry/append result))))
        result)

      :site/mark-placement-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-placement! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:placement-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :placements registry/append result))))
        result)

      :site/mark-handed-over
      (let [site-id (first path)
            {:keys [result site-patch]} (complete-handover! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:handover-sequences jurisdiction] (fnil inc 0))
                       (update-in [:sites site-id] merge site-patch)
                       (update :handovers registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo site set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :weather-assessments {} :inspections {} :ledger []
                           :alert-sequences {} :alerts []
                           :resume-sequences {} :resumes []
                           :accident-report-sequences {} :accident-reports []
                           :periodic-report-sequences {} :periodic-reports []
                           :placement-sequences {} :placements []
                           :handover-sequences {} :handovers []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (weather-assessment/inspection payloads, ledger
  facts, alert/resume/report records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses. The identity-schema
  builder, EDN-blob codec and seq-keyed event-log read/append are the
  shared kotoba-lang/langchain-store machinery (ADR-2607141600) -- the
  seam ~190 actors hand-roll; this store keeps only its domain wiring."
  (ls/identity-schema
   [:site/id :weather-assessment/site-id :inspection/site-id
    :ledger/seq :alert/seq :resume/seq :accident-report/seq :periodic-report/seq
    :placement/seq :handover/seq
    :alert-sequence/jurisdiction :resume-sequence/jurisdiction
    :accident-report-sequence/jurisdiction :periodic-report-sequence/jurisdiction
    :placement-sequence/jurisdiction :handover-sequence/jurisdiction]))

(defn- site->tx [{:keys [id name jurisdiction wind-speed-actual rainfall-actual snowfall-actual
                         hazard-unresolved? injury-occurred? injury-description worker-contacts build-target
                         alert-dispatched? alert-number
                         work-resumed? resume-number
                         accident-reported? accident-report-number
                         periodic-report-filed? periodic-report-number
                         placement-dispatched? placement-number
                         handed-over? handover-number
                         permit-issued? build-inspection-passed?
                         as-built-deviation-actual as-built-deviation-min as-built-deviation-max
                         design-mix-rated-strength-mpa cylinder-height-actual-mm cylinder-diameter-actual-mm
                         sim-compressive-strength-mpa
                         robotics-sim-verified? robotics-sim-record status]}]
  (cond-> {:site/id id}
    name                                (assoc :site/name name)
    jurisdiction                        (assoc :site/jurisdiction jurisdiction)
    (number? wind-speed-actual)          (assoc :site/wind-speed-actual wind-speed-actual)
    (number? rainfall-actual)            (assoc :site/rainfall-actual rainfall-actual)
    (number? snowfall-actual)            (assoc :site/snowfall-actual snowfall-actual)
    (some? hazard-unresolved?)          (assoc :site/hazard-unresolved? hazard-unresolved?)
    (some? injury-occurred?)            (assoc :site/injury-occurred? injury-occurred?)
    injury-description                  (assoc :site/injury-description injury-description)
    worker-contacts                     (assoc :site/worker-contacts-edn (ls/enc worker-contacts))
    build-target                        (assoc :site/build-target-edn (ls/enc build-target))
    (some? alert-dispatched?)           (assoc :site/alert-dispatched? alert-dispatched?)
    alert-number                        (assoc :site/alert-number alert-number)
    (some? work-resumed?)               (assoc :site/work-resumed? work-resumed?)
    resume-number                        (assoc :site/resume-number resume-number)
    (some? accident-reported?)          (assoc :site/accident-reported? accident-reported?)
    accident-report-number              (assoc :site/accident-report-number accident-report-number)
    (some? periodic-report-filed?)      (assoc :site/periodic-report-filed? periodic-report-filed?)
    periodic-report-number              (assoc :site/periodic-report-number periodic-report-number)
    (some? placement-dispatched?)       (assoc :site/placement-dispatched? placement-dispatched?)
    placement-number                    (assoc :site/placement-number placement-number)
    (some? handed-over?)                (assoc :site/handed-over? handed-over?)
    handover-number                     (assoc :site/handover-number handover-number)
    (some? permit-issued?)              (assoc :site/permit-issued? permit-issued?)
    (some? build-inspection-passed?)    (assoc :site/build-inspection-passed? build-inspection-passed?)
    (number? as-built-deviation-actual)  (assoc :site/as-built-deviation-actual as-built-deviation-actual)
    (number? as-built-deviation-min)     (assoc :site/as-built-deviation-min as-built-deviation-min)
    (number? as-built-deviation-max)     (assoc :site/as-built-deviation-max as-built-deviation-max)
    (number? design-mix-rated-strength-mpa) (assoc :site/design-mix-rated-strength-mpa design-mix-rated-strength-mpa)
    (number? cylinder-height-actual-mm)  (assoc :site/cylinder-height-actual-mm cylinder-height-actual-mm)
    (number? cylinder-diameter-actual-mm) (assoc :site/cylinder-diameter-actual-mm cylinder-diameter-actual-mm)
    (number? sim-compressive-strength-mpa) (assoc :site/sim-compressive-strength-mpa sim-compressive-strength-mpa)
    (some? robotics-sim-verified?)       (assoc :site/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)          (assoc :site/robotics-sim-record (ls/enc robotics-sim-record))
    status                              (assoc :site/status status)))

(def ^:private site-pull
  [:site/id :site/name :site/jurisdiction
   :site/wind-speed-actual :site/rainfall-actual :site/snowfall-actual
   :site/hazard-unresolved? :site/injury-occurred? :site/injury-description :site/worker-contacts-edn :site/build-target-edn
   :site/alert-dispatched? :site/alert-number
   :site/work-resumed? :site/resume-number
   :site/accident-reported? :site/accident-report-number
   :site/periodic-report-filed? :site/periodic-report-number
   :site/placement-dispatched? :site/placement-number
   :site/handed-over? :site/handover-number
   :site/permit-issued? :site/build-inspection-passed?
   :site/as-built-deviation-actual :site/as-built-deviation-min :site/as-built-deviation-max
   :site/design-mix-rated-strength-mpa :site/cylinder-height-actual-mm :site/cylinder-diameter-actual-mm
   :site/sim-compressive-strength-mpa
   :site/robotics-sim-verified? :site/robotics-sim-record :site/status])

(defn- pull->site [m]
  (when (:site/id m)
    (cond-> {:id (:site/id m) :name (:site/name m) :jurisdiction (:site/jurisdiction m)
             :wind-speed-actual (:site/wind-speed-actual m) :rainfall-actual (:site/rainfall-actual m)
             :snowfall-actual (:site/snowfall-actual m)
             :hazard-unresolved? (boolean (:site/hazard-unresolved? m))
             :injury-occurred? (boolean (:site/injury-occurred? m))
             :injury-description (:site/injury-description m)
             :worker-contacts (ls/dec* (:site/worker-contacts-edn m))
             :alert-dispatched? (boolean (:site/alert-dispatched? m)) :alert-number (:site/alert-number m)
             :work-resumed? (boolean (:site/work-resumed? m)) :resume-number (:site/resume-number m)
             :accident-reported? (boolean (:site/accident-reported? m)) :accident-report-number (:site/accident-report-number m)
             :periodic-report-filed? (boolean (:site/periodic-report-filed? m)) :periodic-report-number (:site/periodic-report-number m)
             :placement-dispatched? (boolean (:site/placement-dispatched? m)) :placement-number (:site/placement-number m)
             :handed-over? (boolean (:site/handed-over? m)) :handover-number (:site/handover-number m)
             :permit-issued? (boolean (:site/permit-issued? m))
             :build-inspection-passed? (boolean (:site/build-inspection-passed? m))
             :as-built-deviation-actual (:site/as-built-deviation-actual m)
             :as-built-deviation-min (:site/as-built-deviation-min m)
             :as-built-deviation-max (:site/as-built-deviation-max m)
             :design-mix-rated-strength-mpa (:site/design-mix-rated-strength-mpa m)
             :cylinder-height-actual-mm (:site/cylinder-height-actual-mm m)
             :cylinder-diameter-actual-mm (:site/cylinder-diameter-actual-mm m)
             :sim-compressive-strength-mpa (:site/sim-compressive-strength-mpa m)
             :robotics-sim-verified? (boolean (:site/robotics-sim-verified? m))
             :robotics-sim-record (ls/dec* (:site/robotics-sim-record m))
             :status (:site/status m)}
      (:site/build-target-edn m) (assoc :build-target (ls/dec* (:site/build-target-edn m))))))

(defrecord DatomicStore [conn]
  Store
  (site [_ id]
    (pull->site (d/pull (d/db conn) site-pull [:site/id id])))
  (all-sites [_]
    (->> (d/q '[:find [?id ...] :where [?e :site/id ?id]] (d/db conn))
         (map #(pull->site (d/pull (d/db conn) site-pull [:site/id %])))
         (sort-by :id)))
  (weather-assessment-of [_ site-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?e :weather-assessment/site-id ?sid] [?e :weather-assessment/payload ?p]]
              (d/db conn) site-id)))
  (inspection-of [_ site-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?e :inspection/site-id ?sid] [?e :inspection/payload ?p]]
              (d/db conn) site-id)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (alert-history [_] (ls/read-stream conn :alert/seq :alert/record))
  (resume-history [_] (ls/read-stream conn :resume/seq :resume/record))
  (accident-report-history [_] (ls/read-stream conn :accident-report/seq :accident-report/record))
  (periodic-report-history [_] (ls/read-stream conn :periodic-report/seq :periodic-report/record))
  (placement-history [_] (ls/read-stream conn :placement/seq :placement/record))
  (handover-history [_] (ls/read-stream conn :handover/seq :handover/record))
  (next-alert-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :alert-sequence/jurisdiction ?j] [?e :alert-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-resume-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :resume-sequence/jurisdiction ?j] [?e :resume-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-accident-report-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :accident-report-sequence/jurisdiction ?j] [?e :accident-report-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-periodic-report-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :periodic-report-sequence/jurisdiction ?j] [?e :periodic-report-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-placement-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :placement-sequence/jurisdiction ?j] [?e :placement-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (next-handover-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :handover-sequence/jurisdiction ?j] [?e :handover-sequence/next ?n]]
            (d/db conn) jurisdiction) 0))
  (site-already-dispatched? [s site-id] (boolean (:alert-dispatched? (site s site-id))))
  (site-already-resumed? [s site-id] (boolean (:work-resumed? (site s site-id))))
  (site-already-accident-reported? [s site-id] (boolean (:accident-reported? (site s site-id))))
  (site-already-periodic-reported? [s site-id] (boolean (:periodic-report-filed? (site s site-id))))
  (site-already-placement-dispatched? [s site-id] (boolean (:placement-dispatched? (site s site-id))))
  (site-already-handed-over? [s site-id] (boolean (:handed-over? (site s site-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :site/upsert
      (d/transact! conn [(site->tx value)])

      :weather-assessment/set
      (d/transact! conn [{:weather-assessment/site-id (first path) :weather-assessment/payload (ls/enc payload)}])

      :inspection/set
      (d/transact! conn [{:inspection/site-id (first path) :inspection/payload (ls/enc payload)}])

      :site/mark-alert-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-alert! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-alert-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:alert-sequence/jurisdiction jurisdiction :alert-sequence/next next-n}
                      {:alert/seq (count (alert-history s)) :alert/record (ls/enc (get result "record"))}])
        result)

      :site/mark-resumed
      (let [site-id (first path)
            {:keys [result site-patch]} (authorize-resume! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-resume-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:resume-sequence/jurisdiction jurisdiction :resume-sequence/next next-n}
                      {:resume/seq (count (resume-history s)) :resume/record (ls/enc (get result "record"))}])
        result)

      :site/mark-accident-reported
      (let [site-id (first path)
            {:keys [result site-patch]} (file-accident-report! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-accident-report-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:accident-report-sequence/jurisdiction jurisdiction :accident-report-sequence/next next-n}
                      {:accident-report/seq (count (accident-report-history s)) :accident-report/record (ls/enc (get result "record"))}])
        result)

      :site/mark-periodic-reported
      (let [site-id (first path)
            {:keys [result site-patch]} (file-periodic-report! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-periodic-report-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:periodic-report-sequence/jurisdiction jurisdiction :periodic-report-sequence/next next-n}
                      {:periodic-report/seq (count (periodic-report-history s)) :periodic-report/record (ls/enc (get result "record"))}])
        result)

      :site/mark-placement-dispatched
      (let [site-id (first path)
            {:keys [result site-patch]} (dispatch-placement! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-placement-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:placement-sequence/jurisdiction jurisdiction :placement-sequence/next next-n}
                      {:placement/seq (count (placement-history s)) :placement/record (ls/enc (get result "record"))}])
        result)

      :site/mark-handed-over
      (let [site-id (first path)
            {:keys [result site-patch]} (complete-handover! s site-id)
            jurisdiction (:jurisdiction (site s site-id))
            next-n (inc (next-handover-sequence s jurisdiction))]
        (d/transact! conn
                     [(site->tx (assoc site-patch :id site-id))
                      {:handover-sequence/jurisdiction jurisdiction :handover-sequence/next next-n}
                      {:handover/seq (count (handover-history s)) :handover/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-sites [s sites]
    (when (seq sites) (d/transact! conn (mapv site->tx (vals sites)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data` ({:sites
  ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [sites]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-sites s sites))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo site set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
