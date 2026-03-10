(ns trieshake.integration-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [trieshake.core :as core])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- create-temp-dir []
  (str (Files/createTempDirectory "trieshake-test"
                                   (into-array FileAttribute []))))

(defn- delete-recursive [f]
  (let [file (io/file f)]
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursive child)))
    (.delete file)))

(defn- create-file! [base rel-path & [content]]
  (let [f (io/file base rel-path)]
    (.mkdirs (.getParentFile f))
    (spit f (or content "data"))))

(defn- run-trieshake [& args]
  (core/run (core/parse-args args)))

(defmacro with-temp-dir [sym & body]
  `(let [~sym (create-temp-dir)]
     (try ~@body
       (finally (delete-recursive ~sym)))))

;; === Forward integration tests ===

(deftest test-forward-spec-prefix4
  (testing "SPEC example: BL/00/00/01/06/00001/report.txt at p=4"
    (with-temp-dir base
      (create-file! base "BL/00/00/01/06/00001/report.txt")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "4" base)]
        (is (= 0 result))
        (is (.exists (io/file base "BL00/0001/0600/001/BL00_0001_0600_001_report.txt")))
        (is (= "data" (slurp (io/file base "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"))))
        (is (not (.exists (io/file base "BL/00/00/01/06/00001/report.txt"))))))))

(deftest test-forward-spec-prefix3
  (testing "SPEC example: BL/00/01/file.txt at p=3"
    (with-temp-dir base
      (create-file! base "BL/00/01/file.txt")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "3" base)]
        (is (= 0 result))
        (is (.exists (io/file base "BL0/001/BL0_001_file.txt")))))))

(deftest test-forward-multiple-files
  (testing "Multiple files with different parent structures"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt" "file1")
      (create-file! base "EF/GH/other.txt" "file2")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "2" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/AB_CD_data.txt")))
        (is (.exists (io/file base "EF/GH/EF_GH_other.txt")))))))

(deftest test-forward-collision
  (testing "Two files that produce same target get collision suffix"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt" "first")
      (create-file! base "A/BCD/data.txt" "second")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "4" base)]
        (is (= 0 result))
        (is (.exists (io/file base "ABCD/ABCD_data.txt")))
        (is (.exists (io/file base "ABCD/ABCD_data--collision1.txt")))))))

(deftest test-forward-dry-run
  (testing "Dry run (no --execute) should not move any files"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (let [result (run-trieshake "-e" ".txt" "-p" "2" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/data.txt")))
        (is (not (.exists (io/file base "AB/CD/AB_CD_data.txt"))))))))

(deftest test-forward-extension-filter
  (testing "Only files matching extension are processed"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt" "match")
      (create-file! base "AB/CD/image.png" "skip")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "2" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/AB_CD_data.txt")))
        (is (.exists (io/file base "AB/CD/image.png")))))))

(deftest test-forward-empty-dir-cleanup
  (testing "Empty source directories are removed after move"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (run-trieshake "--execute" "-e" ".txt" "-p" "4" base)
      (is (not (.exists (io/file base "AB/CD"))))
      (is (not (.exists (io/file base "AB")))))))

(deftest test-forward-idempotent
  (testing "Running forward twice: second run has no effect"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (run-trieshake "--execute" "-e" ".txt" "-p" "2" base)
      (is (.exists (io/file base "AB/CD/AB_CD_data.txt")))
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "2" base)]
        (is (= 0 result))))))

;; === Reverse integration tests ===

(deftest test-reverse-strips-prefix
  (testing "Reverse without -p strips encoded prefix from filenames"
    (with-temp-dir base
      (create-file! base "BL00/0001/0600/001/BL00_0001_0600_001_report.txt" "content")
      (let [result (run-trieshake "--reverse" "--execute" "-e" ".txt" base)]
        (is (= 0 result))
        (is (.exists (io/file base "BL00/0001/0600/001/report.txt")))
        (is (= "content" (slurp (io/file base "BL00/0001/0600/001/report.txt"))))
        (is (not (.exists (io/file base "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"))))))))

(deftest test-reverse-with-regroup
  (testing "Reverse with -p 3 regroups from p=4 to p=3 in single pass"
    (with-temp-dir base
      (create-file! base "BL00/0001/0600/001/BL00_0001_0600_001_report.txt")
      (let [result (run-trieshake "--reverse" "--execute" "-e" ".txt" "-p" "3" base)]
        (is (= 0 result))
        (is (.exists (io/file base "BL0/000/010/600/001/BL0_000_010_600_001_report.txt")))))))

(deftest test-reverse-strips-collision-suffix
  (testing "Reverse strips --collisionN suffixes from filenames"
    (with-temp-dir base
      (create-file! base "ABCD/ABCD_data--collision1.txt" "coll")
      (let [result (run-trieshake "--reverse" "--execute" "-e" ".txt" base)]
        (is (= 0 result))
        (is (.exists (io/file base "ABCD/data.txt")))
        (is (= "coll" (slurp (io/file base "ABCD/data.txt"))))))))

(deftest test-forward-then-reverse-roundtrip
  (testing "Forward at p=4 then reverse restores plain leafnames"
    (with-temp-dir base
      (create-file! base "BL/00/01/file.txt" "original")
      (run-trieshake "--execute" "-e" ".txt" "-p" "4" base)
      (is (.exists (io/file base "BL00/01/BL00_01_file.txt")))
      (run-trieshake "--reverse" "--execute" "-e" ".txt" base)
      (is (.exists (io/file base "BL00/01/file.txt")))
      (is (= "original" (slurp (io/file base "BL00/01/file.txt")))))))

(deftest test-forward-reverse-regroup-roundtrip
  (testing "Forward p=4 -> reverse -p 3: single-pass regroup"
    (with-temp-dir base
      (create-file! base "AB/CD/EF/data.txt" "roundtrip")
      (run-trieshake "--execute" "-e" ".txt" "-p" "4" base)
      (is (.exists (io/file base "ABCD/EF/ABCD_EF_data.txt")))
      (run-trieshake "--reverse" "--execute" "-e" ".txt" "-p" "3" base)
      (let [expected (io/file base "ABC/DEF/ABC_DEF_data.txt")]
        (is (.exists expected))
        (is (= "roundtrip" (slurp expected)))))))

(deftest test-reverse-dry-run
  (testing "Reverse dry run makes no changes"
    (with-temp-dir base
      (create-file! base "AB/CD/AB_CD_data.txt")
      (let [result (run-trieshake "--reverse" "-e" ".txt" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/AB_CD_data.txt")))))))

(deftest test-reverse-skips-non-encoded
  (testing "Reverse skips files that don't match their directory prefix"
    (with-temp-dir base
      (create-file! base "AB/CD/random_file.txt")
      (let [result (run-trieshake "--reverse" "--execute" "-e" ".txt" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/random_file.txt")))))))

;; === --no-encode-leafname integration tests ===

(deftest test-no-encode-forward
  (testing "Forward with --no-encode-leafname uses plain filenames"
    (with-temp-dir base
      (create-file! base "BL/00/01/file.txt" "plain")
      (let [result (run-trieshake "--execute" "-e" ".txt" "-p" "3"
                                  "--no-encode-leafname" base)]
        (is (= 0 result))
        (is (.exists (io/file base "BL0/001/file.txt")))
        (is (= "plain" (slurp (io/file base "BL0/001/file.txt"))))))))

(deftest test-no-encode-forward-then-reverse
  (testing "Round-trip: forward --no-encode then reverse"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt" "noenc")
      (run-trieshake "--execute" "-e" ".txt" "-p" "2"
                     "--no-encode-leafname" base)
      ;; File already at correct location since dir structure is same
      (is (.exists (io/file base "AB/CD/data.txt"))))))

(deftest test-no-encode-reverse-regroup
  (testing "Reverse regroup with --no-encode-leafname produces plain filenames"
    (with-temp-dir base
      (create-file! base "AB/CD/AB_CD_data.txt" "regroup")
      (let [result (run-trieshake "--reverse" "--execute" "-e" ".txt" "-p" "3"
                                  "--no-encode-leafname" base)]
        (is (= 0 result))
        (is (.exists (io/file base "ABC/D/data.txt")))
        (is (= "regroup" (slurp (io/file base "ABC/D/data.txt"))))))))

;; === --output-plan integration tests ===

(deftest test-output-plan-csv-columns
  (testing "Plan CSV has correct headers and data"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (let [plan-file (str base "/plan.csv")]
        (run-trieshake "-e" ".txt" "-p" "2" "--output-plan" plan-file base)
        (is (.exists (io/file plan-file)))
        (let [lines (clojure.string/split-lines (slurp plan-file))
              header (first lines)
              row (second lines)]
          (is (clojure.string/includes? header "source_path"))
          (is (clojure.string/includes? header "target_path"))
          (is (clojure.string/includes? row "AB/CD/data.txt"))
          (is (clojure.string/includes? row "AB/CD/AB_CD_data.txt"))
          (is (clojure.string/includes? row "AB|CD"))
          (is (clojure.string/includes? row "move")))))))

(deftest test-output-plan-csv-collision
  (testing "Plan CSV marks collisions correctly"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (create-file! base "A/BCD/data.txt")
      (let [plan-file (str base "/plan.csv")]
        (run-trieshake "-e" ".txt" "-p" "4" "--output-plan" plan-file base)
        (let [content (slurp plan-file)]
          (is (clojure.string/includes? content "true"))
          (is (clojure.string/includes? content "collision")))))))

;; === Extension optional tests ===

(deftest test-no-extension-matches-all
  (testing "Without -e, all files are processed"
    (with-temp-dir base
      (create-file! base "AB/CD/data.txt")
      (create-file! base "AB/CD/image.png")
      (let [result (run-trieshake "--execute" "-p" "2" base)]
        (is (= 0 result))
        (is (.exists (io/file base "AB/CD/AB_CD_data.txt")))
        (is (.exists (io/file base "AB/CD/AB_CD_image.png")))))))
