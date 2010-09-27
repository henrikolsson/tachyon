(ns tachyon.core
  (:import [org.apache.mina.transport.socket.nio NioSocketConnector]
           [org.apache.mina.core.service IoHandlerAdapter]
           [org.apache.mina.filter.codec ProtocolCodecFilter ProtocolCodecFactory ProtocolDecoder ProtocolEncoder]
           [org.apache.mina.filter.codec.textline TextLineCodecFactory TextLineEncoder TextLineDecoder LineDelimiter]
           [org.apache.mina.filter.logging LoggingFilter]
           [org.apache.mina.util ExceptionMonitor]
           [java.net InetSocketAddress]
           [java.nio.charset Charset]
           [java.util.regex Pattern]
           [org.slf4j LoggerFactory Logger])
  (:require [tachyon.hooks :as hooks]
            [clj-stacktrace.repl :as stacktrace]))



(def logger (LoggerFactory/getLogger "tachyon.core"))
(def message-regex #"^(?::([^ ]+) +)?([^ ]+)(?: +(.+))?$")
(def param-regex #"(?:(?<!:)[^ :][^ ]*|(?<=:).*)")
(def prefix-regex #"((.*)!(.*)@)?(.*)")
(defstruct irc-connection :connector :config :session :raw-hooks :message-hooks :server-idx)

(defn parse-prefix [prefix]
  (if prefix 
    (let [matches (re-find prefix-regex prefix)]
      {:nick (nth matches 2)
       :username (nth matches 3)
       :host (nth matches 4)})
    {:nick nil
     :username nil
     :host nil}))

(defn parse-line [line]
  (let [[prefix command params] (rest (re-matches message-regex line))]
    (let [args (if params
                 (re-seq param-regex params)
                 [])]
      {:prefix (parse-prefix prefix)
       :command command
       :args (vec args)})))

(defn send-line [irc & rest]
  (let [message (apply str rest)]
    (.trace logger (str "-> " message))
    (.write (:session @irc) message)))

(defn send-message [irc target & rest]
  (let [message (str "PRIVMSG " target " :" (apply str rest))]
   (send-line message)))

(defn join-channel-hook [irc object match]
  (str "JOIN " (apply str (interpose "," (:channels (:config @irc))))))

(defn ping-hook [irc object match]
  (str "PONG " (first (:args object))))

(defn privmsg-hook [irc object match]
  (let [results (hooks/apply-hooks irc object (:message-hooks @irc) (second (:args object)))]
    (let [target (first (:args object))
          reply-target (if (.startsWith target "#")
                         target
                         (:nick (:prefix object)))]
      (doseq [result results]
        (if result
          (if (or (list? result)
                  (seq? result)
                  (vector? result))
            (doseq [r result]
              (send-message irc target r))
            (send-message irc target result)))))))
  
(defn handle-incoming [irc object]
  (let [session (:session @irc)]
    (doseq [raw-hook (:raw-hooks @irc)]
      (if ((first raw-hook) irc object)
        ((second raw-hook) irc object)))
    (let [results (hooks/apply-hooks irc object (:command-hooks @irc) (:command object))]
      (doseq [result results]
        (if (isa? (class result) String)
          (send-line irc result))))))

(defn handle-exception [irc cause]
  ; TODO: Implement something better..
  (stacktrace/pst cause))

(defn connect [irc]
  (let [connector (:connector @irc)
        config (:config @irc)
        server-idx (:server-idx @irc)
        server (nth (:servers config) server-idx)]
    ; inc server-idx
    (dosync 
     (ref-set irc (assoc @irc
                    :server-idx (if (= server-idx (count (:servers config)))
                                  0
                                  (+ server-idx 1)))))
    (.info logger (str "Connecting to " (first server) ":" (second server) ".."))
    (.connect connector (new InetSocketAddress (first server) (second server)))))

(defn handler [irc]
  (proxy [IoHandlerAdapter] []
    (exceptionCaught [session cause]
                     (handle-exception irc cause))
    (sessionOpened [session]
                   (dosync
                    (ref-set irc (assoc @irc :session session)))
                   (.info logger "Connected")
                   (send-line irc "NICK " (:nick (:config @irc)))
                   (send-line irc "USER " (:username (:config @irc)) " 8 * :" (:realname (:config @irc))))
    (sessionClosed [session]
                   (.info logger "Disconnected")
                   (Thread/sleep 2000)
                   ; TODO: Will this work? We will re-associate :session in connection object..
                   (connect irc))
    (messageReceived [session message]
                     (.trace logger (str "<- " message))
                     (handle-incoming irc (parse-line message)))))

(defn diconnect [irc]
  (.close (:session @irc) true))

(defn wait-for [irc]
  (.awaitUninterruptibly (.getCloseFuture (:session @irc))))

(defn create [config]
  (let [connector (new NioSocketConnector)
        irc (ref (struct-map irc-connection
                   :connector connector
                   :config config
                   :server-idx 0
                   :session nil
                   :raw-hooks '()
                   :message-hooks '()))]
    (hooks/add-command-hook irc "001" join-channel-hook)
    (hooks/add-command-hook irc "PRIVMSG" privmsg-hook)
    (hooks/add-command-hook irc "PING" ping-hook)
    ; Singletons are bad... mkay?
    (ExceptionMonitor/setInstance (proxy [ExceptionMonitor] []
                                    (exceptionCaught [cause]
                                                     (handle-exception irc cause))))
    (.setHandler connector (handler irc))
    (doto (.getFilterChain connector)
      (.addLast "codec" (new ProtocolCodecFilter (new TextLineCodecFactory (Charset/forName "UTF-8")))))
      ;(.addLast "logger" (new LoggingFilter)))
    irc))

