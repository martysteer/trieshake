(ns trieshake.cleaner
  "Cleaner module — remove empty/obsolete directories after moves."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn cleanup-directories!
  "Remove directories that are empty or contain no files matching extension.
   Directories are removed deepest-first.
   Returns list of removed directory paths (relative to base-dir)."
  [base-dir extension]
  (let [base (io/file base-dir)
        base-path (.toPath base)
        ;; Collect all directories, sorted deepest-first
        all-dirs (->> (file-seq base)
                      (filter #(.isDirectory %))
                      (remove #(= (.getCanonicalPath %) (.getCanonicalPath base)))
                      (sort-by #(.getNameCount (.toPath %))
                               >))]
    (reduce
     (fn [removed d]
       (let [contents (seq (.listFiles d))
             ;; Check if directory has any non-hidden files or subdirs
             has-matching (some (fn [f]
                                 (cond
                                   (str/starts-with? (.getName f) ".") false
                                   (.isDirectory f) true
                                   (.isFile f) (if extension
                                                 (str/ends-with?
                                                  (str/lower-case (.getName f))
                                                  (str/lower-case extension))
                                                 true)
                                   :else false))
                               contents)]
         (if has-matching
           removed
           (let [rel (str (.relativize base-path (.toPath d)))]
             (try
               ;; Remove hidden files first (e.g. .DS_Store)
               (doseq [f (.listFiles d)
                        :when (and (.isFile f) (str/starts-with? (.getName f) "."))]
                 (.delete f))
               (.delete d)
               (conj removed rel)
               (catch Exception _
                 removed))))))
     []
     all-dirs)))
