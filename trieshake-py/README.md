# trieshake (Python)

Python implementation of trieshake with zero external dependencies.

## Build

```bash
cd trieshake-py
pip install -e .
```

Or from root:

```bash
make trieshake-py
```

Installs trieshake command to active virtualenv/system.

## Development

### Tests

```bash
pytest
```

### Run

```bash
trieshake /data -e .txt -p 4
```

Or during development:

```bash
python -m trieshake /data -e .txt -p 4
```

## Implementation Notes

- Source: `trieshake/` — modular structure with scanner, planner, executor, reporter, cleaner
- Tests: `tests/` — unit and integration tests
- Zero external dependencies (uses Python stdlib only)
- Requires Python 3.9+

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [BUILDPLAN](../docs/BUILDPLAN-python.md) for historical implementation context
