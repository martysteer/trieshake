(ns trieshake.reporter
  "Reporter module — output formatting and plan CSV export."
  (:require [clojure.string :as str]))

(defn print-scan-result [count]
  (println (str "\uD83D\uDD0D Scanning \u2014 found " count " matching files.")))

(defn print-plan-summary [plans dry-run?]
  (let [total (count plans)
        moves (count (filter (fn [p]
                               (not= (:source-path p)
                                     (str (:target-dir p) "/" (:target-filename p))))
                             plans))
        skips (- total moves)
        collisions (count (filter :is-collision plans))]
    (println (str "\uD83D\uDCCB Building plan \u2014 " total " files total."))
    (println (str "   " moves " to move, " skips " already correct."))
    (when (pos? collisions)
      (println (str "   \u26A0\uFE0F  " collisions " collisions detected.")))
    (when (seq plans)
      (let [p (first plans)]
        (println (str "   Example: " (:source-path p) " -> "
                      (:target-dir p) "/" (:target-filename p)))))
    (when dry-run?
      (println (str "\n\uD83D\uDEAB Dry run \u2014 no files moved. Use --execute to apply.")))))

(defn print-move-result [successes failures]
  (println (str "\uD83D\uDCE6 Moving files \u2014 " (count successes) " succeeded, "
                (count failures) " failed."))
  (doseq [[plan err] failures]
    (println (str "   \u274C " (:source-path plan) ": " err))))

(defn print-verify-result [lost]
  (if (seq lost)
    (do
      (println (str "\u26A0\uFE0F  Verification \u2014 " (count lost) " files lost!"))
      (doseq [p lost]
        (println (str "   Missing: " (:target-dir p) "/" (:target-filename p)))))
    (println "\u2705 Verification \u2014 all files accounted for.")))

(defn print-cleanup-result [removed]
  (println (str "\uD83E\uDDF9 Cleanup \u2014 removed " (count removed) " directories.")))

(defn print-collision-report [plans]
  (let [collisions (filter :is-collision plans)]
    (when (seq collisions)
      (println (str "\n\uD83D\uDCCB Collision report (" (count collisions) " collisions):"))
      (doseq [p collisions]
        (println (str "   " (:source-path p) " -> "
                      (:target-dir p) "/" (:target-filename p)))
        (println (str "      collides with: " (:collision-of p)))))))

(defn- csv-quote
  "Quote a field for CSV output if it contains commas, quotes, or newlines."
  [s]
  (let [s (str s)]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn export-plan-csv
  "Write the move plan to a CSV file."
  [plans output-path]
  (let [header "source_path,target_path,leafname,concat_string,prefix_groups,is_collision,collision_of,already_correct,action"
        rows (map (fn [p]
                    (let [target-path (str (:target-dir p) "/" (:target-filename p))
                          already-correct (= (:source-path p) target-path)
                          action (cond
                                   (:is-collision p) "collision"
                                   already-correct "skip"
                                   :else "move")]
                      (str/join ","
                                (map csv-quote
                                     [(:source-path p)
                                      target-path
                                      (:leafname p)
                                      (:concat-string p)
                                      (str/join "|" (:chunks p))
                                      (str/lower-case (str (:is-collision p)))
                                      (or (:collision-of p) "")
                                      (str/lower-case (str already-correct))
                                      action]))))
                  plans)]
    (spit output-path (str header "\n" (str/join "\n" rows) "\n"))))
