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

(defn strip-prefix-from-parents
  "Strip N leading path components from parents vector.
   Returns stripped parents (may be empty if N >= parent count)."
  [parents n]
  (if (and (pos? n) (>= (count parents) n))
    (vec (drop n parents))
    (if (pos? n)
      [] ;; n > parent count, strip all
      parents)))

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
