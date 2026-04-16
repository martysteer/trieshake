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
   [nil "--start-depth N" "Keep first N path levels, transform below (transform mode, default 0)"
    :default 0
    :parse-fn #(Integer/parseInt %)]
   [nil "--max-files N" "Max files to extract (sample mode, default 100)"
    :default 100
    :parse-fn #(Integer/parseInt %)]
   [nil "--max-dirs N" "Max parent directories (sample mode, default 10)"
    :default 10
    :parse-fn #(Integer/parseInt %)]
   [nil "--no-encode-leafname" "Use plain leafnames (transform mode)"]
   [nil "--exclude PATTERN" "Exclude files matching glob pattern (transform mode, repeatable)"
    :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   [nil "--report FILE" "Write collision report (transform mode)"]
   [nil "--execute" "Actually perform transformation (transform mode, default: dry run)"]
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

      ;; --output required for sample mode or transform with --execute
      (and (not (:output options))
           (or (= "sample" (first arguments))
               (and (= "transform" (first arguments)) (:execute options))))
      {:exit-message "Error: --output is required (unless transform without --execute)\n\n" :ok? false}

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
          (if (:execute options)
            ;; Execute mode - actually transform
            (let [result (transformer/transform input (:output options)
                                                {:prefix-length (:prefix-length options)
                                                 :start-depth (:start-depth options)
                                                 :encode-leafname (not (:no-encode-leafname options))
                                                 :exclude (:exclude options)
                                                 :report (:report options)})]
              (println (format "\nTransformed %d files (%d collisions, %d excluded)"
                               (:processed result)
                               (:collisions result)
                               (:excluded result))))
            ;; Dry run mode - preview only
            (transformer/preview-transform input
                                           {:prefix-length (:prefix-length options)
                                            :start-depth (:start-depth options)
                                            :encode-leafname (not (:no-encode-leafname options))
                                            :exclude (:exclude options)}))

          (exit 1 (str "Error: Unknown mode '" mode "'")))

        (catch Exception e
          (exit 1 (str "Error: " (.getMessage e))))))))
