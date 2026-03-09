# trieshake — Specification

## Overview

A Python CLI utility that reorganizes files of a given extension into grouped, prefix-based directory trees. It flattens parent directory paths into filenames using underscores, and chunks the concatenated path characters into nested directories of a configurable size.

It supports two modes:

- **Forward**: Flatten existing directory structures into chunked prefix directories with encoded filenames.
- **Reverse**: Decode prefixed filenames back into a directory structure, optionally re-chunking at a different character length in a single pass.

Both modes default to a **dry run** (preview only). The `--execute` flag is required to make filesystem changes.

The algorithm is analogous to radix tree rebalancing: the forward pass builds a filesystem radix tree at a given radix (prefix-length), and the reverse pass decompresses it back to the full key before optionally rebuilding at a new radix.

---

## Terminology

| Term | Meaning |
|---|---|
| **Leafname** | The original filename without any prefix, e.g. `report.txt` |
| **Parent segments** | The directory components above a file, e.g. `BL`, `00`, `00`, `01` |
| **Concat string** | Parent segments joined with no separator, e.g. `BL000001` |
| **Prefix length** | The chunk size used to split the concat string into directory levels |
| **Prefix groups** | The chunked segments, e.g. `['BL00', '0001']` for prefix-length 4 |
| **Encoded filename** | Prefix groups joined by underscores, prepended to the leafname |
| **Collision** | Two or more source files that would produce the same target path |

---

## Forward Mode (default)

### Algorithm

Given a file at a relative path within the base directory:

1. **Collect parent segments.** Split the relative path into directory components (excluding the leafname).
2. **Concatenate.** Join all parent segments into a single string with no separator.
3. **Chunk.** Split the concat string into pieces of exactly `--prefix-length` characters. If the total length is not a clean multiple, the remainder becomes a single final chunk (shorter than `--prefix-length`).
4. **Build directory tree.** Each chunk becomes a nested directory level.
5. **Build filename.** Join all chunks with underscores, append another underscore, then append the original leafname.
6. **Move** the file from its original location to the new path.

### Example: prefix-length 4

**Input:** `BL/00/00/01/06/00001/report.txt`

| Step | Result |
|---|---|
| Parent segments | `BL`, `00`, `00`, `01`, `06`, `00001` |
| Concat string | `BL0000010600001` (15 chars) |
| Chunks (size 4) | `BL00`, `0001`, `0600` (full), `001` (remainder) |
| Directory tree | `BL00/0001/0600/001/` |
| Encoded filename | `BL00_0001_0600_001_report.txt` |
| **Output** | `BL00/0001/0600/001/BL00_0001_0600_001_report.txt` |

### Example: prefix-length 3

**Input:** `BL/00/01/file.txt`

| Step | Result |
|---|---|
| Parent segments | `BL`, `00`, `01` |
| Concat string | `BL0001` (6 chars) |
| Chunks (size 3) | `BL0`, `001` (clean modulo, no remainder) |
| Directory tree | `BL0/001/` |
| Encoded filename | `BL0_001_file.txt` |
| **Output** | `BL0/001/BL0_001_file.txt` |

### Files at root level (no parent directories)

If a file has no parent directories, the concat string is derived from the filename stem (filename without extension). The chunking and encoding then proceeds as normal.

---

## Reverse Mode (`--reverse`)

### Algorithm

Given a file with an encoded filename inside a prefix directory tree:

1. **Identify prefix from the directory path.** Read the directory components the file sits in (excluding any `collisionN` suffixes). These are the prefix groups.
2. **Build expected prefix string.** Join the directory-derived prefix groups with underscores and append a trailing underscore. For example, directory path `BL00/0001/0600/001/` produces prefix `BL00_0001_0600_001_`.
3. **Validate.** Check that the filename starts with this expected prefix. If it doesn't, the file was not produced by the forward mode — skip it.
4. **Strip prefix from filename.** Remove the prefix string to recover the original leafname.
5. **Strip collision suffix.** If the leafname contains a `--collisionN` suffix (e.g. `report--collision1.txt`), remove it to recover the true original leafname.

**Then, depending on whether `-p` is provided:**

**Without `-p`:** Re-expand directories from the prefix groups. Each prefix group (as derived from the current directory path) becomes a directory level. The restored leafname goes into this directory tree. This effectively strips the encoded prefix from every filename without changing the directory layout.

**With `-p`:** After stripping the prefix, rejoin all prefix groups into a single concat string. Re-chunk this string at the new prefix-length using the same chunking algorithm as forward mode. Build the new directory tree and encoded filename from the new chunks. This performs a full regroup in a single pass.

### Example: reverse without `-p`

**Input:** `BL00/0001/0600/001/BL00_0001_0600_001_report.txt`

| Step | Result |
|---|---|
| Directory parts | `BL00`, `0001`, `0600`, `001` |
| Expected prefix | `BL00_0001_0600_001_` |
| Filename starts with prefix? | Yes |
| Stripped leafname | `report.txt` |
| Re-expanded directory | `BL00/0001/0600/001/` |
| **Output** | `BL00/0001/0600/001/report.txt` |

### Example: reverse with `-p 3` (single-pass regroup)

**Input:** `BL00/0001/0600/001/BL00_0001_0600_001_report.txt`

| Step | Result |
|---|---|
| Directory parts | `BL00`, `0001`, `0600`, `001` |
| Expected prefix | `BL00_0001_0600_001_` |
| Stripped leafname | `report.txt` |
| Concat string (prefix groups joined) | `BL0000010600001` (15 chars) |
| Re-chunked at 3 | `BL0`, `000`, `010`, `600`, `001` |
| New directory tree | `BL0/000/010/600/001/` |
| New encoded filename | `BL0_000_010_600_001_report.txt` |
| **Output** | `BL0/000/010/600/001/BL0_000_010_600_001_report.txt` |

### Example with collision file

**Input:** `ABCD/collisions/ABCD_data--collision1.txt`

| Step | Result |
|---|---|
| Directory parts (excluding `collisions`) | `ABCD` |
| Expected prefix | `ABCD_` |
| Stripped leafname (before collision strip) | `data--collision1.txt` |
| Stripped leafname (after collision strip) | `data.txt` |
| **Output (no `-p`)** | `ABCD/data.txt` |

If this target collides with another file already at `ABCD/data.txt`, the collision handling logic kicks in (see below).

---

## Regroup Workflow

To change the prefix-length of an already-organized directory, use `--reverse` with `-p`:

```bash
# Single-pass regroup from current chunking to prefix-length 3
python3 trieshake.py /data -e .txt --reverse -p 3 --execute
```

This is equivalent to, but more convenient than, the two-step approach:

```bash
# Step 1: Reverse — strip prefixes from filenames
python3 trieshake.py /data -e .txt --reverse --execute

# Step 2: Forward — re-chunk at new prefix-length
python3 trieshake.py /data -e .txt -p 3 --execute
```

---

## Collision Handling

### Detection

A collision occurs when two or more source files would produce the same target path. This can happen in forward mode (e.g. `AB/CD/data.txt` and `A/BCD/data.txt` both produce `ABCD/data.txt`) and in reverse mode (e.g. two collision files that both restore to the same leafname in the same directory).

### Resolution

- The **first** file encountered keeps the intended target path.
- Each **subsequent** file gains a `--collisionsN` suffix appended to the filename stem (before the extension) within the target directory.
- Example: `ABCD/data.txt` (first), `ABCD/data--collision1.txt` (second), `ABCD/data--collision2.txt` (third).

### Unexpected collisions

If a file already exists at the target path from an external source (not tracked in the move plan), the script detects this at move time and renames/manages the file to the `--collisionsN` suffix with the next available collision number.

### Collision report

When collisions occur, the script:
- Prints a detailed collision report to stdout.
- Writes a timestamped collision log file (`collisions_YYYYMMDD_HHMMSS.txt`) in the base directory.

---

## Directory Cleanup

After moving files, the script removes directories that are:

- Empty (after hidden files are excluded), or
- Do not contain any files matching the target extension.

Directories are removed deepest-first to avoid removing parents before children.

Cleanup is **skipped entirely** if any files are reported as lost/missing after the move phase.

---

## Verification

After all moves complete, the script verifies that every file in the move plan exists at its expected target path. Files that cannot be found at either their target or original location are reported as **lost** with full path details, and a timestamped log file is written.

---

## CLI Interface

```
trieshake.py [-h] -e EXTENSION [-p PREFIX_LENGTH] [--reverse] [--execute] directory
```

### Positional arguments

| Argument | Description |
|---|---|
| `directory` | Path to the directory containing files to reorganize |

### Required arguments

| Flag | Description |
|---|---|
| `--extension`, `-e` | File extension to match, e.g. `.mets.xml`, `.json`, `.csv`, `.txt`. The leading dot is optional (added automatically if missing). Matching is case-insensitive. If this flag is omitted, all files are managed. |

### Optional arguments

| Flag | Default | Description |
|---|---|---|
| `--prefix-length`, `-p` | `4` | Number of characters per directory chunk. In forward mode, controls the chunk size. In reverse mode, if provided, performs a single-pass regroup: strips the old prefix, re-chunks the concat string at the new length, and writes files with new encoded filenames. If omitted in reverse mode, files are simply stripped of their prefix and placed with plain leafnames. |
| `--reverse` | off | Reverse mode: strip encoded prefixes from filenames and restore directory structure from the underscore-separated segments. Optionally combine with `-p` to regroup in a single pass. |
| `--no-encode-leafname` | off | Do not prepend prefix groups to the leafname. Files are placed with their original filename only; provenance is encoded solely in the directory path. See **Leafname Encoding** section. |
| `--output-plan` | none | Save the move plan to a CSV file at the given path. The plan is written during dry run or execute, before any files are moved. The plan file is output-only and cannot be used as input — it exists solely for human review. See **Plan Export** section. |
| `--execute` | off | Actually move files. Without this flag, the script performs a dry run showing what would happen. |

### Examples

```bash
# Forward dry run (preview, no changes)
python3 trieshake.py /data -e .txt

# Forward execute with default prefix-length (4)
python3 trieshake.py /data -e .txt --execute

# Forward execute with prefix-length 3
python3 trieshake.py /data -e .txt -p 3 --execute

# Reverse dry run — strip prefixes only (preview)
python3 trieshake.py /data -e .txt --reverse

# Reverse execute — strip prefixes, restore plain leafnames
python3 trieshake.py /data -e .txt --reverse --execute

# Reverse with regroup — single-pass regroup from current chunking to prefix-length 3
python3 trieshake.py /data -e .txt --reverse -p 3 --execute

# Equivalent two-step regroup (same result as above)
python3 trieshake.py /data -e .txt --reverse --execute
python3 trieshake.py /data -e .txt -p 3 --execute

# Export plan to CSV for review before executing
python3 trieshake.py /data -e .txt -p 3 --output-plan plan.csv

# Export plan and execute in one command
python3 trieshake.py /data -e .txt -p 3 --output-plan plan.csv --execute
```

---

## Output

The script prints progress to stdout with emoji-prefixed step headers:

1. **Scanning** — counts matching files.
2. **Building plan** — computes all target paths, detects collisions, shows example mappings.
3. **Identifying cleanup** — catalogues directories to remove after moves.
4. **Moving files** — performs the moves (or shows dry run summary).
5. **Verifying** — confirms all files reached their targets.
6. **Cleaning up** — removes empty/obsolete directories.
7. **Final report** — summary statistics, collision report, lost files report, error report, directory breakdown.

In dry run mode, steps 3–7 are replaced by a summary of planned moves.

---

## Error Handling

- Files that fail to move are logged with the error message and included in an error report at the end.
- The script continues processing remaining files after individual move errors.
- Fatal errors (e.g. invalid base directory) halt execution immediately.
- `Ctrl+C` is caught and exits cleanly.
- Exit code `0` on success, `1` on warnings or failure.

---

## Edge Cases

| Scenario | Behaviour |
|---|---|
| Underscores in original leafname | Handled correctly — the prefix is identified from the directory path, not parsed from the filename. `my_data.txt` survives round-trips intact. |
| File already at correct location | Counted as "already correct", not moved. |
| Empty concat string | Falls back to first N characters of the filename stem. |
| Concat string shorter than prefix-length | Produces a single chunk shorter than prefix-length. |
| Files at root level (no parent dirs) | In forward mode, uses the filename stem as the concat string. In reverse mode, skipped (no directory path to derive prefix from). |
| Collision files from `--collisionsN` suffixes | In reverse mode, the `--collisionN` suffixes are stripped from leafnames. |
| Multiple extensions (e.g. `.mets.xml`) | Fully supported — the extension is matched as a suffix, not split on the last dot. |

---

## Leafname Encoding (`--encode-leafname`)

By default, trieshake prepends the prefix groups to the leafname (e.g. `BL00_0001_0600_001_report.txt`). The `--encode-leafname` flag controls this behaviour:

- **`--encode-leafname`** (default): The filename includes the full prefix. This provides metadata resilience — if a file is moved or copied out of its directory, its provenance is still recoverable from the filename alone.
- **`--no-encode-leafname`**: The filename is the plain original leafname (e.g. `report.txt`). The file's identity depends entirely on its position in the directory tree.

### Impact on collisions

The collision surface is **identical in both modes**. A file's uniqueness is determined by its full path (directory + filename). Since the directory structure is the same regardless of whether the prefix is encoded in the filename, two files collide if and only if they produce the same concat-string chunks AND have the same original leafname — in both modes.

### Impact on reverse mode

- When reversing **encoded** leafnames, the prefix is identified from the directory path and stripped from the filename. This is reliable because the directory path and filename prefix are redundant — they encode the same information.
- When reversing **plain** leafnames, no stripping is needed — the file already has its original name. The directory path alone provides the concat string for re-chunking.

The `--encode-leafname` / `--no-encode-leafname` flag applies to both forward and reverse-with-rechunk operations, controlling what the *output* filenames look like.

---

## Plan Export (`--output-plan`)

When `--output-plan <filename.csv>` is provided, trieshake writes the complete move plan to a CSV file before any files are moved. This works in both dry run and execute modes, allowing the user to review planned changes before committing.

### Purpose

The plan file is **output-only**. It cannot be fed back into trieshake as input. Its sole purpose is human review and audit — allowing someone to inspect every planned move, verify the mapping is correct, and spot any collisions before running `--execute`.

### CSV columns

| Column | Description |
|---|---|
| `source_path` | Relative path of the file in its current location |
| `target_path` | Relative path the file will be moved to |
| `leafname` | The original leafname (stripped of any prefix) |
| `concat_string` | The full concatenated key derived from parent segments |
| `prefix_groups` | The chunked groups, pipe-separated (e.g. `BL00|0001|0600|001`) |
| `is_collision` | `true` if this file collides with another and will receive a `--collisionN` filename suffix |
| `collision_of` | If `is_collision` is `true`, the target path of the file it collides with |
| `already_correct` | `true` if the file is already at its target location |
| `action` | One of: `move`, `skip` (already correct), `collision` |

### Example usage

```bash
# Generate a plan for review
python3 trieshake.py /data -e .txt --output-plan plan.csv

# Inspect the plan
# (open in Excel, sort/filter, check for surprises)

# If satisfied, execute
python3 trieshake.py /data -e .txt --execute
```

### Behaviour

- The plan is written at the end of step 2 (after the move plan is fully computed and collisions are resolved), before any filesystem changes occur.
- If `--execute` is also provided, the plan is written first, then the moves proceed.
- If the plan file already exists, it is overwritten with a warning.
- The CSV uses UTF-8 encoding with a header row.

---

## Design Rationale: Radix Trie Model

### Structural analogy

trieshake operates on a filesystem as a **radix trie** (compact prefix tree):

- **Internal nodes** are directories. Each directory name is a fixed-width slice of the key.
- **Leaf nodes** are files. The leafname is the stored value.
- **The key** for each file is the concatenation of all parent directory segments — the concat string.
- **The radix** (branching factor) is the `--prefix-length`.

Changing the prefix-length is equivalent to **rebalancing the trie at a new radix**: the key space is the same, but the branching structure changes. A prefix-length of 4 produces a wider, shallower tree; a prefix-length of 2 produces a narrower, deeper tree.

### Forward pass = trie construction

The forward pass performs a bulk-load of keys into a radix trie:

1. Extract the full key (concat string) from each file's directory path.
2. Partition the key into fixed-width chunks (the radix).
3. Create the corresponding directory hierarchy.
4. Place the file (leaf value) at the terminal node.

### Reverse pass = trie decompression

The reverse pass recovers the full key from the trie structure:

1. Walk the path from root to leaf, collecting directory names.
2. Concatenate them to reconstruct the full key.
3. Optionally re-partition at a new radix (single-pass regroup).

### Why collisions occur

In a pure radix trie, each unique key maps to exactly one leaf position, so collisions are impossible. In trieshake, however, the leaf node stores a **value** (the original leafname) that is independent of the key. Two files with different keys but identical values will occupy different trie positions and never collide.

Collisions arise only when the **key itself is ambiguous** — when two different original directory structures produce the same concat string. This happens because concatenation is lossy: `AB` + `CD` and `A` + `BCD` both produce `ABCD`. In trie terms, the original segment boundaries are lost during key extraction.

This ambiguity is only a problem when:

1. The original directory segments have **variable widths** (e.g. some are 2 chars, some are 3), AND
2. Two different segmentations produce the **same concatenation**, AND
3. The files at those paths have the **same leafname**.

If the source data uses fixed-width segments (as is common in institutional identifier schemes like SOBEK), condition 1 is not met and collisions cannot occur. The collision handling machinery is a safety net for heterogeneous or unpredictable source filesystem tree structures.

### Leafname encoding in trie terms

With `--encode-leafname`, the prefix groups are redundantly stored in both the trie path (directories) and the leaf value (filename). This is analogous to a trie that stores the full key at each leaf node — useful for integrity checking and provenance recovery, but not required for correct operation.

With `--no-encode-leafname`, the leaf stores only the original value, and the key exists solely in the trie structure. This is the more "pure" trie representation but sacrifices the ability to recover provenance from a leaf in isolation.

The default behaviour is `--encode-leafname`.
