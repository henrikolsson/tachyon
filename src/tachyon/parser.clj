(ns tachyon.parser)

(def message-regex #"^(?::([^ ]+) +)?([^ ]+)(?: +(.+))?$")
(def param-regex #"(?:(?<!:)[^ :][^ ]*|(?<=:).*)")
(def prefix-regex #"((.*)!(.*)@)?(.*)")

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
