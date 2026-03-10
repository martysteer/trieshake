"""Integration tests for forward mode — end-to-end with temp directories."""

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
