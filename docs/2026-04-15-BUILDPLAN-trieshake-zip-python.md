# trieshake-zip (Python) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build trieshake-zip tool for Python—sample and transform zip archives using trieshake algorithm without disk extraction.

**Architecture:** Separate tool in `trieshake-zip/python/` directory. Reuses trieshake's chunking/encoding logic. Two modes: sample (filter entries by group/count) and transform (apply trieshake path transformation). Streams zip entries through memory using zipfile module.

**Tech Stack:** Python 3.9+, zipfile (stdlib), zero external deps

---

## File Structure

**New files to create:**

```
trieshake-zip/
└── python/
    ├── pyproject.toml                        # Project config
    ├── trieshake_zip/
    │   ├── __init__.py                       # Package marker
    │   ├── __main__.py                       # Entry point for -m execution
    │   ├── cli.py                            # CLI argument parsing
    │   ├── algorithm.py                      # Trieshake path computation
    │   ├── sampler.py                        # Sample mode implementation
    │   └── transformer.py                    # Transform mode implementation
    └── tests/
        ├── test_algorithm.py                 # Unit tests for algorithm
        ├── test_sampler.py                   # Integration tests for sampler
        └── test_transformer.py               # Integration tests for transformer
```

**Files referenced (existing):**
- `trieshake/python/trieshake/planner.py` — source for algorithm functions

---

## Task 1: Project Setup

**Files:**
- Create: `trieshake-zip/python/pyproject.toml`
- Create: `trieshake-zip/python/trieshake_zip/__init__.py`

- [ ] **Step 1: Create project structure**

```bash
mkdir -p trieshake-zip/python/trieshake_zip
mkdir -p trieshake-zip/python/tests
```

- [ ] **Step 2: Write pyproject.toml**

```toml
[build-system]
requires = ["setuptools>=61.0"]
build-backend = "setuptools.build_meta"

[project]
name = "trieshake-zip"
version = "0.1.0"
description = "Apply trieshake algorithm to zip archives"
authors = [{name = "martysteer", email = ""}]
license = {text = "CC-BY-SA 4.0"}
requires-python = ">=3.9"
dependencies = []

[project.scripts]
trieshake-zip = "trieshake_zip.cli:main"

[tool.pytest.ini_options]
testpaths = ["tests"]
python_files = "test_*.py"
python_functions = "test_*"
```

- [ ] **Step 3: Write __init__.py**

```python
"""trieshake-zip — Apply trieshake algorithm to zip archives."""

__version__ = "0.1.0"
```

- [ ] **Step 4: Install in development mode**

Run: `cd trieshake-zip/python && pip install -e .`
Expected: Package installed, no errors

- [ ] **Step 5: Commit**

```bash
git add trieshake-zip/python/pyproject.toml \
        trieshake-zip/python/trieshake_zip/__init__.py
git commit -m "feat(trieshake-zip): add Python project setup"
```

---

## Task 2: Algorithm Module (Path Computation)

**Files:**
- Create: `trieshake-zip/python/trieshake_zip/algorithm.py`
- Create: `trieshake-zip/python/tests/test_algorithm.py`

- [ ] **Step 1: Write failing test for chunk_string**

```python
"""Tests for algorithm module."""
import pytest
from trieshake_zip.algorithm import chunk_string, parse_zip_path, compute_target_path


def test_chunk_string_empty():
    """Empty string produces empty list."""
    assert chunk_string("", 4) == []


def test_chunk_string_shorter_than_prefix():
    """String shorter than prefix-length produces single chunk."""
    assert chunk_string("AB", 4) == ["AB"]


def test_chunk_string_equal_to_prefix():
    """String equal to prefix-length produces single chunk."""
    assert chunk_string("ABCD", 4) == ["ABCD"]


def test_chunk_string_with_remainder():
    """String longer than prefix-length splits with remainder."""
    assert chunk_string("ABCDEF", 4) == ["ABCD", "EF"]


def test_chunk_string_clean_multiple():
    """Clean multiple produces exact chunks."""
    assert chunk_string("ABCDEF", 2) == ["AB", "CD", "EF"]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd trieshake-zip/python && pytest tests/test_algorithm.py::test_chunk_string_empty -v`
Expected: FAIL - "cannot import name 'chunk_string'"

- [ ] **Step 3: Write algorithm.py with chunk_string**

```python
"""Path computation functions for trieshake algorithm.

Pure functions adapted from trieshake.planner.
"""
from typing import List


def chunk_string(s: str, n: int) -> List[str]:
    """Split string into chunks of n characters, with shorter remainder if needed.

    Args:
        s: String to chunk
        n: Chunk size

    Returns:
        List of chunks
    """
    if not s:
        return []

    chunks = []
    for i in range(0, len(s), n):
        chunks.append(s[i:i+n])
    return chunks
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_algorithm.py -v`
Expected: PASS - 5 tests

- [ ] **Step 5: Write failing test for parse_zip_path**

```python
def test_parse_zip_path_with_parents():
    """Path with multiple parent directories."""
    result = parse_zip_path("BL/00/01/file.txt")
    assert result["parents"] == ["BL", "00", "01"]
    assert result["leafname"] == "file.txt"


def test_parse_zip_path_root_level():
    """Root-level file has no parents."""
    result = parse_zip_path("file.txt")
    assert result["parents"] == []
    assert result["leafname"] == "file.txt"


def test_parse_zip_path_leading_slash():
    """Leading slash is stripped."""
    result = parse_zip_path("/dir/file.txt")
    assert result["parents"] == ["dir"]
    assert result["leafname"] == "file.txt"


def test_parse_zip_path_deeply_nested():
    """Deeply nested path."""
    result = parse_zip_path("a/b/c/d/data.csv")
    assert result["parents"] == ["a", "b", "c", "d"]
    assert result["leafname"] == "data.csv"
```

- [ ] **Step 6: Run test to verify it fails**

Run: `pytest tests/test_algorithm.py::test_parse_zip_path_with_parents -v`
Expected: FAIL - "cannot import name 'parse_zip_path'"

- [ ] **Step 7: Implement parse_zip_path**

```python
from typing import Dict


def parse_zip_path(path_str: str) -> Dict[str, any]:
    """Parse zip entry path into parent directories and leafname.

    Handles leading slashes.

    Args:
        path_str: Zip entry path

    Returns:
        Dict with 'parents' (list) and 'leafname' (str)
    """
    # Strip leading slash
    if path_str.startswith("/"):
        path_str = path_str[1:]

    parts = [p for p in path_str.split("/") if p]

    return {
        "parents": parts[:-1] if len(parts) > 1 else [],
        "leafname": parts[-1] if parts else ""
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `pytest tests/test_algorithm.py -v`
Expected: PASS - 9 tests

- [ ] **Step 9: Write failing test for compute_target_path**

```python
def test_compute_target_path_encoded():
    """Forward transformation with encoded leafname."""
    result = compute_target_path(["BL", "00", "01"], "file.txt", 4, True)
    assert result["target_dir"] == "BL00/01"
    assert result["target_filename"] == "BL00_01_file.txt"
    assert result["chunks"] == ["BL00", "01"]
    assert result["concat_string"] == "BL0001"


def test_compute_target_path_not_encoded():
    """Forward transformation without encoded leafname."""
    result = compute_target_path(["BL", "00"], "data.csv", 4, False)
    assert result["target_dir"] == "BL00"
    assert result["target_filename"] == "data.csv"


def test_compute_target_path_root_level():
    """Root-level file uses leafname stem as concat string."""
    result = compute_target_path([], "report.txt", 4, True)
    assert result["target_dir"] == "repo/rt"
    assert result["target_filename"] == "repo_rt_report.txt"
    assert result["concat_string"] == "report"


def test_compute_target_path_prefix_3():
    """Prefix-length 3 produces different chunking."""
    result = compute_target_path(["AB", "CD", "EF"], "x.txt", 3, True)
    assert result["target_dir"] == "ABC/DEF"
    assert result["target_filename"] == "ABC_DEF_x.txt"
```

- [ ] **Step 10: Run test to verify it fails**

Run: `pytest tests/test_algorithm.py::test_compute_target_path_encoded -v`
Expected: FAIL - "cannot import name 'compute_target_path'"

- [ ] **Step 11: Implement compute_target_path**

```python
def compute_target_path(
    parents: List[str],
    leafname: str,
    prefix_length: int,
    encode_leafname: bool
) -> Dict[str, any]:
    """Compute trieshake target path for a file.

    Args:
        parents: List of parent directory names
        leafname: Original filename
        prefix_length: Chunk size
        encode_leafname: If True, prepend prefix to filename

    Returns:
        Dict with target_dir, target_filename, chunks, concat_string
    """
    # Determine concat string
    if parents:
        concat_string = "".join(parents)
        actual_leafname = leafname
    else:
        # Root-level: use filename stem as concat string
        stem = leafname.rsplit(".", 1)[0] if "." in leafname else leafname
        concat_string = stem
        actual_leafname = leafname

    # Chunk concat string
    chunks = chunk_string(concat_string, prefix_length)

    # Build target directory
    target_dir = "/".join(chunks) if chunks else "."

    # Build target filename
    if encode_leafname and chunks:
        target_filename = "_".join(chunks) + "_" + actual_leafname
    else:
        target_filename = actual_leafname

    return {
        "target_dir": target_dir,
        "target_filename": target_filename,
        "chunks": chunks,
        "concat_string": concat_string
    }
```

- [ ] **Step 12: Run test to verify it passes**

Run: `pytest tests/test_algorithm.py -v`
Expected: PASS - 13 tests

- [ ] **Step 13: Commit**

```bash
git add trieshake-zip/python/trieshake_zip/algorithm.py \
        trieshake-zip/python/tests/test_algorithm.py
git commit -m "feat(trieshake-zip): add algorithm module with path computation"
```

---

## Task 3: Sampler Module

**Files:**
- Create: `trieshake-zip/python/trieshake_zip/sampler.py`
- Create: `trieshake-zip/python/tests/test_sampler.py`

- [ ] **Step 1: Write failing test for extract_parent_dir**

```python
"""Tests for sampler module."""
import pytest
from trieshake_zip.sampler import extract_parent_dir


def test_extract_parent_dir_with_parents():
    """Extracts first parent directory."""
    assert extract_parent_dir("BL/00/01/file.txt") == "BL"


def test_extract_parent_dir_root_level():
    """Root-level file returns None."""
    assert extract_parent_dir("file.txt") is None


def test_extract_parent_dir_single_level():
    """Single-level path returns parent."""
    assert extract_parent_dir("data/file.csv") == "data"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_sampler.py::test_extract_parent_dir_with_parents -v`
Expected: FAIL - "cannot import name 'extract_parent_dir'"

- [ ] **Step 3: Write sampler.py with extract_parent_dir**

```python
"""Sample mode: extract subset of zip entries by parent dir count and file count."""
import zipfile
from typing import Optional, Dict


def extract_parent_dir(entry_name: str) -> Optional[str]:
    """Extract first parent directory from zip entry path.

    Args:
        entry_name: Zip entry path

    Returns:
        First parent directory name, or None if at root level
    """
    slash_pos = entry_name.find("/")
    if slash_pos == -1:
        return None
    return entry_name[:slash_pos]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_sampler.py -v`
Expected: PASS - 3 tests

- [ ] **Step 5: Write failing integration test for sample**

```python
import tempfile
import os
from pathlib import Path
from trieshake_zip.sampler import sample


def create_test_zip(zip_path, entries):
    """Create a zip file with given entries.

    Args:
        zip_path: Path to output zip
        entries: List of (path, content) tuples
    """
    with zipfile.ZipFile(zip_path, "w") as zf:
        for entry_name, content in entries:
            zf.writestr(entry_name, content)


def list_zip_entries(zip_path):
    """List all entry names in a zip file."""
    with zipfile.ZipFile(zip_path, "r") as zf:
        return zf.namelist()


def test_sample_integration():
    """Sample creates zip with limited files and dirs."""
    with tempfile.TemporaryDirectory() as tmpdir:
        source_zip = os.path.join(tmpdir, "source.zip")
        output_zip = os.path.join(tmpdir, "output.zip")

        # Create test source zip with 3 dirs, 5 files each
        entries = []
        for i in range(1, 4):  # dir1, dir2, dir3
            for j in range(1, 6):  # file1-5
                entries.append((f"dir{i}/file{j}.txt", f"content{j}"))

        create_test_zip(source_zip, entries)

        # Sample: max-dirs=2, max-files=7
        result = sample(source_zip, output_zip, max_dirs=2, max_files=7)

        # Verify output
        output_entries = list_zip_entries(output_zip)
        assert len(output_entries) == 7
        assert all(
            e.startswith("dir1/") or e.startswith("dir2/")
            for e in output_entries
        )
        assert result["files_written"] == 7
        assert result["dirs_included"] == 2
```

- [ ] **Step 6: Run test to verify it fails**

Run: `pytest tests/test_sampler.py::test_sample_integration -v`
Expected: FAIL - "cannot import name 'sample'"

- [ ] **Step 7: Implement sample function**

```python
def sample(
    source_zip: str,
    output_zip: str,
    max_dirs: int = 10,
    max_files: int = 100
) -> Dict[str, int]:
    """Sample entries from source zip to output zip.

    Args:
        source_zip: Path to source zip file
        output_zip: Path to output zip file
        max_dirs: Maximum parent directories to include
        max_files: Maximum total files to include

    Returns:
        Dict with dirs_included and files_written counts
    """
    dirs_seen = set()
    files_written = 0

    with zipfile.ZipFile(source_zip, "r") as zin:
        with zipfile.ZipFile(output_zip, "w") as zout:
            for entry in zin.infolist():
                entry_name = entry.filename

                # Skip directory entries
                if entry_name.endswith("/"):
                    continue

                parent_dir = extract_parent_dir(entry_name)
                if parent_dir:
                    new_dirs_seen = dirs_seen | {parent_dir}
                else:
                    new_dirs_seen = dirs_seen

                # Check limits
                if len(new_dirs_seen) <= max_dirs and files_written < max_files:
                    # Copy entry
                    zout.writestr(entry, zin.read(entry_name))
                    dirs_seen = new_dirs_seen
                    files_written += 1
                else:
                    # Limits reached
                    break

    return {
        "dirs_included": len(dirs_seen),
        "files_written": files_written
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `pytest tests/test_sampler.py -v`
Expected: PASS - 4 tests

- [ ] **Step 9: Commit**

```bash
git add trieshake-zip/python/trieshake_zip/sampler.py \
        trieshake-zip/python/tests/test_sampler.py
git commit -m "feat(trieshake-zip): add sampler module with integration test"
```

---

## Task 4: Transformer Module

**Files:**
- Create: `trieshake-zip/python/trieshake_zip/transformer.py`
- Create: `trieshake-zip/python/tests/test_transformer.py`

- [ ] **Step 1: Write failing test for collision tracking**

```python
"""Tests for transformer module."""
import pytest
from trieshake_zip.transformer import track_collision


def test_track_collision_first_occurrence():
    """First occurrence has no suffix."""
    tracker = {}
    result = track_collision(tracker, "BL00/01/file.txt")
    assert result == "file.txt"
    assert tracker["BL00/01/file.txt"] == 0


def test_track_collision_second_occurrence():
    """Second occurrence gets --collision1 suffix."""
    tracker = {"BL00/01/file.txt": 0}
    result = track_collision(tracker, "BL00/01/file.txt")
    assert result == "file--collision1.txt"
    assert tracker["BL00/01/file.txt"] == 1


def test_track_collision_third_occurrence():
    """Third occurrence gets --collision2 suffix."""
    tracker = {"BL00/01/file.txt": 1}
    result = track_collision(tracker, "BL00/01/file.txt")
    assert result == "file--collision2.txt"
    assert tracker["BL00/01/file.txt"] == 2
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_transformer.py::test_track_collision_first_occurrence -v`
Expected: FAIL - "cannot import name 'track_collision'"

- [ ] **Step 3: Write transformer.py with collision tracking**

```python
"""Transform mode: apply trieshake algorithm to zip entries."""
import zipfile
from typing import Dict
from trieshake_zip.algorithm import parse_zip_path, compute_target_path


def insert_collision_suffix(filename: str, n: int) -> str:
    """Insert --collisionN before the file extension.

    Args:
        filename: Original filename
        n: Collision number

    Returns:
        Filename with suffix inserted
    """
    dot_pos = filename.rfind(".")
    if dot_pos > 0:
        return f"{filename[:dot_pos]}--collision{n}{filename[dot_pos:]}"
    return f"{filename}--collision{n}"


def track_collision(tracker: Dict[str, int], target_path: str) -> str:
    """Check if target path collides, update tracker, return final filename.

    Args:
        tracker: Dict of {\"dir/file.txt\" -> collision_count}
        target_path: Full target path including filename

    Returns:
        Filename with --collisionN suffix if needed
    """
    if target_path not in tracker:
        tracker[target_path] = 0
        # Extract filename from path
        return target_path.split("/")[-1]
    else:
        n = tracker[target_path]
        tracker[target_path] = n + 1
        filename = target_path.split("/")[-1]
        return insert_collision_suffix(filename, n + 1)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_transformer.py -v`
Expected: PASS - 3 tests

- [ ] **Step 5: Write failing integration test for transform**

```python
import tempfile
import os
import zipfile


def create_test_zip(zip_path, entries):
    """Create a zip file with given entries."""
    with zipfile.ZipFile(zip_path, "w") as zf:
        for entry_name, content in entries:
            zf.writestr(entry_name, content)


def list_zip_entries(zip_path):
    """List all entry names in a zip file."""
    with zipfile.ZipFile(zip_path, "r") as zf:
        return zf.namelist()


from trieshake_zip.transformer import transform


def test_transform_integration():
    """Transform applies trieshake structure."""
    with tempfile.TemporaryDirectory() as tmpdir:
        source_zip = os.path.join(tmpdir, "source.zip")
        output_zip = os.path.join(tmpdir, "output.zip")

        # Create test source zip
        create_test_zip(source_zip, [
            ("BL/00/01/file.txt", "content1"),
            ("BL/00/02/file.txt", "content2"),
            ("AB/CD/data.csv", "data")
        ])

        # Transform with prefix-length 4, encoded leafnames
        result = transform(source_zip, output_zip, prefix_length=4, encode_leafname=True)

        # Verify output structure
        entries = set(list_zip_entries(output_zip))
        assert len(entries) == 3
        assert "BL00/01/BL00_01_file.txt" in entries
        assert "BL00/02/BL00_02_file.txt" in entries
        assert "ABCD/ABCD_data.csv" in entries
        assert result["processed"] == 3
```

- [ ] **Step 6: Run test to verify it fails**

Run: `pytest tests/test_transformer.py::test_transform_integration -v`
Expected: FAIL - "cannot import name 'transform'"

- [ ] **Step 7: Implement transform function**

```python
from typing import Optional, List, Tuple


def transform(
    source_zip: str,
    output_zip: str,
    prefix_length: int = 4,
    encode_leafname: bool = True,
    report: Optional[str] = None
) -> Dict[str, int]:
    """Transform source zip to output zip using trieshake algorithm.

    Args:
        source_zip: Path to source zip
        output_zip: Path to output zip
        prefix_length: Chunk size
        encode_leafname: Prepend prefix to filename
        report: Path to write collision report (optional)

    Returns:
        Dict with processed count and collision count
    """
    collision_tracker = {}
    collisions: List[Tuple[str, str, str]] = []
    processed = 0

    with zipfile.ZipFile(source_zip, "r") as zin:
        with zipfile.ZipFile(output_zip, "w") as zout:
            for entry in zin.infolist():
                entry_name = entry.filename

                # Skip directory entries
                if entry_name.endswith("/"):
                    continue

                # Parse path
                parsed = parse_zip_path(entry_name)
                parents = parsed["parents"]
                leafname = parsed["leafname"]

                # Compute target
                target = compute_target_path(parents, leafname, prefix_length, encode_leafname)
                target_dir = target["target_dir"]
                target_filename = target["target_filename"]

                # Check collision
                full_target = f"{target_dir}/{target_filename}"
                final_filename = track_collision(collision_tracker, full_target)

                # Track collision if occurred
                if final_filename != target_filename:
                    collisions.append((
                        entry_name,
                        full_target,
                        f"{target_dir}/{final_filename}"
                    ))

                # Write entry
                new_entry_name = f"{target_dir}/{final_filename}"
                zout.writestr(new_entry_name, zin.read(entry_name))
                processed += 1

    # Write collision report if requested
    if report and collisions:
        with open(report, "w") as f:
            for source, target, actual in collisions:
                f.write(f"{source} -> {target} (collision, actual: {actual})\n")

    return {
        "processed": processed,
        "collisions": len(collisions)
    }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `pytest tests/test_transformer.py -v`
Expected: PASS - 4 tests

- [ ] **Step 9: Write failing test for collision handling**

```python
def test_transform_with_collisions():
    """Collisions get --collisionN suffix."""
    with tempfile.TemporaryDirectory() as tmpdir:
        source_zip = os.path.join(tmpdir, "source.zip")
        output_zip = os.path.join(tmpdir, "output.zip")

        # Create test zip with paths that produce collision
        create_test_zip(source_zip, [
            ("AB/CD/file.txt", "content1"),
            ("A/BCD/file.txt", "content2")
        ])

        # Transform (both produce "ABCD/ABCD_file.txt")
        result = transform(source_zip, output_zip, prefix_length=4)

        # Verify collision handling
        entries = set(list_zip_entries(output_zip))
        assert len(entries) == 2
        assert "ABCD/ABCD_file.txt" in entries
        assert "ABCD/ABCD_file--collision1.txt" in entries
        assert result["collisions"] == 1
```

- [ ] **Step 10: Run test to verify it passes**

Run: `pytest tests/test_transformer.py -v`
Expected: PASS - 5 tests

- [ ] **Step 11: Commit**

```bash
git add trieshake-zip/python/trieshake_zip/transformer.py \
        trieshake-zip/python/tests/test_transformer.py
git commit -m "feat(trieshake-zip): add transformer module with collision handling"
```

---

## Task 5: CLI Module

**Files:**
- Create: `trieshake-zip/python/trieshake_zip/cli.py`
- Create: `trieshake-zip/python/trieshake_zip/__main__.py`

- [ ] **Step 1: Write cli.py with argument parsing**

```python
"""CLI entry point for trieshake-zip."""
import argparse
import sys
from trieshake_zip.sampler import sample
from trieshake_zip.transformer import transform


def create_parser() -> argparse.ArgumentParser:
    """Create argument parser."""
    parser = argparse.ArgumentParser(
        description="trieshake-zip — Apply trieshake algorithm to zip archives",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )

    parser.add_argument("mode", choices=["sample", "transform"],
                        help="Mode: sample or transform")
    parser.add_argument("input", help="Input zip path")
    parser.add_argument("-o", "--output", required=True,
                        help="Output zip path")

    # Sample mode options
    parser.add_argument("--max-files", type=int, default=100,
                        help="Max files to extract (sample mode, default 100)")
    parser.add_argument("--max-dirs", type=int, default=10,
                        help="Max parent directories (sample mode, default 10)")

    # Transform mode options
    parser.add_argument("-p", "--prefix-length", type=int, default=4,
                        help="Characters per chunk (transform mode, default 4)")
    parser.add_argument("--no-encode-leafname", action="store_true",
                        help="Use plain leafnames (transform mode)")
    parser.add_argument("--report", help="Write collision report (transform mode)")

    return parser


def main():
    """CLI entry point."""
    parser = create_parser()
    args = parser.parse_args()

    try:
        if args.mode == "sample":
            result = sample(
                args.input,
                args.output,
                max_dirs=args.max_dirs,
                max_files=args.max_files
            )
            print(f"Sampled {result['files_written']} files from {result['dirs_included']} directories")

        elif args.mode == "transform":
            result = transform(
                args.input,
                args.output,
                prefix_length=args.prefix_length,
                encode_leafname=not args.no_encode_leafname,
                report=args.report
            )
            print(f"Transformed {result['processed']} files ({result['collisions']} collisions)")

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Write __main__.py**

```python
"""Entry point for python -m trieshake_zip."""
from trieshake_zip.cli import main

if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Test CLI manually - sample mode**

Run:
```bash
cd trieshake-zip/python
python -m trieshake_zip sample ../../METADATA.zip -o /tmp/sample.zip --max-files 10 --max-dirs 2
```

Expected: "Sampled 10 files from 2 directories"

- [ ] **Step 4: Verify sample output**

Run: `python -m zipfile -l /tmp/sample.zip`
Expected: List of ~10 files from 2 parent directories

- [ ] **Step 5: Test CLI manually - transform mode**

Run:
```bash
python -m trieshake_zip transform /tmp/sample.zip -o /tmp/transformed.zip -p 4
```

Expected: "Transformed 10 files (N collisions)"

- [ ] **Step 6: Verify transform output**

Run: `python -m zipfile -l /tmp/transformed.zip`
Expected: Files with trieshake structure (chunked dirs, encoded filenames)

- [ ] **Step 7: Test help flag**

Run: `python -m trieshake_zip -h`
Expected: Usage message with modes and options

- [ ] **Step 8: Commit**

```bash
git add trieshake-zip/python/trieshake_zip/cli.py \
        trieshake-zip/python/trieshake_zip/__main__.py
git commit -m "feat(trieshake-zip): add CLI with sample and transform modes"
```

---

## Task 6: Documentation

**Files:**
- Create: `trieshake-zip/python/README.md`

- [ ] **Step 1: Write README**

```markdown
# trieshake-zip (Python)

Apply trieshake algorithm to zip archives without disk extraction.

## Installation

```bash
pip install -e .
```

## Usage

### Sample Mode

Extract subset of entries from large zip:

```bash
# Using installed command
trieshake-zip sample input.zip -o sample.zip --max-files 100 --max-dirs 10

# Using python -m
python -m trieshake_zip sample input.zip -o sample.zip --max-files 100 --max-dirs 10
```

Options:
- `--max-files N` - Maximum files to extract (default 100)
- `--max-dirs N` - Maximum parent directories to sample (default 10)

### Transform Mode

Apply trieshake algorithm to reorganize zip contents:

```bash
# Using installed command
trieshake-zip transform input.zip -o output.zip -p 4

# Using python -m
python -m trieshake_zip transform input.zip -o output.zip -p 4
```

Options:
- `-p, --prefix-length N` - Characters per directory chunk (default 4)
- `--no-encode-leafname` - Use plain leafnames (no prefix encoding)
- `--report FILE` - Write collision report to file

## Examples

```bash
# Create sample from large archive
trieshake-zip sample METADATA.zip -o sample.zip

# Transform with default settings
trieshake-zip transform sample.zip -o transformed.zip

# Transform with prefix-length 3
trieshake-zip transform sample.zip -o out.zip -p 3

# Transform with plain leafnames
trieshake-zip transform sample.zip -o out.zip --no-encode-leafname

# Transform and save collision report
trieshake-zip transform sample.zip -o out.zip --report collisions.txt
```

## Testing

```bash
pytest
```

## Algorithm

See [trieshake SPEC.md](../../trieshake/SPEC.md) for algorithm details.

## License

CC-BY-SA 4.0
```

- [ ] **Step 2: Commit**

```bash
git add trieshake-zip/python/README.md
git commit -m "docs(trieshake-zip): add README with usage examples"
```

---

## Task 7: Final Integration Test

**Files:**
- Test with real METADATA.zip file

- [ ] **Step 1: Create sample from METADATA.zip**

Run:
```bash
cd trieshake-zip/python
trieshake-zip sample ../../METADATA.zip -o /tmp/metadata-sample.zip --max-files 50 --max-dirs 5
```

Expected: "Sampled 50 files from 5 directories"

- [ ] **Step 2: Verify sample size**

Run: `ls -lh /tmp/metadata-sample.zip`
Expected: Much smaller than 1.4GB (probably <10MB)

- [ ] **Step 3: Transform sample**

Run:
```bash
trieshake-zip transform /tmp/metadata-sample.zip -o /tmp/metadata-transformed.zip -p 4 --report /tmp/collisions.txt
```

Expected: "Transformed 50 files (N collisions)"

- [ ] **Step 4: Extract and inspect transformed zip**

Run:
```bash
mkdir -p /tmp/metadata-extracted
python -m zipfile -e /tmp/metadata-transformed.zip /tmp/metadata-extracted
ls -R /tmp/metadata-extracted | head -30
```

Expected: Trieshake directory structure (chunked dirs, encoded filenames)

- [ ] **Step 5: Check collision report if exists**

Run: `cat /tmp/collisions.txt`
Expected: List of collisions (if any occurred) or file doesn't exist

- [ ] **Step 6: Verify file contents preserved**

Run:
```bash
# Extract one file from each zip and compare
python -m zipfile -e /tmp/metadata-sample.zip /tmp/sample-extracted
python -m zipfile -e /tmp/metadata-transformed.zip /tmp/transformed-extracted
# Find a file and compare content
find /tmp/sample-extracted -type f | head -1 | xargs cat > /tmp/original.txt
find /tmp/transformed-extracted -type f | head -1 | xargs cat > /tmp/transformed.txt
diff /tmp/original.txt /tmp/transformed.txt
```

Expected: No differences (files identical)

- [ ] **Step 7: Run full test suite**

Run: `pytest -v`
Expected: All tests pass

- [ ] **Step 8: Final commit**

```bash
git add -A
git commit -m "test(trieshake-zip): verify end-to-end workflow with METADATA.zip"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Sample mode: extract subset by dir/file count - Task 3
- [x] Transform mode: apply trieshake algorithm - Task 4
- [x] Algorithm: concat, chunk, encode - Task 2
- [x] CLI: modes, options, validation - Task 5
- [x] Error handling: validation, runtime errors - integrated in all tasks
- [x] Testing: unit + integration tests - Tasks 2, 3, 4
- [x] Documentation: README - Task 6

**Placeholder check:**
- No TBD/TODO
- All code blocks complete
- All test assertions specific
- All commands have expected output

**Type consistency:**
- `chunk_string` returns List[str] - consistent
- `parse_zip_path` returns Dict with "parents" and "leafname" - consistent
- `compute_target_path` returns Dict with "target_dir", "target_filename", etc. - consistent
- `track_collision` takes Dict and str, returns str - consistent

---

## Execution Notes

- Each task builds incrementally
- Tests written before implementation (TDD)
- Frequent commits after each task
- Manual testing with real METADATA.zip in final task
- Zero external dependencies (stdlib only)
