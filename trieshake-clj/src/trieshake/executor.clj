(ns trieshake.executor
  "Executor module — perform filesystem moves from a plan."
  (:require [clojure.java.io :as io]))

(defn execute-plan!
  "Execute the move plan: create directories and move files.
   Returns {:successes [...] :failures [...]} where failures are [plan error-msg]."
  [base-dir plans]
  (reduce
   (fn [acc plan]
     (let [source (io/file base-dir (:source-path plan))
           target-dir (io/file base-dir (:target-dir plan))
           target (io/file target-dir (:target-filename plan))]
       (if (= (.getCanonicalPath source) (.getCanonicalPath target))
         ;; Already at correct location
         (update acc :successes conj plan)
         (try
           (.mkdirs target-dir)
           (.renameTo source target)
           (when (not (.exists target))
             ;; renameTo can fail silently; fall back to copy+delete
             (io/copy source target)
             (.delete source))
           (update acc :successes conj plan)
           (catch Exception e
             (update acc :failures conj [plan (.getMessage e)]))))))
   {:successes [] :failures []}
   plans))

(defn verify-plan
  "Verify all files exist at their target locations.
   Returns list of plan entries for files that are missing."
  [base-dir plans]
  (filterv
   (fn [plan]
     (let [target (io/file base-dir (:target-dir plan) (:target-filename plan))
           source (io/file base-dir (:source-path plan))]
       (and (not (.exists target))
            (not (.exists source)))))
   plans))
