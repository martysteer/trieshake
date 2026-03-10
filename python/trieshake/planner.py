"""Planner module — chunking, target computation, collision detection.

Pure functions: no filesystem side effects.
"""

from dataclasses import dataclass, field
from pathlib import PurePosixPath


@dataclass
class PlanEntry:
    """A single planned file move."""

    source_path: PurePosixPath
    target_dir: PurePosixPath
    target_filename: str
    chunks: list[str]
    concat_string: str
    leafname: str
    extension: str = ""
    is_collision: bool = False
    collision_of: str | None = None


def chunk_string(s: str, prefix_length: int) -> list[str]:
    """Split a concat string into chunks of prefix_length, with a shorter remainder."""
    if not s:
        return []
    chunks = []
    for i in range(0, len(s), prefix_length):
        chunks.append(s[i : i + prefix_length])
    return chunks


def _strip_extension(filename: str, extension: str) -> tuple[str, str]:
    """Strip a (possibly multi-part) extension from a filename.

    Returns (stem, ext) where ext includes the leading dot.
    """
    lower = filename.lower()
    ext_lower = extension.lower()
    if lower.endswith(ext_lower):
        stem = filename[: len(filename) - len(extension)]
        return stem, filename[len(stem) :]
    return filename, ""


def compute_target(
    rel_path: PurePosixPath,
    prefix_length: int,
    extension: str,
    encode_leafname: bool = True,
) -> PlanEntry:
    """Compute the forward-mode target for a file at rel_path."""
    parts = list(rel_path.parts)
    filename = parts[-1]
    parent_segments = parts[:-1]

    if parent_segments:
        concat_string = "".join(parent_segments)
        leafname = filename
    else:
        # Root-level file: use filename stem as concat string
        stem, _ = _strip_extension(filename, extension)
        concat_string = stem
        leafname = filename

    chunks = chunk_string(concat_string, prefix_length)
    target_dir = PurePosixPath(*chunks) if chunks else PurePosixPath(".")

    if encode_leafname and chunks:
        target_filename = "_".join(chunks) + "_" + leafname
    else:
        target_filename = leafname

    return PlanEntry(
        source_path=rel_path,
        target_dir=target_dir,
        target_filename=target_filename,
        chunks=chunks,
        concat_string=concat_string,
        leafname=leafname,
        extension=extension,
    )


def _strip_collision_suffix(leafname: str) -> str:
    """Remove --collisionN suffix from a leafname.

    E.g. 'data--collision1.txt' -> 'data.txt'
    """
    import re

    return re.sub(r"--collision\d+", "", leafname)


def compute_reverse_target(
    rel_path: PurePosixPath,
    extension: str,
    new_prefix_length: int | None = None,
    encode_leafname: bool = True,
) -> PlanEntry | None:
    """Compute the reverse-mode target for a file at rel_path.

    Args:
        rel_path: Relative path of the file (within base_dir).
        extension: The file extension being processed.
        new_prefix_length: If provided, regroup at this prefix length.
        encode_leafname: Whether to encode prefix in output filename.

    Returns:
        PlanEntry for the reverse target, or None if file should be skipped.
    """
    parts = list(rel_path.parts)
    filename = parts[-1]
    dir_parts = parts[:-1]

    # Root-level files are skipped in reverse mode
    if not dir_parts:
        return None

    # Filter out 'collisions' directory segments (not prefix groups)
    prefix_groups = [d for d in dir_parts if d != "collisions"]

    if not prefix_groups:
        return None

    # Build expected prefix from directory parts
    expected_prefix = "_".join(prefix_groups) + "_"

    # Validate filename starts with expected prefix
    if not filename.startswith(expected_prefix):
        return None

    # Strip prefix to recover leafname
    leafname = filename[len(expected_prefix) :]

    # Strip collision suffix
    leafname = _strip_collision_suffix(leafname)

    # Rejoin prefix groups into concat string
    concat_string = "".join(prefix_groups)

    if new_prefix_length is not None:
        # Regroup: re-chunk at new prefix length
        chunks = chunk_string(concat_string, new_prefix_length)
        target_dir = PurePosixPath(*chunks) if chunks else PurePosixPath(".")

        if encode_leafname and chunks:
            target_filename = "_".join(chunks) + "_" + leafname
        else:
            target_filename = leafname
    else:
        # No regroup: keep same directory, just strip prefix from filename
        chunks = prefix_groups
        target_dir = PurePosixPath(*prefix_groups)
        target_filename = leafname

    return PlanEntry(
        source_path=rel_path,
        target_dir=target_dir,
        target_filename=target_filename,
        chunks=chunks,
        concat_string=concat_string,
        leafname=leafname,
        extension=extension,
    )


def _collision_filename(filename: str, extension: str, n: int) -> str:
    """Insert --collisionN before the extension."""
    stem, ext = _strip_extension(filename, extension)
    if not ext:
        # Fallback: split on last dot
        dot_pos = filename.rfind(".")
        if dot_pos > 0:
            stem = filename[:dot_pos]
            ext = filename[dot_pos:]
        else:
            stem = filename
            ext = ""
    return f"{stem}--collision{n}{ext}"


def detect_collisions(plans: list[PlanEntry]) -> list[PlanEntry]:
    """Detect and resolve collisions in a list of plan entries.

    First occurrence keeps its target; subsequent get --collisionN suffixes.
    """
    seen: dict[str, int] = {}  # target_path -> collision count

    for plan in plans:
        target_key = str(plan.target_dir / plan.target_filename)

        if target_key not in seen:
            seen[target_key] = 0
        else:
            seen[target_key] += 1
            n = seen[target_key]
            original_target = target_key
            # Determine extension from original filename
            base_filename = plan.target_filename
            new_filename = _collision_filename(base_filename, plan.extension, n)
            plan.target_filename = new_filename
            plan.is_collision = True
            plan.collision_of = original_target

    return plans
