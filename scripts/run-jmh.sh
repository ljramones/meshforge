#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$ROOT_DIR/meshforge"
CP_FILE="$MODULE_DIR/target/jmh.classpath"

FILTER="${JMH_FILTER:-.*}"
FORKS="${JMH_FORKS:-1}"
WARMUPS="${JMH_WARMUP_ITER:-3}"
MEASURE="${JMH_MEASURE_ITER:-5}"

cd "$ROOT_DIR"

mvn -q -pl meshforge -DskipTests test-compile dependency:build-classpath \
  -Dmdep.outputFile="$CP_FILE" \
  -Dmdep.includeScope=test

CP="$(cat "$CP_FILE"):$MODULE_DIR/target/test-classes:$MODULE_DIR/target/classes"

exec java --enable-preview -cp "$CP" org.openjdk.jmh.Main \
  "$FILTER" \
  -f "$FORKS" \
  -wi "$WARMUPS" \
  -i "$MEASURE"

