(ns tachyon.parser-test
  (:use [tachyon.parser] :reload)
  (:use [clojure.test]))

(deftest can-parse-line
  (is (=
       {:prefix {:nick nil, :username nil, :host nil}, :command "TEST", :args ["a" "b"]}
       (parse-line "TEST a b")))
  (is (=
       {:prefix {:nick nil, :username nil, :host "test.example.com"}, :command "372", :args ["foo bar"]}
       (parse-line ":test.example.com 372 :foo bar")))
  (is (=
       {:prefix {:nick "someone", :username "foo", :host "example.com"}, :command "PRIVMSG", :args ["me" "hey you"]}
       (parse-line ":someone!foo@example.com PRIVMSG me :hey you"))))


