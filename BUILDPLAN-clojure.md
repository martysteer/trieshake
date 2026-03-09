# trieshake — Clojure Build Plan for Claude Code

## Overview

This document is a build plan for implementing trieshake in Clojure.
The full specification is in `SPEC.md`. This plan is written for
Claude Code to follow as a step-by-step implementation guide.

**Zero external dependencies: Clojure core only.**

Babashka compatibility is a secondary goal — the implementation should
avoid unnecessary Java interop so it can also run via `bb` for instant
startup.

---

## Architecture

### Core namespaces

The implementation has five distinct concerns, each a separate namespace:

1. **trieshake.scanner** — Walk a directory tree, collect files matching
   an extension.
2. **trieshake.planner** — Given a list of files, compute the full move
   plan. This namespace is **pure** — no side effects, no `java.io`
   imports. It operates on path strings and maps only.
   - Extract parent segments from each file's relative path.
   - Concatenate segments into a key string.
   - Chunk the key at the configured prefix-length.
   - Determine target directory and filename (with or without encoding).
   - Detect collisions (duplicate target paths).
   - Resolve collisions with `--collisionN` suffixes.
   - Mark files already at their correct location.
3. **trieshake.executor** — Given a move plan, perform filesystem
   operations. This is the **only** namespace with side effects
   (besides scanner).
4. **trieshake.cleaner** — Remove empty/obsolete directories.
5. **trieshake.reporter** — Print progress, summaries, collision reports,
   and optionally write the plan CSV.

### Data flow

```
CLI args
  → scanner/scan (collect files)
  → planner/plan (compute moves, detect collisions)
  → [optional] reporter/write-plan-csv
  → [if --execute] executor/execute! (move files)
  → [if --execute] executor/verify (check all files arrived)
  → [if --execute] cleaner/clean! (remove old dirs)
  → reporter/report (print summary)
```

---

## Project structure

```
trieshake-clj/
├── project.clj
├── bb.edn              # Babashka entry point (optional)
├── src/
│   └── trieshake/
│       ├── core.clj    # entry point, CLI parsing
│       ├── scanner.clj
│       ├── planner.clj # pure functions only
│       ├── executor.clj
│       ├── cleaner.clj
│       └── reporter.clj
├── test/
│   └── trieshake/
│       ├── planner_test.clj
│       └── integration_test.clj
└── README.md
```

### Dependencies

```clojure
;; project.clj
(defproject trieshake "0.1.0"
  :description "Radix trie-based filesystem reorganizer"
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :main trieshake.core
  :aot [trieshake.core]
  :profiles {:uberjar {:aot :all}})
```

No external libraries. CLI parsing and CSV writing are hand-rolled:

- **CLI parsing:** Walk the args vector, match flags with a simple
  `loop/recur` or `reduce`. Clojure destructuring handles the rest.
  For a CLI with 6 flags this is cleaner than pulling in `tools.cli`.
- **CSV export:** A ~5 line function that quotes fields containing
  commas/quotes/newlines and joins with commas. Output-only, no
  parsing needed.

### Entry point

```bash
lein uberjar
java -jar target/trieshake-0.1.0-standalone.jar /data -e .txt -p 4 --execute
```

During development:

```bash
lein run /data -e .txt -p 4 --execute
```

Babashka (if compatible):

```bash
bb -m trieshake.core /data -e .txt -p 4 --execute
```

---

## Key Clojure idioms

The planner is where Clojure shines. The core chunking:

```clojure
(defn chunk-string [s n]
  (let [full (quot (count s) n)
        chunks (mapv #(subs s (* % n) (* (inc %) n)) (range full))
        remainder (subs s (* full n))]
    (if (seq remainder)
      (conj chunks remainder)
      chunks)))
```

The move plan as a data pipeline:

```clojure
(->> files
     (map #(compute-target % prefix-length extension))
     (group-by :target-path)
     (mapcat resolve-collisions))
```

Each file in the plan is a map:

```clojure
{:source-path "BL/00/01/data.txt"
 :target-path "BL00/01/BL00_01_data.txt"
 :leafname "data.txt"
 :concat-string "BL0001"
 :prefix-groups ["BL00" "01"]
 :collision? false
 :already-correct? false
 :action :move}
```

---

## Implementation phases

### Phase 1: Core forward mode

**Goal:** Forward mode with `--execute` and dry run working end-to-end.

1. Implement CLI parsing: `reduce` over args vector, match flags
   (`-e`, `-p`, `--execute`, `--reverse`, etc.), collect into opts map.
2. Implement `scanner/scan`: walk directory via `file-seq`,
   filter by extension, return seq of maps with `:abs-path`
   and `:rel-path` (as strings, not File objects).
3. Implement `planner/chunk-string`:
   - Input: string + prefix-length.
   - Output: vector of chunks.
   - Unit test with SPEC.md cases.
4. Implement `planner/compute-target`:
   - Extract parent segments, concatenate, chunk.
   - Build dir groups and encoded filename.
   - Return a map (the plan entry).
5. Implement `planner/detect-collisions`:
   - Group plan entries by `:target-path`.
   - First keeps target; rest get `--collisionN` suffix.
6. Implement `planner/plan` — orchestrate the above, return full plan.
7. Implement dry run output via `reporter/report`.
8. Implement `executor/execute!`: create dirs, move files.
9. Implement `executor/verify`: check all targets exist.
10. Implement `cleaner/clean!`: remove empty dirs deepest-first.
11. Integration test with temp dirs.

### Phase 2: Reverse mode

**Goal:** `--reverse` strips prefixes; `--reverse -p N` regroups.

1. Implement `planner/compute-reverse-target`:
   - Read dir parts from rel-path.
   - Build expected prefix, validate, strip.
   - Strip `--collisionN` suffix.
   - If new prefix-length given: rejoin, re-chunk, build new target.
2. Unit test reverse with SPEC.md examples.
3. Integration test: forward → reverse → verify.
4. Integration test: forward → reverse -p 3 → verify regrouped.
5. Round-trip test.

### Phase 3: Options and reporting

**Goal:** `--no-encode-leafname`, `--output-plan`, `-e` optional.

1. `--no-encode-leafname`: conditional in `compute-target`.
2. `--output-plan`: hand-roll CSV writer (quote, join, spit).
3. Make `-e` optional: if nil, match all files in scanner.
4. Full reporter output with collision/lost/error reports.

### Phase 4: Edge cases and hardening

1. Files at root level, underscores in leafnames.
2. Empty directories, hidden files.
3. Permission errors, long paths.
4. Test with real SOBEK data if available.

---

## Test strategy

### Unit tests (planner_test.clj — pure, no filesystem)

- `chunk-string` — all SPEC.md examples.
- `compute-target` — forward mapping.
- `compute-reverse-target` — reverse mapping and regroup.
- `get-collision-filename` — collision naming.
- `detect-collisions` on a list of plan entries.

### Integration tests (integration_test.clj — with temp dirs)

- Forward: known input → expected output.
- Forward with collisions: verify `--collisionN` files.
- Reverse: strip prefixes, verify plain leafnames.
- Reverse with `-p`: regroup, verify new structure.
- Round-trip: forward(p=4) → reverse(-p 3) → verify.
- `--no-encode-leafname`: round-trip.
- `--output-plan`: verify CSV contents.
- Idempotency: forward twice → no changes second run.
- Large-scale: generate 1000+ files, verify counts.

Helper for temp dirs:

```clojure
(defmacro with-temp-dir [sym & body]
  `(let [~sym (java.nio.file.Files/createTempDirectory
                "trieshake-test"
                (into-array java.nio.file.attribute.FileAttribute []))]
     (try ~@body
       (finally
         ;; recursive delete
         ))))
```

---

## Implementation notes for Claude Code

- Read SPEC.md thoroughly before starting each phase.
- The planner namespace must be **pure**: no `java.io` imports,
  no side effects. It operates on strings and maps only.
- Write unit tests for `chunk-string` FIRST.
- Use the SPEC.md examples as test cases verbatim.
- Collisions are filename suffixes, NOT subdirectories.
- The `--execute` flag defaults to off.
- Extension matching is case-insensitive and suffix-based.
- If `-e` is omitted, all files are matched.
- Use `clojure.java.io/file` and `java.nio.file.Files` for filesystem
  ops in executor and scanner only.
- Hand-roll CLI parsing: `reduce` over args vector, match known flags,
  collect into an options map. No external library needed.
- Hand-roll CSV writing: quote fields, join with commas, write with
  `spit`. No external library needed.
- Use `clojure.test` with `is` and `testing`.
- For Babashka compatibility: avoid Java interop beyond `java.io`
  and `java.nio.file`. A `bb.edn` file can provide the entry point
  alongside the lein project.

### Commit strategy

- Commit after each phase.
- Tag releases: `v0.1.0` (phase 1), `v0.2.0` (phase 2), etc.
- Each commit message should reference the phase and what was implemented.
