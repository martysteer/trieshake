# trieshake-zip (Clojure)

Apply trieshake algorithm to zip archives without disk extraction.

## Build

```bash
cd trieshake-zip-clj
lein uberjar
```

Creates `target/uberjar/trieshake-zip-*-standalone.jar`.

Or from root:

```bash
make trieshake-zip-clj
```

Creates `bin/trieshake-zip` executable wrapper.

## Usage

### Sample Mode

Extract subset of entries from large zip:

```bash
./bin/trieshake-zip sample input.zip -o sample.zip --max-files 100 --max-dirs 10
```

Options:
- `--max-files N` - Maximum files to extract (default 100)
- `--max-dirs N` - Maximum parent directories to sample (default 10)

### Transform Mode

Apply trieshake algorithm to reorganize zip contents:

```bash
./bin/trieshake-zip transform input.zip -o output.zip -p 4
```

Options:
- `-p, --prefix-length N` - Characters per directory chunk (default 4)
- `--no-encode-leafname` - Use plain leafnames (no prefix encoding)
- `--report FILE` - Write collision report to file

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
lein run sample input.zip -o output.zip
lein run transform input.zip -o output.zip -p 4
```

## Implementation Notes

- Source: `src/trieshake_zip/` — sampler, transformer, algorithm modules
- Tests: `test/trieshake_zip/` — unit and integration tests
- Zero external dependencies (uses Java stdlib only)
- Streams data through memory, no disk extraction required

## See Also

- [Root README](../README.md) for usage examples and overview
- [Algorithm Specification](../docs/SPEC.md) for detailed design
- [trieshake-zip Design](../docs/trieshake-refactor-design.md) for architecture details
