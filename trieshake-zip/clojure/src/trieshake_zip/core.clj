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
   [nil "--strip-prefix N" "Strip N leading path components (transform mode, default 0)"
    :default 0
    :parse-fn #(Integer/parseInt %)]
   [nil "--max-files N" "Max files to extract (sample mode, default 100)"
    :default 100
    :parse-fn #(Integer/parseInt %)]
   [nil "--max-dirs N" "Max parent directories (sample mode, default 10)"
    :default 10
    :parse-fn #(Integer/parseInt %)]
   [nil "--no-encode-leafname" "Use plain leafnames (transform mode)"]
   [nil "--report FILE" "Write collision report (transform mode)"]
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
                                               :strip-prefix (:strip-prefix options)
                                               :encode-leafname (not (:no-encode-leafname options))
                                               :report (:report options)})]
            (println (format "Transformed %d files (%d collisions)"
                             (:processed result)
                             (:collisions result))))

          (exit 1 (str "Error: Unknown mode '" mode "'")))

        (catch Exception e
          (exit 1 (str "Error: " (.getMessage e))))))))
