(defproject tachyon "1.0.0-SNAPSHOT"
  :description "IRC library for clojure"
  :url "https://github.com/henrikolsson/tachyon"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/mit-license.php"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.slf4j/slf4j-api "1.7.10"]
                 [org.clojure/tools.logging "0.3.1"]
                 [io.netty/netty-all "4.0.27.Final"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :profiles {:dev {:dependencies [[org.slf4j/slf4j-simple "1.7.10"]]}})

