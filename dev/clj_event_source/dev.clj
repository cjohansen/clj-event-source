(ns clj-event-source.dev)

(comment

(require '[clj-event-source.core.async :as event-source]
         '[clojure.core.async :refer [<! go-loop close!]])

(let [event-stream (event-source/connect "http://localhost:9119/event-stream")]
  (go-loop []
    (if-let [msg (<! event-stream)]
      (do
        (case (:kind msg)
          :error (println "Encountered error" (:content msg))
          :message (let [{:keys [id data]} (:content msg)]
                     (println "Message without event" id data))
          :event (let [{:keys [id event data]} (:content msg)]
                   (println "Message with event" id event data)))
        (recur))
      (println "Event source disconnected")))
  (Thread/sleep 60000)
  (close! event-stream))

  )
