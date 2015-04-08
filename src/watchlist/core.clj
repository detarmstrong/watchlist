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
  (:import (java.awt Desktop)
           (com.bulenkov.iconloader IconLoader)))

(declare convert-update)
(declare set-update-items-list-ui)
(declare load-preferences)
(declare in?)
(declare is-a-watcher?)
(declare is-assignee?)
(declare is-author?)
(declare is-related-ticket?)
(declare is-a-update-participant?)

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
(defn get-preferences[]
  @preferences)

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
                        (time-core/days 3)))
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
  "Invoke webservice. f takes one arg being a map containing
   connection url and token"
  [ws f]
  (f ws))

(defn check-connectivity []
  (and
    (get-in @preferences [:url])
    (get-in @preferences [:api-token])
    (ws-do @preferences (fn [ws]
                          (api/http-any-response?
                            (get-in ws [:url])
                            12000)))
    (ws-do @preferences (fn [ws]
                          (api/valid-token?
                            (get-in ws [:url])
                            (get-in ws [:api-token]))))))

(defn pref-selected?
  "In the disk persisted pref data, is arg selected?"
  [needle prefs]
  (some
    #(= needle (first %))
    prefs))

(defn open-options-dialog [e options]
  (-> (dialog
        :id :settings-dialog
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
                     [(text :columns 26
                            :id :url
                            :text (-> options :url))
                      "wrap"]
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold)
                        :text "Redmine API Key")
                      "gaptop 5, wrap"]
                     [(text :columns 26
                            :id :api-token
                            :text (-> options :api-token))
                      "wrap"]
                     [(label
                        :font (font :from (default-font "Label.font")
                                    :style :bold)
                        :text "Show me updates for tickets where ...")
                      "gaptop 5, wrap"]
                     [(checkbox :text "I'm the assignee"
                                :id :is-assignee?
                                :selected? (pref-selected?
                                             :is-assignee?
                                             (-> options :filter-options)))
                      "wrap"]
                     [(checkbox :text "I'm a watcher"
                                :id :is-a-watcher?
                                :selected? (pref-selected?
                                             :is-a-watcher?
                                             (-> options :filter-options)))
                      "wrap"]
                     [(checkbox :text "I'm the author"
                                :id :is-author?
                                :selected? (pref-selected?
                                             :is-author?
                                             (-> options :filter-options)))
                      "wrap"]
                     [(checkbox :text "The ticket is related to one of my assigned tickets"
                                :id :is-related-ticket?
                                :selected? (pref-selected?
                                             :is-related-ticket?
                                             (-> options :filter-options))
                                :tip "For example, the ticket is blocked by or precedes one of my tickets")
                      "wrap"]
                     [(checkbox :text "I've participated in the ticket updates"
                                :id :is-a-update-participant?
                                :selected? (pref-selected?
                                             :is-a-update-participant?
                                             (-> options :filter-options)))
                      "wrap"]
                     [(checkbox :text "The project name contains (comma separated):"
                                  :id :is-project-substring?
                                  :selected? (pref-selected?
                                               :is-project-substring?
                                               (-> options :filter-options)))
                      "wrap"]
                     [(text :columns 22
                             :id :project-substring-list
                             :text (-> (filter
                                         #(= (first %) :is-project-substring?)
                                         (:filter-options options))
                                     first
                                     second))
                      "gapleft 24, wrap"]
                     ["<html><br/><i>WatchList will get the last <b>3</b> days of updates</i></html>"
                      "wrap"]
                     ])
         :parent (to-root e)
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
                        :filter-options (filterv
                                          identity
                                          [
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-a-watcher?]))
                                            [:is-a-watcher? (:id @current-user)])
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-assignee?]))
                                            [:is-assignee? (:id @current-user)])
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-author?]))
                                            [:is-author? (:id @current-user)])
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-related-ticket?]))
                                            [:is-related-ticket? (:id @current-user)])
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-a-update-participant?]))
                                            [:is-a-update-participant? (:id @current-user)])
                                          (if (selection (select
                                                           (to-frame p)
                                                           [:#is-project-substring?]))
                                            [:is-project-substring? (config
                                                                      (select
                                                                        (to-frame p)
                                                                        [:#project-substring-list])
                                                                      :text)])])})
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

                :east (action 
                        :name ""
                        :icon (icon (IconLoader/findIcon (.getResource
                                                           (.getContextClassLoader
                                                             (Thread/currentThread))
                                                           "gear.png")))
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
  (str
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
        :else (str delta-seconds "s")))
    " ago"))

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
                           converted-maybe-update (convert-update
                                                    maybe-related-issue)]
                       (if
                         (or
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
   Requires user info like first and last name and email to search for
   ident-options strings
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

(defn is-project?
  "Does update-record have a project found in project-ids? Works against
  the intermediate record, not the raw data returned from web service"
  [project-ids update-record]
  (in? project-ids (-> update-record :project :id)))

(defn is-project-substring?
  "Does update-record have a project name matching any of the user
  test strings provided? Works against the intermediate record returned
  from convert-update, not the raw data returned from web service"
  [project-substrings update-record]
  (let [substrings (clojure.string/split project-substrings #",")
        update-project-name (-> update-record :project :name)]
    (not
      (nil?
        (some
           (fn [candidate]
             (re-find (re-pattern candidate) update-project-name))
           substrings)))))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))

(defn convert-update
  "Convert an update from it's original redmine model to one watchlist expects.
   Really just adding a few fields, resolving status id to human readable, etc."
  [issue]
  (let [issue-id (-> issue :id)
        update-rank (count (-> issue :journals))
        last-journal-entry (-> issue :journals (last))
        is-note-update? (and (contains? last-journal-entry :notes) 
                             (not (= "" (-> last-journal-entry :notes))))
        is-status-update? (some
                            (fn [prop-update]
                              (= (:name prop-update) "status_id"))
                            (:details last-journal-entry))
        is-description-update? (some
                                  (fn [prop-update]
                                    (= (:name prop-update) "description"))
                                  (:details last-journal-entry))]
    (if (or is-note-update? is-status-update? is-description-update?)
      {:id issue-id
       :subject (-> issue :subject)
       :assignee-id (-> issue :assigned_to :id)
       :ticket-author-id (-> issue :author :id)
       :watchers (-> issue :watchers)
       :update-rank (count (-> issue :journals))
       :update-uri (str (:url (get-preferences))
                     "/issues/"
                     issue-id
                     "#note-"
                     update-rank)
       :update-uri-label (str "#" update-rank)
       :updated-at (-> issue :updated_on)
       :relations (-> issue :relations)
       :update-author (ws-do
                        (get-preferences)
                        (fn [ws]
                          (api/resolve-formatted-name
                            (get-in ws [:url])
                            (get-in ws [:api-token])
                            (-> last-journal-entry :user :id))))
       :update-author-email (:mail
                               (ws-do
                                  (get-preferences)
                                  (fn [ws]
                                    (api/get-user-by-id
                                      (get-in ws [:url])
                                      (get-in ws [:api-token])
                                      (-> last-journal-entry :user :id)))))
       :project (-> issue :project)
       :update-text (-> last-journal-entry :notes)
       :status-update (map
                        (fn [detail]
                          (assoc
                            detail
                            :new_value 
                            (ws-do
                              @preferences
                              (fn [ws]
                                (api/get-issue-status-name-by-id
                                  (get-in ws [:url])
                                  (get-in ws [:api-token])
                                  (:new_value detail))))))
                        (filter
                          (fn [prop-update]
                            (= (:name prop-update) "status_id"))
                          (:details last-journal-entry)))
       :description-update (filter
                             (fn [prop-update]
                               (= (:name prop-update) "description"))
                             (:details last-journal-entry))}
      ; nil will be filtered out implicitly
      nil)))

(defn make-label-text [author updated-at]
  (str (first
         (clojure.string/split author #"\s"))
       ", "
       (format-time-ago
         updated-at)))

(defn rounded-edges-painter [c g]
  (let [w (.getWidth c)
        h (.getHeight c)]
    (doto g
      (seesaw.graphics/draw
       (seesaw.graphics/rounded-rect -5 -5 (+ w 10) (+ h 10) 20)
       (seesaw.graphics/style 
         :stroke 10
         :foreground (seesaw.color/color 255 255 255 255)
         :background (seesaw.color/color 0 0 0 0))))))

(defn build-update-row [[record tags]]
  (mig-panel
    :border [(empty-border :thickness 0)]
    :background (color "white")
    :constraints ["ins 0", "10[][]0[grow]10", "10[top]0[]8"]
    :items [
      [(label
         :icon (str 
                 "https://secure.gravatar.com/avatar/"
                 ; TODO check for empty and nil emails
                 ; show some gravatar placeholder?
                 (->
                   (:update-author-email record)
                   (.toLowerCase)
                   (trim)
                   (string.string/md5hex))
                 "?rating=PG&size=40&d=retro")
         :paint rounded-edges-painter)
       "span 1 2, w 40:40:40"]
      [(vertical-panel
         :border (empty-border :top 0)
         :id :updates-panel
         :background (color "white")
         :items [(label
                   :text (:update-author record)
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
                 (let [parsed-updated-at (time-format/parse
                                           (:updated-at record))
                       initial-delay (- 60 (time-core/second
                                             parsed-updated-at))
                       l (label
                           :text (format-time-ago parsed-updated-at)
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
                                                 (format-time-ago
                                                   parsed-updated-at))
                                               -1)
                                               :delay 60000
                                               :initial-delay initial-delay)]
                   l)])
       "span 1 2, w 40:54:100"]
      [(label
         :text (str "#"
                 (:id record)
                 " "
                 (:subject record))
         :h-text-position :right
         :font "HELVETICA-BOLD-14")
       "gapleft 10, gapbottom 0, gaptop 2, growx, w 70:90, wrap"]
      [(vertical-panel
         :border (empty-border :top 4)
         :id :updates-panel
         :background (color "white")
         :items [(text
                   :text (reduce (fn [state update]
                                   (str state
                                     (condp = (:name update)
                                       "status_id" (str "Status set to: "
                                                        (:new_value update))
                                       "description" "Description updated"
                                       "Property updated")))
                                 ""
                                 (into (:description-update record)
                                       (:status-update record)))
                    :visible? (if (some #(and (not (nil? %)) (not (= "" %)))
                                        (into (:description-update record)
                                              (:status-update record)))
                                true
                                false)
                    :multi-line? true
                    :editable? false
                    :wrap-lines? true
                    :background (color "#f9f9f9")
                    :margin 4)
                  [:fill-v 2]
                  (text
                    :text (:update-text record)
                    :visible? (if (and (not (nil? (:update-text record)))
                                       (not (= (:update-text record) "")))
                                true
                                false)
                    :multi-line? true
                    :editable? false
                    :wrap-lines? true
                    :background (color "#f9f9f9")
                    :margin 4)])
          "gapleft 10, gaptop 0, growx, w 100:200:700, hidemode 1"]
      ]))

(defn get-issue-updates
  "Iterate issue updates and convert to intermediate representation"
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
    :minimum-size [400 :by 500]
    :icon "https://raw.githubusercontent.com/detarmstrong/watchlist/master/resources/gear%402x.png"))

(defn tag-updates
  "For each update in update-list, run preds and collect results."
  [update-list preds]
  (let [some-preds (apply some-fn preds)]
    (mapv
      (fn [update]
        ; The or here is required because some-fn may return nil if 4
        ; or more preds are given to it and they all fail evaluation
        [update (or (some-preds update)
                    false)])
      update-list)))

(defn is-tagged-item?
  [item]
  (not (false? (second item))))

(defn build-preds-from-filter-options
  "filter-options are stored with
  the func itself and it's arguments after it in a vec. This returns
  a partial with those args applied."
  [filter-options]
  (reduce
    (fn [accum filter-opt]
      (if filter-opt
        (conj
          accum
          (fn [update]
            (if
              ((partial
                 (ns-resolve
                   'watchlist.core
                   (-> (first filter-opt) name symbol))
                 (second filter-opt)
                 update))
              (first filter-opt)
              false)))))
    []
    filter-options))

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
                         merged-items
                         (build-preds-from-filter-options
                           (-> @preferences :filter-options)))
          filtered-items (filterv
                           is-tagged-item?
                           tagged-items)
          built-items (mapv
                        build-update-row
                        filtered-items)]
      (set-master-updates filtered-items)
      (invoke-later
        (config!
          (select
            watchlist-frame
            [:#updates-panel])
          :items built-items))
      ; terrible hack to get the damn ui to correct itself
      ; after reloading the items (large whitespace between rows)
      (seesaw.timer/timer
        (fn [e]
          (-> (select watchlist-frame [:#updates-panel]) (.revalidate)))
        :initial-delay 10
        :repeats? false)
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
                       (alert "No connectivity.\nClick the gear to set up Watchlist")))))

(defn -main
  [& args]
  ; should I use invoke-later here?
  (start-app))
