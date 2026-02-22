#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASELINE_FILE="${PERF_BASELINE:-$ROOT_DIR/perf/baseline.csv}"
RESULT_DIR="${PERF_RESULT_DIR:-$ROOT_DIR/perf/results}"
STAMP="$(date +%Y%m%d-%H%M%S)"
RESULT_CSV="$RESULT_DIR/jmh-$STAMP.csv"

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "Baseline file not found: $BASELINE_FILE" >&2
  exit 1
fi

mkdir -p "$RESULT_DIR"

JMH_FILTER_DEFAULT='.*(OptimizeVertexCacheBenchmark|MeshPipelineBenchmark|MeshPackerBenchmark|MeshOpsBenchmark).*'
export JMH_FILTER="${JMH_FILTER:-$JMH_FILTER_DEFAULT}"
export JMH_FORKS="${JMH_FORKS:-2}"
export JMH_WARMUP_ITER="${JMH_WARMUP_ITER:-5}"
export JMH_MEASURE_ITER="${JMH_MEASURE_ITER:-10}"

echo "Running benchmarks for perf gate..."
echo "  filter: $JMH_FILTER"
echo "  forks: $JMH_FORKS"
echo "  warmup iterations: $JMH_WARMUP_ITER"
echo "  measurement iterations: $JMH_MEASURE_ITER"

"$ROOT_DIR/scripts/run-jmh.sh" -rf csv -rff "$RESULT_CSV" "$@"

echo
echo "Evaluating results against baseline: $BASELINE_FILE"
echo "Result file: $RESULT_CSV"
echo

failures=0

while IFS=, read -r benchmark baseline tolerance; do
  if [[ "$benchmark" == "benchmark" ]]; then
    continue
  fi
  if [[ -z "$benchmark" ]]; then
    continue
  fi

  current="$(
    awk -F, -v bench="$benchmark" '
      NR == 1 { next }
      {
        gsub(/"/, "", $1);
        gsub(/"/, "", $2);
        gsub(/"/, "", $5);
        if ($1 == bench && $2 == "avgt") {
          print $5;
          exit;
        }
      }
    ' "$RESULT_CSV"
  )"

  if [[ -z "$current" ]]; then
    echo "FAIL  $benchmark  missing in benchmark output"
    failures=$((failures + 1))
    continue
  fi

  allowed="$(awk "BEGIN { printf \"%.6f\", $baseline * (1.0 + $tolerance / 100.0) }")"
  regression_pct="$(awk "BEGIN { printf \"%.2f\", (($current - $baseline) / $baseline) * 100.0 }")"

  if awk "BEGIN { exit !($current <= $allowed) }"; then
    echo "PASS  $benchmark  current=$current ms/op baseline=$baseline allowed<=$allowed (${tolerance}% max)"
  else
    echo "FAIL  $benchmark  current=$current ms/op baseline=$baseline allowed<=$allowed regression=${regression_pct}%"
    failures=$((failures + 1))
  fi
done < "$BASELINE_FILE"

echo
if [[ "$failures" -gt 0 ]]; then
  echo "Perf gate FAILED: $failures regression(s)."
  exit 2
fi

echo "Perf gate PASSED."
