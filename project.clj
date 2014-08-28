(defproject watchlist "0.1.0"
  :description "watchlist watches redmine updates"
  :url "https://github.com/detarmstrong/watchlist"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [seesaw "1.4.4"]
                 [cheshire "5.3.1"]
                 [clj-http "0.9.1"]
                 [org.clojure/core.memoize "0.5.6"]
                 [overtone/at-at "1.2.0"]
                 [clj-time "0.8.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.0"]]}}
  :main watchlist.core
  :jvm-opts ["-Xmx128m"])