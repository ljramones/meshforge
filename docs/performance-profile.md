# MeshForge Performance Profile

Last updated: 2026-02-23

This document describes how performance is measured and what is currently prioritized.

## Priority

1. Correctness and deterministic output
2. Fail-fast validation boundaries
3. Throughput on large real fixtures
4. Allocation control in hot paths

## Measurement Rules

- Use forked JMH runs for publishable numbers.
- Run outside sandbox for stable control socket/profiler behavior.
- Keep benchmark setup at `Level.Iteration` for op benchmarks to avoid charging per-invocation fixture copy to the op under test.

Run:

```bash
./scripts/run-jmh.sh
```

Filtered run:

```bash
JMH_FILTER='.*MeshOpsBenchmark.*' ./scripts/run-jmh.sh
```

Perf gate:

```bash
JMH_JAVA_OPTS='--add-modules jdk.incubator.vector' ./scripts/perf-gate.sh
```

## Current Baseline Source

Authoritative gate baseline:
- `perf/baseline.csv`

Methodology and latest tracked values:
- `docs/perf-baseline.md`

Latest full gate CSV snapshot:
- `perf/results/jmh-20260222-191649.csv`

## Notes on Weld

- Weld is boundary-safe and uses neighbor-cell checks with union-find.
- This is intentionally slower than old single-bucket weld logic that missed boundary cases.
- Current measured weld baseline is tracked in `perf/baseline.csv`.

## Notes on Packing

- Interleaved pack path is optimized and benchmarked in `MeshPackerBenchmark`.
- Vector API module is required for SIMD-enabled pack benchmarks:
  - `--add-modules jdk.incubator.vector`
- Multi-stream packing is declared in API but not implemented in `MeshPacker`.
