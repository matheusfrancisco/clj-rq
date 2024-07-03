(ns com.moclojer.rq.queue
  (:require
   [clojure.edn :as edn]
   [clojure.tools.logging :as log]
   [com.moclojer.rq.utils :as utils]))

(defn push!
  [client queue-name message & options]
  (let [{:keys [direction pattern _at _in _retry _retry-delay]
         :or {direction :l
              pattern :rq}
         :as opts} options
        packed-queue-name (utils/pack-pattern pattern queue-name)
        encoded-message (into-array [(pr-str message)])
        pushed-count (if (= direction :l)
                       (.lpush @client packed-queue-name encoded-message)
                       (.rpush @client packed-queue-name encoded-message))]

    (log/debug "pushed to queue"
               {:queue-name packed-queue-name
                :message message
                :options opts
                :pushed-count pushed-count})

    pushed-count))

(comment
  (import redis.clients.jedis.JedisPooled)

  (let [client (atom (JedisPooled. "redis://localhost:6379"))]
    (push! client "my-queue" {:hello true} :direction :l)
    (.close @client))
  ;;
  )

(defn pop!
  [client queue-name & options]
  (let [{:keys [direction pattern]
         :or {direction :l
              pattern :rq}
         :as opts} options
        packed-queue-name (utils/pack-pattern pattern queue-name)
        message (if (= direction :l)
                  (.lpop @client packed-queue-name)
                  (.rpop @client packed-queue-name))]

    (log/debug "popped from queue"
               {:queue-name packed-queue-name
                :options opts
                :message message})

    (edn/read-string message)))

(comment
  (import redis.clients.jedis.JedisPooled)

  (let [client (atom (JedisPooled. "redis://localhost:6379"))]
    (push! client "my-queue" {:hello true} :direction :r)
    (let [popped-val (pop! client "my-queue" :direction :r)]
      (.close @client)
      popped-val))
  ;; => {:hello true}
  )

(defn llen
  "get size of a queue"
  [client queue-name & options]
  (let [{:keys [pattern]
         :or {pattern :rq}} options]
    (.llen @client (utils/pack-pattern pattern queue-name))))

(comment
  (import redis.clients.jedis.JedisPooled)

  (let [client (atom (JedisPooled. "redis://localhost:6379"))]
    (push! client "my-queue" {:hello 1} :direction :r)
    (push! client "my-queue" {:hello 2} :direction :r)
    (push! client "my-queue" {:hello 3} :direction :r)
    (let [queue-length (llen client "my-queue")]
      (pop! client "my-queue" :direction :r)
      (pop! client "my-queue" :direction :r)
      (pop! client "my-queue" :direction :r)
      (.close @client)
      queue-length))
  ;; => 3
  )
