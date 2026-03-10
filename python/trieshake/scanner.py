"""Scanner module — walk a directory tree, collect files matching an extension."""

from pathlib import Path


def scan_files(
    base_dir: Path, extension: str | None = None
) -> list[tuple[Path, Path]]:
    """Walk base_dir and collect files matching the extension.

    Args:
        base_dir: The root directory to scan.
        extension: File extension to match (case-insensitive, suffix-based).
                   If None, match all files.

    Returns:
        List of (absolute_path, relative_path) tuples, sorted by relative path.
    """
    results = []
    for abs_path in sorted(base_dir.rglob("*")):
        if not abs_path.is_file():
            continue
        # Skip hidden files
        if any(part.startswith(".") for part in abs_path.relative_to(base_dir).parts):
            continue
        if extension is not None:
            if not abs_path.name.lower().endswith(extension.lower()):
                continue
        rel_path = abs_path.relative_to(base_dir)
        results.append((abs_path, rel_path))
    return results
