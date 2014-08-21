(ns watchlist.core
  (:gen-class) ; required for uberjar
  (:require [clojure.java.io :as io]
            [clojure.core :refer :all]
            [clojure.string :refer [join split-lines]]
            [seesaw.border :refer [empty-border]]
            [seesaw.keymap :refer :all]
            [seesaw.mig :refer :all]
            [seesaw.swingx :refer [hyperlink]]
            [clj-http.util :refer [url-encode]]
            [overtone.at-at :as at-at])
  (:use seesaw.core
        seesaw.color)
  (:import (java.awt Desktop)))

(defn frame-content []
  (border-panel
       ;:north (border-panel
       ;         :border [(seesaw.border/empty-border :thickness 6)]
       ;         :center "You have 5 new updates")
       :center (scrollable
                 (vertical-panel
                   :border (empty-border :thickness 0)
                   :id :updates-panel
                   :items [])
                 :border 0
                 )
       :south (border-panel 
                :border [(empty-border :thickness 6)]
                :west "Feed fresh as of 3 minutes ago"
                :east (button :id :settings
                                          :icon (io/resource
                                                  "gear.png")))))

(defn mig []
  (mig-panel 
    :border [(empty-border :thickness 0)]
    :background (color "white")
    :constraints ["ins 10", "[][grow][]", "[top]"]
    :items [
      ["<html><b>#8400 Add tertiary insurance</b></html>" "span 2"]
      ; Hack to get the text to set. :text on hyperlink did not work
      [(config! (hyperlink :uri "http://google.com") :text "#13") "wrap"]
      ["Danny, 30m"]
      [(text :text "Update is really long update that goes and goes and goes and goes and goes and goes \nnew line\nnew line\nnewline"
             :multi-line? true
             :editable? false
             :wrap-lines? true
             :background (color "lightgray")
             :margin 5)
       "span 2 2, gap 8, growx, wrap"]
      ["(urgent)" "wrap"]
      ["You're the assignee" "span 2"]]))
               
(def watchlist-frame
  (frame
    :title "WatchList"
    :on-close :exit
    :content (frame-content)))

(defn show-frame []
  (config! watchlist-frame :content (frame-content))
  (doseq [item (select watchlist-frame [:#updates-panel :> :*])]
    (remove! (select watchlist-frame [:#updates-panel]) item))
  (repeatedly 20  #(add! (select watchlist-frame [:#updates-panel]) (mig)))
  (-> watchlist-frame pack! show!))
(show-frame)