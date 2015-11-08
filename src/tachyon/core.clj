(ns tachyon.core
  (:import [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.bootstrap Bootstrap]
           [io.netty.channel.socket.nio NioSocketChannel]
           [io.netty.handler.codec LineBasedFrameDecoder]
           [io.netty.handler.codec.string StringDecoder StringEncoder]
           [io.netty.channel SimpleChannelInboundHandler ChannelFutureListener ChannelHandler ChannelOption ChannelInitializer]
           [java.nio.charset Charset])
  (:require [tachyon.parser :refer [parse-prefix parse-line]]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan close! <! >! pub sub unsub go go-loop timeout]]))

(def CHARSET (Charset/forName "UTF-8"))

(defn- add-future-listener [cf f]
  (.addListener cf
   (proxy [ChannelFutureListener] []
     (operationComplete [future]
       (f)))))

(defn send-line [connection & rest]
  (let [pipeline @(:pipeline connection)
        result (promise)
        line (apply str rest)]
    (log/trace "->" line)
    (add-future-listener (.write pipeline (str line "\r\n"))
                         #(deliver result true))
    (.flush pipeline)
    result))

(defn send-message [connection target msg]
  (send-line connection (str "PRIVMSG " target " :" msg)))

(defn- create-bootstrap []
  (let [bootstrap (Bootstrap.)]
    (-> bootstrap
        (.group (NioEventLoopGroup.))
        (.channel NioSocketChannel)
        (.option ChannelOption/SO_KEEPALIVE true))))

(defn- publish [connection type body]
  (go (>! (:channel connection)
          (assoc body :type type))))

(defn- handle-line [connection msg]
  (log/trace "<-" (.trim msg))
  (let [parsed (parse-line msg)]
    (publish connection :raw {:message msg})
    (publish connection :raw-parsed {:message parsed})))

(defn- add-handlers [connection]
  (let [bootstrap (:bootstrap connection)]
    (.handler
     bootstrap
     (proxy [ChannelInitializer] []
       (initChannel [channel]
         (reset! (:pipeline connection) (.pipeline channel))
         (.addLast (.pipeline channel)
                   (into-array
                    ChannelHandler
                    [(LineBasedFrameDecoder. 2048)
                     (StringDecoder. CHARSET)
                     (StringEncoder. CHARSET)
                     (proxy [SimpleChannelInboundHandler] []
                       (channelRead0 [ctx msg]
                         (handle-line connection msg))
                       (exceptionCaught [ctx cause]
                         (publish connection :error {:exception cause})
                         (log/error cause "general error")))])))))))


(defn- add-listener [connection type f]
  (let [subscriber (chan)]
    (sub (:publication connection) type subscriber)
    (go-loop []
      (let [val (<! subscriber)]        
        (if val
          (do
            (try
              (f val)
              (catch Exception e
                (log/error e "error in listener")))
            (recur)))))))

(defn create [config]
  (let [bootstrap (create-bootstrap)
        channel (chan)
        connection {:bootstrap bootstrap
                    :channel channel
                    :publication (pub channel :type)
                    :channel-future (atom nil)
                    :pipeline (atom nil)
                    :server-idx (atom -1)
                    :config config}]
    (add-handlers connection)
    (add-listener connection :connected
                  (fn [m]
                    (send-line connection "NICK " (:nick config))
                    (send-line connection "USER " (:username config) " 8 * :" (:realname config))))
    (add-listener connection :raw-parsed
                  (fn [msg]
                    (if (= (get-in msg [:message :command]) "001")
                      (do
                        (log/debug "registered" (:channels config))
                        (doseq [c (:channels config)]
                          (send-line connection "JOIN " c))
                        (publish connection :registered {})))))
    (add-listener connection :raw-parsed
                  (fn [msg]
                    (if (= (get-in msg [:message :command]) "PRIVMSG")
                      (publish connection :privmsg {:prefix (:prefix (:message msg))
                                                    :target (first (:args (:message msg)))
                                                    :msg (second (:args (:message msg)))}))))
    (add-listener connection :raw-parsed
                  (fn [msg]
                    (if (= (get-in msg [:message :command]) "PING")
                      (do (log/debug msg)
                          (send-line connection (str "PONG :" (first (get-in msg [:message :args]))))))))
    connection))


(defn- next-server [connection]
  (let [current-idx @(:server-idx connection)
        servers (get-in connection [:config :servers])
        new-idx (if (= (+ current-idx 1) (count servers))
                  0
                  (+ current-idx 1))]
    (reset! (:server-idx connection) new-idx)
    (nth servers new-idx)))

(defn connect [connection]
  (let [server (next-server connection)
        host (first server)
        port (second server)
        cf (.connect (:bootstrap connection) host port)]
    (reset! (:channel-future connection) cf)
    (add-future-listener cf
     #(publish connection :connected {:host host :port port}))))

(defn get-publication [connection]
  (:publication connection))

(defn shutdown [c]
  (let [cf @(:channel-future c)
        event-loop-group (.group (:bootstrap c))]
    (add-future-listener (.closeFuture (.channel cf))
                         (fn []
                           (close! (:channel c))
                           (.shutdownGracefully event-loop-group)))
    (.close (.channel cf))))

(defn listen [conn]
  (let [subscriber (chan)]
    (sub (:publication conn) :privmsg subscriber)
    (go-loop []
      (let [x (<! subscriber)]
        (log/debug "got value:" x)
        (if x
          (do
            (log/debug "recuring")
            (recur)))))))

(comment
  (def conn (create {:nick "adsfasdf"
                     :username "user"
                     :realname "real name"
                     :servers [["irc.du.se" 6667] ["efnet.port80.se" 6667]]
                     :channels ["#asdasdsd"]}))
  (def listener (listen conn))
  (def listener2 (listen conn))
  (connect conn)
  (shutdown conn)
  (close! listener)
  (close! listener2))
