(ns clj-event-source.event-source-test
  (:require [clj-event-source.event-stream :as sut]
            [clojure.test :refer [deftest is]]))

(deftest consume-first-blank
  (is (= (sut/consume-event-stream-line {} "") {:next-message {}})))

(deftest consume-id
  (is (= (sut/consume-event-stream-line {} "id: 1234") {:next-message {:id "1234"}})))

(deftest consume-id-event-data
  (is (= (-> {}
             (sut/consume-event-stream-line "id: 1234")
             :next-message
             (sut/consume-event-stream-line "event: yolo")
             :next-message
             (sut/consume-event-stream-line "data: {:message \"Hello\"}"))
         {:next-message {:id "1234"
                         :event "yolo"
                         :data "{:message \"Hello\"}"}})))

(deftest consume-emits-event-after-double-newline
  (is (= (-> {}
             (sut/consume-event-stream-line "data: {:message \"Hello\"}")
             :next-message
             (sut/consume-event-stream-line ""))
         {:message {:data "{:message \"Hello\"}"}
          :next-message {}})))

(deftest consume-ignores-comment
  (is (= (sut/consume-event-stream-line {} ": Don't mind me")
         {:next-message {}})))

(deftest consume-ignores-unrecognized-line
  (is (= (sut/consume-event-stream-line {} "lol: Don't mind me")
         {:next-message {}})))

(deftest updates-state-and-returns-message-to-emit
  (let [state (atom {:id "my-message"
                     :data "Some data"})]
    (is (= (sut/process-line state "") {:id "my-message"
                                        :data "Some data"}))
    (is (= @state {}))))
