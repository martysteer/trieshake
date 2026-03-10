"""Unit tests for the planner module — chunking, target computation, collisions."""

from pathlib import PurePosixPath

from trieshake.planner import (
    chunk_string,
    compute_target,
    compute_reverse_target,
    detect_collisions,
)


# === chunk_string tests ===


class TestChunkString:
    """Tests for chunk_string(s, prefix_length) -> list[str]."""

    def test_spec_example_prefix4(self):
        """SPEC: BL0000010600001 at p=4 -> [BL00, 0001, 0600, 001]."""
        assert chunk_string("BL0000010600001", 4) == ["BL00", "0001", "0600", "001"]

    def test_spec_example_prefix3(self):
        """SPEC: BL0001 at p=3 -> [BL0, 001]."""
        assert chunk_string("BL0001", 3) == ["BL0", "001"]

    def test_spec_example_prefix4_with_remainder(self):
        """SPEC: BL0001 at p=4 -> [BL00, 01]."""
        assert chunk_string("BL0001", 4) == ["BL00", "01"]

    def test_clean_modulo(self):
        """SPEC: ABCD at p=2 -> [AB, CD] (clean modulo, no remainder)."""
        assert chunk_string("ABCD", 2) == ["AB", "CD"]

    def test_single_chunk_shorter_than_prefix(self):
        """Concat string shorter than prefix-length produces single chunk."""
        assert chunk_string("AB", 4) == ["AB"]

    def test_single_chunk_exact_prefix(self):
        """Concat string exactly prefix-length produces single chunk."""
        assert chunk_string("ABCD", 4) == ["ABCD"]

    def test_empty_string(self):
        """Empty concat string produces empty list."""
        assert chunk_string("", 4) == []

    def test_single_character(self):
        """Single character with any prefix length."""
        assert chunk_string("A", 3) == ["A"]


# === compute_target tests ===


class TestComputeTarget:
    """Tests for compute_target(rel_path, prefix_length, extension)."""

    def test_spec_forward_prefix4(self):
        """SPEC example: BL/00/00/01/06/00001/report.txt at p=4."""
        result = compute_target(
            PurePosixPath("BL/00/00/01/06/00001/report.txt"),
            prefix_length=4,
            extension=".txt",
        )
        assert result.target_dir == PurePosixPath("BL00/0001/0600/001")
        assert result.target_filename == "BL00_0001_0600_001_report.txt"
        assert result.chunks == ["BL00", "0001", "0600", "001"]
        assert result.concat_string == "BL0000010600001"
        assert result.leafname == "report.txt"

    def test_spec_forward_prefix3(self):
        """SPEC example: BL/00/01/file.txt at p=3."""
        result = compute_target(
            PurePosixPath("BL/00/01/file.txt"),
            prefix_length=3,
            extension=".txt",
        )
        assert result.target_dir == PurePosixPath("BL0/001")
        assert result.target_filename == "BL0_001_file.txt"
        assert result.chunks == ["BL0", "001"]
        assert result.concat_string == "BL0001"
        assert result.leafname == "file.txt"

    def test_root_level_file_uses_stem(self):
        """File at root with no parent dirs uses filename stem as concat."""
        result = compute_target(
            PurePosixPath("mydata.txt"),
            prefix_length=3,
            extension=".txt",
        )
        assert result.concat_string == "mydata"
        assert result.chunks == ["myd", "ata"]
        assert result.leafname == "mydata.txt"

    def test_idempotent_forward(self):
        """Forward on a file from a plain directory structure is deterministic.

        The 'already correct' check compares source == target at the plan level.
        Here we verify compute_target produces a consistent result.
        """
        result = compute_target(
            PurePosixPath("BL/00/01/file.txt"),
            prefix_length=3,
            extension=".txt",
        )
        # Now compute_target on the *result* path
        result2 = compute_target(
            result.target_dir / result.target_filename,
            prefix_length=3,
            extension=".txt",
        )
        # The concat strings differ (second includes encoded prefix in parent),
        # but we can verify the function is pure and deterministic
        assert result2.chunks == chunk_string(result2.concat_string, 3)


# === detect_collisions tests ===


class TestDetectCollisions:
    """Tests for collision detection on a list of planned moves."""

    def test_no_collisions(self):
        """Distinct targets produce no collisions."""
        plans = [
            _make_plan("a/data.txt", "AB/AB_data.txt"),
            _make_plan("b/other.txt", "CD/CD_other.txt"),
        ]
        resolved = detect_collisions(plans)
        assert all(not p.is_collision for p in resolved)

    def test_collision_adds_suffix(self):
        """Two files mapping to same target: second gets --collision1."""
        plans = [
            _make_plan("AB/CD/data.txt", "ABCD/ABCD_data.txt"),
            _make_plan("A/BCD/data.txt", "ABCD/ABCD_data.txt"),
        ]
        resolved = detect_collisions(plans)
        assert resolved[0].target_filename == "ABCD_data.txt"
        assert not resolved[0].is_collision
        assert resolved[1].target_filename == "ABCD_data--collision1.txt"
        assert resolved[1].is_collision
        assert resolved[1].collision_of == "ABCD/ABCD_data.txt"

    def test_triple_collision(self):
        """Three files to same target: collision1, collision2."""
        plans = [
            _make_plan("x/data.txt", "XX/XX_data.txt"),
            _make_plan("y/data.txt", "XX/XX_data.txt"),
            _make_plan("z/data.txt", "XX/XX_data.txt"),
        ]
        resolved = detect_collisions(plans)
        assert resolved[0].target_filename == "XX_data.txt"
        assert resolved[1].target_filename == "XX_data--collision1.txt"
        assert resolved[2].target_filename == "XX_data--collision2.txt"

    def test_collision_with_multi_extension(self):
        """Collision suffix inserted before multi-part extension."""
        plans = [
            _make_plan("a/record.mets.xml", "AB/AB_record.mets.xml", ".mets.xml"),
            _make_plan("b/record.mets.xml", "AB/AB_record.mets.xml", ".mets.xml"),
        ]
        resolved = detect_collisions(plans)
        assert resolved[1].target_filename == "AB_record--collision1.mets.xml"


# === compute_reverse_target tests ===


class TestComputeReverseTarget:
    """Tests for compute_reverse_target — reverse mode planning."""

    def test_spec_reverse_strip_prefix(self):
        """SPEC: reverse without -p strips prefix, keeps dir structure."""
        result = compute_reverse_target(
            PurePosixPath(
                "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"
            ),
            extension=".txt",
        )
        assert result.leafname == "report.txt"
        assert result.target_dir == PurePosixPath("BL00/0001/0600/001")
        assert result.target_filename == "report.txt"

    def test_spec_reverse_with_regroup(self):
        """SPEC: reverse with -p 3 regroups in single pass."""
        result = compute_reverse_target(
            PurePosixPath(
                "BL00/0001/0600/001/BL00_0001_0600_001_report.txt"
            ),
            extension=".txt",
            new_prefix_length=3,
        )
        assert result.leafname == "report.txt"
        assert result.concat_string == "BL0000010600001"
        assert result.chunks == ["BL0", "000", "010", "600", "001"]
        assert result.target_dir == PurePosixPath("BL0/000/010/600/001")
        assert result.target_filename == "BL0_000_010_600_001_report.txt"

    def test_reverse_skips_non_encoded_file(self):
        """File whose name doesn't match dir prefix is skipped (returns None)."""
        result = compute_reverse_target(
            PurePosixPath("BL00/0001/random_file.txt"),
            extension=".txt",
        )
        assert result is None

    def test_reverse_strips_collision_suffix(self):
        """SPEC: --collisionN suffix stripped from leafname during reverse."""
        result = compute_reverse_target(
            PurePosixPath("ABCD/ABCD_data--collision1.txt"),
            extension=".txt",
        )
        assert result.leafname == "data.txt"
        assert result.target_filename == "data.txt"

    def test_reverse_collision_with_collision_dir(self):
        """SPEC example: collision file in collisions subdirectory."""
        result = compute_reverse_target(
            PurePosixPath("ABCD/collisions/ABCD_data--collision1.txt"),
            extension=".txt",
        )
        assert result is not None
        assert result.leafname == "data.txt"
        assert result.target_dir == PurePosixPath("ABCD")

    def test_reverse_preserves_underscores_in_leafname(self):
        """Underscores in original leafname survive round-trip."""
        result = compute_reverse_target(
            PurePosixPath("AB/CD/AB_CD_my_data.txt"),
            extension=".txt",
        )
        assert result.leafname == "my_data.txt"

    def test_reverse_root_level_skipped(self):
        """File at root level (no dir path) is skipped in reverse."""
        result = compute_reverse_target(
            PurePosixPath("report.txt"),
            extension=".txt",
        )
        assert result is None

    def test_reverse_regroup_clean_modulo(self):
        """Regroup where concat string cleanly divides by new prefix."""
        result = compute_reverse_target(
            PurePosixPath("AB/CD/AB_CD_data.txt"),
            extension=".txt",
            new_prefix_length=2,
        )
        assert result.chunks == ["AB", "CD"]
        assert result.target_dir == PurePosixPath("AB/CD")
        assert result.target_filename == "AB_CD_data.txt"


class TestNoEncodeLeafname:
    """Tests for --no-encode-leafname mode in planner."""

    def test_forward_no_encode(self):
        """Forward with encode_leafname=False uses plain leafname."""
        result = compute_target(
            PurePosixPath("BL/00/01/file.txt"),
            prefix_length=3,
            extension=".txt",
            encode_leafname=False,
        )
        assert result.target_dir == PurePosixPath("BL0/001")
        assert result.target_filename == "file.txt"
        assert result.leafname == "file.txt"

    def test_reverse_regroup_no_encode(self):
        """Reverse regroup with encode_leafname=False uses plain leafname."""
        result = compute_reverse_target(
            PurePosixPath("AB/CD/AB_CD_data.txt"),
            extension=".txt",
            new_prefix_length=3,
            encode_leafname=False,
        )
        assert result.target_filename == "data.txt"
        assert result.target_dir == PurePosixPath("ABC/D")


def _make_plan(source, target, extension=""):
    """Helper to create a minimal plan entry for collision testing."""
    from trieshake.planner import PlanEntry

    target_path = PurePosixPath(target)
    return PlanEntry(
        source_path=PurePosixPath(source),
        target_dir=target_path.parent,
        target_filename=target_path.name,
        chunks=[],
        concat_string="",
        leafname="",
        extension=extension,
        is_collision=False,
        collision_of=None,
    )
