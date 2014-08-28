(ns watchlist.core
  (:gen-class) ; required for uberjar
  (:require [watchlist.web-api :as api]
            [clojure.java.io :as io]
            [clojure.core :refer :all]
            [clojure.string :refer [join split-lines]]
            [seesaw.core :refer :all]
            [seesaw.color :refer :all]
            [seesaw.border :refer [empty-border]]
            [seesaw.keymap :refer :all]
            [seesaw.mig :refer :all]
            [seesaw.swingx :refer [hyperlink]]
            [clj-http.util :refer [url-encode]]
            [overtone.at-at :as at-at]
            [clj-time.core :as time-core]
            [clj-time.format :as time-format]
            [clj-time.local :as time-local])
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

(defn contains-every? [m keyseqs]
  (let [not-found (Object.)]
    (not-any? #{not-found}
              (for [ks keyseqs]
                (get-in m ks not-found)))))

(defn format-time-ago [from-time]
  "Return formatted string indicating the time delta between
   now in utc and from-time"
  (let [delta (time-core/interval from-time (time-core/now))
        delta-years (time-core/in-years delta)
        delta-days (time-core/in-days delta)
        delta-hours (time-core/in-hours delta)
        delta-minutes (time-core/in-minutes delta)
        delta-seconds (time-core/in-seconds delta)]
    (cond
      (>= delta-years 1) (str delta-years "y")
      (>= delta-days 30) (str (/ delta-days 30) "mon")
      (>= delta-days 1) (str delta-days "d")
      (>= delta-hours) 1) (str delta-hours "h")
      (>= delta-minutes 1) (str delta-minutes "m")
      :else (str delta-seconds "s")))

(defrecord NoteUpdate [author subject update-text update-uri update-uri-label updated-at])
(defrecord StatusUpdate [author subject old-status new-status update-uri update-uri-label updated-at])
(defrecord NoteAndStatusUpdate [author subject update-text old-status new-status update-uri update-uri-label updated-at])

(defn determine-update [issue]
  "Given an issue update determine if it's a NoteUpdate
   or a StatusUpdate or both or something else and return it"
  (let [issue-id (-> issue :id)
        subject (-> issue :subject)
        author (-> issue :author :name)
        update-rank (count (-> issue :journals))
        update-uri (str "http://redmine.visiontree.com/issues/"
                         issue-id
                         "#"
                         update-rank)
        update-uri-label (str "#" update-rank)
        updated-at (time-format/parse (-> issue :updated_on))]
    (cond
      ; first check if NoteAndStatus
      (and
        (-> issue :journals (last) :notes)
        (= "status_id" (-> issue :journals (last) :details (last) :name)))
      (let [last-journal-entry (-> issue :journals (last))
            update-text (-> last-journal-entry :notes)
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)]
        (NoteAndStatusUpdate. author
                              subject
                              update-text
                              old-status
                              new-status
                              update-uri
                              update-uri-label
                              updated-at))
      ; second, check if StatusUpdate
      (and
        (= "status_id" (-> issue :journals (last) :details (last) :name))
        (not (-> issue :journals (last) :notes)))
      (let [last-journal-entry (-> issue :journals (last))
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)]
        (StatusUpdate. author
                       subject
                       old-status
                       new-status
                       update-uri
                       update-uri-label
                       updated-at))
      ; third, check if NoteUpdate
      (and
        ; if :notes field but not status_id
        (-> issue :journals (last) :notes)
        (not= "status_id" (-> issue :journals (last) :details (last) :name)))
      (let [last-journal-entry (-> issue :journals (last))
            update-text (-> last-journal-entry :notes)
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)]
        (NoteUpdate. author
                     subject
                     update-text
                     update-uri
                     update-uri-label
                     updated-at)))))

(defn build-update-row [data]
  (mig-panel 
    :border [(empty-border :thickness 0)]
    :background (color "white")
    :constraints ["ins 10", "[][grow][]", "[top]"]
    :items [
      [(label :text (:subject data)
              :font "ARIAL-BOLD-14")
       "span 2"]
      ; Hack to get the text to set. :text on hyperlink did not work
      [(config! (hyperlink
                  :uri (:update-uri data)
                  :tip "Open in browser")
                :text (:update-uri-label data))
       "wrap"]
      [(label :text (str (:author data)
                         ", "
                         (format-time-ago (:updated-at data)))
              :tip (str "Updated at "
                        (time-format/unparse 
                          (time-format/formatter-local
                            "MM/dd/yyyy hh:mm:ssa")
                          (time-local/to-local-date-time
                            (:updated-at data)))))]
      [(text :text (if (or (instance? NoteUpdate data)
                           (instance? NoteAndStatusUpdate data))
                     (:update-text data)
                     "Not a note update.")
             :multi-line? true
             :editable? false
             :wrap-lines? true
             :background (color "lightgray")
             :margin 5)
       "span 2 2, gap 8, growx, wrap"]]))

(defn get-issue-updates [from-ts]
  "Iterate issue updates and convert to simple map to 
   hand to View func"
  (map #(determine-update (api/issue (get-in % [:id]))) (api/get-updated-issues from-ts)))

(def watchlist-frame
  (frame
    :title "WatchList"
    :on-close :exit
    :content (frame-content)))

;(defn show-frame []
;  (config! watchlist-frame :content (frame-content))
;  (doseq [item (select watchlist-frame [:#updates-panel :> :*])]
;    (remove! (select watchlist-frame [:#updates-panel]) item))
;  (repeatedly
;    20
;    #(add!
;       (select watchlist-frame [:#updates-panel])
;       (build-update-row {:name "xx"})))
;  (-> watchlist-frame pack! show!))
;
;(show-frame)