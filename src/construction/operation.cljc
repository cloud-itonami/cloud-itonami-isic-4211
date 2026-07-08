(ns construction.operation
  "OperationActor -- one construction-site disaster-safety operation =
  one supervised actor run, expressed as a langgraph-clj StateGraph.
  The advisor (Construction Advisor) is sealed into a single node
  (:advise); its proposal is ALWAYS routed through the Construction
  Governor (:govern) and the rollout phase gate (:decide) before
  anything commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (MemStore today; Datomic/kotoba-server is the next seam) - `store` arg
    - the Advisor  (mock | real LLM)                                       - :advisor opt
    - the Notifier (mock | real Resend+Twilio)                             - :notifier opt
    - the Phase    (0->3 rollout)                                          - :phase in ctx

  One graph run = one construction-safety operation (intake -> advise
  -> govern -> decide -> commit | hold | approval). No unbounded inner
  loop -- each operation is auditable and checkpointed.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to a human operator (the site safety officer). The approver
  resumes with `{:approval {:status :approved}}` (or :rejected).
  `:actuation/authorize-resume`/`:actuation/file-accident-report`/
  `:actuation/file-periodic-report` ALWAYS reach this node when the
  governor is clean -- see `construction.phase`. `:actuation/dispatch-
  alert` is the deliberate exception: at phase 3, governor-clean, it
  commits directly (see `construction.phase` ns docstring 'Actuation')
  and `:notify` (below) actually sends the mail + phone messages."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [construction.constructionadvisor :as constructionadvisor]
            [construction.governor :as governor]
            [construction.notify :as notify]
            [construction.phase :as phase]
            [construction.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn build
  "Compiles an OperationActor graph bound to `store` (any
  `construction.store/Store`).
  opts:
    :advisor      -- a `construction.constructionadvisor/Advisor` (default: mock-advisor)
    :notifier     -- a `construction.notify/Notifier` (default: mock-notifier)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor notifier checkpointer]
             :or   {advisor      (constructionadvisor/mock-advisor)
                    notifier     (notify/mock-notifier)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected actor-id/role/phase
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; Construction Advisor inference (the contained intelligence node) -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (constructionadvisor/-advise advisor store request)]
            {:proposal p :audit [(constructionadvisor/trace request p)]})))

      ;; Construction Governor -- independent censor (separate system than the LLM).
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition, then the rollout-phase gate (which can
      ;; only add caution). HARD governor violations -> HOLD (no override).
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :actuation
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      ;; Approval handoff -- paused by interrupt-before; a human resumes
      ;; with :approval. Then route commit/hold.
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; Commit -- the ONLY node that writes the SSoT + audit ledger. For
      ;; :site/mark-alert-dispatched specifically, ALSO actually sends the
      ;; mail + phone messages via the injected Notifier -- the one
      ;; actuation event in this actor whose "commit" is itself the
      ;; real-world act (see README `Actuation`).
      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (store/commit-record! store record)
          (let [f (commit-fact request context proposal)
                notify-result (when (= :site/mark-alert-dispatched (:effect record))
                                (let [site (store/site store (:subject request))]
                                  (notify/dispatch-alert! notifier (:worker-contacts site) (:value proposal))))]
            (store/append-ledger! store f)
            (cond-> {:audit [f]}
              notify-result (assoc :notify-result notify-result)))))

      ;; Hold -- write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
