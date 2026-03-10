"""CLI entry point for trieshake."""

import argparse
import sys
from pathlib import Path, PurePosixPath

from trieshake.scanner import scan_files
from trieshake.planner import compute_target, detect_collisions
from trieshake.executor import execute_plan, verify_plan
from trieshake.cleaner import cleanup_directories
from trieshake import reporter


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="trieshake",
        description="Reorganize files into grouped, prefix-based directory trees.",
    )
    parser.add_argument(
        "directory",
        type=Path,
        help="Path to the directory containing files to reorganize.",
    )
    parser.add_argument(
        "-e", "--extension",
        help="File extension to match (e.g. .txt, .mets.xml). Leading dot added if missing.",
    )
    parser.add_argument(
        "-p", "--prefix-length",
        type=int,
        default=4,
        help="Number of characters per directory chunk (default: 4).",
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually move files (default: dry run).",
    )
    parser.add_argument(
        "--output-plan",
        type=Path,
        help="Save the move plan to a CSV file.",
    )
    return parser.parse_args(argv)


def normalize_extension(ext: str | None) -> str | None:
    if ext is None:
        return None
    if not ext.startswith("."):
        ext = "." + ext
    return ext


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    base_dir = args.directory.resolve()

    if not base_dir.is_dir():
        print(f"Error: {base_dir} is not a directory.", file=sys.stderr)
        return 1

    extension = normalize_extension(args.extension)
    prefix_length = args.prefix_length

    # 1. Scan
    files = scan_files(base_dir, extension)
    reporter.print_scan_result(len(files))

    if not files:
        print("Nothing to do.")
        return 0

    # 2. Plan
    plans = []
    for abs_path, rel_path in files:
        plan = compute_target(
            PurePosixPath(*rel_path.parts),
            prefix_length=prefix_length,
            extension=extension or "",
        )
        plans.append(plan)

    plans = detect_collisions(plans)

    # Optional CSV export
    if args.output_plan:
        reporter.export_plan_csv(plans, args.output_plan)
        print(f"\U0001F4BE Plan exported to {args.output_plan}")

    reporter.print_plan_summary(plans, dry_run=not args.execute)

    if not args.execute:
        return 0

    # 3. Execute
    successes, failures = execute_plan(base_dir, plans)
    reporter.print_move_result(successes, failures)

    # 4. Verify
    lost = verify_plan(base_dir, plans)
    reporter.print_verify_result(lost)

    # 5. Cleanup (skip if files lost)
    if not lost:
        removed = cleanup_directories(base_dir, extension)
        reporter.print_cleanup_result(removed)
    else:
        print("\u26A0\uFE0F  Skipping cleanup — files are missing.")

    # 6. Collision report
    reporter.print_collision_report(plans)

    if failures or lost:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
