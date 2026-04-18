# trieshake-zip (Clojure) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build trieshake-zip tool for Clojure—sample and transform zip archives using trieshake algorithm without disk extraction.

**Architecture:** Separate tool in `trieshake-zip/clojure/` directory. Reuses trieshake's chunking/encoding logic. Two modes: sample (filter entries by group/count) and transform (apply trieshake path transformation). Streams zip entries through memory.

**Tech Stack:** Clojure, java.util.zip (ZipInputStream/ZipOutputStream), zero external deps

---

## File Structure

**New files to create:**

```
trieshake-zip/
└── clojure/
    ├── project.clj                           # Lein project config
    ├── src/trieshake_zip/
    │   ├── core.clj                          # CLI entry, arg parsing
    │   ├── algorithm.clj                     # Trieshake path computation (reused logic)
    │   ├── sampler.clj                       # Sample mode implementation
    │   └── transformer.clj                   # Transform mode implementation
    └── test/trieshake_zip/
        ├── algorithm_test.clj                # Unit tests for algorithm functions
        ├── sampler_test.clj                  # Integration tests for sampler
        └── transformer_test.clj              # Integration tests for transformer
```

**Files referenced (existing):**
- `trieshake/clojure/src/trieshake/planner.clj` — source for algorithm functions

---

## Task 1: Project Setup

**Files:**
- Create: `trieshake-zip/clojure/project.clj`

- [ ] **Step 1: Create project structure**

```bash
mkdir -p trieshake-zip/clojure/src/trieshake_zip
mkdir -p trieshake-zip/clojure/test/trieshake_zip
```

- [ ] **Step 2: Write project.clj**

```clojure
(defproject trieshake-zip "0.1.0"
  :description "Apply trieshake algorithm to zip archives"
  :url "https://github.com/martysteer/trieshake"
  :license {:name "CC-BY-SA 4.0"
            :url "https://creativecommons.org/licenses/by-sa/4.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.219"]]
  :main trieshake-zip.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
```

- [ ] **Step 3: Verify project builds**

Run: `cd trieshake-zip/clojure && lein deps`
Expected: Dependencies downloaded, no errors

- [ ] **Step 4: Commit**

```bash
git add trieshake-zip/clojure/project.clj
git commit -m "feat(trieshake-zip): add Clojure project setup"
```

---

## Task 2: Algorithm Module (Path Computation)

**Files:**
- Create: `trieshake-zip/clojure/src/trieshake_zip/algorithm.clj`
- Create: `trieshake-zip/clojure/test/trieshake_zip/algorithm_test.clj`

- [ ] **Step 1: Write failing test for chunk-string**

```clojure
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `lein test trieshake-zip.algorithm-test`
Expected: FAIL - "Could not locate trieshake_zip/algorithm.clj"

- [ ] **Step 3: Write algorithm.clj with chunk-string**

```clojure
(ns trieshake-zip.algorithm
  "Path computation functions for trieshake algorithm.
   Pure functions adapted from trieshake.planner.")

(defn chunk-string
  "Split a string into chunks of n characters, with a shorter remainder if needed."
  [s n]
  (if (empty? s)
    []
    (let [len (count s)
          full (quot len n)
          chunks (mapv #(subs s (* % n) (* (inc %) n)) (range full))
          remainder (subs s (* full n))]
      (if (seq remainder)
        (conj chunks remainder)
        chunks))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `lein test trieshake-zip.algorithm-test`
Expected: PASS - 5 tests

- [ ] **Step 5: Write failing test for path parsing**

```clojure
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
```

- [ ] **Step 6: Run test to verify it fails**

Run: `lein test trieshake-zip.algorithm-test`
Expected: FAIL - "Unable to resolve symbol: parse-zip-path"

- [ ] **Step 7: Implement parse-zip-path**

```clojure
(defn parse-zip-path
  "Parse zip entry path into parent directories and leafname.
   Handles leading slashes, returns {:parents [...] :leafname \"...\"}."
  [path-str]
  (let [;; Strip leading slash if present
        path-str (if (clojure.string/starts-with? path-str "/")
                   (subs path-str 1)
                   path-str)
        parts (clojure.string/split path-str #"/")
        parts (filterv (complement empty?) parts)]
    {:parents (vec (butlast parts))
     :leafname (last parts)}))
```

- [ ] **Step 8: Run test to verify it passes**

Run: `lein test trieshake-zip.algorithm-test`
Expected: PASS - 9 tests

- [ ] **Step 9: Write failing test for compute-target-path**

```clojure
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
```

- [ ] **Step 10: Run test to verify it fails**

Run: `lein test trieshake-zip.algorithm-test`
Expected: FAIL - "Unable to resolve symbol: compute-target-path"

- [ ] **Step 11: Implement compute-target-path**

```clojure
(defn- strip-extension
  "Strip extension from filename, return [stem ext].
   Handles multi-part extensions like .mets.xml."
  [filename]
  (let [dot-pos (clojure.string/last-index-of filename ".")]
    (if (and dot-pos (pos? dot-pos))
      [(subs filename 0 dot-pos) (subs filename dot-pos)]
      [filename ""])))

(defn compute-target-path
  "Compute trieshake target path for a file.
   Returns {:target-dir \"...\" :target-filename \"...\" :chunks [...] :concat-string \"...\"}.

   Args:
     parents - vector of parent directory names
     leafname - original filename
     prefix-length - chunk size
     encode-leafname? - if true, prepend prefix to filename"
  [parents leafname prefix-length encode-leafname?]
  (let [[concat-string actual-leafname]
        (if (seq parents)
          [(apply str parents) leafname]
          ;; Root-level: use filename stem as concat string
          (let [[stem _] (strip-extension leafname)]
            [stem leafname]))
        chunks (chunk-string concat-string prefix-length)
        target-dir (if (seq chunks)
                     (clojure.string/join "/" chunks)
                     ".")
        target-filename (if (and encode-leafname? (seq chunks))
                          (str (clojure.string/join "_" chunks) "_" actual-leafname)
                          actual-leafname)]
    {:target-dir target-dir
     :target-filename target-filename
     :chunks chunks
     :concat-string concat-string}))
```

- [ ] **Step 12: Run test to verify it passes**

Run: `lein test trieshake-zip.algorithm-test`
Expected: PASS - 13 tests

- [ ] **Step 13: Commit**

```bash
git add trieshake-zip/clojure/src/trieshake_zip/algorithm.clj \
        trieshake-zip/clojure/test/trieshake_zip/algorithm_test.clj
git commit -m "feat(trieshake-zip): add algorithm module with path computation"
```

---

## Task 3: Sampler Module

**Files:**
- Create: `trieshake-zip/clojure/src/trieshake_zip/sampler.clj`
- Create: `trieshake-zip/clojure/test/trieshake_zip/sampler_test.clj`

- [ ] **Step 1: Write failing test for extract-parent-dir**

```clojure
(ns trieshake-zip.sampler-test
  (:require [clojure.test :refer :all]
            [trieshake-zip.sampler :as sampler]))

(deftest test-extract-parent-dir
  (testing "Extracts first parent directory"
    (is (= "BL" (sampler/extract-parent-dir "BL/00/01/file.txt"))))

  (testing "Root-level file returns nil"
    (is (nil? (sampler/extract-parent-dir "file.txt"))))

  (testing "Single-level path returns parent"
    (is (= "data" (sampler/extract-parent-dir "data/file.csv")))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `lein test trieshake-zip.sampler-test`
Expected: FAIL - "Could not locate trieshake_zip/sampler.clj"

- [ ] **Step 3: Write sampler.clj with extract-parent-dir**

```clojure
(ns trieshake-zip.sampler
  "Sample mode: extract subset of zip entries by parent dir count and file count."
  (:import [java.util.zip ZipInputStream ZipOutputStream ZipEntry]
           [java.io FileInputStream FileOutputStream]))

(defn extract-parent-dir
  "Extract first parent directory from zip entry path.
   Returns nil if entry is at root level."
  [entry-name]
  (let [slash-pos (clojure.string/index-of entry-name "/")]
    (when slash-pos
      (subs entry-name 0 slash-pos))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `lein test trieshake-zip.sampler-test`
Expected: PASS - 3 tests

- [ ] **Step 5: Write failing integration test for sample**

```clojure
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
```

- [ ] **Step 6: Write test helpers**

```clojure
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
```

- [ ] **Step 7: Run test to verify it fails**

Run: `lein test trieshake-zip.sampler-test`
Expected: FAIL - "Unable to resolve symbol: sample"

- [ ] **Step 8: Implement sample function**

```clojure
(defn sample
  "Sample entries from source-zip to output-zip.

   Options:
     :max-dirs - maximum parent directories to include (default 10)
     :max-files - maximum total files to include (default 100)"
  [source-zip output-zip {:keys [max-dirs max-files] :or {max-dirs 10 max-files 100}}]
  (with-open [zis (ZipInputStream. (FileInputStream. source-zip))
              zos (ZipOutputStream. (FileOutputStream. output-zip))]
    (loop [dirs-seen #{}
           files-written 0]
      (if-let [entry (.getNextEntry zis)]
        (let [entry-name (.getName entry)]
          ;; Skip directory entries
          (if (.endsWith entry-name "/")
            (recur dirs-seen files-written)
            (let [parent-dir (extract-parent-dir entry-name)
                  new-dirs-seen (if parent-dir (conj dirs-seen parent-dir) dirs-seen)]
              ;; Check limits
              (if (and (< (count new-dirs-seen) max-dirs)
                       (< files-written max-files))
                (do
                  ;; Copy entry
                  (.putNextEntry zos (ZipEntry. entry-name))
                  (let [buffer (byte-array 8192)]
                    (loop []
                      (let [n (.read zis buffer)]
                        (when (pos? n)
                          (.write zos buffer 0 n)
                          (recur)))))
                  (.closeEntry zos)
                  (recur new-dirs-seen (inc files-written)))
                ;; Limits reached
                nil))))
        ;; Done
        {:dirs-included (count dirs-seen)
         :files-written files-written}))))
```

- [ ] **Step 9: Run test to verify it passes**

Run: `lein test trieshake-zip.sampler-test`
Expected: PASS - 4 tests

- [ ] **Step 10: Commit**

```bash
git add trieshake-zip/clojure/src/trieshake_zip/sampler.clj \
        trieshake-zip/clojure/test/trieshake_zip/sampler_test.clj
git commit -m "feat(trieshake-zip): add sampler module with integration test"
```

---

## Task 4: Transformer Module

**Files:**
- Create: `trieshake-zip/clojure/src/trieshake_zip/transformer.clj`
- Create: `trieshake-zip/clojure/test/trieshake_zip/transformer_test.clj`

- [ ] **Step 1: Write failing test for collision tracking**

```clojure
(ns trieshake-zip.transformer-test
  (:require [clojure.test :refer :all]
            [trieshake-zip.transformer :as transformer]))

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `lein test trieshake-zip.transformer-test`
Expected: FAIL - "Could not locate trieshake_zip/transformer.clj"

- [ ] **Step 3: Write transformer.clj with collision tracking**

```clojure
(ns trieshake-zip.transformer
  "Transform mode: apply trieshake algorithm to zip entries."
  (:require [trieshake-zip.algorithm :as alg])
  (:import [java.util.zip ZipInputStream ZipOutputStream ZipEntry]
           [java.io FileInputStream FileOutputStream]))

(defn- insert-collision-suffix
  "Insert --collisionN before the file extension."
  [filename n]
  (let [dot-pos (clojure.string/last-index-of filename ".")]
    (if (and dot-pos (pos? dot-pos))
      (str (subs filename 0 dot-pos) "--collision" n (subs filename dot-pos))
      (str filename "--collision" n))))

(defn track-collision
  "Check if target path collides, update tracker, return final filename.

   tracker: atom of {\"dir/file.txt\" -> collision-count}
   target-path: full target path including filename

   Returns: filename with --collisionN suffix if needed."
  [tracker target-path]
  (let [current-count (get @tracker target-path 0)]
    (if (zero? current-count)
      (do
        (swap! tracker assoc target-path 0)
        ;; Extract filename from path
        (last (clojure.string/split target-path #"/")))
      (let [n current-count
            _ (swap! tracker assoc target-path n)
            filename (last (clojure.string/split target-path #"/"))]
        (insert-collision-suffix filename n)))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `lein test trieshake-zip.transformer-test`
Expected: PASS - 3 tests

- [ ] **Step 5: Write failing integration test for transform**

```clojure
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
```

- [ ] **Step 6: Write test helper create-test-zip**

```clojure
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
```

- [ ] **Step 7: Run test to verify it fails**

Run: `lein test trieshake-zip.transformer-test`
Expected: FAIL - "Unable to resolve symbol: transform"

- [ ] **Step 8: Implement transform function**

```clojure
(defn transform
  "Transform source-zip to output-zip using trieshake algorithm.

   Options:
     :prefix-length - chunk size (default 4)
     :encode-leafname - prepend prefix to filename (default true)
     :report - path to write collision report (optional)"
  [source-zip output-zip {:keys [prefix-length encode-leafname report]
                          :or {prefix-length 4 encode-leafname true}}]
  (let [collision-tracker (atom {})
        collisions (atom [])]
    (with-open [zis (ZipInputStream. (FileInputStream. source-zip))
                zos (ZipOutputStream. (FileOutputStream. output-zip))]
      (loop [processed 0]
        (if-let [entry (.getNextEntry zis)]
          (let [entry-name (.getName entry)]
            ;; Skip directory entries
            (if (.endsWith entry-name "/")
              (recur processed)
              (let [;; Parse path
                    {:keys [parents leafname]} (alg/parse-zip-path entry-name)
                    ;; Compute target
                    {:keys [target-dir target-filename]}
                    (alg/compute-target-path parents leafname prefix-length encode-leafname)
                    ;; Check collision
                    full-target (str target-dir "/" target-filename)
                    final-filename (track-collision collision-tracker full-target)
                    ;; Track if collision occurred
                    _ (when (not= final-filename target-filename)
                        (swap! collisions conj {:source entry-name
                                                :target full-target
                                                :actual (str target-dir "/" final-filename)}))
                    ;; Write entry
                    new-entry-name (str target-dir "/" final-filename)]
                (.putNextEntry zos (ZipEntry. new-entry-name))
                (let [buffer (byte-array 8192)]
                  (loop []
                    (let [n (.read zis buffer)]
                      (when (pos? n)
                        (.write zos buffer 0 n)
                        (recur)))))
                (.closeEntry zos)
                (recur (inc processed)))))
          ;; Done
          (let [result {:processed processed
                        :collisions (count @collisions)}]
            ;; Write collision report if requested
            (when (and report (seq @collisions))
              (spit report
                    (clojure.string/join "\n"
                      (map #(format "%s -> %s (collision, actual: %s)"
                                    (:source %) (:target %) (:actual %))
                           @collisions))))
            result))))
    ))
```

- [ ] **Step 9: Run test to verify it passes**

Run: `lein test trieshake-zip.transformer-test`
Expected: PASS - 4 tests

- [ ] **Step 10: Write failing test for collision handling**

```clojure
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
```

- [ ] **Step 11: Run test to verify it passes**

Run: `lein test trieshake-zip.transformer-test`
Expected: PASS - 5 tests

- [ ] **Step 12: Commit**

```bash
git add trieshake-zip/clojure/src/trieshake_zip/transformer.clj \
        trieshake-zip/clojure/test/trieshake_zip/transformer_test.clj
git commit -m "feat(trieshake-zip): add transformer module with collision handling"
```

---

## Task 5: CLI Module

**Files:**
- Create: `trieshake-zip/clojure/src/trieshake_zip/core.clj`

- [ ] **Step 1: Write core.clj with CLI parsing**

```clojure
(ns trieshake-zip.core
  "CLI entry point for trieshake-zip."
  (:require [clojure.tools.cli :as cli]
            [trieshake-zip.sampler :as sampler]
            [trieshake-zip.transformer :as transformer])
  (:gen-class))

(def cli-options
  [["-o" "--output FILE" "Output zip path (required)"]
   ["-p" "--prefix-length N" "Characters per chunk (transform mode, default 4)"
    :default 4
    :parse-fn #(Integer/parseInt %)]
   ["--max-files N" "Max files to extract (sample mode, default 100)"
    :default 100
    :parse-fn #(Integer/parseInt %)]
   ["--max-dirs N" "Max parent directories (sample mode, default 10)"
    :default 10
    :parse-fn #(Integer/parseInt %)]
   ["--no-encode-leafname" "Use plain leafnames (transform mode)"]
   ["--report FILE" "Write collision report (transform mode)"]
   ["-h" "--help" "Show help"]])

(defn- usage [summary]
  (clojure.string/join "\n"
    ["trieshake-zip — Apply trieshake algorithm to zip archives"
     ""
     "Usage: trieshake-zip <mode> <input.zip> [options]"
     ""
     "Modes:"
     "  sample      Extract subset of entries"
     "  transform   Apply trieshake algorithm"
     ""
     "Options:"
     summary]))

(defn- error-msg [errors]
  (str "Error:\n" (clojure.string/join "\n" errors)))

(defn- validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (error-msg errors)}

      (not= 2 (count arguments))
      {:exit-message "Error: Must specify mode and input zip\n\n" :ok? false}

      (not (:output options))
      {:exit-message "Error: --output is required\n\n" :ok? false}

      :else
      {:mode (first arguments)
       :input (second arguments)
       :options options})))

(defn- exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [mode input options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (try
        (case mode
          "sample"
          (let [result (sampler/sample input (:output options)
                                       {:max-dirs (:max-dirs options)
                                        :max-files (:max-files options)})]
            (println (format "Sampled %d files from %d directories"
                             (:files-written result)
                             (:dirs-included result))))

          "transform"
          (let [result (transformer/transform input (:output options)
                                              {:prefix-length (:prefix-length options)
                                               :encode-leafname (not (:no-encode-leafname options))
                                               :report (:report options)})]
            (println (format "Transformed %d files (%d collisions)"
                             (:processed result)
                             (:collisions result))))

          (exit 1 (str "Error: Unknown mode '" mode "'")))

        (catch Exception e
          (exit 1 (str "Error: " (.getMessage e))))))))
```

- [ ] **Step 2: Test CLI manually - sample mode**

Run:
```bash
cd trieshake-zip/clojure
# Create test zip first
lein run sample ../../METADATA.zip -o /tmp/sample.zip --max-files 10 --max-dirs 2
```

Expected: "Sampled 10 files from 2 directories"

- [ ] **Step 3: Verify sample output**

Run:
```bash
unzip -l /tmp/sample.zip | head -20
```

Expected: List of ~10 files from 2 parent directories

- [ ] **Step 4: Test CLI manually - transform mode**

Run:
```bash
lein run transform /tmp/sample.zip -o /tmp/transformed.zip -p 4
```

Expected: "Transformed 10 files (N collisions)"

- [ ] **Step 5: Verify transform output**

Run:
```bash
unzip -l /tmp/transformed.zip
```

Expected: Files with trieshake structure (chunked dirs, encoded filenames)

- [ ] **Step 6: Test help flag**

Run: `lein run -h`
Expected: Usage message with modes and options

- [ ] **Step 7: Commit**

```bash
git add trieshake-zip/clojure/src/trieshake_zip/core.clj
git commit -m "feat(trieshake-zip): add CLI with sample and transform modes"
```

---

## Task 6: Build and Package

**Files:**
- Modify: `trieshake-zip/clojure/project.clj` (already created in Task 1)

- [ ] **Step 1: Build uberjar**

Run: `cd trieshake-zip/clojure && lein uberjar`
Expected: JAR files created in `target/uberjar/`

- [ ] **Step 2: Test uberjar - sample mode**

Run:
```bash
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  sample ../../METADATA.zip -o /tmp/sample-jar.zip --max-files 20
```

Expected: "Sampled 20 files from N directories"

- [ ] **Step 3: Test uberjar - transform mode**

Run:
```bash
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  transform /tmp/sample-jar.zip -o /tmp/transformed-jar.zip -p 3
```

Expected: "Transformed 20 files (N collisions)"

- [ ] **Step 4: Verify transformed output**

Run: `unzip -l /tmp/transformed-jar.zip | head -15`
Expected: Trieshake structure with prefix-length 3

- [ ] **Step 5: Ensure build artifacts ignored**

Run: `echo "target/" >> trieshake-zip/clojure/.gitignore`
Expected: Build artifacts excluded from git

- [ ] **Step 6: Commit .gitignore**

```bash
git add trieshake-zip/clojure/.gitignore
git commit -m "chore(trieshake-zip): ignore build artifacts"
```

---

## Task 7: Documentation

**Files:**
- Create: `trieshake-zip/clojure/README.md`

- [ ] **Step 1: Write README**

```markdown
# trieshake-zip (Clojure)

Apply trieshake algorithm to zip archives without disk extraction.

## Usage

### Build

```bash
lein uberjar
```

### Sample Mode

Extract subset of entries from large zip:

```bash
# With lein
lein run sample input.zip -o sample.zip --max-files 100 --max-dirs 10

# With jar
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  sample input.zip -o sample.zip --max-files 100 --max-dirs 10
```

Options:
- `--max-files N` - Maximum files to extract (default 100)
- `--max-dirs N` - Maximum parent directories to sample (default 10)

### Transform Mode

Apply trieshake algorithm to reorganize zip contents:

```bash
# With lein
lein run transform input.zip -o output.zip -p 4

# With jar
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  transform input.zip -o output.zip -p 4
```

Options:
- `-p, --prefix-length N` - Characters per directory chunk (default 4)
- `--no-encode-leafname` - Use plain leafnames (no prefix encoding)
- `--report FILE` - Write collision report to file

## Examples

```bash
# Create sample from large archive
lein run sample METADATA.zip -o sample.zip

# Transform with default settings
lein run transform sample.zip -o transformed.zip

# Transform with prefix-length 3
lein run transform sample.zip -o out.zip -p 3

# Transform with plain leafnames
lein run transform sample.zip -o out.zip --no-encode-leafname

# Transform and save collision report
lein run transform sample.zip -o out.zip --report collisions.txt
```

## Testing

```bash
lein test
```

## Algorithm

See [trieshake SPEC.md](../../trieshake/SPEC.md) for algorithm details.

## License

CC-BY-SA 4.0
```

- [ ] **Step 2: Commit**

```bash
git add trieshake-zip/clojure/README.md
git commit -m "docs(trieshake-zip): add README with usage examples"
```

---

## Task 8: Final Integration Test

**Files:**
- Test with real METADATA.zip file

- [ ] **Step 1: Create sample from METADATA.zip**

Run:
```bash
cd trieshake-zip/clojure
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  sample ../../METADATA.zip -o /tmp/metadata-sample.zip --max-files 50 --max-dirs 5
```

Expected: "Sampled 50 files from 5 directories"

- [ ] **Step 2: Verify sample size**

Run: `ls -lh /tmp/metadata-sample.zip`
Expected: Much smaller than 1.4GB (probably <10MB)

- [ ] **Step 3: Transform sample**

Run:
```bash
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  transform /tmp/metadata-sample.zip -o /tmp/metadata-transformed.zip -p 4 --report /tmp/collisions.txt
```

Expected: "Transformed 50 files (N collisions)"

- [ ] **Step 4: Extract and inspect transformed zip**

Run:
```bash
mkdir -p /tmp/metadata-extracted
cd /tmp/metadata-extracted
unzip /tmp/metadata-transformed.zip
ls -R | head -30
```

Expected: Trieshake directory structure (chunked dirs, encoded filenames)

- [ ] **Step 5: Check collision report if exists**

Run: `cat /tmp/collisions.txt`
Expected: List of collisions (if any occurred) or empty file

- [ ] **Step 6: Verify file contents preserved**

Run:
```bash
# Pick a file from transformed zip
unzip -p /tmp/metadata-sample.zip "$(unzip -l /tmp/metadata-sample.zip | grep -m1 '.txt' | awk '{print $4}')" > /tmp/original.txt
# Find corresponding transformed file
find /tmp/metadata-extracted -name "*.txt" -type f | head -1 | xargs cat > /tmp/transformed.txt
# Compare
diff /tmp/original.txt /tmp/transformed.txt
```

Expected: No differences (files identical)

- [ ] **Step 7: Final commit**

```bash
git add -A
git commit -m "test(trieshake-zip): verify end-to-end workflow with METADATA.zip"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Sample mode: extract subset by dir/file count - Task 3
- [x] Transform mode: apply trieshake algorithm - Task 4
- [x] Algorithm: concat, chunk, encode - Task 2
- [x] CLI: modes, options, validation - Task 5
- [x] Error handling: validation, runtime errors - integrated in all tasks
- [x] Testing: unit + integration tests - Tasks 2, 3, 4
- [x] Build: uberjar - Task 6
- [x] Documentation: README - Task 7

**Placeholder check:**
- No TBD/TODO
- All code blocks complete
- All test assertions specific
- All commands have expected output

**Type consistency:**
- `chunk-string` returns vector - consistent
- `parse-zip-path` returns map with `:parents` and `:leafname` - consistent
- `compute-target-path` returns map with `:target-dir`, `:target-filename`, etc. - consistent
- `track-collision` takes atom and path, returns filename - consistent

---

## Execution Notes

- Each task builds incrementally
- Tests written before implementation (TDD)
- Frequent commits after each task
- Manual testing with real METADATA.zip in final task
- Zero external dependencies (stdlib only)
