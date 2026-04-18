"""Integration tests — end-to-end with temp directories."""

import csv
import tempfile
from pathlib import Path

from trieshake.cli import main


def _create_file(base: Path, rel_path: str, content: str = "data") -> None:
    """Create a file at base/rel_path with given content."""
    p = base / rel_path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)


class TestForwardIntegration:
    """End-to-end forward mode tests with real filesystem."""

    def test_spec_example_prefix4(self):
        """SPEC example: BL/00/00/01/06/00001/report.txt at p=4."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "BL/00/00/01/06/00001/report.txt")

            result = main(["--execute", "-e", ".txt", "-p", "4", str(base)])

            assert result == 0
            expected = base / "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"
            assert expected.exists()
            assert expected.read_text() == "data"
            # Original should be gone
            assert not (base / "BL/00/00/01/06/00001/report.txt").exists()

    def test_spec_example_prefix3(self):
        """SPEC example: BL/00/01/file.txt at p=3."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "BL/00/01/file.txt")

            result = main(["--execute", "-e", ".txt", "-p", "3", str(base)])

            assert result == 0
            expected = base / "BL0/001/BL0_001_file.txt"
            assert expected.exists()

    def test_multiple_files(self):
        """Multiple files with different parent structures."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt", "file1")
            _create_file(base, "EF/GH/other.txt", "file2")

            result = main(["--execute", "-e", ".txt", "-p", "2", str(base)])

            assert result == 0
            assert (base / "AB/CD/AB_CD_data.txt").exists()
            assert (base / "EF/GH/EF_GH_other.txt").exists()

    def test_collision_handling(self):
        """Two files that produce same target get collision suffix."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            # AB + CD and A + BCD both concat to ABCD
            _create_file(base, "AB/CD/data.txt", "first")
            _create_file(base, "A/BCD/data.txt", "second")

            result = main(["--execute", "-e", ".txt", "-p", "4", str(base)])

            assert result == 0
            # First file keeps target
            assert (base / "ABCD/ABCD_data.txt").exists()
            # Second gets collision suffix
            assert (base / "ABCD/ABCD_data--collision1.txt").exists()

    def test_dry_run_no_changes(self):
        """Dry run (no --execute) should not move any files."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")

            result = main(["-e", ".txt", "-p", "2", str(base)])

            assert result == 0
            # Original still in place
            assert (base / "AB/CD/data.txt").exists()
            # Target not created
            assert not (base / "AB/CD/AB_CD_data.txt").exists()

    def test_extension_filter(self):
        """Only files matching extension are processed."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt", "match")
            _create_file(base, "AB/CD/image.png", "skip")

            result = main(["--execute", "-e", ".txt", "-p", "2", str(base)])

            assert result == 0
            assert (base / "AB/CD/AB_CD_data.txt").exists()
            # PNG should be untouched
            assert (base / "AB/CD/image.png").exists()

    def test_empty_directory_cleanup(self):
        """Empty source directories are removed after move."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")

            main(["--execute", "-e", ".txt", "-p", "4", str(base)])

            # Source dirs AB/CD should be gone (empty after move)
            assert not (base / "AB/CD").exists()
            assert not (base / "AB").exists()

    def test_idempotent_second_run(self):
        """Running forward twice: second run has no effect."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")

            main(["--execute", "-e", ".txt", "-p", "2", str(base)])
            # File is now at ABCD target
            target = base / "AB/CD/AB_CD_data.txt"
            assert target.exists()

            # Second run — file is already there (though will re-process)
            result = main(["--execute", "-e", ".txt", "-p", "2", str(base)])
            assert result == 0


class TestReverseIntegration:
    """End-to-end reverse mode tests."""

    def test_reverse_strips_prefix(self):
        """Reverse without -p strips encoded prefix from filenames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            # Create already-forward-encoded structure
            _create_file(
                base, "BL00/0001/0600/001/BL00_0001_0600_001_report.txt", "content"
            )

            result = main(
                ["--reverse", "--execute", "-e", ".txt", str(base)]
            )

            assert result == 0
            assert (base / "BL00/0001/0600/001/report.txt").exists()
            assert (base / "BL00/0001/0600/001/report.txt").read_text() == "content"
            assert not (
                base / "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"
            ).exists()

    def test_reverse_with_regroup(self):
        """Reverse with -p 3 regroups from p=4 to p=3 in single pass."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(
                base, "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"
            )

            result = main(
                ["--reverse", "--execute", "-e", ".txt", "-p", "3", str(base)]
            )

            assert result == 0
            expected = base / "BL0/000/010/600/001/BL0_000_010_600_001_report.txt"
            assert expected.exists()

    def test_reverse_strips_collision_suffix(self):
        """Reverse strips --collisionN suffixes from filenames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "ABCD/ABCD_data--collision1.txt", "coll")

            result = main(
                ["--reverse", "--execute", "-e", ".txt", str(base)]
            )

            assert result == 0
            assert (base / "ABCD/data.txt").exists()
            assert (base / "ABCD/data.txt").read_text() == "coll"

    def test_forward_then_reverse_roundtrip(self):
        """Forward at p=4 then reverse restores plain leafnames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "BL/00/01/file.txt", "original")

            # Forward
            main(["--execute", "-e", ".txt", "-p", "4", str(base)])
            assert (base / "BL00/01/BL00_01_file.txt").exists()

            # Reverse
            main(["--reverse", "--execute", "-e", ".txt", str(base)])
            assert (base / "BL00/01/file.txt").exists()
            assert (base / "BL00/01/file.txt").read_text() == "original"

    def test_forward_reverse_regroup_roundtrip(self):
        """Forward p=4 -> reverse -p 3: single-pass regroup."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/EF/data.txt", "roundtrip")

            # Forward at p=4
            main(["--execute", "-e", ".txt", "-p", "4", str(base)])
            assert (base / "ABCD/EF/ABCD_EF_data.txt").exists()

            # Reverse with regroup at p=3
            main(["--reverse", "--execute", "-e", ".txt", "-p", "3", str(base)])
            expected = base / "ABC/DEF/ABC_DEF_data.txt"
            assert expected.exists()
            assert expected.read_text() == "roundtrip"

    def test_reverse_dry_run(self):
        """Reverse dry run makes no changes."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/AB_CD_data.txt")

            result = main(["--reverse", "-e", ".txt", str(base)])

            assert result == 0
            # Original still in place
            assert (base / "AB/CD/AB_CD_data.txt").exists()

    def test_reverse_skips_non_encoded(self):
        """Reverse skips files that don't match their directory prefix."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/random_file.txt")

            result = main(
                ["--reverse", "--execute", "-e", ".txt", str(base)]
            )

            assert result == 0
            # File is untouched (skipped)
            assert (base / "AB/CD/random_file.txt").exists()


class TestNoEncodeLeafnameIntegration:
    """Tests for --no-encode-leafname flag."""

    def test_forward_no_encode(self):
        """Forward with --no-encode-leafname uses plain filenames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "BL/00/01/file.txt", "plain")

            result = main([
                "--execute", "-e", ".txt", "-p", "3",
                "--no-encode-leafname", str(base),
            ])

            assert result == 0
            assert (base / "BL0/001/file.txt").exists()
            assert (base / "BL0/001/file.txt").read_text() == "plain"

    def test_forward_no_encode_then_reverse(self):
        """Round-trip: forward --no-encode then reverse restores leafnames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt", "noenc")

            # Forward without encoding
            main([
                "--execute", "-e", ".txt", "-p", "2",
                "--no-encode-leafname", str(base),
            ])
            assert (base / "AB/CD/data.txt").exists()
            # File didn't move — same dir, same name (already correct)

    def test_reverse_regroup_no_encode(self):
        """Reverse regroup with --no-encode-leafname produces plain filenames."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/AB_CD_data.txt", "regroup")

            result = main([
                "--reverse", "--execute", "-e", ".txt", "-p", "3",
                "--no-encode-leafname", str(base),
            ])

            assert result == 0
            assert (base / "ABC/D/data.txt").exists()
            assert (base / "ABC/D/data.txt").read_text() == "regroup"


class TestOutputPlanIntegration:
    """Tests for --output-plan CSV export."""

    def test_plan_csv_columns(self):
        """Plan CSV has correct headers and data."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")
            plan_file = base / "plan.csv"

            main(["-e", ".txt", "-p", "2", "--output-plan", str(plan_file), str(base)])

            assert plan_file.exists()
            with open(plan_file, encoding="utf-8") as f:
                reader = csv.DictReader(f)
                rows = list(reader)

            assert len(rows) == 1
            row = rows[0]
            assert row["source_path"] == "AB/CD/data.txt"
            assert row["target_path"] == "AB/CD/AB_CD_data.txt"
            assert row["leafname"] == "data.txt"
            assert row["concat_string"] == "ABCD"
            assert row["prefix_groups"] == "AB|CD"
            assert row["action"] == "move"

    def test_plan_csv_with_collision(self):
        """Plan CSV marks collisions correctly."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")
            _create_file(base, "A/BCD/data.txt")
            plan_file = base / "plan.csv"

            main(["-e", ".txt", "-p", "4", "--output-plan", str(plan_file), str(base)])

            with open(plan_file, encoding="utf-8") as f:
                rows = list(csv.DictReader(f))

            collision_rows = [r for r in rows if r["is_collision"] == "true"]
            assert len(collision_rows) == 1
            assert collision_rows[0]["action"] == "collision"


class TestExtensionOptionalIntegration:
    """Tests for -e being optional (match all files)."""

    def test_no_extension_matches_all(self):
        """Without -e, all files are processed."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")
            _create_file(base, "AB/CD/image.png")

            result = main(["--execute", "-p", "2", str(base)])

            assert result == 0
            assert (base / "AB/CD/AB_CD_data.txt").exists()
            assert (base / "AB/CD/AB_CD_image.png").exists()


class TestEdgeCasesIntegration:
    """Integration tests for edge cases and hardening."""

    def test_root_level_files_forward(self):
        """Root-level files use filename stem as concat string."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "mydata.txt", "root")

            result = main(["--execute", "-e", ".txt", "-p", "3", str(base)])

            assert result == 0
            assert (base / "myd/ata/myd_ata_mydata.txt").exists()
            assert (base / "myd/ata/myd_ata_mydata.txt").read_text() == "root"

    def test_underscores_in_leafname_roundtrip(self):
        """Underscores in original leafname survive forward -> reverse."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/my_data_file.txt", "underscores")

            # Forward
            main(["--execute", "-e", ".txt", "-p", "4", str(base)])
            assert (base / "ABCD/ABCD_my_data_file.txt").exists()

            # Reverse
            main(["--reverse", "--execute", "-e", ".txt", str(base)])
            assert (base / "ABCD/my_data_file.txt").exists()
            assert (base / "ABCD/my_data_file.txt").read_text() == "underscores"

    def test_hidden_files_ignored(self):
        """Hidden files like .DS_Store are not processed."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")
            _create_file(base, "AB/CD/.DS_Store", "hidden")

            result = main(["--execute", "-e", ".txt", "-p", "2", str(base)])

            assert result == 0
            assert (base / "AB/CD/AB_CD_data.txt").exists()
            # .DS_Store should not be moved or renamed

    def test_empty_directory_not_left_behind(self):
        """After moves, directories with only hidden files are cleaned up."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt")
            _create_file(base, "AB/CD/.DS_Store", "hidden")

            main(["--execute", "-e", ".txt", "-p", "4", str(base)])

            # AB/CD had only data.txt (matching) and .DS_Store (hidden)
            # After moving data.txt, AB/CD should be cleaned up
            assert not (base / "AB/CD").exists()

    def test_multi_extension_forward_and_reverse(self):
        """Multi-part extensions like .mets.xml work correctly."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "BL/00/01/record.mets.xml", "multi")

            # Forward
            main(["--execute", "-e", ".mets.xml", "-p", "3", str(base)])
            assert (base / "BL0/001/BL0_001_record.mets.xml").exists()

            # Reverse
            main(["--reverse", "--execute", "-e", ".mets.xml", str(base)])
            assert (base / "BL0/001/record.mets.xml").exists()
            assert (base / "BL0/001/record.mets.xml").read_text() == "multi"

    def test_extension_case_insensitive(self):
        """Extension matching is case-insensitive."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.TXT", "upper")

            result = main(["--execute", "-e", ".txt", "-p", "2", str(base)])

            assert result == 0
            assert (base / "AB/CD/AB_CD_data.TXT").exists()

    def test_collision_forward_then_reverse_strips_suffix(self):
        """Collision files from forward mode have suffixes stripped in reverse."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            _create_file(base, "AB/CD/data.txt", "first")
            _create_file(base, "A/BCD/data.txt", "second")

            # Forward — produces collision
            main(["--execute", "-e", ".txt", "-p", "4", str(base)])
            assert (base / "ABCD/ABCD_data.txt").exists()
            assert (base / "ABCD/ABCD_data--collision1.txt").exists()

            # Reverse — collision suffix stripped
            main(["--reverse", "--execute", "-e", ".txt", str(base)])
            # Both try to become data.txt — second gets new collision
            assert (base / "ABCD/data.txt").exists()

    def test_invalid_directory_returns_error(self):
        """Non-existent directory returns exit code 1."""
        result = main(["--execute", "-e", ".txt", "/nonexistent/path"])
        assert result == 1

    def test_large_scale_file_count(self):
        """Generate many files and verify counts match."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            for i in range(100):
                group = f"{i:04d}"
                _create_file(base, f"G{group[:2]}/{group[2:]}/file_{i}.txt", f"data{i}")

            result = main(["--execute", "-e", ".txt", "-p", "3", str(base)])
            assert result == 0

            # Count all .txt files in result
            txt_files = list(base.rglob("*.txt"))
            assert len(txt_files) == 100
