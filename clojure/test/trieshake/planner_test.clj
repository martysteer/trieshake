(ns trieshake.planner-test
  (:require [clojure.test :refer [deftest testing is]]
            [trieshake.planner :as planner]))

;; === chunk-string tests ===

(deftest test-chunk-string-spec-example-prefix4
  (testing "SPEC: BL0000010600001 at p=4 -> [BL00, 0001, 0600, 001]"
    (is (= ["BL00" "0001" "0600" "001"]
           (planner/chunk-string "BL0000010600001" 4)))))

(deftest test-chunk-string-spec-example-prefix3
  (testing "SPEC: BL0001 at p=3 -> [BL0, 001]"
    (is (= ["BL0" "001"]
           (planner/chunk-string "BL0001" 3)))))

(deftest test-chunk-string-prefix4-with-remainder
  (testing "SPEC: BL0001 at p=4 -> [BL00, 01]"
    (is (= ["BL00" "01"]
           (planner/chunk-string "BL0001" 4)))))

(deftest test-chunk-string-clean-modulo
  (testing "SPEC: ABCD at p=2 -> [AB, CD]"
    (is (= ["AB" "CD"]
           (planner/chunk-string "ABCD" 2)))))

(deftest test-chunk-string-single-chunk-shorter
  (testing "Concat string shorter than prefix-length produces single chunk"
    (is (= ["AB"]
           (planner/chunk-string "AB" 4)))))

(deftest test-chunk-string-single-chunk-exact
  (testing "Concat string exactly prefix-length produces single chunk"
    (is (= ["ABCD"]
           (planner/chunk-string "ABCD" 4)))))

(deftest test-chunk-string-empty
  (testing "Empty concat string produces empty list"
    (is (= []
           (planner/chunk-string "" 4)))))

(deftest test-chunk-string-single-character
  (testing "Single character with any prefix length"
    (is (= ["A"]
           (planner/chunk-string "A" 3)))))

;; === compute-target tests ===

(deftest test-compute-target-spec-forward-prefix4
  (testing "SPEC example: BL/00/00/01/06/00001/report.txt at p=4"
    (let [result (planner/compute-target "BL/00/00/01/06/00001/report.txt" 4 ".txt")]
      (is (= "BL00/0001/0600/001" (:target-dir result)))
      (is (= "BL00_0001_0600_001_report.txt" (:target-filename result)))
      (is (= ["BL00" "0001" "0600" "001"] (:chunks result)))
      (is (= "BL0000010600001" (:concat-string result)))
      (is (= "report.txt" (:leafname result))))))

(deftest test-compute-target-spec-forward-prefix3
  (testing "SPEC example: BL/00/01/file.txt at p=3"
    (let [result (planner/compute-target "BL/00/01/file.txt" 3 ".txt")]
      (is (= "BL0/001" (:target-dir result)))
      (is (= "BL0_001_file.txt" (:target-filename result)))
      (is (= ["BL0" "001"] (:chunks result)))
      (is (= "BL0001" (:concat-string result)))
      (is (= "file.txt" (:leafname result))))))

(deftest test-compute-target-root-level-uses-stem
  (testing "File at root with no parent dirs uses filename stem as concat"
    (let [result (planner/compute-target "mydata.txt" 3 ".txt")]
      (is (= "mydata" (:concat-string result)))
      (is (= ["myd" "ata"] (:chunks result)))
      (is (= "mydata.txt" (:leafname result))))))

(deftest test-compute-target-idempotent
  (testing "Forward produces consistent results"
    (let [result (planner/compute-target "BL/00/01/file.txt" 3 ".txt")
          result2 (planner/compute-target
                   (str (:target-dir result) "/" (:target-filename result))
                   3 ".txt")]
      (is (= (:chunks result2)
             (planner/chunk-string (:concat-string result2) 3))))))

;; === detect-collisions tests ===

(defn- make-plan [source target & [extension]]
  (let [parts (clojure.string/split target #"/")
        target-dir (clojure.string/join "/" (butlast parts))
        target-filename (last parts)]
    {:source-path source
     :target-dir target-dir
     :target-filename target-filename
     :chunks []
     :concat-string ""
     :leafname ""
     :extension (or extension "")
     :is-collision false
     :collision-of nil}))

(deftest test-detect-collisions-none
  (testing "Distinct targets produce no collisions"
    (let [plans [(make-plan "a/data.txt" "AB/AB_data.txt")
                 (make-plan "b/other.txt" "CD/CD_other.txt")]
          resolved (planner/detect-collisions plans)]
      (is (every? (complement :is-collision) resolved)))))

(deftest test-detect-collisions-adds-suffix
  (testing "Two files mapping to same target: second gets --collision1"
    (let [plans [(make-plan "AB/CD/data.txt" "ABCD/ABCD_data.txt")
                 (make-plan "A/BCD/data.txt" "ABCD/ABCD_data.txt")]
          resolved (planner/detect-collisions plans)]
      (is (= "ABCD_data.txt" (:target-filename (first resolved))))
      (is (not (:is-collision (first resolved))))
      (is (= "ABCD_data--collision1.txt" (:target-filename (second resolved))))
      (is (:is-collision (second resolved)))
      (is (= "ABCD/ABCD_data.txt" (:collision-of (second resolved)))))))

(deftest test-detect-collisions-triple
  (testing "Three files to same target: collision1, collision2"
    (let [plans [(make-plan "x/data.txt" "XX/XX_data.txt")
                 (make-plan "y/data.txt" "XX/XX_data.txt")
                 (make-plan "z/data.txt" "XX/XX_data.txt")]
          resolved (planner/detect-collisions plans)]
      (is (= "XX_data.txt" (:target-filename (nth resolved 0))))
      (is (= "XX_data--collision1.txt" (:target-filename (nth resolved 1))))
      (is (= "XX_data--collision2.txt" (:target-filename (nth resolved 2)))))))

(deftest test-detect-collisions-multi-extension
  (testing "Collision suffix inserted before multi-part extension"
    (let [plans [(make-plan "a/record.mets.xml" "AB/AB_record.mets.xml" ".mets.xml")
                 (make-plan "b/record.mets.xml" "AB/AB_record.mets.xml" ".mets.xml")]
          resolved (planner/detect-collisions plans)]
      (is (= "AB_record--collision1.mets.xml" (:target-filename (second resolved)))))))

;; === compute-reverse-target tests ===

(deftest test-reverse-strip-prefix
  (testing "SPEC: reverse without -p strips prefix, keeps dir structure"
    (let [result (planner/compute-reverse-target
                  "BL00/0001/0600/001/BL00_0001_0600_001_report.txt" ".txt")]
      (is (= "report.txt" (:leafname result)))
      (is (= "BL00/0001/0600/001" (:target-dir result)))
      (is (= "report.txt" (:target-filename result))))))

(deftest test-reverse-with-regroup
  (testing "SPEC: reverse with -p 3 regroups in single pass"
    (let [result (planner/compute-reverse-target
                  "BL00/0001/0600/001/BL00_0001_0600_001_report.txt" ".txt"
                  :new-prefix-length 3)]
      (is (= "report.txt" (:leafname result)))
      (is (= "BL0000010600001" (:concat-string result)))
      (is (= ["BL0" "000" "010" "600" "001"] (:chunks result)))
      (is (= "BL0/000/010/600/001" (:target-dir result)))
      (is (= "BL0_000_010_600_001_report.txt" (:target-filename result))))))

(deftest test-reverse-skips-non-encoded
  (testing "File whose name doesn't match dir prefix is skipped"
    (is (nil? (planner/compute-reverse-target
               "BL00/0001/random_file.txt" ".txt")))))

(deftest test-reverse-strips-collision-suffix
  (testing "--collisionN suffix stripped from leafname during reverse"
    (let [result (planner/compute-reverse-target
                  "ABCD/ABCD_data--collision1.txt" ".txt")]
      (is (= "data.txt" (:leafname result)))
      (is (= "data.txt" (:target-filename result))))))

(deftest test-reverse-collision-with-collision-dir
  (testing "Collision file in collisions subdirectory"
    (let [result (planner/compute-reverse-target
                  "ABCD/collisions/ABCD_data--collision1.txt" ".txt")]
      (is (some? result))
      (is (= "data.txt" (:leafname result)))
      (is (= "ABCD" (:target-dir result))))))

(deftest test-reverse-preserves-underscores
  (testing "Underscores in original leafname survive round-trip"
    (let [result (planner/compute-reverse-target
                  "AB/CD/AB_CD_my_data.txt" ".txt")]
      (is (= "my_data.txt" (:leafname result))))))

(deftest test-reverse-root-level-skipped
  (testing "File at root level is skipped in reverse"
    (is (nil? (planner/compute-reverse-target "report.txt" ".txt")))))

(deftest test-reverse-regroup-clean-modulo
  (testing "Regroup where concat string cleanly divides by new prefix"
    (let [result (planner/compute-reverse-target
                  "AB/CD/AB_CD_data.txt" ".txt"
                  :new-prefix-length 2)]
      (is (= ["AB" "CD"] (:chunks result)))
      (is (= "AB/CD" (:target-dir result)))
      (is (= "AB_CD_data.txt" (:target-filename result))))))
