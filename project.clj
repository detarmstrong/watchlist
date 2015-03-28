(defproject watchlist "0.1.0"
  :description "watchlist watches redmine updates"
  :url "https://github.com/detarmstrong/watchlist"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["local_maven_repo" "file:local_maven_repo"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [seesaw "1.4.4"]
                 [cheshire "5.3.1"]
                 [clj-http "0.9.1"]
                 [org.clojure/core.memoize "0.5.6"]
                 [overtone/at-at "1.2.0"]
                 [clj-time "0.8.0"]
                 [com.bulenkov/iconloader "1.1"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}}
  :main watchlist.core
  :jvm-opts ["-Xmx128m"])
