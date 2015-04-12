(ns watchlist.window
  (:require [seesaw.core :as s]
            [seesaw.icon :as i]))

(defn set-icon!
  "Sets the dock icon on OS X."
  [path]
  (some-> (try (Class/forName "com.apple.eawt.Application")
            (catch Exception _))
          (.getMethod "getApplication" (into-array Class []))
          (.invoke nil (object-array []))
          (.setDockIconImage (.getImage (i/icon path)))))