# Performance Baseline and Gate

This project uses a benchmark baseline gate for high-confidence performance tracking.

## Baseline Source

- File: `perf/baseline.csv`
- Format:
  - `benchmark`: full JMH benchmark id
  - `baseline_ms_per_op`: expected baseline score in `ms/op`
  - `max_regression_pct`: allowed slowdown percentage before failure

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
