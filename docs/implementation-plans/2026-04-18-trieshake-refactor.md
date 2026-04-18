# trieshake Repository Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize trieshake repository to enable root-level tool invocation, flatten directory structure, and consolidate documentation.

**Architecture:** Six-phase incremental refactor using git-based approach. Each phase independently testable and committable. Preserves git history via `git mv`, enables rollback per commit.

**Tech Stack:** Bash, Git, Leiningen, Python setuptools, Make

---

## Phase 1: Consolidate Documentation

### Task 1: Move reference documents to docs/

**Files:**
- Create: `docs/` directory structure
- Move: SPEC.md, BUILDPLAN files, design spec, implementation plans
- Verify: git history preserved

- [ ] **Step 1: Create docs/implementation-plans directory**

```bash
cd trieshake
mkdir -p docs/implementation-plans
git add -A
```

- [ ] **Step 2: Move SPEC.md to docs/**

```bash
git mv SPEC.md docs/
```

- [ ] **Step 3: Move BUILDPLAN files to docs/**

```bash
git mv BUILDPLAN-python.md docs/
git mv BUILDPLAN-clojure.md docs/
```

- [ ] **Step 4: Move trieshake-zip design spec to docs/**

```bash
git mv docs/superpowers/specs/2026-04-15-trieshake-zip-design.md docs/trieshake-zip-design.md
```

- [ ] **Step 5: Move implementation plans to docs/implementation-plans**

```bash
git mv docs/superpowers/plans/* docs/implementation-plans/ 2>/dev/null || true
```

Note: If superpowers directory becomes empty, it will be left as-is (not deleted yet).

- [ ] **Step 6: Verify git history preserved**

```bash
git log --follow docs/SPEC.md | head -5
```

Expected: Shows commits that modified/created SPEC.md in old location.

- [ ] **Step 7: Commit documentation consolidation**

```bash
git commit -m "$(cat <<'EOF'
docs: consolidate reference material to docs/

Move SPEC, BUILDPLANs, design specs, and implementation plans
to centralized docs/ directory. Update references in README.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2: Flatten Directory Structure

### Task 2: Create refactor branch and flatten directories

**Files:**
- Move: `clojure/` → `trieshake-clj/`
- Move: `python/` → `trieshake-py/`
- Move: `trieshake-zip/clojure/` → `trieshake-zip-clj/`
- Create: `trieshake-zip-py/` placeholder
- Delete: `trieshake/` and `trieshake-zip/` parent dirs

- [ ] **Step 1: Create and checkout refactor branch**

```bash
git checkout -b refactor/repo-structure
```

- [ ] **Step 2: Move Clojure trieshake to trieshake-clj**

```bash
git mv clojure ../trieshake-clj
```

- [ ] **Step 3: Move Python trieshake to trieshake-py**

```bash
git mv python ../trieshake-py
```

- [ ] **Step 4: Move trieshake-zip/clojure to trieshake-zip-clj**

```bash
git mv trieshake-zip/clojure ../trieshake-zip-clj
```

- [ ] **Step 5: Verify no directories left in trieshake/**

```bash
ls -la trieshake/
```

Expected: Only shows `.`, `..`, `.git`, `.gitignore`, `docs/` — no code directories.

- [ ] **Step 6: Delete empty trieshake directory**

```bash
cd ..
rm -rf trieshake/
```

- [ ] **Step 7: Create trieshake-zip-py placeholder**

```bash
mkdir -p trieshake-zip-py
cat > trieshake-zip-py/README.md << 'EOF'
# trieshake-zip (Python)

Placeholder for future Python implementation.

See `../docs/trieshake-zip-design.md` for specification.

## Implementation Status

- [x] Specification complete
- [ ] Python implementation
- [ ] Tests
- [ ] Integration tests

For now, use the Clojure implementation in `../trieshake-zip-clj/`.
EOF
git add trieshake-zip-py/README.md
```

- [ ] **Step 8: Verify new structure**

```bash
ls -la
```

Expected output shows:
```
trieshake-clj/
trieshake-py/
trieshake-zip-clj/
trieshake-zip-py/
docs/
Makefile (not yet)
README.md (not yet)
```

- [ ] **Step 9: Verify git history preserved for moved dirs**

```bash
git log --follow trieshake-clj/src/trieshake/ | head -5
```

Expected: Shows commits from old location `trieshake/clojure/src/trieshake/`.

- [ ] **Step 10: Commit directory flattening**

```bash
git commit -m "$(cat <<'EOF'
refactor: flatten directory structure

Move implementations to flat naming:
- trieshake/clojure → trieshake-clj
- trieshake/python → trieshake-py
- trieshake/trieshake-zip/clojure → trieshake-zip-clj

Add trieshake-zip-py placeholder for future Python impl.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3: Verify Code Paths Still Work

### Task 3: Run tests to confirm no code changes needed

**Files:**
- Verify: `trieshake-clj/project.clj` paths unchanged
- Verify: `trieshake-py/pyproject.toml` paths unchanged
- Verify: `trieshake-zip-clj/project.clj` paths unchanged
- Test: Run test suites for each

- [ ] **Step 1: Check trieshake-clj/project.clj**

```bash
head -20 trieshake-clj/project.clj
```

Expected: Shows `:source-paths ["src"]` and `:test-paths ["test"]` — relative to project root, no changes needed.

- [ ] **Step 2: Build and test Clojure trieshake**

```bash
cd trieshake-clj
lein test
cd ..
```

Expected: All tests pass. If tests fail, debug and fix inline before committing.

- [ ] **Step 3: Check trieshake-py/pyproject.toml**

```bash
cat trieshake-py/pyproject.toml | head -20
```

Expected: Shows `name = "trieshake"` — package name unchanged, no code changes needed.

- [ ] **Step 4: Build and test Python trieshake**

```bash
cd trieshake-py
pip install -e .
pytest
cd ..
```

Expected: All tests pass.

- [ ] **Step 5: Build and test trieshake-zip-clj**

```bash
cd trieshake-zip-clj
lein test
cd ..
```

Expected: All tests pass.

- [ ] **Step 6: Confirm no code changes needed**

If all tests passed without code modifications, commit with message:

```bash
git commit --allow-empty -m "$(cat <<'EOF'
refactor: verify build configs work in new structure

Confirm that relative paths in project.clj and pyproject.toml
still work after directory moves. All tests pass without changes.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

If tests failed, fix the code now and commit those changes instead.

---

## Phase 4: Create Build System

### Task 4: Write Makefile for root-level invocation

**Files:**
- Create: `Makefile` at root
- Modify: `.gitignore` to ignore `bin/`

- [ ] **Step 1: Create Makefile**

```bash
cat > Makefile << 'MAKEFILE_EOF'
# trieshake repository build targets

.PHONY: all clean test test-clj test-py trieshake trieshake-zip trieshake-clj trieshake-zip-clj trieshake-py

# Default target: build primary tools (Clojure)
all: trieshake trieshake-zip

# High-level targets (delegates to impl-specific targets)
trieshake: trieshake-clj
trieshake-zip: trieshake-zip-clj

# Clojure trieshake build
trieshake-clj:
	cd trieshake-clj && lein uberjar
	mkdir -p bin
	cp trieshake-clj/target/uberjar/*-standalone.jar bin/trieshake.jar
	echo '#!/bin/bash' > bin/trieshake
	echo 'java -jar "$$(dirname "$$0")/trieshake.jar" "$$@"' >> bin/trieshake
	chmod +x bin/trieshake

# Clojure trieshake-zip build
trieshake-zip-clj:
	cd trieshake-zip-clj && lein uberjar
	mkdir -p bin
	cp trieshake-zip-clj/target/uberjar/*-standalone.jar bin/trieshake-zip.jar
	echo '#!/bin/bash' > bin/trieshake-zip
	echo 'java -jar "$$(dirname "$$0")/trieshake-zip.jar" "$$@"' >> bin/trieshake-zip
	chmod +x bin/trieshake-zip

# Python trieshake (editable install to current venv/system)
trieshake-py:
	cd trieshake-py && pip install -e .

# Test targets
test: test-clj test-py
	@echo "All tests passed!"

test-clj:
	cd trieshake-clj && lein test
	cd trieshake-zip-clj && lein test

test-py:
	cd trieshake-py && pytest

# Cleanup
clean:
	rm -rf bin/
	cd trieshake-clj && lein clean
	cd trieshake-zip-clj && lein clean
	cd trieshake-py && rm -rf build/ dist/ *.egg-info
MAKEFILE_EOF
git add Makefile
```

- [ ] **Step 2: Update .gitignore to ignore bin/**

```bash
echo "bin/" >> .gitignore
git add .gitignore
```

- [ ] **Step 3: Test Makefile targets**

```bash
# Clean slate
make clean

# Build primary tools
make all

# Verify executables created
ls -lh bin/trieshake bin/trieshake-zip
```

Expected: Both scripts exist and are executable.

- [ ] **Step 4: Test executable functionality**

```bash
./bin/trieshake --help
./bin/trieshake-zip --help
```

Expected: Both show help output without errors.

- [ ] **Step 5: Test all tests**

```bash
make test
```

Expected: All Clojure and Python tests pass.

- [ ] **Step 6: Commit Makefile and build system**

```bash
git commit -m "$(cat <<'EOF'
build: add Makefile for root-level invocation

Add Makefile with targets for building trieshake and trieshake-zip
from root directory. Creates executable wrappers in bin/ (gitignored).

Targets:
- make (or make all): build both Clojure tools
- make trieshake-py: build Python alternative
- make test: run all tests
- make clean: remove build artifacts

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5: Write Consolidated Documentation

### Task 5: Write root README with user-facing content

**Files:**
- Create: `README.md` at root
- Modify: `trieshake-clj/README.md`
- Modify: `trieshake-py/README.md`
- Modify: `trieshake-zip-clj/README.md`

- [ ] **Step 1: Write root README.md**

```bash
cat > README.md << 'README_EOF'
# trieshake

A CLI utility for reorganizing files into prefix-based directory trees using radix trie principles.

## What It Does

**trieshake** reorganizes files of a given extension into grouped, prefix-based directory trees. It flattens parent directory paths into filenames using underscores, and chunks the concatenated path characters into nested directories of a configurable size.

### Forward Mode

Given files in a deep directory tree:
```
BL/00/00/01/06/00001/report.txt
```

trieshake reorganizes them into:
```
BL00/0001/0600/001/BL00_0001_0600_001_report.txt
```

### Reverse Mode

Strips the encoded prefix, restoring the original leafname and optionally re-chunking at a new width in one pass.

### Regroup

Reverse and re-chunk at a new prefix-length in a single operation.

## Quick Start

### Build

```bash
# Build both tools (Clojure implementations)
make

# Build Python alternative
make trieshake-py
```

Executables available in `bin/trieshake` and `bin/trieshake-zip`.

### Usage

```bash
# Preview what would happen (default: dry run)
./bin/trieshake /data -e .txt -p 4

# Execute
./bin/trieshake /data -e .txt -p 4 --execute

# Reverse (strip prefixes)
./bin/trieshake /data -e .txt --reverse --execute

# Regroup from current chunking to prefix-length 3
./bin/trieshake /data -e .txt --reverse -p 3 --execute

# Sample from large zip archive
./bin/trieshake-zip sample METADATA.zip -o sample.zip

# Transform zip contents
./bin/trieshake-zip transform sample.zip -o transformed.zip -p 4
```

## Options

### trieshake

| Flag | Description |
|---|---|
| `-e`, `--extension` | File extension to match (e.g. `.txt`, `.mets.xml`). Omit to match all files. |
| `-p`, `--prefix-length` | Characters per directory chunk (default: 4). In reverse mode, triggers single-pass regroup. |
| `--reverse` | Strip encoded prefixes from filenames. Combine with `-p` to regroup. |
| `--no-encode-leafname` | Don't prepend prefix to filename. |
| `--output-plan FILE` | Save move plan as CSV for review. |
| `--execute` | Actually move files. Without this, nothing changes. |

### trieshake-zip

| Flag | Description |
|---|---|
| `sample` | Extract subset of entries from large zip |
| `transform` | Apply trieshake algorithm to zip contents |
| `-o`, `--output FILE` | Output zip path (required) |
| `-p`, `--prefix-length N` | Characters per directory chunk (default: 4) |
| `--max-files N` | Maximum files to sample (default: 100) |
| `--max-dirs N` | Maximum parent directories to sample (default: 10) |
| `--no-encode-leafname` | Use plain leafnames (no prefix encoding) |
| `--report FILE` | Write collision report to file |

## Architecture

trieshake treats the filesystem as a [radix trie](https://en.wikipedia.org/wiki/Radix_tree). Directory names are fixed-width slices of a key (the concatenated path segments), and files are leaf nodes. Changing the prefix-length rebalances the trie at a new radix.

## Implementations

**Primary (Clojure):**
- `trieshake-clj/` — Clojure implementation of trieshake
- `trieshake-zip-clj/` — Clojure implementation of trieshake-zip

**Alternative (Python):**
- `trieshake-py/` — Python implementation of trieshake (zero external dependencies)

**Future:**
- `trieshake-zip-py/` — Python implementation of trieshake-zip (spec available, implementation pending)

## Documentation

- **[docs/SPEC.md](docs/SPEC.md)** — Full algorithm specification with worked examples and edge cases
- **[docs/trieshake-zip-design.md](docs/trieshake-zip-design.md)** — trieshake-zip architecture and design
- **[trieshake-clj/README.md](trieshake-clj/README.md)** — Clojure implementation build/development notes
- **[trieshake-py/README.md](trieshake-py/README.md)** — Python implementation build/development notes
- **[trieshake-zip-clj/README.md](trieshake-zip-clj/README.md)** — trieshake-zip build/development notes

## Development

### Run Tests

```bash
# All tests
make test

# Clojure only
make test-clj

# Python only
make test-py
```

### Clean Build

```bash
make clean
```

## License

[CC-BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)

Authors: martysteer and Claude
README_EOF
git add README.md
```

- [ ] **Step 2: Update trieshake-clj/README.md**

```bash
cat > trieshake-clj/README.md << 'README_EOF'
# trieshake (Clojure)

Clojure implementation of trieshake with zero external dependencies (Clojure core only).

## Build

```bash
cd trieshake-clj
lein uberjar
```

Creates `target/uberjar/trieshake-*-standalone.jar`.

Or from root:

```bash
make trieshake-clj
```

Creates `bin/trieshake` executable wrapper.

## Development

### Tests

```bash
lein test
```

### REPL

```bash
lein repl
```

### Run

```bash
lein run /data -e .txt -p 4
```

## Implementation Notes

- Source: `src/trieshake/` — modular structure with scanner, planner, executor, reporter, cleaner
- Tests: `test/trieshake/` — unit and integration tests
- Zero external dependencies (uses Clojure stdlib only)

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [BUILDPLAN](../docs/BUILDPLAN-clojure.md) for historical implementation context
README_EOF
git add trieshake-clj/README.md
```

- [ ] **Step 3: Update trieshake-py/README.md**

```bash
cat > trieshake-py/README.md << 'README_EOF'
# trieshake (Python)

Python implementation of trieshake with zero external dependencies.

## Build

```bash
cd trieshake-py
pip install -e .
```

Or from root:

```bash
make trieshake-py
```

Installs trieshake command to active virtualenv/system.

## Development

### Tests

```bash
pytest
```

### Run

```bash
trieshake /data -e .txt -p 4
```

Or during development:

```bash
python -m trieshake /data -e .txt -p 4
```

## Implementation Notes

- Source: `trieshake/` — modular structure with scanner, planner, executor, reporter, cleaner
- Tests: `tests/` — unit and integration tests
- Zero external dependencies (uses Python stdlib only)
- Requires Python 3.9+

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [BUILDPLAN](../docs/BUILDPLAN-python.md) for historical implementation context
README_EOF
git add trieshake-py/README.md
```

- [ ] **Step 4: Update trieshake-zip-clj/README.md**

```bash
cat > trieshake-zip-clj/README.md << 'README_EOF'
# trieshake-zip (Clojure)

Apply trieshake algorithm to zip archives without disk extraction.

## Build

```bash
cd trieshake-zip-clj
lein uberjar
```

Creates `target/uberjar/trieshake-zip-*-standalone.jar`.

Or from root:

```bash
make trieshake-zip-clj
```

Creates `bin/trieshake-zip` executable wrapper.

## Usage

### Sample Mode

Extract subset of entries from large zip:

```bash
./bin/trieshake-zip sample input.zip -o sample.zip --max-files 100 --max-dirs 10
```

Options:
- `--max-files N` - Maximum files to extract (default 100)
- `--max-dirs N` - Maximum parent directories to sample (default 10)

### Transform Mode

Apply trieshake algorithm to reorganize zip contents:

```bash
./bin/trieshake-zip transform input.zip -o output.zip -p 4
```

Options:
- `-p, --prefix-length N` - Characters per directory chunk (default 4)
- `--no-encode-leafname` - Use plain leafnames (no prefix encoding)
- `--report FILE` - Write collision report to file

## Development

### Tests

```bash
lein test
```

### REPL

```bash
lein repl
```

### Run

```bash
lein run sample input.zip -o output.zip
lein run transform input.zip -o output.zip -p 4
```

## Implementation Notes

- Source: `src/trieshake_zip/` — sampler, transformer, algorithm modules
- Tests: `test/trieshake_zip/` — unit and integration tests
- Zero external dependencies (uses Java stdlib only)
- Streams data through memory, no disk extraction required

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [trieshake-zip Design](../docs/trieshake-zip-design.md) for architecture details
README_EOF
git add trieshake-zip-clj/README.md
```

- [ ] **Step 5: Commit documentation**

```bash
git commit -m "$(cat <<'EOF'
docs: add consolidated root README and implementation docs

Write new root README with project overview, quick start, usage examples,
and links to detailed documentation. Update implementation READMEs to
focus on build/dev instructions.

Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6: Final Verification and Merge

### Task 6: Run final integration tests and merge to main

**Files:**
- Verify: All builds work
- Verify: All tests pass
- Verify: Git history preserved
- Verify: Documentation accurate

- [ ] **Step 1: Clean and rebuild everything**

```bash
make clean
make all
```

Expected: Both `bin/trieshake` and `bin/trieshake-zip` created successfully.

- [ ] **Step 2: Test executable invocation**

```bash
./bin/trieshake --help
./bin/trieshake-zip --help
```

Expected: Both show help output.

- [ ] **Step 3: Run full test suite**

```bash
make test
```

Expected: All Clojure and Python tests pass.

- [ ] **Step 4: Verify git history preserved**

```bash
# Check trieshake-clj history
git log --follow trieshake-clj/src/trieshake/ | grep "^commit" | wc -l

# Should show commits from original trieshake/clojure/ location
git log --all --grep="trieshake" -- "**/src/trieshake/" | head -20
```

Expected: History shows original location changes.

- [ ] **Step 5: Verify documentation links**

```bash
# Check root README links resolve
head -50 README.md | grep "docs/"
head -50 README.md | grep ".md"
```

Expected: Links point to valid file locations (`docs/SPEC.md`, etc.)

- [ ] **Step 6: Create tag for refactor**

```bash
git tag v1.0-refactored -m "$(cat <<'EOF'
Repository refactor complete

- Flattened directory structure
- Consolidated documentation
- Added Makefile for root-level invocation
- All implementations buildable and testable
EOF
)"
```

- [ ] **Step 7: Switch to main and merge**

```bash
git checkout main
git merge refactor/repo-structure
```

Expected: Fast-forward merge (no conflicts).

- [ ] **Step 8: Final sanity check on main**

```bash
# Clean build on main
make clean
make all
make test
```

Expected: Everything builds and tests pass.

- [ ] **Step 9: Commit success**

```bash
git log --oneline main | head -8
```

Expected: Shows the 6 commits from refactor branch integrated into main.

---

## Testing Checklist

- [ ] Phase 1: `git log --follow docs/SPEC.md` shows history
- [ ] Phase 2: `git log --follow trieshake-clj/` shows history from old location
- [ ] Phase 3: All test suites pass without code changes
- [ ] Phase 4: `make all` builds both tools, wrappers work
- [ ] Phase 5: README files render correctly, links valid
- [ ] Phase 6: Final integration test passes, merge succeeds

---

## Rollback Plan

If issues encountered at any phase:

```bash
# Revert last commit
git revert HEAD

# Or reset to specific commit before issue
git reset --hard <commit-hash>

# Or abandon refactor branch
git checkout main
git branch -D refactor/repo-structure
```

Each commit is independent and can be reverted individually.

---

## Success Criteria

✅ Directory structure flattened (trieshake-clj, trieshake-py, etc.)
✅ Documentation consolidated to docs/
✅ Makefile enables root-level invocation
✅ Root README provides user-facing entry point
✅ Implementation READMEs focus on development
✅ All tests pass (Clojure and Python)
✅ Git history preserved via git mv
✅ Both tools invokable from root without cd
✅ Build artifacts (.jar, bin/) gitignored
