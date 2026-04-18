# trieshake (Clojure)

Clojure implementation of trieshake with zero external dependencies (Clojure core only).

## Build

```bash
cd trieshake-clj
lein uberjar
```

Creates `target/trieshake-*-standalone.jar`.

Or from root:

```bash
make trieshake-clj
```

Creates `bin/trieshake` executable wrapper.

## Development

### Tests

```bash
lein test
```

### REPL

```bash
lein repl
```

### Run

```bash
lein run /data -e .txt -p 4
```

## Implementation Notes

- Source: `src/trieshake/` — modular structure with scanner, planner, executor, reporter, cleaner
- Tests: `test/trieshake/` — unit and integration tests
- Zero external dependencies (uses Clojure stdlib only)

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [BUILDPLAN](../docs/BUILDPLAN-clojure.md) for historical implementation context
