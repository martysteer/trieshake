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
              (if (and (<= (count new-dirs-seen) max-dirs)
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
