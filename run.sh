#!/usr/bin/env bash
# Compiles (if needed) and runs the MovieRater command-line application.
set -e

# Classpath separator is ':' on Linux/macOS and ';' on Windows.
CP="out:lib/*"

if [ ! -d out ] || [ -z "$(ls -A out 2>/dev/null)" ]; then
    ./compile.sh
fi

java -cp "$CP" MovieRaterApp
