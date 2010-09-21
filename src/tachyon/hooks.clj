(ns tachyon.hooks
  (:import [java.util.regex Pattern])
  (:use [tachyon core]))


(defn regexify [obj]
  (if (isa? (class obj) Pattern)
    obj
    (re-pattern (str "^" obj "$"))))

(defn add-raw-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :raw-hooks (cons [pred callback] (:raw-hooks @irc))))))

(defn remove-raw-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :raw-hooks (remove
                              (fn [x] (= x [pred callback]))
                              (:raw-hooks @irc))))))


(defn add-message-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :message-hooks (cons [(regexify pred) callback] (:message-hooks @irc))))))

(defn remove-message-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :message-hooks (remove
                                  (fn [x] (= x [(regexify pred) callback]))
                                  (:message-hooks @irc))))))

(defn add-command-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :command-hooks (cons [(regexify pred) callback] (:command-hooks @irc))))))

(defn remove-command-hook [irc pred callback]
  (dosync
   (ref-set irc (assoc @irc
                  :command-hooks (remove
                                  (fn [x] (= x [(regexify pred) callback]))
                                  (:command-hooks @irc))))))

(defn apply-hooks [irc object hooks filter]
  (map (fn [hook]
         (let [match (re-find (first hook) filter)]
           (if match
             ((second hook) irc object match))))
       hooks))

