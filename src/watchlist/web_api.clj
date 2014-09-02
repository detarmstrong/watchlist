(ns watchlist.web-api
  "Wrap the api calls - no guarding of params here"
  (:require [clj-http.client :as client])
  (:require [clojure.java.io :as io])
  (:require [clojure.core.memoize :refer [memo]])
  (:require [clj-time.core :as time-core])
  (:require [clj-time.format :as time-format])
  (:use [cheshire.core :only (generate-string)])
  (:use [clojure.string :only (trim)]))

(def obelisk-token-file-path
  (let [dot-file ".obelisk_rm_token"
        home-dir (System/getProperty "user.home")
        file-separator (System/getProperty "file.separator")
        full-path (apply str (interpose file-separator [home-dir dot-file]))]
    full-path))

(defn token? []
  (-> (io/file obelisk-token-file-path) (.isFile)))

(defn load-token []
  (def api-token 
    (trim (slurp obelisk-token-file-path))))

(defn projects []
  (sort-by :name
	  (get-in 
	    (client/get
	      "http://redmine.visiontree.com/projects.json" 
	      {:basic-auth [api-token "d"]
	       :as :json
	       :query-params {:limit 300}})
	    [:body :projects])))

(defn valid-token? [api-token]
  "Make a request using the token provided, expect 200"
  (let [response (client/get "http://redmine.visiontree.com/users/current.json" 
                                  {:basic-auth [api-token "d"]
                                   :as :json
                                   :socket-timeout 9000
                                   :conn-timeout 8000
                                   :throw-exceptions false})]
    (= 200 (:status response))))

(defn iso-8601-fmt [ts]
  (time-format/unparse (time-format/formatters :date-time-no-ms) ts))

(defn YYYY-mm-dd-fmt [ts]
  (time-format/unparse (time-format/formatter "YYYY-MM-dd") ts))

(defn get-updated-issues [since-ts]
  "Return issues updated since-ts"
  (let [iso-ts (iso-8601-fmt since-ts)
        response (client/get
                   "http://redmine.visiontree.com/issues.json"
                   {:as :json
                    :basic-auth [api-token ""]
                    :query-params {:status_id "*"
                                   :updated_on (str
                                                 ">="
                                                 iso-ts)
                                   :sort "updated_on:desc"
                                   :limit 35}
                    :debug false
                    :debug-body false})]
    (get-in response [:body :issues])))

(defn issue [id]
  "Get detailed info of issue"
  (get-in
    (client/get 
      (format "http://redmine.visiontree.com/issues/%d.json" 
        (#(Integer/parseInt %) (trim id))) 
      {:as :json
       :basic-auth [api-token ""]
       :query-params {:include "children,journals,watchers,relations"}
       :debug false})
    [:body :issue]))

(defn issue-statuses []
  "Get all issue statuses from redmine"
  (reduce
    (fn [accum value]
       (assoc accum (:id value) (:name value)))
    {}
    (get-in
      (clj-http.client/get 
        "http://redmine.visiontree.com/issue_statuses.json"
        {:as :json
         :basic-auth [api-token ""]})
      [:body :issue_statuses])))

(def memoized-issue-statuses (memo issue-statuses))

(defn get-issue-status-name-by-id [id]
  (let [iid (if (integer? id)
              id
              (Integer/parseInt id))
        statuses (memoized-issue-statuses)]
    (get-in statuses [iid])))
    