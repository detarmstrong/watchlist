(ns watchlist.t-web-api
  (:require [clj-time.core :as time-core])
  (:use midje.sweet)
  (:use [watchlist.web-api]))

(defn user-home-dir []
  (System/getProperty "user.home"))

(facts "about obelisk_token_file_path"
       (fact "it is located in the user's home directory"
             obelisk-token-file-path
             =>
             (contains (user-home-dir))))

(facts "about iso-8601-fmt"
  (fact "iso-8601-fmt returns iso 8601 formatted date"
    (iso-8601-fmt (time-core/date-time 2014 1 2 8 12 32))
    =>
    "2014-01-02T08:12:32Z"))

(facts "about YYYY-mm-dd-fmt"
  (fact "YYYY-mm-dd-fmt returns a formatted date"
    (YYYY-mm-dd-fmt (time-core/date-time 2014 1 2 8 12 32))
    =>
    "2014-01-02"))