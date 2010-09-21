(ns demogorgon.nh)
  ;; (:import [org.apache.mina.transport.socket.nio NioSocketConnector]
  ;;          [org.apache.mina.core.service IoHandlerAdapter]
  ;;          [org.apache.mina.filter.codec ProtocolCodecFilter ProtocolCodecFactory ProtocolDecoder ProtocolEncoder]
  ;;          [org.apache.mina.filter.codec.textline TextLineCodecFactory TextLineEncoder TextLineDecoder LineDelimiter]
  ;;          [org.apache.mina.filter.logging LoggingFilter]
  ;;          [org.apache.mina.util ExceptionMonitor]
  ;;          [java.net InetSocketAddress]
  ;;          [java.nio.charset Charset]
  ;;          [java.util.regex Pattern]
  ;;          [org.apache.log4j Logger])
  ;; (:use [clj-stacktrace core repl]))

(defn parse-line [line]
  (let [props (.split line ":")
        keys (map (fn [x] (keyword (aget (.split x "=") 0))) props)
        values (map (fn [x] (aget (.split x "=") 1)) props)]
    (zipmap keys values)))




