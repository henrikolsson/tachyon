(defproject tachyon "0.0.3-SNAPSHOT"
  :description "a clojure irc library"
  :url "https://github.com/henrikolsson/tachyon"
  :license {:name "Apache license version 2"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.apache.mina/mina-core  "2.0.9"]
                 [org.slf4j/slf4j-api "1.7.10"]
                 [clj-stacktrace "0.2.8"]
                 [org.clojure/tools.logging "0.3.1"]]
  :profiles {:dev {:resource-paths ["resources-dev"]
                   :dependencies [[org.slf4j/slf4j-simple "1.7.10"]]}})
