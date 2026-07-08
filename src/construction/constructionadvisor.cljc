(ns construction.constructionadvisor
  "Construction Advisor client -- the *contained intelligence node* for
  the construction-site disaster-safety actor.

  It normalizes site intake, drafts a per-jurisdiction severe-weather
  work-stoppage assessment, screens sites for an unresolved post-event
  hazard, drafts the disaster-alert dispatch (mail + phone) action,
  drafts the work-resume authorization, and drafts the accident-report
  and periodic-report filings. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record, a real mail/phone send, or a real
  filing. Every output is censored downstream by `construction.governor`
  before anything touches the SSoT, and every `:actuation/*` proposal
  is gated by phase + governor -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the legal-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/* op or nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [construction.facts :as facts]
            [construction.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the site, its weather figures or its jurisdiction.
  High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "現場記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :site/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-weather
  "Per-jurisdiction severe-weather work-stoppage assessment draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing an assessment for a jurisdiction with NO official spec-
  basis in `construction.facts` -- the Construction Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/site db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式legal-basisが見つかりません")
       :rationale  "construction.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :weather-assessment/set
       :value      {:jurisdiction iso3 :recommendation :unknown :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      (let [exceeded (facts/weather-threshold-exceeded? iso3 a)
            recommendation (case exceeded
                             true :stop-work
                             false :monitor
                             :qualitative :review-required)]
        {:summary    (str iso3 " (" (:owner-authority sb) ") 向け気象判定: " (name recommendation))
         :rationale  (str "法的根拠: " (:legal-basis sb) " / 出典: " (:provenance sb)
                         (when (:threshold-note sb) (str " / " (:threshold-note sb))))
         :cites      [(:legal-basis sb) (:provenance sb)]
         :effect     :weather-assessment/set
         :value      {:jurisdiction iso3
                      :recommendation recommendation
                      :threshold-model (:threshold-model sb)
                      :spec-basis (:provenance sb)
                      :legal-basis (:legal-basis sb)}
         :stake      nil
         :confidence 0.9}))))

(defn- screen-hazard
  "Post-severe-weather/earthquake mandatory-inspection hazard-screening
  draft. `:hazard-unresolved?` on the site record injects the failure
  mode: the Construction Governor must HOLD, un-overridably, on any
  unresolved hazard."
  [db {:keys [subject]}]
  (let [a (store/site db subject)]
    (cond
      (nil? a)
      {:summary "対象現場記録が見つかりません" :rationale "no site record"
       :cites [] :effect :inspection/set :value {:site-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:hazard-unresolved? a))
      {:summary    (str (:name a) ": 未解決のハザードを検出（足場・地盤・電気設備等）")
       :rationale  "義務付けられた悪天候・地震後点検が未解決のハザードを検出。人手確認とホールドが必須。"
       :cites      (facts/inspection-checklist (:jurisdiction a))
       :effect     :inspection/set
       :value      {:site-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:name a) ": 悪天候後点検完了、未解決ハザードなし")
       :rationale  "点検チェックリスト完了。"
       :cites      (facts/inspection-checklist (:jurisdiction a))
       :effect     :inspection/set
       :value      {:site-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-dispatch-alert
  "Draft the disaster/severe-weather ALERT-DISPATCH action -- warning
  site workers and foremen by mail + phone. `:stake :actuation/
  dispatch-alert` -- a real external communication, but see README
  `Actuation`: UNIQUELY among this actor's actuation events, this MAY
  auto-commit at phase 3 when the governor is clean, because for a
  disaster warning, dispatch SPEED is itself the safety property (a
  delayed warning can cost lives; an extra warning costs almost
  nothing)."
  [db {:keys [subject]}]
  (let [a (store/site db subject)
        sb (facts/spec-basis (:jurisdiction a))
        wa (store/weather-assessment-of db subject)]
    {:summary    (str subject " 向け警報配信提案 (mail+phone, " (count (:worker-contacts a)) " 名)"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if sb
                   (str "法的根拠: " (:legal-basis sb) " / 気象判定: " (:recommendation wa "未実施"))
                   "現場記録が見つかりません")
     :cites      (if sb [(:legal-basis sb) (:provenance sb)] [])
     :effect     :site/mark-alert-dispatched
     :value      {:site-id subject
                  :subject-line (str "[至急] " (:name a) " 悪天候・災害警報 -- 作業中止のお知らせ")
                  :body (str (:name a) "の現場で悪天候・災害の恐れがあります。安全のため作業を中止してください。"
                            "法的根拠: " (:legal-basis sb))
                  :message (str (:name a) "、悪天候・災害警報です。作業を中止してください。")}
     :stake      :actuation/dispatch-alert
     :confidence (if sb 0.9 0.3)}))

(defn- propose-authorize-resume
  "Draft the WORK-RESUME AUTHORIZATION -- authorizing work to resume at
  a site after a severe-weather/disaster hold. ALWAYS `:stake
  :actuation/authorize-resume` -- always a human safety officer's call,
  see README `Actuation`."
  [db {:keys [subject]}]
  (let [a (store/site db subject)
        insp (store/inspection-of db subject)]
    {:summary    (str subject " 向け作業再開authorize提案"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if a
                   (str "inspection-verdict=" (:verdict insp "未実施")
                        " wind=" (:wind-speed-actual a) " rain=" (:rainfall-actual a) " snow=" (:snowfall-actual a))
                   "現場記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :site/mark-resumed
     :value      {:site-id subject}
     :stake      :actuation/authorize-resume
     :confidence (if (and a (= :resolved (:verdict insp))) 0.9 0.3)}))

(defn- propose-file-accident-report
  "Draft the ACCIDENT-REPORT filing (e.g. Japan's 労働者死傷病報告).
  ALWAYS `:stake :actuation/file-accident-report` -- a real legal
  filing, always a human's call."
  [db {:keys [subject]}]
  (let [a (store/site db subject)
        sb (facts/spec-basis (:jurisdiction a))]
    {:summary    (str subject " 向け労働災害報告書作成提案"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if (and a sb)
                   (str "法的根拠: " (:accident-report-basis sb) " / 出典: " (:accident-report-provenance sb))
                   "現場記録または法域spec-basisが見つかりません")
     :cites      (if sb [(:accident-report-basis sb) (:accident-report-provenance sb)] [])
     :effect     :site/mark-accident-reported
     :value      {:site-id subject}
     :stake      :actuation/file-accident-report
     :confidence (if (and a sb (:injury-occurred? a)) 0.9 0.3)}))

(defn- propose-file-periodic-report
  "Draft the PERIODIC-REPORT filing (e.g. Japan's Building Standards
  Act Art.12 12条点検). ALWAYS `:stake :actuation/file-periodic-
  report` -- a real legal filing, always a human's call."
  [db {:keys [subject]}]
  (let [a (store/site db subject)
        sb (facts/spec-basis (:jurisdiction a))]
    {:summary    (str subject " 向け定期報告書作成提案"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if (and a sb (:periodic-report-basis sb))
                   (str "法的根拠: " (:periodic-report-basis sb) " / 出典: " (:periodic-report-provenance sb))
                   "現場記録または法域の定期報告basisが見つかりません（対象外法域を含む）")
     :cites      (if (and sb (:periodic-report-basis sb)) [(:periodic-report-basis sb) (:periodic-report-provenance sb)] [])
     :effect     :site/mark-periodic-reported
     :value      {:site-id subject}
     :stake      :actuation/file-periodic-report
     :confidence (if (and a sb (:periodic-report-basis sb)) 0.85 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :site/intake                           (normalize-intake db request)
    :weather/assess                        (assess-weather db request)
    :inspection/screen                     (screen-hazard db request)
    :actuation/dispatch-alert              (propose-dispatch-alert db request)
    :actuation/authorize-resume            (propose-authorize-resume db request)
    :actuation/file-accident-report        (propose-file-accident-report db request)
    :actuation/file-periodic-report        (propose-file-periodic-report db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは建設現場の災害・悪天候安全対応エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:site/upsert|:weather-assessment/set|:inspection/set|"
       ":site/mark-alert-dispatched|:site/mark-resumed|:site/mark-accident-reported|:site/mark-periodic-reported) "
       ":stake(:actuation/dispatch-alert|:actuation/authorize-resume|:actuation/file-accident-report|"
       ":actuation/file-periodic-report か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "legal-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [subject]}]
  {:site (store/site st subject)
   :weather-assessment (store/weather-assessment-of st subject)
   :inspection (store/inspection-of st subject)})

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Construction Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch an alert,
  auto-authorize resume, or auto-file a report."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :constructionadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
