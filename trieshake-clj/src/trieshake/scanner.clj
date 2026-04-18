(ns trieshake.scanner
  "Scanner module — walk a directory tree, collect files matching an extension."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn scan-files
  "Walk base-dir and collect files matching the extension.
   Returns a sorted seq of {:abs-path abs :rel-path rel} maps."
  [base-dir extension]
  (let [base (io/file base-dir)
        base-path (.toPath base)
        all-files (file-seq base)]
    (->> all-files
         (filter #(.isFile %))
         ;; Skip hidden files (any path component starting with .)
         (remove (fn [f]
                   (let [rel (.relativize base-path (.toPath f))
                         parts (iterator-seq (.iterator rel))]
                     (some #(str/starts-with? (str %) ".") parts))))
         ;; Filter by extension (case-insensitive, suffix-based)
         (filter (fn [f]
                   (if extension
                     (str/ends-with? (str/lower-case (.getName f))
                                     (str/lower-case extension))
                     true)))
         ;; Build result maps
         (map (fn [f]
                {:abs-path (.getAbsolutePath f)
                 :rel-path (str (.relativize base-path (.toPath f)))}))
         (sort-by :rel-path)
         vec)))
