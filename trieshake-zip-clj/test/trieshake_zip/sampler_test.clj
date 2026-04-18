(ns trieshake-zip.sampler-test
  (:require [clojure.test :refer :all]
            [trieshake-zip.sampler :as sampler])
  (:import [java.util.zip ZipInputStream ZipOutputStream ZipEntry]
           [java.io FileInputStream FileOutputStream]))

(deftest test-extract-parent-dir
  (testing "Extracts first parent directory"
    (is (= "BL" (sampler/extract-parent-dir "BL/00/01/file.txt"))))

  (testing "Root-level file returns nil"
    (is (nil? (sampler/extract-parent-dir "file.txt"))))

  (testing "Single-level path returns parent"
    (is (= "data" (sampler/extract-parent-dir "data/file.csv")))))

(defn- create-test-zip
  "Create a zip file with given entries.
   entries: vector of [path content] pairs."
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

(deftest test-sample-integration
  (testing "Sample creates zip with limited files and dirs"
    (let [source-zip "test-resources/sample-source.zip"
          output-zip "test-resources/sample-output.zip"]
      ;; Create test source zip with 3 dirs, 5 files each
      (create-test-zip source-zip
                       [["dir1/file1.txt" "content1"]
                        ["dir1/file2.txt" "content2"]
                        ["dir1/file3.txt" "content3"]
                        ["dir1/file4.txt" "content4"]
                        ["dir1/file5.txt" "content5"]
                        ["dir2/file1.txt" "content1"]
                        ["dir2/file2.txt" "content2"]
                        ["dir2/file3.txt" "content3"]
                        ["dir2/file4.txt" "content4"]
                        ["dir2/file5.txt" "content5"]
                        ["dir3/file1.txt" "content1"]
                        ["dir3/file2.txt" "content2"]
                        ["dir3/file3.txt" "content3"]
                        ["dir3/file4.txt" "content4"]
                        ["dir3/file5.txt" "content5"]])

      ;; Sample: max-dirs=2, max-files=7
      (sampler/sample source-zip output-zip {:max-dirs 2 :max-files 7})

      ;; Verify output
      (let [entries (list-zip-entries output-zip)]
        (is (= 7 (count entries)))
        (is (every? #(or (clojure.string/starts-with? % "dir1/")
                         (clojure.string/starts-with? % "dir2/"))
                    entries))))))
