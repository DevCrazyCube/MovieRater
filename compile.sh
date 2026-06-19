#!/usr/bin/env bash
# Compiles the MovieRater Java sources into the out/ directory.
set -e
mkdir -p out
javac -cp "lib/sqlite-jdbc.jar" -d out src/*.java
echo "Compiled to out/"
