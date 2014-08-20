(ns watchlist.core
  (:gen-class) ; required for uberjar
  (:require [clojure.java.io :as io]
            [clojure.core :refer :all]
            [clojure.string :refer [join split-lines]]
            [seesaw.border :refer [empty-border]]
            [seesaw.keymap :refer :all]
            [seesaw.mig :refer :all]
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
                   :border (empty-border)
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
    :background (color "white")
    :constraints ["", "[][grow][]", "[top]"]
    :items [
      ["<html><b>#8400 Add tertiary insurance</b></html>" "span 2"]
      ["<html><a href='#'>#13</a></html>" "wrap"]
      ["Danny, 30m"]
      [
         (text :text "Update is really long update that goes and goes and goes and goes and goes and goes \nnew line\nnew line\nnewline"
               :multi-line? true
                       :editable? false
                       :wrap-lines? true
                       :background (color "lightgray")
                       :margin 5
                       )
         
       "span 2 2, gap 8, growx, wrap"]
      ["(urgent)"]]))
               
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