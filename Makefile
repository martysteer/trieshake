# trieshake repository build targets

.PHONY: all clean test test-clj test-py trieshake trieshake-zip trieshake-clj trieshake-zip-clj trieshake-py

# Default target: build primary tools (Clojure)
all: trieshake trieshake-zip

# High-level targets (delegates to impl-specific targets)
trieshake: trieshake-clj
trieshake-zip: trieshake-zip-clj

# Clojure trieshake build
trieshake-clj:
	cd trieshake-clj && lein uberjar
	mkdir -p bin
	cp trieshake-clj/target/*-standalone.jar bin/trieshake.jar
	echo '#!/bin/bash' > bin/trieshake
	echo 'java -jar "$$(dirname "$$0")/trieshake.jar" "$$@"' >> bin/trieshake
	chmod +x bin/trieshake

# Clojure trieshake-zip build
trieshake-zip-clj:
	cd trieshake-zip-clj && lein uberjar
	mkdir -p bin
	cp trieshake-zip-clj/target/uberjar/*-standalone.jar bin/trieshake-zip.jar
	echo '#!/bin/bash' > bin/trieshake-zip
	echo 'java -jar "$$(dirname "$$0")/trieshake-zip.jar" "$$@"' >> bin/trieshake-zip
	chmod +x bin/trieshake-zip

# Python trieshake (editable install to current venv/system)
trieshake-py:
	cd trieshake-py && pip install -e .

# Test targets
test: test-clj test-py
	@echo "All tests passed!"

test-clj:
	cd trieshake-clj && lein test
	cd trieshake-zip-clj && lein test

test-py:
	cd trieshake-py && pytest

# Cleanup
clean:
	rm -rf bin/
	cd trieshake-clj && lein clean
	cd trieshake-zip-clj && lein clean
	cd trieshake-py && rm -rf build/ dist/ *.egg-info
