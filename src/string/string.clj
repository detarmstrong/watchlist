(ns string.string
  (:require [clojure.string :as s :refer [join split-lines split]]))

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

(defn index-of-difference
  "Compare two strings and return the index at which the two strings begin
  to differ."
  [string1 string2]
  (cond
    (= string1 string2)
    -1
    (or (= "" string1) (= "" string2) (nil? string1) (nil? string2))
    0
    :else
    (loop [i 0]
      (if (and (< i (count string1)) (< i (count string2)))
        (if (not (= (get string1 i) (get string2 i)))
          (if (or (< i (count string1)) (< i (count string2)))
            i
            -1)
          (recur (inc i)))
        i))))

(defn shortest-unique-strings
  "Given seq of strings return the shortest unique string possible
  within the set, starting from left at position in string determined by
  pref-len.
  Repeated occurrences of a string are treated as one, as though uniq is
  run first"
  [pref-len strings]
  (map
    (fn [string]
      (let [max-diff-at (reduce
                          (fn [accum el]
                            (if (not (= string el))
                              (let [diff-at (index-of-difference string el)]
                                (max accum diff-at))
                              (max accum -1)))
                          0
                          strings)
            preferred-min (pref-len string)]
        (cond
          (neg? (compare max-diff-at preferred-min))
          (subs string 0 preferred-min)
          (= 0 max-diff-at)
          (subs string 0 preferred-min)
          :else
          (subs string 0 (min (+ 1 max-diff-at)
                              (count string))))))
    strings))

