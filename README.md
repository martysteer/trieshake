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
- **[docs/trieshake-refactor-design.md](docs/trieshake-refactor-design.md)** — Repository refactor design
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
