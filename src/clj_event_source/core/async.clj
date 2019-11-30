(ns clj-event-source.core.async
  (:require [clojure.core.async :refer [go put! chan <!! close!]]
            [clj-event-source.event-stream :as event-stream])
  (:import java.net.http.HttpClient
           java.net.http.HttpRequest
           java.net.http.HttpResponse$BodyHandlers))

(defn envelope [message]
  {:kind (if (:event message) :event :message)
   :content message})

(def default-params
  {:retry-attempts 3
   :retry-delay [1000 2000 3000]
   :current-retries 0})

(defn sleep-duration [retry-delay current-retries]
  (if (vector? retry-delay)
    (or (first (drop current-retries retry-delay)) (last retry-delay))
    retry-delay))

(defn connect [url & [params]]
  (let [port (or (:chan params) (chan))
        params (atom (merge default-params params))
        client (.build (HttpClient/newBuilder))
        response-subscription (atom nil)
        message-state (atom {})
        subscriber (reify java.util.concurrent.Flow$Subscriber
                     (^void onComplete [this]
                      (when-let [message (event-stream/process-line message-state "")]
                        (put! port (envelope message)))
                      (close! port))

                     (^void onError [this ^Throwable throwable]
                      {:kind :error
                       :content throwable})

                     (^void onNext [this line]
                      (let [message (event-stream/process-line message-state line)]
                        (if message
                          (if (put! port (envelope message))
                            (.request @response-subscription 1)
                            (.cancel @response-subscription))
                          (.request @response-subscription 1))))

                     (^void onSubscribe [this ^java.util.concurrent.Flow$Subscription subscription]
                      (swap! params assoc :current-retries 0)
                      (.request subscription 1)
                      (reset! response-subscription subscription)))
        res (-> client
                (.sendAsync
                 (.. (HttpRequest/newBuilder (java.net.URI. url)) GET build)
                 (HttpResponse$BodyHandlers/fromLineSubscriber subscriber)))]
    (future
      (try
        (.join res)
        (close! port)
        (catch Throwable t
          (put! port {:kind :error :content t})
          (let [{:keys [retry-attempts retry-delay current-retries]} @params]
            (if (< 0 (- retry-attempts current-retries))
              (let [sleep-ms (sleep-duration retry-delay current-retries)]
                (put! port {:kind :meta
                            :content {:event :retry
                                      :retry-attempts (inc current-retries)
                                      :retry-after sleep-ms}})
                (Thread/sleep sleep-ms)
                (connect url (-> @params
                                 (assoc :chan port)
                                 (update :current-retries inc))))
              (do
                (put! port {:kind :meta
                            :content {:event :no-connection
                                      :retry-attempts current-retries}})
                (close! port)))))))
    port))
