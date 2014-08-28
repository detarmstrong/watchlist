(ns watchlist.t-core
  (:require [clj-time.core :as time-core]
            [clj-time.format :as time-format])
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

(def status-update-issue-ex
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
     [{:id 36815,
       :user {:id 63, :name "Kyle Whalen"},
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
     "Site and company contact information is good.",
   :priority {:id 5, :name "Normal"},
   :spent_hours 0.0,
   :project {:id 1, :name "VTOC "}})

(facts "about determine-update-type"
  (fact "it determines an update is just a note"
    (determine-update note-update-issue-ex)
    =>
    (just (->NoteUpdate "Danny Armstrong"
                        "Sites should be able to manage their contact information"
                        "Login card updated successfully. "
                        "http://redmine.visiontree.com/issues/8475#2"
                        "#2"
                        "2014-08-21T21:48:35Z")))
       
  (fact "it determines an update is just a status change"
    (determine-update status-update-issue-ex)
    =>
    (just (->StatusUpdate "Danny Armstrong"
                          "Sites should be able to manage their contact information"
                          "13"
                          "18"
                          "http://redmine.visiontree.com/issues/8475#1"
                          "#1"
                          "2014-08-21T21:48:35Z")))

  (fact "it determines an update is both a status
         update and a note update"
    (determine-update status-and-note-update-issue-ex)
    =>
    (just (->NoteAndStatusUpdate "Danny Armstrong"
                                 "Sites should be able to manage their contact information"
                                 "Login card updated successfully. "
                                 "13"
                                 "18"
                                 "http://redmine.visiontree.com/issues/8475#2"
                                 "#2"
                                 "2014-08-21T21:48:35Z"))))

(facts "about contains-every?"
       (fact "it returns true if each path is found. Value can be nil."
             (contains-every? (last 
                                (get-in status-update-issue-ex
                                        [:relations]))
                              [[:id] [:delay]])
             =>
             true))

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