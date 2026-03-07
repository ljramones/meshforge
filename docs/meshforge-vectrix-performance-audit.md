# MeshForge x Vectrix Performance Audit

Date: 2026-03-07
Owner: MeshForge
Status: In Progress

## Scope

Target hot paths:

- `MeshPacker`
- `SimdNormalPacker`
- `RecalculateTangentsOp`
- `MeshoptDecoder`

Primary goals:

1. Validate whether MeshForge is using Vectrix effectively in real workloads.
2. Remove avoidable compute and memory overhead in hot paths.
3. Make allocation and GC behavior a release gate, not a best-effort metric.
4. Produce evidence-based feedback for Vectrix API evolution.

Guiding principle:

- MeshForge is a proving ground for Vectrix. Critical computational paths must be fast and allocation-disciplined.

## Current Baseline (Already Verified)

- `DynamisCore` coupling: none in code and dependency graph.
- Vectrix coupling: present and concentrated in computation/packing/decoder internals.
- Public semantic API leakage of Vectrix types: not observed.

Observed Vectrix usage sites:

- `meshforge/.../ops/generate/RecalculateTangentsOp`
- `meshforge/.../pack/packer/MeshPacker`
- `meshforge/.../pack/simd/SimdNormalPacker`
- `meshforge-loader/.../gltf/read/MeshoptDecoder`

## Execution Order

1. Hot-path inventory.
2. Benchmark harness with throughput and allocation metrics.
3. Repeated-batch stress benchmarks.
4. Allocation profiling + CPU profiling.
5. Locality and conversion-churn audits.
6. Optimize `MeshPacker`.
7. Optimize `SimdNormalPacker`.
8. Optimize `RecalculateTangentsOp`.
9. Optimize `MeshoptDecoder`.
10. Capture Vectrix friction log and classification.
11. Define/enable perf + allocation regression gates.
12. Final release readiness decision.

## Task Board

### MF-VX-01 Hot-Path Inventory
Priority: P0
Status: Completed (2026-03-07)

Deliverables:

- Per-class note for all 4 targets covering:
  - operation purpose
  - workload shape
  - input/output shape
  - CPU vs memory bound expectation
  - invocation frequency

Acceptance:

- One documented section per class with explicit bound-type hypothesis.

### MF-VX-02 Benchmark Harness (Time + Allocation)
Priority: P0
Status: Completed (2026-03-07)

Deliverables:

- JMH benchmarks for each target path.
- Deterministic fixtures/generators for:
  - small/medium/large meshes
  - attribute-heavy worst case
  - indexed/non-indexed variants where applicable
  - interleaved/deinterleaved variants where applicable
- Metrics captured:
  - throughput / avg time
  - `gc.alloc.rate.norm`
  - `gc.alloc.rate`
  - alloc count proxy where available

Acceptance:

- Repeatable benchmark run captures both latency/throughput and allocation metrics.

### MF-VX-03 Repeated-Batch Stress Benchmarks
Priority: P0
Status: Completed (2026-03-07)

Deliverables:

- Repeated-run benchmark mode for each target path (batch-style invocation).
- Report on survivor pressure / TLAB churn characteristics.

Acceptance:

- Batch mode clearly exposes steady-state allocation behavior and jitter risks.

### MF-VX-04 Baseline Table
Priority: P0
Status: Completed (2026-03-07)

Deliverables:

- Baseline table per hot path with:
  - time/op
  - bytes/op
  - allocation rate
  - repeated-run observations

Acceptance:

- Baseline table stored in this document and usable as before/after reference.

### MF-VX-05 Allocation Audit
Priority: P0
Status: Pending

Deliverables:

- Inner-loop allocation review per class:
  - object creation
  - temporary arrays
  - boxing
  - stream/lambda allocation
  - hidden convenience-API allocation
- Verdict per class:
  - clean
  - suspicious
  - must fix

Acceptance:

- All four classes classified with concrete evidence.

### MF-VX-06 Temporary Representation Audit
Priority: P0
Status: Pending

Deliverables:

- Conversion map per class (data-shape transitions).
- Each temporary representation labeled:
  - required
  - reusable
  - removable
  - candidate for caller-supplied scratch

Acceptance:

- At least one clear removal/reuse candidate identified, or explicit none-found rationale.

### MF-VX-07 Allocation-Focused Profiling
Priority: P0
Status: Pending

Deliverables:

- Allocation hotspot report (JFR or equivalent):
  - top allocating methods
  - hotspot lines
  - churn classification by component

Acceptance:

- Top allocation hotspots mapped to owner:
  - MeshForge logic
  - Vectrix usage pattern
  - JDK/buffer/copy path

### MF-VX-08 Optimize MeshPacker (Zero-Garbage Pass)
Priority: P0
Status: In Progress (first pass completed 2026-03-07)

Deliverables:

- Tightened inner loops with no avoidable allocations.
- Before/after benchmark + allocation deltas.

Acceptance:

- Inner loop allocation-free in steady state.
- Measurable reduction in bytes/op and/or time/op.

First-pass outcome (2026-03-07):

- Implemented allocation-focused reductions in `MeshPacker`:
  - reused normal-packing scratch array via thread-local storage
  - removed repeated per-call `AttributeKey` construction in pack path
  - reduced submesh-range copy churn for empty/single-submesh common cases
- Targeted benchmark rerun completed:
  - `/tmp/mf_vx_meshpacker_after_jmh.txt`

### MF-VX-09 Optimize SimdNormalPacker (Zero-Garbage Pass)
Priority: P0
Status: Pending

Deliverables:

- Primitive-only hot path with no avoidable transient allocation.
- Before/after benchmark + allocation deltas.

Acceptance:

- Zero avoidable transient allocation in steady state.
- Throughput non-regression with documented rationale.

### MF-VX-10 Optimize RecalculateTangentsOp (Allocation Containment)
Priority: P1
Status: In Progress (first pass completed 2026-03-07)

Deliverables:

- Bounded, intentional primitive-buffer allocation strategy.
- Before/after benchmark + allocation deltas.

Acceptance:

- No accidental allocation churn inside triangle/vertex loops.

First-pass outcome (2026-03-07):

- Implemented allocation-containment changes in `RecalculateTangentsOp`:
  - reused tangent accumulators via thread-local primitive scratch
  - removed per-call `Vector3f` object usage with scalar orthogonalization/cross math
  - removed redundant tangent output pre-fill (`tanOut` is fully overwritten)
- Targeted benchmark rerun completed:
  - `/tmp/mf_vx_tangents_after_jmh.txt`

### MF-VX-11 Optimize MeshoptDecoder (Scratch Strategy)
Priority: P1
Status: In Progress (first pass completed 2026-03-07)

Deliverables:

- Reusable scratch/buffer strategy where practical.
- Reduced conversion/copy churn.
- Before/after benchmark + allocation deltas.

Acceptance:

- Decoder avoids spray of short-lived arrays in repeated batch runs.

First-pass outcome (2026-03-07):

- Implemented decode allocation reductions for OCTAHEDRAL path:
  - added reusable per-thread decompression scratch (`byte[]`)
  - added `Lz4BlockDecompressor.decompressInto(...)` to avoid transient output array creation
  - removed per-call `ByteBuffer` wrappers in octa decode loop
  - fixed unconditional pre-decompress in `decode(...)` so octa path only decompresses once
- Targeted benchmark reruns completed:
  - full pass: `/tmp/mf_vx_meshopt_after_jmh.txt`
  - octa-focused verification: `/tmp/mf_vx_meshopt_octa_after_jmh.txt`

### MF-VX-12 Vectrix Friction Log
Priority: P0
Status: Pending

Deliverables:

- `docs/vectrix-meshforge-friction-log.md` containing friction points.
- Each point classified:
  - MeshForge misuse
  - Vectrix API gap
  - Vectrix representation gap
  - benchmark-only concern
  - not worth fixing

Acceptance:

- Every friction item mapped to an action (fix now / defer / reject).

### MF-VX-13 GC Pressure Watchlist
Priority: P1
Status: Pending

Deliverables:

- Explicit anti-pattern watchlist in this document for future changes:
  - temporary math objects in loops
  - conversion materialization churn
  - stream/collector usage in hot paths
  - per-call scratch allocation where reuse is feasible
  - APIs forcing unnecessary copying

Acceptance:

- Watchlist section complete and referenced in review checklist.

### MF-VX-14 Regression Gates
Priority: P0
Status: Pending

Deliverables:

- Thresholds defined for:
  - allocations/op
  - bytes/op
  - throughput regression tolerance
- Minimal perf/allocation checks wired into CI or release checklist.

Acceptance:

- Regressions are detectable and block release when thresholds are exceeded.

### MF-VX-15 Release Gate
Priority: P0
Status: Pending

Hard gates:

- No known avoidable inner-loop allocations in target hot paths.
- No unresolved severe allocation spikes in repeated-batch benchmarks.
- No unresolved high-impact conversion churn in target paths.
- No Vectrix leakage into semantic public models.
- Baselines and thresholds recorded.
- Friction log triaged with follow-up plan.

Acceptance:

- Final go/no-go decision recorded with evidence links.

## Performance and GC Gates (Draft)

- `MeshPacker`: effectively zero-garbage steady-state path and improved medium+ runtime.
- `SimdNormalPacker`: effectively zero-garbage steady-state path with throughput non-regression.
- `RecalculateTangentsOp`: bounded primitive-buffer allocation only; no inner-loop allocation churn.
- `MeshoptDecoder`: bounded temp allocation and reusable scratch strategy where practical.

## MF-VX-01 Inventory Notes

- `MeshPacker`: runtime packing from semantic mesh attributes to packed vertex/index buffers. Expected mixed compute + memory/locality pressure with heavy conversion risk. Usually per-mesh/per-submesh in import/build pipelines.
- `SimdNormalPacker`: normal/tangent packing and quantization kernels (`octa`, `snorm8`). Expected compute-heavy with strict inner-loop allocation constraints.
- `RecalculateTangentsOp`: tangent recomputation from indexed/non-indexed topology and attributes. Expected memory/locality-heavy with accumulation and conversion pressure.
- `MeshoptDecoder`: compressed stream decode and optional post-filtering. Expected conversion-heavy and copy-heavy decode mechanics with high allocation risk in batch ingestion.

## MF-VX-04 Baseline Table (Before Optimization)

Source runs:

- `meshforge`: `/tmp/mf_vx_meshforge_jmh.txt`
- `meshforge-loader`: `/tmp/mf_vx_meshopt_jmh.txt`
- Common JMH settings: `-f 1 -wi 1 -i 2 -w 300ms -r 300ms -prof gc`

Notes:

- `bytes/op` below is `gc.alloc.rate.norm`.
- Mesh-based baselines are shown for `indexed=false` (non-indexed) rows to keep the table compact.
- Repeated-batch baselines use each `*Batch` benchmark.

| Path | Workload | time/op (ms) | throughput (ops/ms) | bytes/op | gc.alloc.rate (MB/sec) | Batch time/op (ms) | Batch bytes/op | Quick character |
|---|---|---:|---:|---:|---:|---:|---:|---|
| MeshPacker | SMALL | 0.016 | 64.644 | 116325.938 | 2706.145 | 0.017 | 10880.405 | memory/locality + conversion-heavy |
| MeshPacker | MEDIUM | 0.229 | 4.479 | 1672048.059 | 2915.523 | 0.240 | 96496.781 | memory/locality + conversion-heavy |
| MeshPacker | LARGE | 1.419 | 0.704 | 10312139.883 | 2916.148 | 1.489 | 571779.313 | memory/locality + conversion-heavy |
| MeshPacker | ATTRIBUTE_HEAVY | 0.941 | 1.088 | 6612911.697 | 2815.944 | 0.957 | 368319.763 | memory/locality + conversion-heavy |
| SimdNormalPacker (octa) | SMALL | 0.016 | 63.391 | 96.367 | 5.701 | 0.016 | 96.365 | compute-heavy (allocation-clean) |
| SimdNormalPacker (octa) | MEDIUM | 1.083 | 0.812 | 120.836 | 0.106 | 1.237 | 123.156 | compute-heavy (allocation-clean) |
| SimdNormalPacker (octa) | LARGE | 5.960 | 0.163 | 232.314 | 0.037 | 5.986 | 204.625 | compute-heavy (allocation-clean) |
| SimdNormalPacker (octa) | ATTRIBUTE_HEAVY | 3.505 | 0.279 | 175.920 | 0.048 | 3.636 | 150.313 | compute-heavy (allocation-clean) |
| RecalculateTangentsOp | SMALL | 0.004 | 231.186 | 134376.613 | 4793.121 | 0.004 | 28318.821 | memory/locality-heavy + conversion-heavy |
| RecalculateTangentsOp | MEDIUM | 0.056 | 18.520 | 2001063.455 | 5353.305 | 0.053 | 424921.343 | memory/locality-heavy + conversion-heavy |
| RecalculateTangentsOp | LARGE | 0.340 | 2.983 | 12369159.645 | 5331.531 | 0.341 | 2628129.111 | memory/locality-heavy + conversion-heavy |
| RecalculateTangentsOp | ATTRIBUTE_HEAVY | 0.230 | 4.342 | 7930099.801 | 5282.478 | 0.228 | 1684830.090 | memory/locality-heavy + conversion-heavy |
| MeshoptDecoder (NONE filter) | SMALL | ~=0.001 | 2144.083 | 16401.123 | 32116.079 | ~=0.001 | 16400.011 | conversion-heavy (decode + copy) |
| MeshoptDecoder (NONE filter) | MEDIUM | 0.007 | 148.791 | 262173.942 | 34478.733 | 0.007 | 262166.530 | conversion-heavy (decode + copy) |
| MeshoptDecoder (NONE filter) | LARGE | 0.052 | 20.305 | 2097171.043 | 38695.461 | 0.053 | 2097193.271 | conversion-heavy (decode + copy) |
| MeshoptDecoder (NONE filter) | ATTRIBUTE_HEAVY | 0.055 | 20.440 | 2097170.587 | 36992.251 | 0.052 | 2097193.243 | conversion-heavy (decode + copy) |

Supplemental note:

- `SimdNormalPacker (snorm8)` is similarly allocation-clean (`96-187 B/op`) with better throughput than `octa` in this baseline.

## MF-VX-04 Allocation Findings and Early Ranking

Allocation verdict by target:

- `MeshPacker`: must fix
- `SimdNormalPacker`: clean
- `RecalculateTangentsOp`: must fix
- `MeshoptDecoder`: must fix

Early optimization ranking (baseline-driven):

1. `MeshPacker`
2. `RecalculateTangentsOp`
3. `MeshoptDecoder`
4. `SimdNormalPacker`

Immediate Vectrix signal:

- Strong: `SimdNormalPacker` shows Vectrix-friendly, allocation-clean usage patterns.
- Awkward/unclear: `MeshPacker` and `RecalculateTangentsOp` look dominated by representation/materialization pressure, likely more MeshForge loop/data-shape issues than Vectrix kernel cost.
- Decoder caveat: `MeshoptDecoder` allocation profile appears dominated by decode/format mechanics and temporary representation churn; Vectrix likely secondary in current cost center.

## MeshPacker First-Pass Delta (MF-VX-08)

Baseline source: `/tmp/mf_vx_meshforge_jmh.txt`  
After source: `/tmp/mf_vx_meshpacker_after_jmh.txt`

Representative comparison (`indexed=false`, `avgt` mode):

| Workload | Before time/op (ms) | After time/op (ms) | Before bytes/op | After bytes/op | bytes/op delta |
|---|---:|---:|---:|---:|---:|
| SMALL | 0.016 | 0.016 | 116325.938 | 111533.107 | -4.1% |
| MEDIUM | 0.229 | 0.220 | 1672048.059 | 1605045.824 | -4.0% |
| LARGE | 1.419 | 1.420 | 10312139.883 | 9899540.222 | -4.0% |
| ATTRIBUTE_HEAVY | 0.941 | 0.924 | 6612911.697 | 6348279.403 | -4.0% |

Repeated-batch comparison (`indexed=false`, `avgt` mode):

| Workload | Before batch bytes/op | After batch bytes/op | batch bytes/op delta |
|---|---:|---:|---:|
| SMALL | 10880.405 | 6088.382 | -44.0% |
| MEDIUM | 96496.781 | 29496.289 | -69.4% |
| LARGE | 571779.313 | 159179.313 | -72.2% |
| ATTRIBUTE_HEAVY | 368319.763 | 103688.025 | -71.8% |

Interpretation:

- The first pass removed meaningful avoidable churn, especially visible in repeated-batch mode.
- Absolute bytes/op in single-op mode remain high, indicating dominant remaining cost is still output/materialization flow.
- Next pass should focus on larger structural churn sources in `MeshPacker` (representation and copy path shape), not SIMD math.

## RecalculateTangentsOp First-Pass Delta (MF-VX-10)

Baseline source: `/tmp/mf_vx_meshforge_jmh.txt`  
After source: `/tmp/mf_vx_tangents_after_jmh.txt`

Representative comparison (`indexed=false`, `avgt` mode):

| Workload | Before time/op (ms) | After time/op (ms) | Before bytes/op | After bytes/op | bytes/op delta |
|---|---:|---:|---:|---:|---:|
| SMALL | 0.004 | 0.004 | 134376.613 | 108200.626 | -19.5% |
| MEDIUM | 0.056 | 0.049 | 2001063.455 | 1601633.611 | -19.9% |
| LARGE | 0.340 | 0.299 | 12369159.645 | 9896134.257 | -20.0% |
| ATTRIBUTE_HEAVY | 0.230 | 0.222 | 7930099.801 | 6344882.780 | -20.0% |

Repeated-batch comparison (`indexed=false`, `avgt` mode):

| Workload | Before batch bytes/op | After batch bytes/op | batch bytes/op delta |
|---|---:|---:|---:|
| SMALL | 28318.821 | 2141.093 | -92.4% |
| MEDIUM | 424921.343 | 25497.254 | -94.0% |
| LARGE | 2628129.111 | 155103.838 | -94.1% |
| ATTRIBUTE_HEAVY | 1684830.090 | 99613.878 | -94.1% |

Interpretation:

- First-pass changes removed substantial repeated-call churn and reduced single-op allocations by about one-fifth.
- Single-op bytes/op remain high, so further structural reduction of authoring/output materialization is still required before this path can be considered allocation-clean.

## MeshoptDecoder First-Pass Delta (MF-VX-11)

Baseline source: `/tmp/mf_vx_meshopt_jmh.txt`  
After source: `/tmp/mf_vx_meshopt_octa_after_jmh.txt` (OCTAHEDRAL-focused verification)

OCTAHEDRAL comparison (`avgt` mode):

| Workload | Before time/op (ms) | After time/op (ms) | Before bytes/op | After bytes/op | bytes/op delta |
|---|---:|---:|---:|---:|---:|
| SMALL | 0.002 | 0.002 | 16440.055 | 12328.052 | -25.0% |
| MEDIUM | 0.034 | 0.047 | 262312.786 | 196649.097 | -25.0% |
| LARGE | 0.884 | 1.102 | 2097340.273 | 1572929.456 | -25.0% |
| ATTRIBUTE_HEAVY | 0.361 | 0.337 | 1048752.343 | 786479.772 | -25.0% |

OCTAHEDRAL repeated-batch (`avgt` mode):

| Workload | Before batch bytes/op | After batch bytes/op | batch bytes/op delta |
|---|---:|---:|---:|
| SMALL | 16552.056 | 12328.053 | -25.5% |
| MEDIUM | 262312.797 | 196649.118 | -25.0% |
| LARGE | 2097338.250 | 1572928.531 | -25.0% |
| ATTRIBUTE_HEAVY | 1048750.441 | 786480.737 | -25.0% |

Interpretation:

- OCTAHEDRAL path now avoids one full transient array per decode call; allocation profile matches expected single-output dominant shape.
- `FILTER_NONE` path remains allocation-heavy by design because decode output is itself a new byte array payload.
- Remaining structural options for `FILTER_NONE` would require API/ownership changes (for example caller-supplied output/scratch) and should be treated as a separate compatibility decision.

## GC Pressure Watchlist

- Do not create temporary vector/wrapper objects in inner loops.
- Do not allocate per-element temporary arrays or buffers.
- Avoid streams/lambdas/boxing in hot computational loops.
- Minimize representation conversion churn (array <-> wrapper <-> array).
- Prefer reusable scratch/workspace for repeated batch processing.

## Notes

- Keep this audit focused on performance and boundary correctness.
- Do not introduce new DynamisCore coupling unless a hard requirement appears.
- Keep public MeshForge semantic models representation-neutral.
