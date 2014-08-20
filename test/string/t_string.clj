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