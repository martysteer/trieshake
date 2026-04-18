# trieshake Repository Refactor — Design Specification

## Overview

Reorganize trieshake repository structure to enable root-level invocation of both tools while consolidating documentation and maintaining clear separation between implementations.

**Goals:**
1. Invoke `trieshake` and `trieshake-zip` from root directory (no `cd` required)
2. Consolidate scattered documentation into logical structure
3. Flatten directory hierarchy for clarity
4. Keep Python trieshake buildable as alternative implementation
5. Preserve git history through the refactor

**Non-goals:**
- Changing code functionality
- Adding new features
- Implementing Python trieshake-zip (future work, spec only)

---

## Problem Statement

**Current pain points:**
- Must `cd` into subdirectories to invoke tools
- Documentation scattered across multiple locations
- Nested directory structure unclear (trieshake/trieshake-zip/clojure)
- Hard to find relevant information

**User workflow desired:**
```bash
# From root directory
make                    # Build both tools
./bin/trieshake /data -e .txt -p 4
./bin/trieshake-zip sample input.zip -o output.zip
```

---

## Target Directory Structure

### Before
```
trieshake/
├── python/
├── clojure/
├── trieshake-zip/clojure/
├── SPEC.md
├── BUILDPLAN-python.md
├── BUILDPLAN-clojure.md
├── README.md
└── docs/superpowers/
```

### After
```
/
├── trieshake-clj/          # Clojure trieshake (primary)
│   ├── src/trieshake/
│   ├── test/
│   ├── project.clj
│   └── README.md           # Build/dev instructions
├── trieshake-py/           # Python trieshake (alternative)
│   ├── trieshake/
│   ├── tests/
│   ├── pyproject.toml
│   └── README.md           # Build/dev instructions
├── trieshake-zip-clj/      # Clojure trieshake-zip (primary)
│   ├── src/trieshake_zip/
│   ├── test/
│   ├── project.clj
│   └── README.md           # Build/dev instructions
├── trieshake-zip-py/       # Future Python impl (spec only)
│   └── README.md           # Placeholder + spec reference
├── docs/                   # All reference material
│   ├── SPEC.md
│   ├── BUILDPLAN-python.md
│   ├── BUILDPLAN-clojure.md
│   ├── trieshake-zip-design.md
│   └── implementation-plans/
│       ├── 2026-04-15-trieshake-zip-python.md
│       └── 2026-04-15-trieshake-zip-clojure.md
├── Makefile                # Build targets
├── README.md               # Main entry point
├── .gitignore
└── bin/                    # Built executables (gitignored)
    ├── trieshake
    ├── trieshake.jar
    ├── trieshake-zip
    └── trieshake-zip.jar
```

**Key principles:**
- Flat structure with tool+language naming convention
- All reference documentation in `docs/`
- Built artifacts in `bin/` (not committed)
- Each implementation has minimal README for developers

---

## Documentation Consolidation

### Root README.md
**Purpose:** User-facing entry point (80% of what users need)

**Contents:**
- Project overview (what trieshake does, use cases)
- Quick start installation (`make`)
- Usage examples for both tools
- Common workflows
- Link to `docs/SPEC.md` for algorithm details
- Link to implementation READMEs for development

### Implementation READMEs
**Purpose:** Developer-focused build/test instructions

**Location:** `trieshake-clj/README.md`, `trieshake-py/README.md`, etc.

**Contents:**
- How to build this specific implementation
- How to run tests
- Development setup requirements
- Implementation-specific notes
- Link back to root README

### docs/ Organization
```
docs/
├── SPEC.md                          # Algorithm specification (canonical reference)
├── BUILDPLAN-python.md              # Historical reference
├── BUILDPLAN-clojure.md             # Historical reference
├── trieshake-zip-design.md          # Moved from superpowers/specs/
└── implementation-plans/            # Renamed from superpowers/plans/
    ├── 2026-04-15-trieshake-zip-python.md
    └── 2026-04-15-trieshake-zip-clojure.md
```

**Content strategy:**
- Root README: Quick start, common use cases, examples
- SPEC.md: Deep algorithmic details, edge cases, design rationale
- Implementation READMEs: Build/test/develop specific impl
- BUILDPLANs: Historical context, not actively maintained

---

## Build System

### Makefile Targets

```makefile
# Primary targets (Clojure)
all: trieshake trieshake-zip

trieshake: trieshake-clj
trieshake-zip: trieshake-zip-clj

# Clojure builds
trieshake-clj:
	cd trieshake-clj && lein uberjar
	mkdir -p bin
	cp trieshake-clj/target/uberjar/*-standalone.jar bin/trieshake.jar
	echo '#!/bin/bash\njava -jar "$$(dirname "$$0")/trieshake.jar" "$$@"' > bin/trieshake
	chmod +x bin/trieshake

trieshake-zip-clj:
	cd trieshake-zip-clj && lein uberjar
	mkdir -p bin
	cp trieshake-zip-clj/target/uberjar/*-standalone.jar bin/trieshake-zip.jar
	echo '#!/bin/bash\njava -jar "$$(dirname "$$0")/trieshake-zip.jar" "$$@"' > bin/trieshake-zip
	chmod +x bin/trieshake-zip

# Alternative Python build
trieshake-py:
	cd trieshake-py && pip install -e .

# Testing
test: test-clj test-py

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

.PHONY: all trieshake trieshake-zip trieshake-clj trieshake-zip-clj trieshake-py test test-clj test-py clean
```

### Executable Wrappers

**Design:**
- Shell scripts in `bin/` that invoke jars
- Use relative paths for portability
- Jars copied from build outputs

**Example wrapper (bin/trieshake):**
```bash
#!/bin/bash
java -jar "$(dirname "$0")/trieshake.jar" "$@"
```

### .gitignore Addition
```
bin/
```

### Usage Examples

```bash
# Build primary tools (Clojure)
make

# Run from root
./bin/trieshake /data -e .txt -p 4
./bin/trieshake-zip sample input.zip -o output.zip

# Build Python alternative
make trieshake-py
trieshake /data -e .txt -p 4  # Uses pip-installed version

# Run all tests
make test

# Clean build artifacts
make clean
```

---

## Migration Strategy

### Approach: Incremental Git-Based Refactor

**Rationale:**
- Preserves git history (`git mv`)
- Each phase independently testable
- Easy rollback per commit
- Clear separation of concerns

**Trade-offs:**
- Multiple commits (acceptable for refactor)
- Intermediate states temporarily inconsistent (managed with branching)

### Phase 1: Consolidate Docs

**Actions:**
```bash
git checkout -b refactor/repo-structure
mkdir -p docs/implementation-plans
git mv trieshake/SPEC.md docs/
git mv trieshake/BUILDPLAN-python.md docs/
git mv trieshake/BUILDPLAN-clojure.md docs/
git mv docs/superpowers/specs/2026-04-15-trieshake-zip-design.md docs/trieshake-zip-design.md
git mv docs/superpowers/plans/* docs/implementation-plans/
git commit -m "docs: consolidate reference material to docs/"
```

**Verification:**
- `git log --follow docs/SPEC.md` shows history preserved
- All doc files in `docs/`

### Phase 2: Flatten Structure

**Actions:**
```bash
git mv trieshake/clojure trieshake-clj
git mv trieshake/python trieshake-py
git mv trieshake/trieshake-zip/clojure trieshake-zip-clj
mkdir trieshake-zip-py
echo "# trieshake-zip (Python)" > trieshake-zip-py/README.md
echo "Placeholder for future Python implementation. See ../docs/trieshake-zip-design.md for spec." >> trieshake-zip-py/README.md
rm -rf trieshake/
git add trieshake-zip-py/README.md
git commit -m "refactor: flatten directory structure"
```

**Verification:**
- `git log --follow trieshake-clj/` shows history from `trieshake/clojure/`
- Directory tree matches target structure
- Old `trieshake/` dir removed

### Phase 3: Verify Code Paths

**Actions:**
```bash
# Check if any code changes needed (expected: none)
# - project.clj files: paths relative to project root (src/, test/) - unchanged
# - pyproject.toml: package name "trieshake" - unchanged
# - Clojure namespaces: src/trieshake/* - unchanged
# - Python imports: trieshake.* - unchanged

# Run tests to verify everything works in new locations
cd trieshake-clj && lein test
cd ../trieshake-py && pytest
cd ../trieshake-zip-clj && lein test
cd ..

# If all tests pass, no code changes needed
# Commit empty to mark phase complete (no-op phase, but good for audit trail)
git commit --allow-empty -m "refactor: verify build configs work with new structure"
```

**Verification:**
- All tests pass
- No import/path errors
- No code changes required (expected outcome)

### Phase 4: Add Build System

**Actions:**
```bash
# Create Makefile (see Build System section for complete content)
# Copy the Makefile targets verbatim from the "Makefile Targets" subsection above

# Update .gitignore
echo "bin/" >> .gitignore

# Test builds
make clean
make all
./bin/trieshake --help
./bin/trieshake-zip --help
make test

git add Makefile .gitignore
git commit -m "build: add Makefile for root-level invocation"
```

**Verification:**
- `make all` builds successfully
- Executables in `bin/` work
- `make test` all green

### Phase 5: Write Root README

**Actions:**
```bash
# Create new README.md at root with:
# - Project overview and use cases
# - Quick start (make commands)
# - Usage examples for trieshake and trieshake-zip
# - Links to docs/SPEC.md and implementation READMEs
# (See "Root README.md" subsection in Documentation Consolidation section)

# Update implementation READMEs to focus on:
# - Build/test commands for that specific impl
# - Development setup requirements
# - Link back to root README
# (See "Implementation READMEs" subsection in Documentation Consolidation section)

git add README.md
git add trieshake-clj/README.md trieshake-py/README.md trieshake-zip-clj/README.md
git commit -m "docs: add consolidated root README"
```

**Verification:**
- Root README renders correctly
- All links resolve
- Examples copy-pasteable

### Phase 6: Verification & Merge

**Actions:**
```bash
# Final integration test
make clean
make all
make test

# Tag refactor
git tag v1.0-refactored

# Merge to main
git checkout main
git merge refactor/repo-structure
```

**Verification:**
- All builds green
- All tests pass
- Git history preserved
- Both tools invokable from root
- Documentation accurate

---

## Testing Strategy

### Per-Phase Verification

**Phase 1 (Docs):**
- `git log --follow docs/SPEC.md` shows history preserved
- All doc links still valid

**Phase 2 (Structure):**
- `git log --follow trieshake-clj/` shows history from `trieshake/clojure/`
- Directory tree matches target structure

**Phase 3 (Paths):**
- `cd trieshake-clj && lein test` passes
- `cd trieshake-py && pytest` passes
- `cd trieshake-zip-clj && lein test` passes

**Phase 4 (Build):**
- `make clean && make all` succeeds
- `./bin/trieshake --help` works
- `./bin/trieshake-zip --help` works
- `make test` all green

**Phase 5 (Docs):**
- Root README renders correctly on GitHub
- All links resolve
- Examples copy-pasteable

### Final Integration Test

```bash
# Clean slate
make clean

# Build primary tools
make

# Run trieshake on test data
./bin/trieshake /path/to/test -e .txt -p 4

# Run trieshake-zip on test archive
./bin/trieshake-zip sample test.zip -o sample.zip

# Build Python alternative
make trieshake-py

# Verify pip install worked
trieshake --help

# Run all tests
make test
```

### Success Criteria

- All builds complete without errors
- All tests pass (Clojure and Python)
- Git history preserved (verify with `git log --follow`)
- Both tools invokable from root directory
- Documentation accurate and complete
- No broken links in READMEs

### Rollback Plan

Each phase is a separate commit. If issue found:

```bash
# Revert specific commit
git revert <commit-hash>

# Or reset to phase before issue
git reset --hard <commit-before-issue>

# Or abandon branch entirely
git checkout main
git branch -D refactor/repo-structure
```

---

## Edge Cases & Considerations

### Git History Preservation

- Use `git mv` for all file/directory moves
- Verify history with `git log --follow <path>` after each phase
- If history lost, git can usually recover with similarity detection

### Existing Checkouts & Branches

- Other branches may break after structure change
- Document in commit message: "BREAKING: directory structure changed"
- Add migration note to merge/rebase instructions

### Build Artifacts

- `bin/` directory gitignored to avoid committing build outputs
- Each developer runs `make` locally after checkout
- CI/CD systems must run `make` before tests

### Python Virtualenv

- `make trieshake-py` installs to current virtualenv/system
- Developers should activate venv before `make trieshake-py`
- Document in trieshake-py/README.md

### Jar Naming

- Makefile uses wildcard `*-standalone.jar` to avoid version hardcoding
- Assumes single uberjar per project (Leiningen default)

---

## Future Enhancements

**Not in this refactor:**

- Implement Python trieshake-zip (spec exists in trieshake-zip-py/)
- Add reverse mode to trieshake-zip
- CI/CD pipeline configuration
- Release automation
- Package distribution (PyPI, Clojars)

---

## Open Questions

None. Design ready for implementation.
