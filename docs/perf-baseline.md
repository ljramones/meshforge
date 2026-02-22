# Performance Baseline and Gate

This project uses a benchmark baseline gate for high-confidence performance tracking.

## Baseline Source

- File: `perf/baseline.csv`
- Format:
  - `benchmark`: full JMH benchmark id
  - `baseline_ms_per_op`: expected baseline score in `ms/op`
  - `max_regression_pct`: allowed slowdown percentage before failure

## Last Updated Benchmarks

- Date: February 22, 2026 (full outside-suite run captured)
- Environment: outside sandbox, JDK 25.0.1, forked JMH runs
- Baseline references currently tracked:
  - `OptimizeVertexCacheBenchmark.optimizeAndMeasureAcmr`: `294.325 ms/op`
  - `MeshPipelineBenchmark.realtimePipeline`: `161.892 ms/op`
  - `MeshPackerBenchmark.packRealtime`: `5.356 ms/op`
  - `MeshOpsBenchmark.weld`: `3.478 ms/op`
  - `MeshOpsBenchmark.recalculateTangents`: `1.109 ms/op`

Note: baseline values above are policy-gated references in `perf/baseline.csv`; refresh only after explicit review.

## Run the Perf Gate

Run outside sandbox on a stable machine profile:

```bash
./scripts/perf-gate.sh
```

Optional overrides:

```bash
JMH_FORKS=3 JMH_WARMUP_ITER=7 JMH_MEASURE_ITER=12 ./scripts/perf-gate.sh
```

Perf gate output:
- writes JMH CSV to `perf/results/jmh-<timestamp>.csv`
- compares each baseline row against current run
- exits non-zero on regression

## Baseline Maintenance

Update baseline only when a performance change is intentional and reviewed.

Suggested process:
1. Run `./scripts/perf-gate.sh` and inspect `perf/results/*.csv`.
2. If improvements are real and consistent, update `perf/baseline.csv`.
3. Include benchmark evidence in commit/PR notes.

## Measurement Discipline

- Keep CPU power mode fixed.
- Avoid background load during benchmark runs.
- Use the same JVM version for baseline comparisons.
- Do not run `mvn test` and `-Pbench` benchmark compilation in parallel.
