(ns watchlist.core
  (:gen-class) ; required for uberjar
  (:require [watchlist.web-api :as api]
            [clojure.java.io :as io]
            [clojure.core :refer :all]
            [clojure.string :refer [join split-lines trim]]
            [clojure.edn :as edn]
            [seesaw.core :refer :all]
            [seesaw.color :refer :all]
            [seesaw.border :refer [empty-border]]
            [seesaw.font :refer [font default-font]]
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

(declare convert-update)
(declare set-update-items-list-ui)
(declare load-preferences)
(declare in?)

(def preferences-file-path
  (let [dot-file ".watchlist-preferences"
        home-dir (System/getProperty "user.home")
        file-separator (System/getProperty "file.separator")
        full-path (apply str (interpose file-separator [home-dir dot-file]))]
    full-path))

(def good-defaults
  {:filter-options [:im-a-watcher
                    :im-the-assignee
                    :im-the-author]})

(defn load-preferences
  "return preferences from disk, or return default set"
  []
  (if (-> (io/file preferences-file-path) (.isFile))
    (edn/read-string (slurp preferences-file-path))
    good-defaults))

(def preferences
  (atom {}))
(defn set-preferences
  "Store to atom and persist to disk"
  [prefs]
  (reset! preferences prefs)
  (spit
    preferences-file-path
    (pr-str prefs)))

(def current-user (atom {:id nil :name nil})) 
(defn set-current-user [id name]
  (reset! current-user {:id id :name name}))

(def master-updates
  "The 'Model' that holds all updates to be rendered in view"
  (atom []))
(defn set-master-updates [new-updates-list]
  (reset! master-updates new-updates-list))

(def default-days-ago (time-core/minus
                        (time-core/now)
                        (time-core/days 2)))
(def last-update-ts (atom default-days-ago))
(defn set-last-update-ts [ts]
  (reset! last-update-ts ts))

(def fetching-updates (atom false))
(defn set-fetching-updates [busy?]
  (reset! fetching-updates busy?))

; Presume no connectivity until tested
(def is-connectivity? (atom false))
(defn set-is-connectivity? [state]
  (reset! is-connectivity? state))

(defn ws-do
  "Invoke webservice. f takes one arg being a map containing connection url and token"
  [ws f]
  (f ws))

(defn check-connectivity []
  (and
    (get-in @preferences [:url])
    (get-in @preferences [:api-token])
    (ws-do @preferences (fn [ws]
                          (api/http-any-response?
                            (get-in ws [:url])
                            6000)))
    (ws-do @preferences (fn [ws]
                          (api/valid-token?
                            (get-in ws [:url])
                            (get-in ws [:api-token]))))))

(defn open-options-dialog [e options]
  (-> (dialog
        :content (mig-panel
                   :items [
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold
                                    :size 24)
                        :text "Settings")
                      "wrap"]
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold)
                        :text "Redmine URL")
                      "gaptop 5, wrap"]
                     [(text :columns 20
                            :id :url
                            :text (-> options :url))
                      "wrap"]
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold)
                        :text "Redmine API Key")
                      "gaptop 5, wrap"]
                     [(text :columns 20
                            :id :api-token
                            :text (-> options :api-token))
                      "wrap"]
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold)
                        :text "Show me updates for tickets where ...")
                      "gaptop 5, wrap"]
                     [(checkbox :text "I'm the assignee"
                                :id :im-the-assignee
                                :selected? (-> options :filter-options (in? :im-the-assignee)))
                      "wrap"]
                     [(checkbox :text "I'm a watcher"
                                :id :im-a-watcher
                                :selected? (-> options :filter-options (in? :im-a-watcher)))
                      "wrap"]
                     [(checkbox :text "I'm the author"
                                :id :im-the-author
                                :selected? (-> options :filter-options (in? :im-the-author)))
                      "wrap"]
                     [(checkbox :text "The ticket is related to one of my assigned tickets"
                                :id :related-ticket
                                :selected? (-> options :filter-options (in? :related-ticket)))
                      "wrap"]
                     [(checkbox :text "I've participated in the ticket updates"
                                :id :participated-in-updates
                                :selected? (-> options :filter-options (in? :participated-in-updates)))
                      "wrap"]
                    ; [(checkbox :text "I'm mentioned in the ticket or in an update to the ticket"
                    ;            :id :mentioned-in-updates
                    ;            :selected? (-> options :filter-options (in? :mentioned-in-updates)))
                    ;  "wrap"]
                     ])
         :parent (to-widget e)
         :option-type :ok-cancel
         :success-fn (fn [p]
                       {:api-token (config
                                           (select
                                             (to-frame p)
                                             [:#api-token])
                                           :text)
                        :url (config
                                       (select
                                         (to-frame p)
                                         [:#url])
                                         :text)
                        :filter-options [
                                         (if (selection (select
                                                           (to-frame p)
                                                             [:#im-a-watcher]))
                                           :im-a-watcher)
                                         (if (selection (select
                                                           (to-frame p)
                                                             [:#im-the-assignee]))
                                           :im-the-assignee)
                                         (if (selection (select
                                                           (to-frame p)
                                                             [:#im-the-author]))
                                           :im-the-author)
                                         (if (selection (select
                                                           (to-frame p)
                                                             [:#related-ticket]))
                                           :related-ticket)
                                         (if (selection (select
                                                           (to-frame p)
                                                             [:#participated-in-updates]))
                                           :participated-in-updates)
                                       ;  (if (selection (select
                                       ;                    (to-frame p)
                                       ;                      [:#mentioned-in-updates]))
                                       ;    :mentioned-in-updates)
                                         ]})
         :cancel-fn (fn [p] nil))
    pack! show!))

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
                                  :text "<html><u>Check now</u>&nbsp;&nbsp;<html>"
                                  :id :check-now
                                  :cursor :hand)
                                (busy-label
                                  :text ""
                                  :visible? false
                                  :id :fetching-indicator
                                  :busy? true)])

                :east (action :name ""
                              :icon (io/resource
                                      "gear.png")
                              :handler (fn [e]
                                         (if-let [options (open-options-dialog
                                                            e
                                                            @preferences)]
                                           (let [prefs-changed? (not (= @preferences options))]
                                             ; write out preferences to file and
                                             ; global ref
                                             (set-preferences options)
                                             (if prefs-changed?
                                               (set-last-update-ts default-days-ago))
                                             ; recheck connectivity and do query if connected
                                             (if (not @is-connectivity?)
                                               (set-is-connectivity?
                                                 (check-connectivity)))
                                             (if @is-connectivity?
                                               (set-update-items-list-ui
                                                 @last-update-ts)))))))))

(defn contains-every? [m keyseqs]
  (let [not-found (Object.)]
    (not-any? #{not-found}
              (for [ks keyseqs]
                (get-in m ks not-found)))))

(defn format-time-ago
  "Return formatted string indicating the time delta between
   now in utc and from-time"
  [from-time]
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

;predicates for filtering updates
(defn is-assignee?
  "Given a user id and NoteUpdate, Status update etc record,
   determine if user-id is the assignee"
  [user-id update-record]
  (= user-id (:assignee-id update-record)))

(defn is-author? [user-id update-record]
  (= user-id (:ticket-author-id update-record)))

(defn is-related-ticket?
  "Determine if this ticket is related or family related to
   a ticket assigned to me or authored by me"
  [user-id update-record]
  (let [related-tickets (-> update-record :relations)]
    (not (empty? (reduce
                   (fn [accum val]
                     (let [maybe-related-issue (ws-do
                                                 @preferences
                                                 (fn [ws]
                                                   (api/issue
                                                     (-> ws :url)
                                                     (-> ws :api-token)
                                                     (-> val :issue_id))))
                           converted-maybe-update (convert-update maybe-related-issue)]
                       (if
                         (and
                           (is-assignee? user-id converted-maybe-update)
                           (is-author? user-id converted-maybe-update))
                         (conj accum
                           {:related? true
                            :reason (:relation_type val)
                            :id (:issue_id val)})
                         accum)))
                   []
                   related-tickets)))))

(defn is-a-watcher? [user-id update-record]
  (not (nil? (first
               (filter
                 (fn [item]
                   (= (-> item :id) user-id))
                 (:watchers update-record))))))

(defn is-a-update-participant?
  "Determine if user authored any updates to ticket"
  [user-id update-record]
  (first
    (filter
      (fn [item]
        (= (-> item :user :id) user-id))
      (:journals update-record))))

(defn is-mentioned-in-ticket-or-update?
  "Determine if user was mentioned by any other user in any updates to ticket.
   Requires user info like first and last name and email to search for ident-options strings
   like @darmstrong, or darmstrong or Danny Armstrong or Danny, if configured"
  [ident-options update-record]
  (or (reduce
        (fn [accum item]
          (and
            accum
            (not (nil?
                   (re-find (re-pattern
                              (str "(?i)" item))
                     (-> update-record :description))))))
        false
        ident-options)
      (not (nil? (first
                   (filter
                     (fn [item]
                       (reduce
                         (fn [accum elem]
                           (re-find (-> item :notes) elem)))
                         ident-options)
                     (:journals update-record)))))))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn tag-updates
  "For each update in update-list, run preds (resolve keyword
   to real func first) and collect results."
  [user-id update-list pred-syms]
  (mapv
    (fn [update]
      [update (cond
                (and
                  (in? pred-syms :im-the-author)
                  (is-author? user-id update))
                '(:im-the-author)
                (and
                  (in? pred-syms :im-the-assignee)
                  (is-assignee? user-id update))
                '(:im-the-assignee)
                (and
                  (in? pred-syms :im-a-watcher)
                  (is-a-watcher? user-id update))
                '(:im-a-watcher)
                (and
                  (in? pred-syms :participated-in-updates)
                  (is-a-update-participant? user-id update))
                '(:participated-in-updates)
               ; (and
               ;   (in? pred-syms :is-mentioned-in-ticket-or-update)
               ;   (is-mentioned-in-ticket-or-update? user-id update))
               ; '(:is-mentioned-in-ticket-or-update)
                (and
                  (in? pred-syms :related-ticket)
                  (is-related-ticket? user-id update))
                '(:related-ticket)
                :else '())])
    update-list))

(defrecord NoteUpdate [id
                       assignee-id
                       ticket-author-id
                       update-author
                       relations
                       subject
                       watchers
                       update-text
                       update-uri
                       update-uri-label
                       updated-at])
(defrecord StatusUpdate [id
                         assignee-id
                         ticket-author-id
                         update-author
                         relations
                         subject
                         watchers
                         old-status
                         new-status
                         new-status-label
                         update-uri
                         update-uri-label
                         updated-at])
(defrecord NoteAndStatusUpdate [id
                                assignee-id
                                ticket-author-id
                                update-author
                                relations
                                subject
                                watchers
                                update-text
                                old-status
                                new-status
                                update-uri
                                update-uri-label
                                updated-at])

(defn convert-update
  "Given an issue update determine if it's a NoteUpdate
   or a StatusUpdate or both or something else and return it"
  [issue]
  (let [issue-id (-> issue :id)
        subject (-> issue :subject)
        assignee-id (-> issue :assigned_to :id)
        ticket-author-id (-> issue :author :id)
        watchers (-> issue :watchers)
        update-rank (count (-> issue :journals))
        update-uri (str "http://redmine.visiontree.com/issues/"
                        issue-id
                        "#note-"
                        update-rank)
        update-uri-label (str "#" update-rank)
        updated-at (-> issue :updated_on)
        relations (-> issue :relations)]
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
                              assignee-id
                              ticket-author-id
                              update-author
                              relations
                              subject
                              watchers
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
            new-status-label (ws-do
                               @preferences
                               (fn [ws]
                                 (api/get-issue-status-name-by-id
                                   (get-in ws [:url])
                                   (get-in ws [:api-token])
                                   new-status)))]
        (StatusUpdate. issue-id
                       assignee-id
                       ticket-author-id
                       update-author
                       relations
                       subject
                       watchers
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
                     assignee-id
                     ticket-author-id
                     update-author
                     relations
                     subject
                     watchers
                     update-text
                     update-uri
                     update-uri-label
                     updated-at))
      ; check here for brand new instance - no :journals
      ; suggests making new type of update like NewIssueUpdate?
      ;or just filter out the nils?
      )))

(defn make-label-text [author updated-at]
  (str (first
      (clojure.string/split author #"\s"))
    ", "
    (format-time-ago
      updated-at)))
  
(defn build-update-row [[record tags]]
  (mig-panel 
    :border [(empty-border :thickness 0)]
    :background (color "white")
    :constraints ["ins 10", "[][grow][]", "[top]"]
    :items [
      [(label :text (str "#"
                         (:id record)
                         " "
                         (:subject record))
              :font "ARIAL-BOLD-14")
       "span 2, growx, w 240:400:700"]
      ; Hack to get the text to set. :text on hyperlink did not work
      [(config! (hyperlink
                  :uri (:update-uri record)
                  :tip "Open in browser")
                :text (:update-uri-label record))
       "wrap"]
      [(let [parsed-updated-at (time-format/parse (:updated-at record))
             initial-delay (- 60 (time-core/second parsed-updated-at))
             l (label
                 :text (make-label-text
                         (:update-author record)
                         parsed-updated-at)
                 :tip (str "Updated at "
                           (time-format/unparse 
                             (time-format/formatter-local
                               "MM/dd/yyyy hh:mm:ssa")
                             (time-local/to-local-date-time
                               (:updated-at record)))
                           " by "
                           (:update-author record)
                           " "
                           tags))
             t (seesaw.timer/timer (fn [_]
                                     (config!
                                       l
                                       :text
                                       (make-label-text
                                         (:update-author record)
                                         parsed-updated-at))
                                     -1)
                                     :delay 60000
                                     :initial-delay initial-delay)]
         l)]
      [(text :text (str (cond
                          (or (instance? NoteUpdate record)
                              (instance? NoteAndStatusUpdate record))
                          (:update-text record)
                          (or (instance? NoteAndStatusUpdate record)
                              (instance? StatusUpdate record))
                          (str (if (instance? NoteAndStatusUpdate record)
                                 "\n")
                               "Status set to: "
                               (:new-status-label record))))
        :multi-line? true
        :editable? false
        :wrap-lines? true
        :background (color "#eeeeee")
        :margin 5)
      "span 2 2, gap 8, growx, growy, w 240:400:700"]]))

(defn get-issue-updates
  "Iterate issue updates and convert to intermediate representation
   that uses defrecord"
  [from-ts]
  (filterv
    #(not (nil? %))
    (mapv
      #(convert-update
         (ws-do
           @preferences
           (fn [ws]
             (api/issue
               (get-in ws [:url])
               (get-in ws [:api-token])
               (get-in % [:id])))))
      (ws-do
        @preferences
        (fn [ws]
          (api/get-updated-issues
            (get-in ws [:url])
            (get-in ws [:api-token])
            from-ts))))))

(defn merge-updates
  "Take old-list of updates and merge new-list by
   first removing duplicate issue id items in old list
   and prepending new-list"
  [old-list new-list] 
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
    :minimum-size [500 :by 500]))

(defn is-tagged-item?
  [item]
  (not (empty? (second item))))

(defn set-update-items-list-ui [from-date]
  (set-last-update-ts (time-core/now))
  (set-fetching-updates true)
  (future
    (let [issue-updates (get-issue-updates from-date)
          merged-items (merge-updates
                         ; Dispose of previous tagging - this is useful
                         ; if the user unchecks an update type
                         (mapv
                           first
                           @master-updates)
                         issue-updates)
          tagged-items (tag-updates
                         (:id @current-user)
                         merged-items
                         (-> @preferences :filter-options))
          filtered-items (filterv
                           is-tagged-item?
                           tagged-items)
          built-items (mapv
                        build-update-row
                        filtered-items)]
      (set-master-updates filtered-items)
      (invoke-later
        (seesaw.core/config!
          (seesaw.core/select
            watchlist-frame
            [:#updates-panel])
          :items built-items))
      (set-fetching-updates false))))

(defn query-and-set-current-user []
  (let [u (ws-do @preferences
                 (fn [ws]
                   (api/current-user (get-in ws [:url])
                                     (get-in ws [:api-token]))))]
    (set-current-user
      (get-in u [:user :id])
      (str (get-in u [:user :firstname])
           " "
           (get-in u [:user :lastname])))))

(defn start-app []
  (native!)
  (set-preferences (load-preferences))
  (add-watch
    is-connectivity?
    :connectivity-watch
    (fn [k r old-state new-state]
      (when new-state
        (query-and-set-current-user))))
  (set-is-connectivity?
    (check-connectivity))
  (-> watchlist-frame pack! show!)
  (config! watchlist-frame :content (frame-content))
  (bind/bind
    fetching-updates
    (bind/property
      (select watchlist-frame [:#fetching-indicator]) :visible?))
  (listen (select watchlist-frame [:#check-now])
    :mouse-clicked (fn [evt-source]
                     (if @is-connectivity?
                       (do
                         (set-update-items-list-ui @last-update-ts)
                         (scroll!
                           (select
                             watchlist-frame
                             [:#updates-panel])
                           :to :top))
                       (alert "No connectivity")))))