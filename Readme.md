# clj-event-source

A Clojure [server-sent events event
stream](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
client. Provides a [core.async](https://github.com/clojure/core.async) based
interface for consuming events.

## Install

```clj
cjohansen/clj-event-source {:mvn/version "2019.11.30"}
```

## Requirements

This library is implemented on top of `java.net.http.HttpClient`, which requires
JDK 11.

## Documentation

`clj-event-source` allows you to consume events from an event stream over a
`core.async` channel. The vals put on the channel will be maps with:

- `:kind` - a keyword
- `:content` - depends on the `:kind`, see below

`:kind` will take one of four values:

- `:meta` - Process information from clj-event-source. Basically a log message.
  See below for details.
- `:error` - An error occurred. `:content` will be an error object
- `:message` - A message without the `event` field was received over the event
  stream. `:content` will be an [event](#event-stream-events).
- `:event` - An event was received over the event stream. `:content` will be an
  [event](#event-stream-events).

### Example

```clj
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
```

If you want to control the buffer size etc, you can supply your own `chan` when
connecting:

```clj
(event-source/connect "http://localhost:9119/event-stream" {:chan my-chan})
```

<a id="event-stream-events"></a>
### Event stream events

The event stream event is a map:

- `:data` - a string
- `:id` - a string
- `:event` - a string

The map will never be empty, but it may have any combination of 1, 2, or all of
these keys.

### Meta events

The library emits meta events instead of logging directly. Meta events always
have an `:event` key in `:content`:

- `:retry` means the connection was lost and a retry is about to be attempted.
  This event will also have `:retry-attempts`, the number of retries tried so
  far (including this), and `:retry-after`, which indicates for how long it will
  wait until attempting the retry.
- `:no-connection` means the connection was lost an retries are exhausted - the
  channel is about to close. Contains `:retry-attempts`, the number of retries
  attempted.

### Parameters

`clj-event-source.core.async/connect` takes an optional second parameter - a map
of parameters:

- `:chan` - the channel to use for relaying messages
- `:retry-attempts` - number of times to retry a failed connection. Defaults to
  3 times. This number of retries will be attempted for every lost connection.
  If the connection is re-established after 2 retries, the next time the
  connection is lost, 3 new retries will be attempted before giving up.
- `:retry-delay` - the number of milliseconds to wait between retries.
  Optionally this can be a vector of numbers. The default value is `[1000 2000
  3000]` which means wait 1 second before the first retry, 2 seconds before the
  second retry, and 3 seconds before any subsequent retry.

## License

Copyright © 2019 Christian Johansen

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
