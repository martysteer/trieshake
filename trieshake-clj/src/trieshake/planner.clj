(ns trieshake.planner
  "Planner module — chunking, target computation, collision detection.
   Pure functions: no filesystem side effects.")

(defn chunk-string
  "Split a concat string into chunks of prefix-length, with a shorter remainder."
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

(defn- strip-extension
  "Strip a (possibly multi-part) extension from a filename.
   Returns [stem ext] where ext includes the leading dot."
  [filename extension]
  (let [lower (clojure.string/lower-case filename)
        ext-lower (clojure.string/lower-case extension)]
    (if (and (seq extension) (clojure.string/ends-with? lower ext-lower))
      [(subs filename 0 (- (count filename) (count extension)))
       (subs filename (- (count filename) (count extension)))]
      [filename ""])))

(defn- strip-collision-suffix
  "Remove --collisionN suffix from a leafname.
   E.g. 'data--collision1.txt' -> 'data.txt'"
  [leafname]
  (clojure.string/replace leafname #"--collision\d+" ""))

(defn- collision-filename
  "Insert --collisionN before the extension."
  [filename extension n]
  (let [[stem ext] (strip-extension filename extension)
        [stem ext] (if (empty? ext)
                     ;; Fallback: split on last dot
                     (let [dot-pos (clojure.string/last-index-of filename ".")]
                       (if (and dot-pos (pos? dot-pos))
                         [(subs filename 0 dot-pos) (subs filename dot-pos)]
                         [filename ""]))
                     [stem ext])]
    (str stem "--collision" n ext)))

(defn- path-parts
  "Split a path string into its components."
  [path-str]
  (let [parts (clojure.string/split path-str #"/")]
    (filterv (complement empty?) parts)))

(defn- join-path
  "Join path components with /."
  [& parts]
  (clojure.string/join "/" (flatten parts)))

(defn compute-target
  "Compute the forward-mode target for a file at rel-path.
   Returns a plan entry map."
  [rel-path prefix-length extension & {:keys [encode-leafname] :or {encode-leafname true}}]
  (let [parts (path-parts rel-path)
        filename (last parts)
        parent-segments (butlast parts)
        [concat-string leafname]
        (if (seq parent-segments)
          [(apply str parent-segments) filename]
          ;; Root-level file: use filename stem as concat string
          (let [[stem _] (strip-extension filename extension)]
            [stem filename]))
        chunks (chunk-string concat-string prefix-length)
        target-dir (if (seq chunks) (join-path chunks) ".")
        target-filename (if (and encode-leafname (seq chunks))
                          (str (clojure.string/join "_" chunks) "_" leafname)
                          leafname)]
    {:source-path rel-path
     :target-dir target-dir
     :target-filename target-filename
     :chunks chunks
     :concat-string concat-string
     :leafname leafname
     :extension extension
     :is-collision false
     :collision-of nil}))

(defn compute-reverse-target
  "Compute the reverse-mode target for a file at rel-path.
   Returns a plan entry map, or nil if the file should be skipped."
  [rel-path extension & {:keys [new-prefix-length encode-leafname]
                         :or {new-prefix-length nil encode-leafname true}}]
  (let [parts (path-parts rel-path)
        filename (last parts)
        dir-parts (butlast parts)]
    ;; Root-level files are skipped in reverse mode
    (when (seq dir-parts)
      ;; Filter out 'collisions' directory segments
      (let [prefix-groups (filterv #(not= % "collisions") dir-parts)]
        (when (seq prefix-groups)
          (let [expected-prefix (str (clojure.string/join "_" prefix-groups) "_")]
            ;; Validate filename starts with expected prefix
            (when (clojure.string/starts-with? filename expected-prefix)
              (let [leafname (subs filename (count expected-prefix))
                    leafname (strip-collision-suffix leafname)
                    concat-string (apply str prefix-groups)]
                (if new-prefix-length
                  ;; Regroup: re-chunk at new prefix length
                  (let [chunks (chunk-string concat-string new-prefix-length)
                        target-dir (if (seq chunks) (join-path chunks) ".")
                        target-filename (if (and encode-leafname (seq chunks))
                                          (str (clojure.string/join "_" chunks) "_" leafname)
                                          leafname)]
                    {:source-path rel-path
                     :target-dir target-dir
                     :target-filename target-filename
                     :chunks chunks
                     :concat-string concat-string
                     :leafname leafname
                     :extension extension
                     :is-collision false
                     :collision-of nil})
                  ;; No regroup: keep same directory, just strip prefix from filename
                  {:source-path rel-path
                   :target-dir (join-path prefix-groups)
                   :target-filename leafname
                   :chunks (vec prefix-groups)
                   :concat-string concat-string
                   :leafname leafname
                   :extension extension
                   :is-collision false
                   :collision-of nil})))))))))

(defn detect-collisions
  "Detect and resolve collisions in a list of plan entries.
   First occurrence keeps its target; subsequent get --collisionN suffixes."
  [plans]
  (let [seen (atom {})]  ; target-path -> collision count
    (mapv (fn [plan]
            (let [target-key (str (:target-dir plan) "/" (:target-filename plan))]
              (if-not (contains? @seen target-key)
                (do (swap! seen assoc target-key 0)
                    plan)
                (let [n (inc (get @seen target-key))
                      _ (swap! seen assoc target-key n)
                      new-filename (collision-filename (:target-filename plan) (:extension plan) n)]
                  (assoc plan
                         :target-filename new-filename
                         :is-collision true
                         :collision-of target-key)))))
          plans)))
