#!/usr/bin/env bash
# Compileaza tot proiectul in ./out  (necesita JDK 17+)
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -Xlint:all -d out $(find src -name '*.java')
echo "Compilat in ./out"
