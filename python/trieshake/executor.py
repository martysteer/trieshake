"""Executor module — perform filesystem moves from a plan."""

import shutil
from pathlib import Path

from trieshake.planner import PlanEntry


def execute_plan(
    base_dir: Path, plans: list[PlanEntry]
) -> tuple[list[PlanEntry], list[tuple[PlanEntry, str]]]:
    """Execute the move plan: create directories and move files.

    Args:
        base_dir: The root directory (all paths are relative to this).
        plans: List of PlanEntry objects from the planner.

    Returns:
        (successes, failures) where failures is a list of (plan, error_message).
    """
    successes = []
    failures = []

    for plan in plans:
        source = base_dir / plan.source_path
        target_dir = base_dir / plan.target_dir
        target = target_dir / plan.target_filename

        # Skip files already at correct location
        if source == target:
            successes.append(plan)
            continue

        try:
            target_dir.mkdir(parents=True, exist_ok=True)
            shutil.move(str(source), str(target))
            successes.append(plan)
        except Exception as e:
            failures.append((plan, str(e)))

    return successes, failures


def verify_plan(
    base_dir: Path, plans: list[PlanEntry]
) -> list[PlanEntry]:
    """Verify all files exist at their target locations.

    Returns list of PlanEntry objects for files that are missing.
    """
    lost = []
    for plan in plans:
        target = base_dir / plan.target_dir / plan.target_filename
        source = base_dir / plan.source_path
        if not target.exists() and not source.exists():
            lost.append(plan)
    return lost
