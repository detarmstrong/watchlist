(ns watchlist.t-core
  (:require [clj-time.core :as time-core]
            [clj-time.format :as time-format]
            [clojure.edn :as edn])
  (:use midje.sweet)
  (:use [watchlist.core]))

(def status-and-note-update-issue-ex
  {
   :status {:id 18, :name "QA Passed"},
   :subject
     "Sites should be able to manage their contact information",
   :created_on "2014-08-20T01:52:49Z",
   :author {:id 5, :name "Danny Armstrong"},
   :assigned_to {:id 5, :name "Danny Armstrong"},
   :watchers [],
   :updated_on "2014-08-21T21:48:35Z",
   :start_date "2014-08-19",
   :journals
     [{:id 36809,
       :user {:id 5, :name "Danny Armstrong"},
       :notes
       "Spec addition for Phase 2",
       :created_on "2014-08-21T17:51:38Z",
       :details []}
      {:id 36815,
       :user {:id 63, :name "Kyle Whalen"},
       :notes "Login card updated successfully. ",
       :created_on "2014-08-21T21:48:35Z",
       :details
       [{:property "attr",
         :name "status_id",
         :old_value "13",
         :new_value "18"}]}],
   :tracker {:id 4, :name "Task"},
   :relations
     [{:id 806,
     :issue_id 8329,
     :issue_to_id 8475,
     :relation_type "relates",
     :delay nil}],
   :id 8475,
   :description
     "Site and company contact information is presented",
   :priority {:id 5, :name "Normal"},
   :spent_hours 0.0,
   :project {:id 1, :name "VTOC "}})

(def note-update-issue-ex
  {
   :status {:id 18, :name "QA Passed"},
   :subject
     "Sites should be able to manage their contact information",
   :created_on "2014-08-20T01:52:49Z",
   :author {:id 5, :name "Danny Armstrong"},
   :assigned_to {:id 5, :name "Danny Armstrong"},
   :watchers [],
   :updated_on "2014-08-21T21:48:35Z",
   :start_date "2014-08-19",
   :journals
     [{:id 36809,
       :user {:id 5, :name "Danny Armstrong"},
       :notes
       "Spec addition for Phase 2",
       :created_on "2014-08-21T17:51:38Z",
       :details []}
      {:id 36815,
       :user {:id 63, :name "Kyle Whalen"},
       :notes "Login card updated successfully. ",
       :created_on "2014-08-21T21:48:35Z",
       :details
       []}],
   :tracker {:id 4, :name "Task"},
   :relations
     [{:id 806,
     :issue_id 8329,
     :issue_to_id 8475,
     :relation_type "relates",
     :delay nil}],
   :id 8475,
   :description
     "Site and company contact information is presented",
   :priority {:id 5, :name "Normal"},
   :spent_hours 0.0,
   :project {:id 1, :name "VTOC "}})

(def related-ticket-ex
  {
   :status {:id 18, :name "QA Passed"},
   :subject
     "Limo lambdo",
   :created_on "2014-08-20T01:52:49Z",
   :author {:id 5, :name "Danny Armstrong"},
   :assigned_to {:id 5, :name "Danny Armstrong"},
   :watchers [],
   :updated_on "2014-08-21T21:48:35Z",
   :start_date "2014-08-19",
   :journals
     [{:id 36815,
       :user {:id 63, :name "Kyle Whalen"},
       :created_on "2014-08-21T21:48:35Z",
   ; note that notes is empty if it's just a status update
       :notes ""
       :details
       [{:property "attr",
         :name "status_id",
         :old_value "13",
         :new_value "18"}]}],
   :tracker {:id 4, :name "Task"},
   :relations
     [{:id 806,
     :issue_id 8329,
     :issue_to_id 8475,
     :relation_type "relates",
     :delay nil}],
   :id 8329,
   :description
     "Site and company contact information is good.",
   :priority {:id 5, :name "Normal"},
   :spent_hours 0.0,
   :project {:id 1, :name "VTOC "}})

(def status-update-issue-ex
  {
   :status {:id 18, :name "QA Passed"},
   :subject
     "Test related ticket",
   :created_on "2014-08-20T01:52:49Z",
   :author {:id 5, :name "Danny Armstrong"},
   :assigned_to {:id 5, :name "Danny Armstrong"},
   :watchers [],
   :updated_on "2014-08-21T21:48:35Z",
   :start_date "2014-08-19",
   :journals
     [{:id 36815,
       :user {:id 63, :name "Kyle Whalen"},
       :created_on "2014-08-21T21:48:35Z",
   ; note that notes is empty if it's just a status update
       :notes ""
       :details
       [{:property "attr",
         :name "status_id",
         :old_value "13",
         :new_value "18"}]}],
   :tracker {:id 4, :name "Task"},
   :relations
     [{:id 806,
     :issue_id 8329,
     :issue_to_id 8475,
     :relation_type "relates",
     :delay nil}],
   :id 8475,
   :description
     "Test related ticket",
   :priority {:id 5, :name "Normal"},
   :spent_hours 0.0,
   :project {:id 1, :name "VTOC "}})

(facts "about convert-update"
  (let [updated-at (time-format/parse "2014-08-21T21:48:35Z")]
    (fact "it converts an update to a Note update"
      (convert-update note-update-issue-ex)
      =>
      ;TODO add ticket author here
      (just (->NoteUpdate 8475
                          5
                          5
                          "Kyle Whalen"
                          [{:delay nil, :id 806, :issue_id 8329, :issue_to_id 8475, :relation_type "relates"}]
                          "Sites should be able to manage their contact information"
                          "Login card updated successfully. "
                          "http://redmine.visiontree.com/issues/8475#note-2"
                          "#2"
                          "2014-08-21T21:48:35Z")))
       
    (fact "it converts an update to a status change"
      (convert-update status-update-issue-ex)
      =>
      (just (->StatusUpdate 8475
                            5
                            5
                            "Kyle Whalen"
                            [{:delay nil, :id 806, :issue_id 8329, :issue_to_id 8475, :relation_type "relates"}]
                            "Test related ticket"
                            "13"
                            "18"
                            "QA Passed"
                            "http://redmine.visiontree.com/issues/8475#note-1"
                            "#1"
                            "2014-08-21T21:48:35Z"))
      (provided (watchlist.web-api/get-issue-status-name-by-id "18") => "QA Passed"))

    (fact "it converts an update to a hybrid status
         update and a note update"
      (convert-update status-and-note-update-issue-ex)
      =>
      (just (->NoteAndStatusUpdate 8475
                                   5
                                   5
                                   "Kyle Whalen"
                                   [{:delay nil, :id 806, :issue_id 8329, :issue_to_id 8475, :relation_type "relates"}] 
                                   "Sites should be able to manage their contact information"
                                   "Login card updated successfully. "
                                   "13"
                                   "18"
                                   "http://redmine.visiontree.com/issues/8475#note-2"
                                   "#2"
                                   "2014-08-21T21:48:35Z")))))

(facts "about contains-every?"
       (fact "it returns true if each path is found. Value can be nil."
             (contains-every? (last 
                                (get-in status-update-issue-ex
                                        [:relations]))
                              [[:id] [:delay]])
             =>
             true)
       (fact "it returns false if path not found. Value can be nil."
             (contains-every? (last 
                                (get-in status-update-issue-ex
                                        [:relations]))
                              [[:id] [:delNOTHERE]])
             =>
             false))

(facts "about format-time-ago"
       (fact "intervals a year ago or greater show 14y"
             (format-time-ago (time-core/date-time 2000 1 1))
             =>
             "14y")
       (fact "intervals a day ago show 2d"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/days 2)))
             =>
             "2d")
       (fact "intervals a hour ago show 2h"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/hours 2)))
             =>
             "2h")
       (fact "intervals less than a hour ago show 15m"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/minutes 15)))
             =>
             "15m")
       (fact "intervals less than a minute ago show 30s"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/seconds 30)))
             =>
             "30s"))

(facts "about merge-updates"
  (fact "it places newer updates by id at the head. assumes items already 
         descendingly sorted"
    (merge-updates [{:id 12 :v "no one updates me:(" :ts 1201} 
                    {:id 14 :v "been there" :ts 1200}]
                   [{:id 14 :v "been there again" :ts 1203}
                    {:id 15 :v "new here" :ts 1202}])
    =>
    [{:id 14 :v "been there again" :ts 1203}
     {:id 15 :v "new here" :ts 1202}
     {:id 12 :v "no one updates me:(" :ts 1201}])
  (fact "if one arg is an empty vector we're all good"
    (merge-updates []
                   [{:id 14 :v "been there again" :ts 1203}
                    {:id 15 :v "new here" :ts 1202}])
    =>
    [{:id 14 :v "been there again" :ts 1203}
     {:id 15 :v "new here" :ts 1202}]))

(facts "about last-update-ts"
  (fact "it's a clj-time instance"
    (type @last-update-ts)
    =>
    (type (time-core/now))))

(def static-issue-updates
  "Represent the list of issues as returned by redmine api.
   DOES NOT include details like :journal or :watcher"
  (edn/read-string (slurp "dev-resources/get-updated-issues.clj")))

(facts "about predicates for tagging updates by user criteria"
 (fact "is-assignee? will return true if the arg is the assignee"
   (is-assignee?
     5
     (convert-update status-and-note-update-issue-ex))
   =>
   true)
 (fact "is-author? will return true if the arg is the author"
   (is-author?
     5
     (convert-update status-and-note-update-issue-ex))
   =>
   true)
 (fact "is-related-ticket? returns true if a ticket is related to this one"
   (is-related-ticket?
     5
     (convert-update status-and-note-update-issue-ex))
   =>
   (contains {:related? true
              :reason "relates"
              :id 8329})
   (provided
     (watchlist.web-api/issue 8329)
     =>
     related-ticket-ex)
   (provided
     (watchlist.web-api/get-issue-status-name-by-id
       "18") => "QA Passed")))

(facts "about update filter pipeline"
  (fact "each update is scrutinized for inclusion - is-author? and is-assignee?"
    (second
      (first
        (tag-updates
          5
          (map
            convert-update
            [status-and-note-update-issue-ex])
          '(:is-author? :is-assignee?))))
    =>
    (contains [:is-author? :is-assignee?] :in-any-order)))
    ;(provided
    ;  (watchlist.web-api/get-issue-status-name-by-id
    ;    "18") => "QA Passed")))