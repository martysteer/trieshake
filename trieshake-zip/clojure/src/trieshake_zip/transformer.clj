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
  (if (contains? @tracker target-path)
    ;; Collision - increment counter and add suffix
    (let [current-count (get @tracker target-path)
          new-count (inc current-count)
          _ (swap! tracker assoc target-path new-count)
          filename (last (clojure.string/split target-path #"/"))]
      (insert-collision-suffix filename new-count))
    ;; First occurrence - no suffix
    (do
      (swap! tracker assoc target-path 0)
      (last (clojure.string/split target-path #"/")))))

(defn transform
  "Transform source-zip to output-zip using trieshake algorithm.

   Options:
     :prefix-length - chunk size (default 4)
     :start-depth - keep first N path components, transform below (default 0)
     :encode-leafname - prepend prefix to filename (default true)
     :report - path to write collision report (optional)"
  [source-zip output-zip {:keys [prefix-length start-depth encode-leafname report]
                          :or {prefix-length 4 start-depth 0 encode-leafname true}}]
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
                    ;; Check if file is above start-depth threshold
                    below-threshold? (< (count parents) start-depth)
                    ;; If below threshold, pass through unchanged
                    new-entry-name (if below-threshold?
                                     entry-name
                                     ;; Otherwise, split and transform
                                     (let [{:keys [prefix to-transform]} (alg/split-at-depth parents start-depth)
                                           {:keys [target-dir target-filename]}
                                           (alg/compute-target-path to-transform leafname prefix-length encode-leafname prefix)
                                           full-target (str target-dir "/" target-filename)
                                           final-filename (track-collision collision-tracker full-target)]
                                       ;; Track if collision occurred
                                       (when (not= final-filename target-filename)
                                         (swap! collisions conj {:source entry-name
                                                                 :target full-target
                                                                 :actual (str target-dir "/" final-filename)}))
                                       (str target-dir "/" final-filename)))]
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

(defn preview-transform
  "Preview transformation without writing output. Shows what would happen.

   Options: same as transform"
  [source-zip {:keys [prefix-length start-depth encode-leafname]
               :or {prefix-length 4 start-depth 0 encode-leafname true}}]
  (let [collision-tracker (atom {})
        collisions (atom [])
        transformations (atom [])]
    (with-open [zis (ZipInputStream. (FileInputStream. source-zip))]
      (loop [processed 0
             skipped 0]
        (if-let [entry (.getNextEntry zis)]
          (let [entry-name (.getName entry)]
            ;; Skip directory entries
            (if (.endsWith entry-name "/")
              (recur processed (inc skipped))
              (let [;; Parse path
                    {:keys [parents leafname]} (alg/parse-zip-path entry-name)
                    ;; Check if file is above start-depth threshold
                    below-threshold? (< (count parents) start-depth)]
                (if below-threshold?
                  ;; Pass through unchanged
                  (do
                    (swap! transformations conj {:source entry-name
                                                 :target entry-name
                                                 :action "pass-through"})
                    (recur (inc processed) skipped))
                  ;; Transform
                  (let [{:keys [prefix to-transform]} (alg/split-at-depth parents start-depth)
                        {:keys [target-dir target-filename]}
                        (alg/compute-target-path to-transform leafname prefix-length encode-leafname prefix)
                        full-target (str target-dir "/" target-filename)
                        final-filename (track-collision collision-tracker full-target)
                        new-entry-name (str target-dir "/" final-filename)]
                    ;; Track collision if occurred
                    (when (not= final-filename target-filename)
                      (swap! collisions conj {:source entry-name
                                              :target full-target
                                              :actual new-entry-name}))
                    ;; Record transformation
                    (swap! transformations conj {:source entry-name
                                                 :target new-entry-name
                                                 :action (if (not= final-filename target-filename)
                                                           "transform-collision"
                                                           "transform")})
                    (recur (inc processed) skipped))))))
          ;; Done - print preview
          (let [result {:processed processed
                        :skipped skipped
                        :collisions (count @collisions)
                        :transformations @transformations}]
            ;; Print preview
            (println "\n=== DRY RUN: Preview of transformations ===\n")
            (println (format "Files to process: %d" processed))
            (println (format "Directory entries skipped: %d" skipped))
            (println (format "Collisions: %d\n" (count @collisions)))

            ;; Show sample transformations
            (println "Sample transformations (first 20):")
            (doseq [{:keys [source target action]} (take 20 @transformations)]
              (case action
                "pass-through" (println (format "  PASS: %s" source))
                "transform" (println (format "  %s\n    -> %s" source target))
                "transform-collision" (println (format "  %s\n    -> %s [COLLISION]" source target))))

            (when (> (count @transformations) 20)
              (println (format "\n... and %d more transformations" (- (count @transformations) 20))))

            ;; Show collisions if any
            (when (seq @collisions)
              (println "\nCollisions:")
              (doseq [{:keys [source target actual]} @collisions]
                (println (format "  %s\n    -> %s\n    ACTUAL: %s" source target actual))))

            result)))
      )))
