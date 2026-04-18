(ns trieshake.core
  "Entry point and CLI parsing for trieshake."
  (:require [trieshake.scanner :as scanner]
            [trieshake.planner :as planner]
            [trieshake.executor :as executor]
            [trieshake.cleaner :as cleaner]
            [trieshake.reporter :as reporter]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))

(defn- normalize-extension
  "Ensure extension starts with a dot. Returns nil if input is nil."
  [ext]
  (when ext
    (if (str/starts-with? ext ".")
      ext
      (str "." ext))))

(defn- user-gave-prefix-length?
  "Check if the user explicitly passed -p / --prefix-length."
  [argv]
  (boolean
   (some #(or (= % "-p")
              (= % "--prefix-length")
              (str/starts-with? % "--prefix-length="))
         argv)))

(defn parse-args
  "Parse CLI arguments into an options map."
  [argv]
  (loop [args (vec argv)
         opts {:prefix-length 4
               :reverse false
               :execute false
               :no-encode-leafname false
               :user-gave-p (user-gave-prefix-length? argv)}]
    (if (empty? args)
      opts
      (let [arg (first args)
            rest-args (rest args)]
        (cond
          (or (= arg "-e") (= arg "--extension"))
          (recur (vec (rest rest-args))
                 (assoc opts :extension (first rest-args)))

          (or (= arg "-p") (= arg "--prefix-length"))
          (recur (vec (rest rest-args))
                 (assoc opts :prefix-length (Integer/parseInt (str (first rest-args)))))

          (= arg "--reverse")
          (recur (vec rest-args) (assoc opts :reverse true))

          (= arg "--execute")
          (recur (vec rest-args) (assoc opts :execute true))

          (= arg "--no-encode-leafname")
          (recur (vec rest-args) (assoc opts :no-encode-leafname true))

          (= arg "--output-plan")
          (recur (vec (rest rest-args))
                 (assoc opts :output-plan (first rest-args)))

          (or (= arg "-h") (= arg "--help"))
          (assoc opts :help true)

          :else
          (recur (vec rest-args) (assoc opts :directory arg)))))))

(defn run
  "Main logic — takes parsed opts, returns exit code."
  [opts]
  (cond
    (:help opts)
    (do
      (println "Usage: trieshake [-h] -e EXTENSION [-p PREFIX_LENGTH] [--reverse] [--execute] directory")
      (println)
      (println "Reorganize files into grouped, prefix-based directory trees.")
      (println)
      (println "Options:")
      (println "  -e, --extension EXT       File extension to match (e.g. .txt, .mets.xml)")
      (println "  -p, --prefix-length N     Characters per directory chunk (default: 4)")
      (println "  --reverse                 Strip encoded prefixes from filenames")
      (println "  --no-encode-leafname      Don't prepend prefix to filename")
      (println "  --output-plan FILE        Save move plan as CSV")
      (println "  --execute                 Actually move files (default: dry run)")
      0)

    (nil? (:directory opts))
    (do
      (binding [*out* *err*]
        (println "Error: directory argument required."))
      1)

    (not (.isDirectory (io/file (:directory opts))))
    (do
      (binding [*out* *err*]
        (println (str "Error: " (:directory opts) " is not a directory.")))
      1)

    :else
    (let [base-dir (:directory opts)
          extension (normalize-extension (:extension opts))
          prefix-length (:prefix-length opts)
          reverse? (:reverse opts)
          execute? (:execute opts)
          encode-leafname (not (:no-encode-leafname opts))
          user-gave-p (:user-gave-p opts)
          files (scanner/scan-files base-dir extension)]

      (reporter/print-scan-result (count files))

      (if (empty? files)
        (do (println "Nothing to do.") 0)

        (let [plans (->> files
                         (map (fn [{:keys [rel-path]}]
                                (if reverse?
                                  (let [new-p (when user-gave-p prefix-length)]
                                    (planner/compute-reverse-target
                                     rel-path extension
                                     :new-prefix-length new-p
                                     :encode-leafname encode-leafname))
                                  (planner/compute-target
                                   rel-path prefix-length extension
                                   :encode-leafname encode-leafname))))
                         (remove nil?)
                         vec)
              plans (planner/detect-collisions plans)]

          ;; Optional CSV export
          (when-let [output-plan (:output-plan opts)]
            (reporter/export-plan-csv plans output-plan)
            (println (str "\uD83D\uDCBE Plan exported to " output-plan)))

          (reporter/print-plan-summary plans (not execute?))

          (if-not execute?
            0
            (let [{:keys [successes failures]} (executor/execute-plan! base-dir plans)
                  _ (reporter/print-move-result successes failures)
                  lost (executor/verify-plan base-dir plans)
                  _ (reporter/print-verify-result lost)]

              (if (empty? lost)
                (let [removed (cleaner/cleanup-directories! base-dir extension)]
                  (reporter/print-cleanup-result removed))
                (println "\u26A0\uFE0F  Skipping cleanup \u2014 files are missing."))

              (reporter/print-collision-report plans)

              (if (or (seq failures) (seq lost)) 1 0))))))))

(defn -main [& args]
  (try
    (let [opts (parse-args args)
          exit-code (run opts)]
      (System/exit (or exit-code 0)))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "Error: " (.getMessage e))))
      (System/exit 1))))
