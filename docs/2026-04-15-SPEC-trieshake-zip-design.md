# trieshake-zip — Design Specification

## Overview

trieshake-zip applies the trieshake algorithm to zip archives. It operates on zip entries in memory—extracting, transforming paths, and recompressing—without writing intermediate files to disk.

Two modes:

1. **Sample mode**: Extract a subset of entries from large archives for testing
2. **Transform mode**: Apply trieshake's forward-mode algorithm to reorganize zip contents

Both modes stream data through memory to minimize disk usage when working with multi-gigabyte archives.

---

## Purpose

Enable trieshake transformation of large zip archives (1+ GB) where extraction-transform-recompress would:
- Consume excessive disk space
- Take longer (disk I/O overhead)
- Risk data loss (if process interrupted mid-extraction)

Streaming approach processes entries one at a time, keeping memory footprint low.

---

## Architecture

### Component Structure

```
trieshake-zip/
├── clojure/
│   ├── src/trieshake_zip/
│   │   ├── core.clj         # CLI entry, arg parsing
│   │   ├── sampler.clj      # Sample mode logic
│   │   ├── transformer.clj  # Transform mode logic
│   │   └── algorithm.clj    # Trieshake path computation
│   ├── test/trieshake_zip/
│   │   ├── sampler_test.clj
│   │   └── transformer_test.clj
│   └── project.clj
└── python/
    ├── trieshake_zip/
    │   ├── __init__.py
    │   ├── __main__.py
    │   ├── cli.py
    │   ├── sampler.py
    │   ├── transformer.py
    │   └── algorithm.py
    ├── tests/
    │   ├── test_sampler.py
    │   └── test_transformer.py
    └── pyproject.toml
```

### Dependencies

**Clojure**: `java.util.zip.ZipInputStream`, `ZipOutputStream`, `ZipEntry` (Java stdlib, zero external deps)

**Python**: `zipfile` module (stdlib, zero external deps)

Algorithm logic reuses trieshake's core functions (concat segments, chunk, encode filename).

---

## Components

### Sampler

**Purpose**: Create small test archives from large sources.

**Algorithm**:

1. Open source zip as stream
2. Read entries sequentially
3. Group entries by first parent directory name (extracted from entry path)
4. Track groups encountered, stop when `--max-dirs` reached
5. From selected groups, take first `--max-files` total entries
6. For each selected entry:
   - Create matching `ZipEntry` in output zip
   - Copy compressed bytes directly (no decompression)
   - Preserve timestamps, compression method
7. Close streams

**Notes**:
- Directories in zip (entries ending in `/`) are skipped
- Sampling stops when either limit reached
- Order preserved from source zip

### Transformer

**Purpose**: Reorganize zip contents using trieshake algorithm.

**Algorithm**:

1. Open source zip as stream, output zip as stream
2. Initialize collision tracker (map of target paths → count)
3. For each entry:
   - Skip directory entries (ending in `/`)
   - Parse path into parent segments + leafname
   - If `--strip-prefix N` specified: drop first N parent segments
   - Apply trieshake algorithm:
     - Concatenate parent segments → concat string
     - Chunk concat string at `--prefix-length`
     - Build new directory path from chunks
     - Build encoded filename (chunks joined with underscores + leafname)
     - Combine into target path
   - Check collision tracker:
     - If target exists, append `--collision1`, `--collision2`, etc.
     - Update tracker
   - Create `ZipEntry` with transformed path
   - Stream bytes from input to output (no decompression needed)
4. Close streams
5. Print collision report if collisions occurred

**Streaming details**:
- Zip entries are compressed data; we copy bytes directly
- No need to decompress → recompress (preserves original compression)
- Memory usage: one entry at a time (even for GB archives)

### Algorithm

**Reused from trieshake**:

Functions:
- `concat-segments [parent-dirs]` → concat string
- `chunk-string [s prefix-length]` → vector of chunks
- `encode-filename [chunks leafname encode?]` → encoded filename
- `build-target-path [chunks encoded-filename]` → full target path

These functions are algorithm-agnostic (work on strings, not filesystem).

---

## CLI Interface

```bash
trieshake-zip <mode> <input.zip> -o <output.zip> [options]
```

### Modes

**sample**: Extract subset of entries

**transform**: Apply trieshake algorithm

### Common Options

| Flag | Description |
|---|---|
| `-o`, `--output FILE` | Output zip path (required) |

### Sample Mode Options

| Flag | Default | Description |
|---|---|---|
| `--max-files N` | 100 | Maximum files to extract |
| `--max-dirs N` | 10 | Maximum parent directories to sample from |

### Transform Mode Options

| Flag | Default | Description |
|---|---|---|
| `-p`, `--prefix-length N` | 4 | Characters per directory chunk |
| `--strip-prefix N` | 0 | Strip N leading path components before transformation |
| `--no-encode-leafname` | off | Use plain leafnames (no prefix encoding) |
| `--report FILE` | none | Write collision report to file |

### Examples

```bash
# Create sample (first 100 files from first 10 parent dirs)
trieshake-zip sample METADATA.zip -o sample.zip

# Create smaller sample
trieshake-zip sample METADATA.zip -o tiny.zip --max-files 20 --max-dirs 3

# Transform with defaults (prefix-length 4, encoded leafnames)
trieshake-zip transform sample.zip -o transformed.zip

# Transform with prefix-length 3
trieshake-zip transform sample.zip -o out.zip -p 3

# Transform with plain leafnames
trieshake-zip transform sample.zip -o out.zip --no-encode-leafname

# Transform with prefix stripping (remove top 2 path levels)
trieshake-zip transform sample.zip -o out.zip --strip-prefix 2

# Transform and save collision report
trieshake-zip transform sample.zip -o out.zip --report collisions.txt
```

---

## Data Flow

### Sample Mode

```
Source zip → ZipInputStream → filter by group/count → ZipOutputStream → Output zip
                ↓
        track parent dirs
        count entries
```

No transformation, pure filtering and copying.

### Transform Mode

```
Source zip → ZipInputStream → parse path → trieshake algorithm → collision check → ZipOutputStream → Output zip
                                  ↓               ↓                    ↓
                          parent segments    concat/chunk      append suffix if needed
                          + leafname         + encode
```

Entry bytes copied directly (no decompress/recompress).

---

## Error Handling

### Input Validation

- Source zip must exist and be readable
- Output path parent directory must exist
- Prefix-length > 0
- max-files, max-dirs > 0

Validation failure: print error, exit code 1.

### Runtime Errors

| Scenario | Behavior |
|---|---|
| Corrupted zip entry | Log warning with entry name, skip entry, continue |
| Write failure (disk full, permissions) | Log error with details, exit code 1 |
| Collision overflow (>1000 same target) | Log error, halt (likely indicates bug or pathological input) |
| Out of memory | Let runtime handle (streaming design minimizes risk) |

### Output

- Progress printed to stderr: `Processing: <entry-name>` (every 100 entries)
- Summary printed at end: entries processed, collisions, skipped, errors
- Collision report (if `--report` specified): one line per collision with source/target paths

---

## Testing Strategy

### Unit Tests

**Algorithm functions** (concat, chunk, encode):
- Empty input
- Single segment
- Multiple segments
- Segment lengths: shorter than prefix-length, equal, longer
- Remainder handling (concat string not multiple of prefix-length)

**Collision detection**:
- First occurrence (no suffix)
- Second occurrence (`--collision1`)
- Nth occurrence (`--collisionN`)

**Path parsing**:
- Root-level files (no parent dirs)
- Nested paths
- Paths with underscores in leafname
- Paths with multiple dots in filename

### Integration Tests

**Sampler**:
- Create test zip with known structure (3 parent dirs, 50 files each)
- Run sampler with `--max-dirs 2 --max-files 30`
- Verify output zip contains exactly 30 files from 2 dirs
- Verify file contents match source

**Transformer**:
- Create test zip with known paths
- Transform with specific prefix-length
- Extract output zip, verify:
  - All files present
  - Paths match expected trieshake structure
  - File contents unchanged
  - Collisions handled correctly (if test includes collision cases)

**Round-trip test**:
- Transform zip → extract to disk → verify structure matches trieshake filesystem output

### Manual Testing

- Sample from METADATA.zip, inspect result
- Transform sample, verify:
  - Output zip smaller than full archive
  - Entries have trieshake structure
  - Can extract and browse contents
- Test with edge cases:
  - Empty zip
  - Zip with single file
  - Zip with deeply nested paths (20+ levels)

---

## Edge Cases

| Scenario | Behavior |
|---|---|
| Empty zip | Output empty zip, no errors |
| Single file at root | Use filename stem as concat string (trieshake default) |
| Entry path with leading slash | Strip slash, treat as relative path |
| Entry path with `..` | Reject entry, log error (security risk) |
| Duplicate entries in source zip | Transform each independently (likely produces collision) |
| Unicode in filenames | Preserve encoding (zip spec supports UTF-8) |
| Symlinks (if stored as entries) | Skip with warning (not portable across zip tools) |
| `--strip-prefix` > path depth | Skip entry with warning (not enough path components to strip) |
| `--strip-prefix` leaves no parent dirs | Use filename stem as concat string (like root-level file) |

---

## Performance Characteristics

### Memory

- **Sample mode**: O(1) — one entry buffered at a time
- **Transform mode**: O(n) for collision tracker, O(1) for entry streaming
  - n = number of unique target paths
  - For typical archives: <1MB overhead

### Speed

Bottleneck: zip compression/decompression (if entries are compressed).

Since we copy compressed bytes without recompressing:
- **Best case**: Fast as raw file copy (~100 MB/s)
- **Worst case**: Limited by zip entry iteration overhead (~50 MB/s)

For 1.4 GB archive: 15-30 seconds expected.

### Disk Usage

Zero intermediate files. Only source and output zips on disk.

---

## Comparison to Filesystem trieshake

| Feature | Filesystem trieshake | trieshake-zip |
|---|---|---|
| Input | Directory tree | Zip archive |
| Output | Directory tree | Zip archive |
| Modes | Forward, Reverse, Regroup | Transform only (forward) |
| Extension filter | Yes | Not yet (process all files) |
| Dry run | Yes | No (zip writing is atomic) |
| Directory cleanup | Yes | N/A (zips have no empty dirs) |
| Verification | Yes (check files exist at target) | No (zip write failures are immediate) |

Future: add reverse mode for zip (strip prefixes from zip entries).

---

## Future Enhancements

**Not in initial version**:

- Reverse mode (decode trieshake-structured zip back to flat structure)
- Extension filter (`-e .txt` to transform only matching files)
- Dry run mode (print planned transformations without writing output)
- Parallel processing (transform multiple entries concurrently)
- Progress bar (instead of periodic stderr messages)
- Compression level control (recompress with different settings)

Initial version: sample + forward transform only.

---

## Open Questions

None. Design ready for implementation.
