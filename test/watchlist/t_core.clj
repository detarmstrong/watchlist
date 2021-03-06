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
   :watchers [{:id 5 :name "Danny Armstrong"}
              {:id 6 :name "Jodie Foster"}],
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

(def related-ticket-ex
  {
   :status {:id 18, :name "QA Passed"},
   :subject
     "Limo lambdo",
   :created_on "2014-08-20T01:52:49Z",
   :author {:id 5, :name "Danny Armstrong"},
   :assigned_to {:id 6, :name "Lorenzo Lamas"},
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
    (fact "it converts an redmine json representation to custom format, only note update"
      (convert-update
        (read-string
          (slurp "dev-resources/note_update_issue.edn")))
      =>
      (contains {:id 8475
                 :assignee-id 5
                 :ticket-author-id 5
                 :update-author "Kyle"
                 :relations [{:delay nil, :id 806, :issue_id 8329, :issue_to_id 8475, :relation_type "relates"}]
                 :subject "Sites should be able to manage their contact information"
                 :watchers []
                 :update-text "Login card updated successfully. "
                 :update-uri "https://redmine.example.com/issues/8475#note-2"
                 :update-uri-label "#2"
                 :updated-at "2014-08-21T21:48:35Z"
                 :status-update []
                 :description-update []
                 :project {:id 1, :name "Mike's Early Inflation Gesticulation"}})
      (provided 
        (watchlist.core/get-preferences)
        =>
        {:url "https://redmine.example.com"
         :api-token nil})
      (provided
        (watchlist.web-api/resolve-formatted-name
          anything ; this will come from get-preferences provided below
          anything
          anything)
        =>
        "Kyle")
      (provided
        (watchlist.web-api/get-user-by-id
          anything
          anything
          anything)
        =>
        "hosier@example.com"))

    (fact "it converts an redmine json representation to custom format, only note status property update"
      (convert-update status-update-issue-ex)
      =>
      (contains {:id 8475
                 :subject "Test related ticket"
                 :update-text ""
                 :status-update [{:name "status_id", :new_value "QA Passed", :old_value "13", :property "attr"}]})
      (provided
        (watchlist.web-api/resolve-formatted-name
          anything
          anything
          anything)
        =>
        "Kyle")
      (provided
        (watchlist.web-api/get-issue-status-name-by-id
          anything
          anything
          anything)
        =>
        "QA Passed")
      (provided
        (watchlist.web-api/get-user-by-id
          anything
          anything
          anything)
        =>
        "hosier@example.com"))

    (fact "it converts an update to a hybrid status update and a note update"
      (convert-update status-and-note-update-issue-ex)
      =>
      (contains {:id 8475
                 :update-text "Login card updated successfully. "
                 :status-update [{:name "status_id", :new_value "QA Pending", :old_value "13", :property "attr"}]})
      (provided
        (watchlist.web-api/resolve-formatted-name
          anything
          anything
          anything)
        =>
        "Kyle")
      (provided
        (watchlist.web-api/get-issue-status-name-by-id
          anything
          anything
          anything)
        =>
        "QA Pending")
      (provided
        (watchlist.web-api/get-user-by-id
          anything
          anything
          anything)
        =>
        "hosier@example.com"))
    (fact "it returns nil for updates it doesn't understand"
          (convert-update (read-string (slurp "dev-resources/9676.clj")))
          =>
          nil)))

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
             "14y ago"
             (provided (time-core/now)
                       =>
                       (time-core/date-time 2014 1 1)))
       (fact "intervals a day ago show 2d"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/days 2)))
             =>
             "2d ago")
       (fact "intervals a hour ago show 2h"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/hours 2)))
             =>
             "2h ago")
       (fact "intervals less than a hour ago show 15m"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/minutes 15)))
             =>
             "15m ago")
       (fact "intervals less than a minute ago show 30s"
             (format-time-ago (time-core/minus
                                (time-core/now)
                                (time-core/seconds 30)))
             =>
             "30s ago"))

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
     true
     (provided
       (watchlist.web-api/resolve-formatted-name
         anything anything anything) => "Ray Bigsander")
     (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  (fact "is-author? will return true if the arg is the author"
    (is-author?
      5
      (convert-update status-and-note-update-issue-ex))
    =>
    true
    (provided
      (watchlist.web-api/resolve-formatted-name
        anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  (fact "is-a-watcher? will return true if the arg is a watcher"
    (is-a-watcher?
      5
      (convert-update status-and-note-update-issue-ex))
    =>
    true
    (provided
          (watchlist.web-api/resolve-formatted-name
            anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  (fact "is-a-watcher? will return false if the arg is not a watcher"
    (is-a-watcher?
      5
      (convert-update
              (read-string
                (slurp "dev-resources/note_update_issue.edn"))))
    =>
    false
    (provided
      (watchlist.web-api/resolve-formatted-name
        anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  ; (fact "is-mentioned-in-ticket-or-update? will return true if the arg mentioned in note update"
  ;   (is-mentioned-in-ticket-or-update?
  ;     5
  ;     (convert-update note-update-issue-ex))
  ;   =>
  ;   false)
  (fact "is-related-ticket? returns true if a ticket is related to this one"
    (is-related-ticket?
      5
      (convert-update status-and-note-update-issue-ex))
    =>
   ; (contains {:related? true
   ;            :reason "relates"
   ;            :id 8329})
    true
    (provided
      (watchlist.web-api/resolve-formatted-name
        anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/issue
        anything anything 8329) => related-ticket-ex)
    (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  (fact "is-project-substring? will return true if any matcher string is found in project name"
    (is-project-substring?
      "His Hero, Live Inflation, Early Inflation"
      (convert-update
              (read-string
                (slurp "dev-resources/note_update_issue.edn"))))
    =>
    true
    (provided
      (watchlist.web-api/resolve-formatted-name
        anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
      =>
      "hosier@example.com"))
  )

(facts "about build-preds-from-filter-options"
  (fact "seq of filters as stored on disk is turned into pred fns"
    (let [preds (build-preds-from-filter-options
                  [[:is-a-watcher? 4] [:is-project? [1 2 3 8 9]]])]
      ((first preds) {:watchers [{:id 4}]}))
    =>
    :is-a-watcher?))

(facts "about update filter pipeline"
 (fact "is-tagged-item? looks for item tag"
   [(is-tagged-item?
      [:the-stuff :the-tag])
    (is-tagged-item?
      [:other-stuff false])]
   =>
   [true false])
 (fact "is-tagged-item? knows if an item is tagged or not"
   (filterv
     is-tagged-item?
     (tag-updates
       (map
         convert-update
         [status-and-note-update-issue-ex
          (read-string
            (slurp "dev-resources/note_update_issue.edn"))])
       (build-preds-from-filter-options
         [[:is-a-watcher? 5]])))
   =>
   (one-of anything)
   ;TODO this provided clause is copied from the next fact
   ; how can I reuse it across facts?
   (provided
     (watchlist.web-api/resolve-formatted-name
       anything anything anything) => "Ray Bigsander")
   (provided
     (watchlist.web-api/get-issue-status-name-by-id
       anything
       anything
       anything)
     =>
     "QA Pending")
   (provided
     (watchlist.web-api/get-user-by-id
        anything
        anything
        anything)
     =>
     "hosier@example.com"))
 (fact "each update is scrutinized for inclusion - is-author? and is-assignee?"
   (first (tag-updates
            (map
              convert-update
              [status-and-note-update-issue-ex])
            (build-preds-from-filter-options
              [[:is-project? [1 2 3 8 9]] [:is-a-watcher? 5]])))
   =>
   (contains '(:is-project?) :in-any-order)
   (provided
     (watchlist.web-api/resolve-formatted-name
       anything anything anything) => "Ray Bigsander")
   (provided
     (watchlist.web-api/get-issue-status-name-by-id
       anything
       anything
       anything)
     =>
     "QA Pending")
   (provided
      (watchlist.web-api/get-user-by-id
         anything
         anything
         anything)
      =>
      "hosier@example.com"))
 (fact "an issue failing all filtering criteria is filtered out"
   (filterv
     is-tagged-item?
     (tag-updates
       (map
         convert-update
         [(read-string (slurp "dev-resources/9174.clj"))])
       (build-preds-from-filter-options
         [[:is-a-watcher? 5]
          [:is-assignee? 5]
          [:is-author? 5]
          [:is-related-ticket? 5]
          [:is-a-update-participant? 5]])))
   =>
   (just [])
   (provided
     (watchlist.web-api/resolve-formatted-name
       anything anything anything) => "Ray Bigsander")
   (provided
      (watchlist.web-api/get-user-by-id
         anything
         anything
         anything)
      =>
      "hosier@example.com")))
  
(facts "about get-issue-updates"
  (fact "filter out parent tasks where the updated_at is
         only because of the child task being updated"
    (get-issue-updates (clj-time.core/now))
    =>
    (just [])
    (provided
     (watchlist.web-api/resolve-formatted-name
       anything anything anything) => "Ray Bigsander")
    (provided
      (watchlist.web-api/get-user-by-id
         anything
         anything
         anything)
      =>
      "hosier@example.com")
    (provided
      (watchlist.web-api/get-updated-issues
        anything
        anything
        anything)
      =>
      [{:id 1}])
    (provided
      (watchlist.web-api/issue
        anything
        anything
        anything)
      =>
      (read-string (slurp "dev-resources/9174.clj")))))
