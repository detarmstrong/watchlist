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

(defn get-token []
  @(load-token))

(defn current-user [redmine-url api-token]
  "Retrieve authenticated user info via token"
  (let [response (client/get (str redmine-url "/users/current.json")
                                  {:basic-auth [api-token "d"]
                                   :as :json
                                   :socket-timeout 9000
                                   :conn-timeout 8000
                                   :throw-exceptions false})]
    (get-in response [:body])))

(defn valid-token? [redmine-url api-token]
  "Make a request using the token provided, expect 200"
  (let [response (client/get (str redmine-url "/users/current.json")
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

(defn get-updated-issues [redmine-url api-token since-ts]
  "Return issues updated since-ts"
  (let [iso-ts (iso-8601-fmt since-ts)
        response (client/get
                   (str redmine-url "/issues.json")
                   {:as :json
                    :basic-auth [api-token ""]
                    :query-params {:status_id "*"
                                   :updated_on (str
                                                 ">="
                                                 iso-ts)
                                   :sort "updated_on:desc"
                                   :limit 60}
                    :debug true
                    :debug-body false})]
    (get-in response [:body :issues])))

(defn issue [redmine-url api-token id]
  "Get detailed info of issue"
  (get-in
    (client/get
      (format "%s/issues/%d.json"
              redmine-url
              (#(Integer/parseInt %) (trim id)))
      {:as :json
       :basic-auth [api-token ""]
       :query-params {:include "children,journals,watchers,relations"}
       :debug true
       :throw-exceptions true})
    [:body :issue]))

(defn issue-statuses [redmine-url api-token]
  "Get all issue statuses from redmine"
  (reduce
    (fn [accum value]
       (assoc accum (:id value) (:name value)))
    {}
    (get-in
      (clj-http.client/get
        (str redmine-url "/issue_statuses.json")
        {:as :json
         :basic-auth [api-token ""]
         :debug true})
      [:body :issue_statuses])))

(def memoized-issue-statuses (memoize issue-statuses))

(defn get-issue-status-name-by-id [redmine-url api-token id]
  (let [iid (if (integer? id)
              id
              (Integer/parseInt id))
        statuses (memoized-issue-statuses redmine-url api-token)]
    (get-in statuses [iid])))

(defn http-any-response?
  "Does the url respond to http?"
  [url timeout-ms]
  (if-let [attempt (try
                     (client/get
                       url
                       {:socket-timeout timeout-ms
                        :conn-timeout timeout-ms
                        :throw-exceptions false})
                     (catch org.apache.http.conn.ConnectTimeoutException e false)
                     (catch java.net.MalformedURLException e false))]
    (> (:status attempt) 0)
    false))

(defn get-all-users [redmine-url api-token]
  (->
    (clj-http.client/get
       (str redmine-url "/users.json")
         {:as :json
          :basic-auth [api-token ""]
          :debug false})
    :body
    :users))
