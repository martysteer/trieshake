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
