(ns string.t_string
  (:use midje.sweet)
  (:use [string.string]))

(facts "about find-prev-occurrence-of-char"
        (fact "find-prev-occurrence-of-char returns the position of char from the
               left of range-start."
              [(find-prev-occurrence-of-char "\n" "you\nsee\nnow?" 7)
               (find-prev-occurrence-of-char "\n" "you\nsee\nnow?" 11)]
              => [7 7])
        (fact "find-prev-occurrence-of-char if no match found returns -1"
              (find-prev-occurrence-of-char "z" "you don't see me" 3)
              => -1))

(facts "about linewise-prepend"
      (fact "it prepends text to lines after whitespace"
            (linewise-prepend
              "//"
              "this\n  is\n  the\n    line"
              (do "preserve whitespace" true))
            => "//this\n  //is\n  //the\n    //line")
      (fact "it prepends text to lines"
            (linewise-prepend
              "//"
              "this\n  is\n  the\n    line"
              (do "preserve whitespace" false))
            => "//this\n//  is\n//  the\n//    line"))

(facts "about index-of-difference"
  (fact "it returns index of difference given two strings"
    (index-of-difference "Chacho" "Cholo")
    =>
    2)
  (fact "it returns index of difference given two strings"
    (index-of-difference "Jon Jules" "Jon Julesa")
    =>
    9)
  (fact "it returns index of difference given two strings, one empty"
    (index-of-difference "" "Cholo")
    =>
    0))

(facts "about shortest-unique-string"
  (fact "it returns the shortest unique string"
    (shortest-unique-strings
      #(let [space-idx (-> % (.indexOf " "))]
         (if (not (neg? space-idx))
           space-idx
           (count %)))
      '("Jon Joy"
        "Jon Joy"
        "Jon Jules"
        "Jon Julesa"
        "Jon Mew"
        "Pokie"
        "Perki"
        "Lerke"
        "Paul Shake"
        "Paul Punch"
        "Madz Hience"))
    =>
    '("Jon Jo"
      "Jon Jo"
      "Jon Jules"
      "Jon Julesa"
      "Jon M"
      "Pokie"
      "Perki"
      "Lerke"
      "Paul S"
      "Paul P"
      "Madz")))