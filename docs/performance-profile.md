# MeshForge v1 Performance Profile

This document defines the implementation priority and realtime packing defaults for MeshForge v1.

## Implementation Priority

1. Canonical authoring model
- `MeshData` with schema + SoA-by-attribute arrays
- `MeshBuilder` + `MeshWriter`
- indexed triangle lists + `Submesh`

2. Mandatory correctness and cleanup ops
- `RemoveDegeneratesOp`
- `ValidateIndicesOp`
- `ComputeBoundsOp` (AABB + sphere)

3. Weld + compaction
- `WeldVerticesOp(epsilon)`
- `CompactVerticesOp`

4. Normals + tangents (rendering-ready)
- `RecalculateNormalsOp(angleThresholdDeg)`
- `RecalculateTangentsOp()` (v1 consistent basis; MikkTSpace-style can follow)

5. Vertex cache optimization
- `OptimizeVertexCacheOp` (Forsyth-style)
- optional v1.1: `OptimizeOverdrawOp`

6. Packing v1
- interleaved AoS `PackedMesh`
- `u16` vs `u32` index selection
- 16-byte stride alignment/padding

7. Compression v1
- normal/tangent -> `SNORM8x4` (or `SNORM16x4`)
- uv -> `F16x2`
- color -> `UNORM8x4`
- weights -> `UNORM8x4`
- joints -> `U8x4` or `U16x4`

8. Nice-to-have after v1
- multi-stream packing
- octahedral normals
- meshlet/cluster generation
- LOD generation

## `PackSpec.realtime()` Default

### Layout
- `INTERLEAVED` single stream by default
- `alignmentBytes = 16`
- optional stricter stride modes later (32/48/64)

### Indices
- prefer 16-bit indices
- do not force 32-bit by default
- fallback to 32-bit when any submesh exceeds 65535 unique vertices

### Attribute policy
Assuming float32 authoring attributes:
- `POSITION` -> `F32x3` (keep uncompressed in v1)
- `NORMAL` -> `SNORM8x4`
- `TANGENT` -> `SNORM8x4` (`w` = handedness)
- `UV0` -> `F16x2`
- `COLOR0` -> `UNORM8x4` (if present)
- `JOINTS0` -> `U8x4` or `U16x4` by policy
- `WEIGHTS0` -> `UNORM8x4` (renormalize on write)

### Derived and removed attributes
- do not store `BITANGENT`; reconstruct in shader from normal + tangent + sign
- drop unknown attributes by default unless `preserveUnknown` is enabled

### Pre-pack safety hooks
`PackSpec.realtime()` assumes ops already ran, but should remain safe:
- compute bounds when missing (`computeBoundsIfMissing = true`)
- normals required for lit material paths (default fail fast if missing)
- tangents required for normal-mapped paths (default fail fast if missing)

## Suggested `PackSpec` Shape

- `LayoutMode { INTERLEAVED, MULTI_STREAM }`
- `int alignmentBytes`
- `IndexPolicy { AUTO_16_IF_POSSIBLE, FORCE_32 }`
- `Map<AttributeKey, VertexFormat> targetFormats`
- `boolean dropUnknownAttributes`
- `boolean preserveUnknownAttributes`
- `boolean requireNormals`
- `boolean requireTangents`
- `boolean computeBoundsIfMissing`

## Realtime Preset Outcome

`PackSpec.realtime()` should produce a `PackedMesh` that is:
- bandwidth efficient
- cache friendly
- correct for PBR shading
- directly uploadable to rendering backends

## Non-Priorities (v1)

- half-edge topology
- advanced memory pooling
- default multithreaded packing
- strip-focused primitive workflows

## Microbenchmarks (JMH)

Run meshforge microbenchmarks:

```bash
mvn -pl meshforge -Pbench test-compile exec:java
```

Run a subset (example: packer benchmarks only):

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshPackerBenchmark.*'
```

Current benchmark classes:
- `org.meshforge.bench.OptimizeVertexCacheBenchmark`
- `org.meshforge.bench.MeshPackerBenchmark`
- `org.meshforge.bench.MeshPipelineBenchmark`
- `org.meshforge.bench.MeshOpsBenchmark`
- `org.meshforge.bench.MeshSizeScalingBenchmark`

Demo fixture profilers:
- `org.meshforge.demo.BaselineFixtureTiming` (load/create medians + normalized throughput)
- `org.meshforge.demo.PhaseSplitFixtureTiming` (parse/pipeline/pack/total median+p95)
- `org.meshforge.demo.PackBreakdownFixtureTiming` (pack sub-phase median+p95)

Per-op hotspot pass:

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshOpsBenchmark.*'
```

Size scaling matrix (`cells` = 64, 128, 256):

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshSizeScalingBenchmark.*'
```

## Optimize Cache Timeline

`OptimizeVertexCacheBenchmark.optimizeAndMeasureAcmr` trend on the same fixture:

- initial baseline: ~850 ms/op
- incremental cache-position update: ~400 ms/op
- heap candidate selection + score lookup tables + push gating: ~294-302 ms/op

Use this as a directional performance guardrail; exact values vary by machine/JVM.

## Fixture Pack Breakdown

Run outside sandbox from repo root:

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast --pack-minimal" exec:java
```

Shortcut via phase-split runner:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --profile-pack" exec:java
```

Outputs CSV to `perf/results/pack-breakdown-*.csv`.
Use `--pack-minimal` to switch to `Packers.realtimeMinimal()` for quick attribute-cost deltas.

## Latest Phase-Split Snapshot (Fast)

Source: `perf/results/phase-split-fast-20260221-215917.csv` (local machine run, Feb 22, 2026).

| Fixture | Parse (median / p95) | Pipeline (median / p95) | Pack (median / p95) | Total (median / p95) |
|---|---:|---:|---:|---:|
| `beast.obj` | 3.366 ms / 9.885 ms | 550 us / 642 us | 464 us / 895 us | 4.411 ms / 10.910 ms |
| `cow.obj` | 300 us / 337 us | 56 us / 66 us | 73 us / 110 us | 430 us / 504 us |
| `lucy.obj` | 6.074 ms / 6.117 ms | 752 us / 763 us | 595 us / 663 us | 7.447 ms / 7.479 ms |
| `nefertiti.obj` | 5.368 ms / 5.638 ms | 750 us / 827 us | 357 us / 449 us | 6.499 ms / 6.806 ms |
| `RevitHouse.obj` | 73.431 ms / 76.959 ms | 7.732 ms / 7.952 ms | 5.199 ms / 5.854 ms | 87.089 ms / 89.072 ms |
| `stanford-bunny.obj` | 3.219 ms / 3.279 ms | 569 us / 594 us | 184 us / 207 us | 3.976 ms / 4.034 ms |
| `suzanne.obj` | 70 us / 82 us | 9 us / 25 us | 8 us / 17 us | 85 us / 125 us |
| `teapot.obj` | 292 us / 305 us | 53 us / 61 us | 23 us / 70 us | 380 us / 414 us |
| `xyzrgb_dragon.obj` | 14.527 ms / 15.664 ms | 1.889 ms / 2.110 ms | 1.079 ms / 1.909 ms | 17.362 ms / 18.697 ms |

Current bottleneck: OBJ parse dominates large fixtures; pipeline and pack are now small fractions of total runtime.
