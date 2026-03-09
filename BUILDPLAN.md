# trieshake — Build Plan for Claude Code

## Overview

This document is a build plan for implementing trieshake, a radix trie-based
filesystem reorganizer. The full specification is in `SPEC.md`.

This plan is written for Claude Code to follow as a step-by-step implementation
guide. Two implementations are described: Python (pragmatic) and Clojure (elegant).
Both should be built and tested.

---

## Architecture

### Core modules (language-agnostic)

The implementation has five distinct concerns, which should be separate
functions/namespaces regardless of language:

1. **Scanner** — Walk a directory tree, collect files matching an extension.
2. **Planner** — Given a list of files, compute the full move plan:
   - Extract parent segments from each file's relative path.
   - Concatenate segments into a key string.
   - Chunk the key at the configured prefix-length.
   - Determine target directory and filename (with or without encoding).
   - Detect collisions (duplicate target paths).
   - Resolve collisions with `--collisionN` suffixes.
   - Mark files already at their correct location.
3. **Executor** — Given a move plan, perform the filesystem operations:
   - Create target directories.
   - Move/rename files.
   - Handle unexpected collisions at move time.
   - Track successes, failures, and skips.
4. **Cleaner** — After execution, remove empty/obsolete directories.
5. **Reporter** — Print progress, summaries, collision reports, lost file
   reports, and optionally write the plan CSV.

### Data flow

```
CLI args
  → Scanner (collect files)
  → Planner (compute moves, detect collisions)
  → [optional] Plan CSV export
  → [if --execute] Executor (move files)
  → [if --execute] Verifier (check all files arrived)
  → [if --execute] Cleaner (remove old dirs)
  → Reporter (print summary)
```

The Planner is a pure function: files in, plan out. No side effects.
The Executor is the only module that touches the filesystem (besides Scanner).

---

## Implementation phases

### Phase 1: Core forward mode

**Goal:** Forward mode with `--execute` and dry run working end-to-end.

1. Implement CLI argument parsing (`-e`, `-p`, `--execute`, positional dir).
2. Implement Scanner: walk directory, filter by extension, return list of
   (absolute-path, relative-path) tuples/maps.
3. Implement the chunking algorithm:
   - Input: concat string + prefix-length.
   - Output: list of chunks (full-width chunks + remainder).
   - Unit test with cases from SPEC.md:
     - `BL0000010600001` at p=4 → `[BL00, 0001, 0600, 001]`
     - `BL0001` at p=3 → `[BL0, 001]`
     - `BL0001` at p=4 → `[BL00, 01]`
     - `ABCD` at p=2 → `[AB, CD]` (clean modulo)
4. Implement `compute_target`:
   - Extract parent segments from relative path.
   - Concatenate, chunk, build dir groups and encoded filename.
   - Unit test with all SPEC.md examples.
5. Implement collision detection:
   - Group files by target path.
   - First occurrence keeps target; subsequent get `--collisionN` suffix.
6. Implement dry run output: print example mappings, summary counts.
7. Implement Executor: create dirs, move files.
8. Implement Verifier: check all files exist at targets.
9. Implement Cleaner: remove empty dirs deepest-first.
10. Integration test: create a temp directory with known structure,
    run forward, verify output matches expected structure.

### Phase 2: Reverse mode

**Goal:** `--reverse` strips prefixes; `--reverse -p N` regroups in one pass.

1. Implement `compute_reverse_target`:
   - Read directory parts (excluding `collisions` dirs if present).
   - Build expected prefix string from dir parts.
   - Validate filename starts with prefix; skip if not.
   - Strip prefix to recover leafname.
   - Strip `--collisionN` suffix from leafname.
   - If `-p` provided: rejoin prefix groups into concat string,
     re-chunk at new prefix-length, build new target.
   - If `-p` not provided: target is same dir with plain leafname.
2. Unit test reverse with SPEC.md examples.
3. Integration test: forward at p=4, reverse, verify plain leafnames.
4. Integration test: forward at p=4, reverse -p 3, verify regrouped.
5. Round-trip test: forward → reverse → forward at different p,
   verify all files present and correctly placed.

### Phase 3: Options and reporting

**Goal:** `--no-encode-leafname`, `--output-plan`, `-e` optional.

1. Implement `--no-encode-leafname` flag:
   - When set, output filename is just the leafname (no prefix).
   - Affects both forward and reverse-with-rechunk.
   - Test round-trip with plain leafnames.
2. Implement `--output-plan` CSV export:
   - Write CSV with columns: source_path, target_path, leafname,
     concat_string, prefix_groups (pipe-separated), is_collision,
     collision_of, already_correct, action.
   - Written after planning, before execution.
   - UTF-8 with header row.
3. Make `-e` optional: if omitted, match all files.
4. Implement full Reporter output:
   - Step-by-step progress with emoji prefixes.
   - Collision report (stdout + log file).
   - Lost files report (stdout + log file).
   - Error report.
   - Directory breakdown.

### Phase 4: Edge cases and hardening

1. Handle files at root level (no parent dirs):
   - Forward: derive concat from filename stem.
   - Reverse: skip (no dir path to derive prefix from).
2. Handle underscores in original leafnames (test round-trip).
3. Handle empty directories, hidden files (`.DS_Store` etc.).
4. Handle very long paths (filesystem limits).
5. Handle permission errors gracefully.
6. Test with real SOBEK data if available.

---

## Test strategy

### Unit tests (pure functions, no filesystem)

- `chunk_string(s, n)` — all SPEC.md examples.
- `compute_target(rel_path, prefix_len, ext)` — forward mapping.
- `compute_reverse_target(rel_path, ext)` — reverse mapping.
- `compute_reverse_target(rel_path, ext, new_prefix_len)` — regroup.
- `get_collision_filename(name, ext, n)` — collision naming.
- Collision detection on a list of planned targets.

### Integration tests (with temp directories)

- Forward: known input → expected output structure.
- Forward with collisions: verify `--collisionN` files.
- Reverse: strip prefixes, verify plain leafnames.
- Reverse with `-p`: regroup, verify new structure.
- Round-trip: forward(p=4) → reverse(-p 3) → verify.
- `--no-encode-leafname`: forward and reverse round-trip.
- `--output-plan`: verify CSV contents match plan.
- Idempotency: running forward twice produces no changes on second run.
- Large-scale: generate 1000+ files, verify counts match.

---

## Python implementation

### Project structure

```
trieshake/
├── SPEC.md
├── BUILDPLAN.md
├── pyproject.toml
├── trieshake/
│   ├── __init__.py
│   ├── cli.py          # argparse, entry point
│   ├── scanner.py      # directory walking
│   ├── planner.py      # chunking, target computation, collision detection
│   ├── executor.py     # file moves
│   ├── cleaner.py      # directory cleanup
│   └── reporter.py     # output formatting, CSV export
├── tests/
│   ├── test_planner.py # unit tests for chunking and target computation
│   ├── test_scanner.py
│   └── test_integration.py  # end-to-end with temp dirs
└── README.md
```

### Dependencies

- Standard library only: `pathlib`, `shutil`, `argparse`, `csv`,
  `os`, `re`, `collections`, `datetime`.
- Test: `pytest`, `tempfile`.
- No external packages required.

### Entry point

```bash
pip install -e .
trieshake /data -e .txt -p 4 --execute
```

Or without install:

```bash
python -m trieshake /data -e .txt -p 4 --execute
```

---

## Clojure implementation

### Project structure

```
trieshake-clj/
├── project.clj         # lein project file
├── src/
│   └── trieshake/
│       ├── core.clj    # entry point, CLI parsing
│       ├── scanner.clj # directory walking
│       ├── planner.clj # chunking, targets, collisions (pure functions)
│       ├── executor.clj # file moves (side effects isolated here)
│       ├── cleaner.clj # directory cleanup
│       └── reporter.clj # output, CSV export
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
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/data.csv "1.1.0"]]
  :main trieshake.core
  :aot [trieshake.core]
  :profiles {:uberjar {:aot :all}})
```

### Key Clojure idioms to use

The planner is where Clojure shines. The core chunking is:

```clojure
(defn chunk-string [s n]
  (let [full (quot (count s) n)
        chunks (mapv #(subs s (* % n) (* (inc %) n)) (range full))
        remainder (subs s (* full n))]
    (if (seq remainder)
      (conj chunks remainder)
      chunks)))
```

The move plan is a sequence of maps — easy to filter, group, and transform:

```clojure
(->> files
     (map #(compute-target % prefix-length extension))
     (group-by :target-path)
     (mapcat resolve-collisions))
```

### Entry point

```bash
lein uberjar
java -jar target/trieshake-0.1.0-standalone.jar /data -e .txt -p 4 --execute
```

Or during development:

```bash
lein run /data -e .txt -p 4 --execute
```

### Babashka compatibility note

The Clojure implementation should be written to be Babashka-compatible
where possible (avoid Java interop beyond java.io and java.nio.file).
This would allow running as:

```bash
bb -m trieshake.core /data -e .txt -p 4 --execute
```

with near-instant startup. The main incompatibility would be
`clojure.tools.cli` (use `babashka.cli` instead) and AOT compilation
(not needed with bb). A `bb.edn` file could provide the Babashka entry
point alongside the lein project.

---

## Implementation notes for Claude Code

### General guidance

- Read SPEC.md thoroughly before starting each phase.
- The Planner must be a pure function (no side effects). All filesystem
  operations go through the Executor only.
- Write unit tests for the chunking algorithm FIRST, before implementing
  the rest. The chunking is the core logic and must be correct.
- Use the SPEC.md examples as test cases verbatim.
- Integration tests should create temp directories, run the tool,
  and assert on the resulting directory structure.
- Collisions are filename suffixes, NOT subdirectories. A collision
  produces `data--collision1.txt` in the SAME directory as `data.txt`.
- The `--execute` flag defaults to off. Without it, nothing moves.
- Extension matching is case-insensitive and suffix-based (not split
  on last dot), so `.mets.xml` works correctly.
- If `-e` is omitted, all files are matched.

### Python-specific

- Use `pathlib.Path` throughout, not `os.path`.
- Use `shutil.move` for cross-device moves.
- Use `argparse` for CLI.
- Use `csv.writer` for plan export.
- Target Python 3.9+ (walrus operator, type hints welcome but optional).

### Clojure-specific

- Use `clojure.java.io/file` and `java.nio.file.Files` for filesystem ops.
- Use `clojure.tools.cli/parse-opts` for CLI.
- Use `clojure.data.csv/write-csv` for plan export.
- Keep the planner namespace free of any `import` of `java.io` — it
  should operate purely on path strings and maps.
- Use `clojure.test` with `is` and `testing` for unit tests.
- Use `with-temp-dir` (or create a helper) for integration tests.

### Commit strategy

- Commit after each phase.
- Tag releases: `v0.1.0` (phase 1), `v0.2.0` (phase 2), etc.
- Each commit message should reference the phase and what was implemented.
