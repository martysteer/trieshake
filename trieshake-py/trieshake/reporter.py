"""Reporter module — output formatting and plan CSV export."""

import csv
from pathlib import Path, PurePosixPath

from trieshake.planner import PlanEntry


def print_scan_result(count: int) -> None:
    print(f"\U0001F50D Scanning — found {count} matching files.")


def print_plan_summary(plans: list[PlanEntry], dry_run: bool = True) -> None:
    moves = sum(
        1 for p in plans
        if str(p.source_path) != str(p.target_dir / p.target_filename)
    )
    skips = len(plans) - moves
    collisions = sum(1 for p in plans if p.is_collision)

    print(f"\U0001F4CB Building plan — {len(plans)} files total.")
    print(f"   {moves} to move, {skips} already correct.")
    if collisions:
        print(f"   \u26A0\uFE0F  {collisions} collisions detected.")

    if plans:
        p = plans[0]
        print(f"   Example: {p.source_path} -> {p.target_dir / p.target_filename}")

    if dry_run:
        print(f"\n\U0001F6AB Dry run — no files moved. Use --execute to apply.")


def print_move_result(
    successes: list[PlanEntry],
    failures: list[tuple[PlanEntry, str]],
) -> None:
    print(f"\U0001F4E6 Moving files — {len(successes)} succeeded, {len(failures)} failed.")
    for plan, err in failures:
        print(f"   \u274C {plan.source_path}: {err}")


def print_verify_result(lost: list[PlanEntry]) -> None:
    if lost:
        print(f"\u26A0\uFE0F  Verification — {len(lost)} files lost!")
        for p in lost:
            print(f"   Missing: {p.target_dir / p.target_filename}")
    else:
        print("\u2705 Verification — all files accounted for.")


def print_cleanup_result(removed: list[Path]) -> None:
    print(f"\U0001F9F9 Cleanup — removed {len(removed)} directories.")


def print_collision_report(plans: list[PlanEntry]) -> None:
    collisions = [p for p in plans if p.is_collision]
    if not collisions:
        return
    print(f"\n\U0001F4CB Collision report ({len(collisions)} collisions):")
    for p in collisions:
        print(f"   {p.source_path} -> {p.target_dir / p.target_filename}")
        print(f"      collides with: {p.collision_of}")


def export_plan_csv(plans: list[PlanEntry], output_path: Path) -> None:
    """Write the move plan to a CSV file."""
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "source_path", "target_path", "leafname", "concat_string",
            "prefix_groups", "is_collision", "collision_of",
            "already_correct", "action",
        ])
        for p in plans:
            target_path = str(p.target_dir / p.target_filename)
            already_correct = str(p.source_path) == target_path
            if p.is_collision:
                action = "collision"
            elif already_correct:
                action = "skip"
            else:
                action = "move"
            writer.writerow([
                str(p.source_path),
                target_path,
                p.leafname,
                p.concat_string,
                "|".join(p.chunks),
                str(p.is_collision).lower(),
                p.collision_of or "",
                str(already_correct).lower(),
                action,
            ])
