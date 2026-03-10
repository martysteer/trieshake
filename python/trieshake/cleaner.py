"""Cleaner module — remove empty/obsolete directories after moves."""

from pathlib import Path


def cleanup_directories(
    base_dir: Path, extension: str | None = None
) -> list[Path]:
    """Remove directories that are empty or contain no files matching extension.

    Directories are removed deepest-first.

    Args:
        base_dir: The root directory.
        extension: If set, remove dirs that have no files with this extension.

    Returns:
        List of removed directory paths (relative to base_dir).
    """
    removed = []

    # Collect all directories, sorted deepest-first
    all_dirs = sorted(
        [d for d in base_dir.rglob("*") if d.is_dir()],
        key=lambda p: len(p.parts),
        reverse=True,
    )

    for d in all_dirs:
        if d == base_dir:
            continue

        # Check if directory has any non-hidden files
        has_matching = False
        for f in d.iterdir():
            if f.name.startswith("."):
                continue
            if f.is_file():
                if extension is None:
                    has_matching = True
                    break
                if f.name.lower().endswith(extension.lower()):
                    has_matching = True
                    break
            if f.is_dir():
                # Still has subdirectories — skip
                has_matching = True
                break

        if not has_matching:
            rel = d.relative_to(base_dir)
            try:
                # Remove hidden files first (e.g. .DS_Store)
                for hidden in d.iterdir():
                    if hidden.is_file() and hidden.name.startswith("."):
                        hidden.unlink()
                d.rmdir()
                removed.append(rel)
            except OSError:
                pass  # Directory not empty or permission error

    return removed
