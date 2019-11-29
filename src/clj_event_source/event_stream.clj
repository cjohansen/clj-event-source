(ns clj-event-source.event-stream
  (:require [clojure.string :as str]))

(defn consume-event-stream-line [next-message line]
  (cond
    (empty? line) (cond-> {:next-message {}}
                    (not (empty? next-message)) (assoc :message next-message))

    (str/starts-with? line ":") {:next-message next-message}

    :default (let [[_ kind data] (re-find #"([^:]+): *(.+)$" line)]
               (case kind
                 "id" {:next-message (assoc next-message :id data)}
                 "event" {:next-message (assoc next-message :event data)}
                 "data" {:next-message (update next-message :data #(str % (when % "\n") data))}
                 {:next-message next-message}))))

(defn process-line [message-state line]
  (let [{:keys [next-message message]} (consume-event-stream-line @message-state line)]
    (reset! message-state next-message)
    message))

