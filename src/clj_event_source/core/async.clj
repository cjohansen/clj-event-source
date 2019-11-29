(ns clj-event-source.core.async
  (:require [clojure.core.async :refer [go put! chan <!! close!]]
            [clj-event-source.event-stream :as event-stream])
  (:import java.net.http.HttpClient
           java.net.http.HttpRequest
           java.net.http.HttpResponse$BodyHandlers))

(defn envelope [message]
  {:kind (if (:event message) :event :message)
   :content message})

(defn connect [url & [params]]
  (let [port (or (:chan params) (chan))
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
                      (.request subscription 1)
                      (reset! response-subscription subscription)))
        res (-> client
                (.sendAsync
                 (.. (HttpRequest/newBuilder (java.net.URI. url)) GET build)
                 (HttpResponse$BodyHandlers/fromLineSubscriber subscriber)))]
    (go
      (try
        (.join res)
        (close! port)
        (catch Throwable t
          (put! port {:kind :error :content t})
          (close! port))))
    port))
