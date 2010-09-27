(ns tachyon.test.hooks
  (:use [tachyon.hooks] :reload)
  (:use [tachyon.core] :reload)
  (:use [clojure.test]))

(deftest can-add-and-remove-message-hook
  (let [conn (create {})
        hook-count (count (:message-hooks @conn))]
    (add-message-hook conn #"\.u ?(.*)?" println)
    (is (=
         (count (:message-hooks @conn))
         (+ hook-count 1)))
    (remove-message-hook conn #"\.u ?(.*)?" println)
    (is (=
         (count (:message-hooks @conn))
         hook-count))))
