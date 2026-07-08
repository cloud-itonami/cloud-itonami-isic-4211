(ns construction.notify
  "Mail + phone outreach transport for `:actuation/dispatch-alert` --
  the concrete 呼びかけ／共有 (outreach/sharing) deliverable. Only this
  namespace touches the network (java.net.http, Resend, Twilio) or the
  RESEND_API_KEY/TWILIO_* env vars; every other namespace in this actor
  stays pure per its own contract -- the same discipline `cloud-
  itonami.mail` (gftdcojp/cloud-itonami) uses for its Resend send.

  `Notifier` is the injection seam: `mock-notifier` (deterministic,
  no network -- the default everywhere, same as every sibling actor's
  mock advisor) for dev/tests/demo, `resend-mail-notifier` /
  `twilio-voice-notifier` for the real thing. `dispatch-alert!` fans
  the SAME alert out to every contact in a site's `:worker-contacts`
  over BOTH channels, isolating one contact's send failure from every
  other contact's -- a bad phone number must never suppress the mail
  (or the other workers' calls) for a disaster warning.

  This namespace builds/sends the message; it does NOT decide whether
  to send one -- that is `construction.governor` (may this proposal
  commit at all?) and `construction.phase` (may it auto-commit, or
  does it need a human first?). See README `Actuation`."
  (:require [clojure.string :as str]))

(defprotocol Notifier
  (-send-mail! [n msg] "msg: {:to :subject :body} -> {:status :channel :to ..}")
  (-send-phone-call! [n msg] "msg: {:to :message} -> {:status :channel :to ..}"))

;; ----------------------------- mock (default) -----------------------------

(defrecord MockNotifier [log]
  Notifier
  (-send-mail! [_ {:keys [to subject body]}]
    (let [result {:status :sent :channel :mail :to to :subject subject :body body}]
      (swap! log conj result)
      result))
  (-send-phone-call! [_ {:keys [to message]}]
    (let [result {:status :sent :channel :phone :to to :message message}]
      (swap! log conj result)
      result)))

(defn mock-notifier
  "A deterministic notifier -- no network, records every send to an
  internal log atom (`sent-log`). Default everywhere (dev/tests/demo)."
  ([] (mock-notifier (atom [])))
  ([log] (->MockNotifier log)))

(defn sent-log [^MockNotifier n] @(:log n))

;; ----------------------------- real transports (JVM-only) -----------------------------

#?(:clj
   (defn jvm-http-fn
     "Real java.net.http transport, same {:url :method :headers :body} ->
     {:status :body} convention `cloud-itonami.mail/jvm-http-fn` uses --
     lets the real notifiers below be tested with a stubbed :http-fn
     instead of a real Resend/Twilio call."
     []
     (fn [{:keys [url method headers body]}]
       (let [builder (-> (java.net.http.HttpRequest/newBuilder (java.net.URI/create url))
                         (as-> b (reduce-kv (fn [b k v] (.header b k v)) b headers)))
             request (case method
                       :post (-> builder
                                (.POST (java.net.http.HttpRequest$BodyPublishers/ofString (or body "")))
                                .build)
                       (throw (ex-info "Unsupported HTTP method" {:method method})))
             resp (.send (java.net.http.HttpClient/newHttpClient) request
                        (java.net.http.HttpResponse$BodyHandlers/ofString))]
         {:status (.statusCode resp) :body (.body resp)}))))

#?(:clj
   (defn- json-string [m]
     ;; Minimal hand-rolled JSON encoder for the small, known-shape
     ;; request bodies this ns sends (from/to/subject/text strings-only)
     ;; -- avoids pulling a JSON library dependency into this standalone
     ;; actor repo just for one outbound call shape.
     (letfn [(esc [s] (-> (str s)
                          (str/replace "\\" "\\\\")
                          (str/replace "\"" "\\\"")
                          (str/replace "\n" "\\n")))
             (encode [v]
               (cond
                 (map? v) (str "{" (str/join "," (map (fn [[k v]] (str "\"" (esc (name k)) "\":" (encode v))) v)) "}")
                 (sequential? v) (str "[" (str/join "," (map encode v)) "]")
                 (string? v) (str "\"" (esc v) "\"")
                 (nil? v) "null"
                 :else (str v)))]
       (encode m))))

#?(:clj
   (defn- resend-api-key []
     (or (System/getenv "RESEND_API_KEY")
         (throw (ex-info "RESEND_API_KEY is not set" {})))))

#?(:clj
   (defrecord ResendMailNotifier [from http-fn token]
     Notifier
     (-send-mail! [_ {:keys [to subject body]}]
       (let [resp ((or http-fn (jvm-http-fn))
                   {:url "https://api.resend.com/emails"
                    :method :post
                    :headers {"Authorization" (str "Bearer " (or token (resend-api-key)))
                              "Content-Type" "application/json"}
                    :body (json-string {:from from :to [to] :subject subject :text body})})]
         (when-not (< (:status resp) 300)
           (throw (ex-info "Resend send failed" {:status (:status resp) :body (:body resp)})))
         {:status :sent :channel :mail :to to :provider "resend" :response (:body resp)}))
     (-send-phone-call! [_ _]
       (throw (ex-info "ResendMailNotifier does not send phone calls -- compose with a phone Notifier via dual-notifier" {})))))

#?(:clj
   (defn resend-mail-notifier
     "A real Resend-backed mail Notifier. `from` is the verified Resend
     sender address (the caller supplies it explicitly, same convention
     `cloud-itonami.mail/send-marketing-outreach!` uses -- never guessed
     or hardcoded here). RESEND_API_KEY resolved from the environment
     unless `:token` is given (tests stub `:http-fn` instead of hitting
     the real network)."
     ([from] (resend-mail-notifier from {}))
     ([from {:keys [http-fn token]}]
      (->ResendMailNotifier from http-fn token))))

#?(:clj
   (defn- twilio-creds []
     {:account-sid (or (System/getenv "TWILIO_ACCOUNT_SID") (throw (ex-info "TWILIO_ACCOUNT_SID is not set" {})))
      :auth-token  (or (System/getenv "TWILIO_AUTH_TOKEN") (throw (ex-info "TWILIO_AUTH_TOKEN is not set" {})))}))

#?(:clj
   (defn- form-encode [m]
     (str/join "&" (map (fn [[k v]] (str (java.net.URLEncoder/encode (name k) "UTF-8") "="
                                        (java.net.URLEncoder/encode (str v) "UTF-8")))
                        m))))

#?(:clj
   (defn- escape-xml [s]
     (-> (str s)
         (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")
         (str/replace "\"" "&quot;") (str/replace "'" "&apos;"))))

#?(:clj
   (defn- twiml-say
     "Inline TwiML for a spoken alert -- Twilio's Voice API accepts raw
     TwiML XML directly in the `Twiml` call param, so this needs no
     separately-hosted webhook/TwiML Bin for a simple spoken message."
     [message]
     (str "<Response><Say language=\"ja-JP\">" (escape-xml message) "</Say></Response>")))

#?(:clj
   (defn- basic-auth-header [account-sid auth-token]
     (str "Basic " (.encodeToString (java.util.Base64/getEncoder)
                                    (.getBytes (str account-sid ":" auth-token) "UTF-8")))))

#?(:clj
   (defrecord TwilioVoiceNotifier [from http-fn account-sid auth-token]
     Notifier
     (-send-mail! [_ _]
       (throw (ex-info "TwilioVoiceNotifier does not send mail -- compose with a mail Notifier via dual-notifier" {})))
     (-send-phone-call! [_ {:keys [to message]}]
       (let [{:keys [account-sid auth-token]} (if (and account-sid auth-token)
                                                {:account-sid account-sid :auth-token auth-token}
                                                (twilio-creds))
             resp ((or http-fn (jvm-http-fn))
                   {:url (str "https://api.twilio.com/2010-04-01/Accounts/" account-sid "/Calls.json")
                    :method :post
                    :headers {"Authorization" (basic-auth-header account-sid auth-token)
                              "Content-Type" "application/x-www-form-urlencoded"}
                    :body (form-encode {:To to :From from :Twiml (twiml-say message)})})]
         (when-not (< (:status resp) 300)
           (throw (ex-info "Twilio call failed" {:status (:status resp) :body (:body resp)})))
         {:status :sent :channel :phone :to to :provider "twilio" :response (:body resp)}))))

#?(:clj
   (defn twilio-voice-notifier
     "A real Twilio Programmable Voice-backed phone Notifier -- places an
     outbound call and speaks `message` via inline TwiML `<Say>` (no
     separate webhook needed for a simple spoken alert). `from` is the
     verified Twilio caller-id number. TWILIO_ACCOUNT_SID/TWILIO_AUTH_
     TOKEN resolved from the environment unless given explicitly (tests
     stub `:http-fn` instead of hitting the real network)."
     ([from] (twilio-voice-notifier from {}))
     ([from {:keys [http-fn account-sid auth-token]}]
      (->TwilioVoiceNotifier from http-fn account-sid auth-token))))

;; ----------------------------- composition -----------------------------

(defrecord DualNotifier [mail-notifier phone-notifier]
  Notifier
  (-send-mail! [_ msg] (-send-mail! mail-notifier msg))
  (-send-phone-call! [_ msg] (-send-phone-call! phone-notifier msg)))

(defn dual-notifier
  "Compose a mail-only Notifier and a phone-only Notifier into one --
  the shape `:actuation/dispatch-alert` needs (mail と 電話 の両方)."
  [mail-notifier phone-notifier]
  (->DualNotifier mail-notifier phone-notifier))

(defn dispatch-alert!
  "Send the alert-dispatch message to every contact in `worker-contacts`
  ({:name :email :phone}) via BOTH mail and phone, using `notifier`.
  Returns a vector of per-contact per-channel send results. A failed
  send for one contact/channel is recorded as `{:status :failed ...}`,
  never thrown past this call -- one bad phone number or a transient
  network error must never suppress warning every other worker."
  [notifier worker-contacts {:keys [subject-line body message]}]
  (vec
   (for [{:keys [name email phone]} worker-contacts]
     {:contact name
      :mail (try (-send-mail! notifier {:to email :subject subject-line :body body})
                 (catch #?(:clj Exception :cljs :default) e
                   {:status :failed :channel :mail :to email :error #?(:clj (.getMessage e) :cljs (str e))}))
      :phone (try (-send-phone-call! notifier {:to phone :message message})
                  (catch #?(:clj Exception :cljs :default) e
                    {:status :failed :channel :phone :to phone :error #?(:clj (.getMessage e) :cljs (str e))}))})))
