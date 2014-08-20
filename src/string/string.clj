(ns string.string
  (:require [clojure.string :as s :refer [join split-lines]]))

(defn find-prev-occurrence-of-char [char text range-start]
  "Find occurrence of single char in text looking backwards
   from point range-start. range-start is 0 indexed"
  (let [reversed-text (reverse text)
        count-minus-one (- (count reversed-text) 1)
        range-start-for-rev (- count-minus-one range-start)]
    (loop [index range-start-for-rev
           candidate-str (nth reversed-text index)]
      (if (= (str candidate-str) (str char))
        (- count-minus-one index)
        (if (> count-minus-one index)
          (recur (inc index)
                 (nth reversed-text (inc index)))
          -1)))))

(defn linewise-prepend [small-text full-text preserve-whitespace]
  "Given full-text prepend small-text to each line"
  (s/replace
    full-text
    #"(?m)^\s*"
    (fn [matched]
      (if preserve-whitespace
        (str matched small-text)
        (str small-text matched)))))
             
        
  