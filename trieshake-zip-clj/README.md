# trieshake-zip (Clojure)

Apply trieshake algorithm to zip archives without disk extraction.

## Usage

### Build

```bash
lein uberjar
```

### Sample Mode

Extract subset of entries from large zip:

```bash
# With lein
lein run sample input.zip -o sample.zip --max-files 100 --max-dirs 10

# With jar
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  sample input.zip -o sample.zip --max-files 100 --max-dirs 10
```

Options:
- `--max-files N` - Maximum files to extract (default 100)
- `--max-dirs N` - Maximum parent directories to sample (default 10)

### Transform Mode

Apply trieshake algorithm to reorganize zip contents:

```bash
# With lein
lein run transform input.zip -o output.zip -p 4

# With jar
java -jar target/uberjar/trieshake-zip-0.1.0-standalone.jar \
  transform input.zip -o output.zip -p 4
```

Options:
- `-p, --prefix-length N` - Characters per directory chunk (default 4)
- `--no-encode-leafname` - Use plain leafnames (no prefix encoding)
- `--report FILE` - Write collision report to file

## Examples

```bash
# Create sample from large archive
lein run sample METADATA.zip -o sample.zip

# Transform with default settings
lein run transform sample.zip -o transformed.zip

# Transform with prefix-length 3
lein run transform sample.zip -o out.zip -p 3

# Transform with plain leafnames
lein run transform sample.zip -o out.zip --no-encode-leafname

# Transform and save collision report
lein run transform sample.zip -o out.zip --report collisions.txt
```

## Testing

```bash
lein test
```

## Algorithm

See [trieshake SPEC.md](../../SPEC.md) for algorithm details.

## License

CC-BY-SA 4.0
