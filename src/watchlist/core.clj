(ns watchlist.core
  (:gen-class) ; required for uberjar
  (:require [watchlist.web-api :as api]
            [clojure.java.io :as io]
            [clojure.core :refer :all]
            [clojure.string :refer [join split-lines trim]]
            [seesaw.core :refer :all]
            [seesaw.color :refer :all]
            [seesaw.border :refer [empty-border]]
            [seesaw.keymap :refer :all]
            [seesaw.mig :refer :all]
            [seesaw.swingx :refer [hyperlink busy-label]]
            [seesaw.bind :as bind] 
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
       :center (let [s (scrollable
                          (vertical-panel
                            :border (empty-border :thickness 0)
                            :id :updates-panel
                            :items [])
                          :border 0)]
                  (-> s (.getVerticalScrollBar) (.setUnitIncrement 8))
                  (-> s (.getHorizontalScrollBar) (.setUnitIncrement 6))
                  s)
       :south (border-panel 
                :border [(empty-border :thickness 6)]
                :west (horizontal-panel
                        :items [;"Feed fresh as of 3 minutes ago  "
                                (label
                                  :text "<html><u>Check now</u>&nbsp;&nbsp;<html>  "
                                  :id :check-now
                                  :cursor :hand)
                                (busy-label
                                  :text ""
                                  :visible? false
                                  :id :fetching-indicator
                                  :busy? true)])
                                   
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
      (>= delta-hours 1) (str delta-hours "h")
      (>= delta-minutes 1) (str delta-minutes "m")
      :else (str delta-seconds "s"))))

(defrecord NoteUpdate [id author subject update-text update-uri update-uri-label updated-at])
(defrecord StatusUpdate [id author subject old-status new-status new-status-label update-uri update-uri-label updated-at])
(defrecord NoteAndStatusUpdate [id author subject update-text old-status new-status update-uri update-uri-label updated-at])

(defn determine-update [issue]
  "Given an issue update determine if it's a NoteUpdate
   or a StatusUpdate or both or something else and return it"
  (let [issue-id (-> issue :id)
        subject (-> issue :subject)
        author (-> issue :author :name)
        update-rank (count (-> issue :journals))
        update-uri (str "http://redmine.visiontree.com/issues/"
                         issue-id
                         "#note-"
                         update-rank)
        update-uri-label (str "#" update-rank)
        updated-at (-> issue :updated_on)]
    (cond
      ; first check if NoteAndStatus
      (and
        (not (empty? (-> issue :journals (last) :notes)))
        (= "status_id" (-> issue :journals (last) :details (last) :name)))
      (let [last-journal-entry (-> issue :journals (last))
            update-text (-> last-journal-entry :notes)
            update-author (-> last-journal-entry :user :name)
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)]
        (NoteAndStatusUpdate. issue-id
                              update-author
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
        (empty? (-> issue :journals (last) :notes)))
      (let [last-journal-entry (-> issue :journals (last))
            update-author (-> last-journal-entry :user :name)
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)
            new-status-label (api/get-issue-status-name-by-id new-status)]
        (StatusUpdate. issue-id
                       update-author
                       subject
                       old-status
                       new-status
                       new-status-label
                       update-uri
                       update-uri-label
                       updated-at))
      ; third, check if NoteUpdate
      (and
        ; if :notes field but not status_id
        (not (empty? (-> issue :journals (last) :notes)))
        (not= "status_id" (-> issue :journals (last) :details (last) :name)))
      (let [last-journal-entry (-> issue :journals (last))
            update-text (-> last-journal-entry :notes)
            update-author (-> last-journal-entry :user :name)
            old-status (-> last-journal-entry :details (last) :old_value)
            new-status (-> last-journal-entry :details (last) :new_value)]
        (NoteUpdate. issue-id
                     update-author
                     subject
                     update-text
                     update-uri
                     update-uri-label
                     updated-at))
      ; check here for brand new instance - no :journals
      ; suggests making new type of update like NewIssueUpdate?
      ;or just filter out the nils?
      )))

(defn build-update-row [data]
  (mig-panel 
    :border [(empty-border :thickness 0)]
    :background (color "white")
    :constraints ["ins 10", "[][grow][]", "[top]"]
    :items [
      [(label :text (str "#"
                         (:id data)
                         " "
                         (:subject data))
              :font "ARIAL-BOLD-14")
       "span 2"]
      ; Hack to get the text to set. :text on hyperlink did not work
      [(config! (hyperlink
                  :uri (:update-uri data)
                  :tip "Open in browser")
                :text (:update-uri-label data))
       "wrap"]
      [(label :text (str (first
                           (clojure.string/split (:author data) #"\s"))
                         ", "
                         (format-time-ago
                           (time-format/parse (:updated-at data))))
              :tip (str "Updated at "
                        (time-format/unparse 
                          (time-format/formatter-local
                            "MM/dd/yyyy hh:mm:ssa")
                          (time-local/to-local-date-time
                            (:updated-at data)))))]
      [(text :text (cond
                     (or (instance? NoteUpdate data)
                         (instance? NoteAndStatusUpdate data))
                     (:update-text data)
                     (instance? StatusUpdate data)
                     (str "Status set to: "
                          (:new-status-label data)))
        :multi-line? true
        :editable? false
        :wrap-lines? true
        :background (color "#eeeeee")
        :margin 5)
      "span 2 2, gap 8, growx, wrap"]]))

(defn get-issue-updates [from-ts]
  "Iterate issue updates and convert to simple map to 
   hand to View func"
  (filterv
    #(not (nil? %))
    (map
      #(determine-update
         (api/issue
           (get-in % [:id])))
      (api/get-updated-issues from-ts))))

(def master-updates
  "The 'Model' that holds all updates to be rendered in view"
  (atom []))

(defn set-master-updates [new-updates-list]
  (reset! master-updates new-updates-list))

(def last-update-ts (atom (time-core/now)))

(defn set-last-update-ts [ts]
  (reset! last-update-ts ts))

(def fetching-updates (atom false))

(defn set-fetching-updates [busy?]
  (reset! fetching-updates busy?))

(defn merge-updates [old-list new-list]
  "Take old-list of updates and merge new-list by
   first removing duplicate issue id items in old list
   and prepending new-list"
  (let [new-update-ids (reduce
                         (fn [accum value]
                           (conj accum (:id value)))
                         #{}
                         new-list)
        filtered-old-list (filterv
                            (fn [old-value]
                              (not-any? #(= (:id old-value) %)
                                new-update-ids))
                            old-list)]
    (into new-list filtered-old-list)))

(def watchlist-frame
  (frame
    :title "WatchList"
    :on-close :exit
    :content (frame-content)
    :size [500 :by 700]
    :minimum-size [300 :by 200]))

(defn set-update-items-list-ui [from-date]
  (set-last-update-ts (time-core/now))
  (set-fetching-updates true)
  (future
    (let [issue-updates (get-issue-updates from-date)
          merged-items (merge-updates @master-updates issue-updates)
          built-items (mapv
                        build-update-row
                        merged-items)]
        (set-master-updates merged-items)
        (seesaw.core/config!
          (seesaw.core/select
            watchlist-frame
            [:#updates-panel])
          :items built-items)
        (set-fetching-updates false))))

(defn start-app []
  (api/load-token)
  (-> watchlist-frame pack! show!)
  (config! watchlist-frame :content (frame-content))
  (bind/bind
    fetching-updates
    (bind/property (select watchlist-frame [:#fetching-indicator]) :visible?))
  (listen (select watchlist-frame [:#check-now])
    :mouse-clicked (fn [evt-source]
                     (set-update-items-list-ui @last-update-ts)
                     (scroll! (select watchlist-frame [:#updates-panel]) :to :top)))
  (set-update-items-list-ui (time-core/date-time 2014 8 22)))

