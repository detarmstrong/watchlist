(ns watchlist.desktop
  (:import (java.awt Desktop)
           (java.net URI)))

(defn open-url-on-desktop
  "Given a url open it in the default desktop browser"
  [url]
  (doto 
    (Desktop/getDesktop) 
    (.browse 
      (URI/create url))))