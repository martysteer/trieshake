(ns trieshake-zip.transformer-test
  (:require [clojure.test :refer :all]
            [trieshake-zip.transformer :as transformer])
  (:import [java.util.zip ZipInputStream ZipOutputStream ZipEntry]
           [java.io FileInputStream FileOutputStream]))

(deftest test-track-collision
  (testing "First occurrence has no suffix"
    (let [tracker (atom {})
          result (transformer/track-collision tracker "BL00/01/file.txt")]
      (is (= "file.txt" result))
      (is (= 0 (get @tracker "BL00/01/file.txt")))))

  (testing "Second occurrence gets --collision1 suffix"
    (let [tracker (atom {"BL00/01/file.txt" 0})
          result (transformer/track-collision tracker "BL00/01/file.txt")]
      (is (= "file--collision1.txt" result))
      (is (= 1 (get @tracker "BL00/01/file.txt")))))

  (testing "Third occurrence gets --collision2 suffix"
    (let [tracker (atom {"BL00/01/file.txt" 1})
          result (transformer/track-collision tracker "BL00/01/file.txt")]
      (is (= "file--collision2.txt" result))
      (is (= 2 (get @tracker "BL00/01/file.txt"))))))

(defn- create-test-zip
  "Create a zip file with given entries."
  [zip-path entries]
  (.mkdirs (.getParentFile (clojure.java.io/file zip-path)))
  (with-open [zos (ZipOutputStream. (FileOutputStream. zip-path))]
    (doseq [[entry-name content] entries]
      (.putNextEntry zos (ZipEntry. entry-name))
      (.write zos (.getBytes content "UTF-8"))
      (.closeEntry zos))))

(defn- list-zip-entries
  "List all entry names in a zip file."
  [zip-path]
  (with-open [zis (ZipInputStream. (FileInputStream. zip-path))]
    (loop [entries []]
      (if-let [entry (.getNextEntry zis)]
        (recur (conj entries (.getName entry)))
        entries))))

(deftest test-transform-integration
  (testing "Transform applies trieshake structure"
    (let [source-zip "test-resources/transform-source.zip"
          output-zip "test-resources/transform-output.zip"]
      ;; Create test source zip
      (create-test-zip source-zip
                       [["BL/00/01/file.txt" "content1"]
                        ["BL/00/02/file.txt" "content2"]
                        ["AB/CD/data.csv" "data"]])

      ;; Transform with prefix-length 4, encoded leafnames
      (transformer/transform source-zip output-zip {:prefix-length 4 :encode-leafname true})

      ;; Verify output structure
      (let [entries (list-zip-entries output-zip)]
        (is (= 3 (count entries)))
        (is (contains? (set entries) "BL00/01/BL00_01_file.txt"))
        (is (contains? (set entries) "BL00/02/BL00_02_file.txt"))
        (is (contains? (set entries) "ABCD/ABCD_data.csv"))))))

(deftest test-transform-with-collisions
  (testing "Collisions get --collisionN suffix"
    (let [source-zip "test-resources/collision-source.zip"
          output-zip "test-resources/collision-output.zip"]
      ;; Create test zip with paths that produce collision
      (create-test-zip source-zip
                       [["AB/CD/file.txt" "content1"]
                        ["A/BCD/file.txt" "content2"]])

      ;; Transform (both produce "ABCD/ABCD_file.txt")
      (transformer/transform source-zip output-zip {:prefix-length 4})

      ;; Verify collision handling
      (let [entries (set (list-zip-entries output-zip))]
        (is (= 2 (count entries)))
        (is (contains? entries "ABCD/ABCD_file.txt"))
        (is (contains? entries "ABCD/ABCD_file--collision1.txt"))))))
