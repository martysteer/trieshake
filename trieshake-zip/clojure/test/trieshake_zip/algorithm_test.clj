(ns trieshake-zip.algorithm-test
  (:require [clojure.test :refer :all]
            [trieshake-zip.algorithm :as alg]))

(deftest test-chunk-string
  (testing "Empty string produces empty vector"
    (is (= [] (alg/chunk-string "" 4))))

  (testing "String shorter than prefix-length produces single chunk"
    (is (= ["AB"] (alg/chunk-string "AB" 4))))

  (testing "String equal to prefix-length produces single chunk"
    (is (= ["ABCD"] (alg/chunk-string "ABCD" 4))))

  (testing "String longer than prefix-length splits with remainder"
    (is (= ["ABCD" "EF"] (alg/chunk-string "ABCDEF" 4))))

  (testing "Clean multiple produces exact chunks"
    (is (= ["AB" "CD" "EF"] (alg/chunk-string "ABCDEF" 2)))))

(deftest test-parse-zip-path
  (testing "Path with multiple parent dirs"
    (is (= {:parents ["BL" "00" "01"] :leafname "file.txt"}
           (alg/parse-zip-path "BL/00/01/file.txt"))))

  (testing "Root-level file (no parents)"
    (is (= {:parents [] :leafname "file.txt"}
           (alg/parse-zip-path "file.txt"))))

  (testing "Path with leading slash stripped"
    (is (= {:parents ["dir"] :leafname "file.txt"}
           (alg/parse-zip-path "/dir/file.txt"))))

  (testing "Deeply nested path"
    (is (= {:parents ["a" "b" "c" "d"] :leafname "data.csv"}
           (alg/parse-zip-path "a/b/c/d/data.csv")))))

(deftest test-compute-target-path
  (testing "Forward transformation with encoded leafname"
    (let [result (alg/compute-target-path ["BL" "00" "01"] "file.txt" 4 true)]
      (is (= "BL00/01" (:target-dir result)))
      (is (= "BL00_01_file.txt" (:target-filename result)))
      (is (= ["BL00" "01"] (:chunks result)))
      (is (= "BL0001" (:concat-string result)))))

  (testing "Forward transformation without encoded leafname"
    (let [result (alg/compute-target-path ["BL" "00"] "data.csv" 4 false)]
      (is (= "BL00" (:target-dir result)))
      (is (= "data.csv" (:target-filename result)))))

  (testing "Root-level file uses leafname stem as concat string"
    (let [result (alg/compute-target-path [] "report.txt" 4 true)]
      (is (= "repo/rt" (:target-dir result)))
      (is (= "repo_rt_report.txt" (:target-filename result)))
      (is (= "report" (:concat-string result)))))

  (testing "Prefix-length 3 produces different chunking"
    (let [result (alg/compute-target-path ["AB" "CD" "EF"] "x.txt" 3 true)]
      (is (= "ABC/DEF" (:target-dir result)))
      (is (= "ABC_DEF_x.txt" (:target-filename result))))))

(deftest test-split-at-depth
  (testing "Depth 0 returns all in to-transform"
    (is (= {:prefix [] :to-transform ["A" "B" "C"]}
           (alg/split-at-depth ["A" "B" "C"] 0))))

  (testing "Depth 1 splits at first component"
    (is (= {:prefix ["A"] :to-transform ["B" "C"]}
           (alg/split-at-depth ["A" "B" "C"] 1))))

  (testing "Depth 2 splits at second component"
    (is (= {:prefix ["A" "B"] :to-transform ["C" "D"]}
           (alg/split-at-depth ["A" "B" "C" "D"] 2))))

  (testing "Depth equal to parent count puts all in prefix"
    (is (= {:prefix ["A" "B"] :to-transform []}
           (alg/split-at-depth ["A" "B"] 2))))

  (testing "Depth greater than parent count puts all in prefix"
    (is (= {:prefix ["A" "B"] :to-transform []}
           (alg/split-at-depth ["A" "B"] 5))))

  (testing "Negative depth returns all in to-transform"
    (is (= {:prefix [] :to-transform ["A" "B"]}
           (alg/split-at-depth ["A" "B"] -1)))))

(deftest test-compute-target-path-with-prefix
  (testing "Path prefix prepended to transformed path"
    (let [result (alg/compute-target-path ["AA" "00" "01"] "file.txt" 4 true ["METADATA" "SOBEKCM"])]
      (is (= "METADATA/SOBEKCM/AA00/01" (:target-dir result)))
      (is (= "AA00_01_file.txt" (:target-filename result)))))

  (testing "Empty path prefix behaves normally"
    (let [result (alg/compute-target-path ["BL" "00"] "data.csv" 4 false [])]
      (is (= "BL00" (:target-dir result)))
      (is (= "data.csv" (:target-filename result))))))
