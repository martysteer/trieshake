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
