# trieshake

A CLI utility for reorganizing files into prefix-based directory trees using radix trie principles.

trieshake flattens directory hierarchies into fixed-width chunked directories, encoding the original path into the filename. It can also reverse the process — stripping encoded prefixes and restoring plain filenames — or regroup an existing structure at a different chunk width in a single pass.

## What it does

**Forward** — given files in a deep directory tree:

```
BL/00/00/01/06/00001/report.txt
```

trieshake concatenates the path segments, chunks them at a configurable width, and produces:

```
BL00/0001/0600/001/BL00_0001_0600_001_report.txt
```

**Reverse** — strips the encoded prefix, restoring the original leafname:

```
BL00/0001/0600/001/report.txt
```

**Regroup** — reverse and re-chunk at a new width in one pass (`--reverse -p 3`):

```
BL0/000/010/600/001/BL0_000_010_600_001_report.txt
```

## How it works

trieshake treats the filesystem as a [radix trie](https://en.wikipedia.org/wiki/Radix_tree). Directory names are fixed-width slices of a key (the concatenated path segments), and files are leaf nodes. Changing the prefix-length rebalances the trie at a new radix — a wider radix gives a shallower tree, a narrower one gives a deeper tree.

Collisions (when different source paths produce the same target) are handled with `--collisionN` filename suffixes. In practice, collisions only occur when source directories have variable-width segments that concatenate ambiguously.

## Installation

```bash
# Install from source (editable/development mode)
pip install -e .

# Or install directly
pip install .
```

Once installed, the `trieshake` command is available. You can also run it as a module:

```bash
python -m trieshake --help
```

Requires Python 3.9+.

## Usage

```bash
# Preview what would happen (default: dry run)
trieshake /data -e .txt -p 4

# Execute
trieshake /data -e .txt -p 4 --execute

# Reverse (strip prefixes)
trieshake /data -e .txt --reverse --execute

# Regroup from current chunking to prefix-length 3
trieshake /data -e .txt --reverse -p 3 --execute

# Export move plan to CSV for review
trieshake /data -e .txt -p 4 --output-plan plan.csv

# Plain leafnames (no prefix encoding)
trieshake /data -e .txt -p 4 --no-encode-leafname --execute
```

## Options

| Flag | Description |
|---|---|
| `-e`, `--extension` | File extension to match (e.g. `.txt`, `.mets.xml`). Omit to match all files. |
| `-p`, `--prefix-length` | Characters per directory chunk (default: 4). In reverse mode, triggers single-pass regroup. |
| `--reverse` | Strip encoded prefixes from filenames. Combine with `-p` to regroup. |
| `--no-encode-leafname` | Don't prepend prefix to filename. |
| `--output-plan FILE` | Save move plan as CSV for review. |
| `--execute` | Actually move files. Without this, nothing changes. |

## Docs

- **[SPEC.md](./synthetic-build-docs/SPEC.md)** — Full specification with worked examples and edge cases
- **[BUILDPLAN-python.md](./synthetic-build-docs/BUILDPLAN-python.md)** — Python implementation plan
- **[BUILDPLAN-clojure.md](./synthetic-build-docs/BUILDPLAN-clojure.md)** — Clojure implementation plan

## License

[CC-BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)

Author(s): martysteer and Claude Opus 4.6
