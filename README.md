# MeshForge

MeshForge is a Java mesh authoring and packaging library designed for modern rendering pipelines.

It provides a clear, layered workflow:

1. Build or import editable `MeshData`
2. Apply processing pipelines (`MeshOp`)
3. Pack into immutable `PackedMesh` for rendering

MeshForge is renderer-agnostic and designed to pair naturally with **Vectrix** for math, transforms, culling, and SIMD acceleration.

---

# Concepts and Definitions

## MeshData

Editable, semantic mesh representation.

Why use it:
Flexible for procedural generation, importers, and mesh processing.

In practice:
`MeshData` stores topology, vertex attributes, submeshes, and index data in a schema-driven format.

---

## VertexSchema

Declares which attributes a mesh contains and in what format.

Why use it:
Ensures consistent attribute contracts across ops and packing.

In practice:
Define POSITION (F32x3), NORMAL (F32x3), UV0 (F32x2), etc.

---

## Submesh

Defines a draw range within a mesh.

Why use it:
Allows multiple materials or draw calls within one mesh.

In practice:
`firstIndex`, `indexCount`, `materialId`.

---

## MeshOp (Processing Operation)

A transformation applied to `MeshData`.

Why use it:
Compose processing steps such as weld, normals, tangents, optimization.

In practice:
`MeshPipeline.run(mesh, ops...)`.

---

## PackedMesh

Immutable, GPU-ready representation of a mesh.

Why use it:
Optimized for upload and rendering.

In practice:
Contains packed vertex buffers, index buffer, layout metadata, and bounds.

---

## PackSpec

Defines how a mesh is packed.

Why use it:
Control layout (interleaved/multi-stream), compression, index width.

In practice:
`MeshPacker.pack(mesh, PackSpec.realtime())`.

---

# Architecture Overview

MeshForge separates authoring and runtime representations intentionally.

```
MeshData (editable)
    ↓
MeshPipeline (ops)
    ↓
MeshPacker (PackSpec)
    ↓
PackedMesh (immutable)
```

This separation:

* Keeps authoring flexible
* Keeps runtime immutable and render-friendly
* Prevents rendering concerns from leaking into authoring logic

The canonical architecture reference is:

```
docs/meshforge-architecture.md
```

Future feature planning is tracked in:

```
docs/roadmap.md
```

Loader format rollout planning is tracked in:

```
docs/loader-roadmap.md
```

---

# Quick Start

```java
import org.meshforge.api.*;
import org.meshforge.core.mesh.*;
import org.meshforge.core.topology.*;

MeshData mesh = Meshes.builder(Topology.TRIANGLES)
    .schema(VertexSchema.standardLit())
    .add(...)
    .build();

mesh = MeshPipeline.run(mesh,
    Ops.weld(1e-6f),
    Ops.normals(60f),
    Ops.tangents(),
    Ops.optimizeVertexCache(),
    Ops.bounds()
);

PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
```

Preset shortcuts:

```java
mesh = Pipelines.realtime(mesh);      // full import-time optimization path
mesh = Pipelines.realtimeFast(mesh);  // fast path for already-clean assets
PackedMesh packed = MeshPacker.pack(mesh, Packers.realtimeFast());
```

---

# Package Layout

Single Maven artifact:

```
org.meshforge:meshforge
```

Layered packages:

```
org.meshforge.api
org.meshforge.core.*
org.meshforge.ops.*
org.meshforge.pack.*
org.meshforge.io.gltf.*
```

Guidelines:

* `core` contains authoring types only.
* `ops` contains processing pipeline.
* `pack` contains runtime packing and buffer logic.
* `io` modules convert external formats → `MeshData`.

Render backends should live outside MeshForge.

---

# Design Principles

* Clear separation between authoring and runtime models
* Immutable runtime mesh
* Schema-driven attribute management
* Composable processing pipeline
* Explicit packing policies
* Renderer-agnostic core
* Performance-focused hot path

---

# Relationship to Vectrix

MeshForge does not implement math primitives directly.

It relies on Vectrix for:

* Vector and matrix math
* Transform types (TRS, affine)
* SIMD-aware kernels
* Frustum and culling helpers
* Packed normal and quaternion utilities
* GPU packing helpers (half, snorm, octa encoding)

Typical integration:

* Use Vectrix for transform propagation
* Use MeshForge to manage and pack geometry
* Use Vectrix GPU utilities during packing if needed

---

# Development Notes

* Java preview features are enabled (`--enable-preview`).
* SIMD or Vector API usage (if any) is confined to `pack` internals.
* Prefer API entry points (`Meshes`, `Ops`, `Packers`) for user workflows.
* `MeshData` is mutable.
* `PackedMesh` is immutable and render-safe.

---

# Build

From repository root:

```
mvn clean test                          # build and test all modules
mvn -pl meshforge clean test            # build/test core mesh module only
mvn -pl meshforge-loader clean test     # build/test loader module
mvn -pl meshforge-demo package          # build demo module
mvn -pl meshforge -Pbench test-compile exec:java   # run JMH benchmarks for meshforge module
```

---

# Demos

The `meshforge-demo` module currently provides two runnable demos:

Prerequisite (fresh checkout or after API changes):

```bash
mvn -pl meshforge,meshforge-loader -DskipTests install
```

1. `org.meshforge.demo.MeshForgeDemo` (CLI)
- Loads one mesh file through `meshforge-loader`
- Runs the fast realtime pipeline (`Pipelines.realtimeFast`)
- Packs with `Packers.realtime` and prints mesh/packing stats
- Run:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.MeshForgeDemo -Dexec.args="fixtures/obj/medium/suzanne.obj" exec:java
```

2. `org.meshforge.demo.MeshViewerApp` (JavaFX viewer)
- File-open UI for `*.obj`, `*.stl`, `*.ply`, `*.off`
- Loads via `MeshLoaders.defaults()`, processes with `Pipelines.realtimeFast`, validates packing path
- Displays the mesh in a JavaFX viewport (left-drag orbit, right-drag pan, wheel zoom)
- Run:

```bash
mvn -pl meshforge-demo javafx:run
```

See `docs/mesh-fixtures.md` for sample asset sources and local fixture setup.

---

# Benchmark Snapshot

Command run outside sandbox on February 22, 2026:

```bash
./scripts/run-jmh.sh
```

Forked run outside sandbox (recommended for trustworthy numbers):

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.forks=1 -Djmh.filter='.*(OptimizeVertexCacheBenchmark|MeshPackerBenchmark|MeshPipelineBenchmark).*'
```

Reliable forked runner script (uses direct `java -cp ...` and works outside sandbox):

```bash
./scripts/run-jmh.sh
JMH_FILTER='.*MeshPackerBenchmark.*' JMH_FORKS=2 ./scripts/run-jmh.sh
```

Results (JMH `avgt`, forked, `Cnt=5`):

| Benchmark | Units | Score | Error | Cnt |
|---|---|---:|---:|---:|
| `MeshOpsBenchmark.computeBounds` | `ms/op` | 0.111 | ±0.002 | 5 |
| `MeshOpsBenchmark.optimizeVertexCache` | `ms/op` | 305.508 | ±28.529 | 5 |
| `MeshOpsBenchmark.recalculateNormals` | `ms/op` | 0.519 | ±0.028 | 5 |
| `MeshOpsBenchmark.recalculateTangents` | `ms/op` | 1.115 | ±0.013 | 5 |
| `MeshOpsBenchmark.removeDegenerates` | `ms/op` | 0.316 | ±0.071 | 5 |
| `MeshOpsBenchmark.validate` | `ms/op` | 0.076 | ±0.012 | 5 |
| `MeshOpsBenchmark.weld` | `ms/op` | 3.454 | ±0.068 | 5 |
| `MeshPackerBenchmark.packDebug` | `ms/op` | 1.152 | ±0.034 | 5 |
| `MeshPackerBenchmark.packRealtime` | `ms/op` | 1.021 | ±0.040 | 5 |
| `MeshPipelineBenchmark.realtimePipeline` | `ms/op` | 168.763 | ±5.911 | 5 |
| `OptimizeVertexCacheBenchmark.optimizeAndMeasureAcmr` | `ms/op` | 304.540 | ±9.173 | 5 |

Focused packer-only run (`JMH_FILTER='.*MeshPackerBenchmark.*'`, `-f 3 -wi 8 -i 15 -prof gc`) previously produced a stable `packRealtime` of `5.356 ± 0.015 ms/op` before hot-path pack specialization.

Notes:
- For publishable/perf-regression baselines, run outside sandbox with forks (`JMH_FORKS>=1`).
- Broader suite runs can show more variance; use focused runs for baseline refresh on specific benchmarks.

How to read the table:
- `Units`: `ms/op` means milliseconds per benchmark operation (lower is faster).
- `Score`: average runtime per operation across measured iterations.
- `Error`: JMH confidence interval half-width (99.9%) for the score.
- `Cnt`: number of measurement iterations used for the final score.

Interpretation tip:
- `MeshPipelineBenchmark.realtimePipeline` is an import/preprocess cost, not per-frame render cost.
- Use `Pipelines.realtimeFast(...)` when source meshes are already clean and you want lower import latency.

Perf gate:

```bash
./scripts/perf-gate.sh
```

Baseline config is in `perf/baseline.csv`, with process notes in `docs/perf-baseline.md`.

---

# Fixture Baseline Timings

Command (run from repo root):

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.BaselineFixtureTiming exec:java
```

Definitions:
- `Load ms (median)`: median of 3 timing passes for file read + parse into `MeshData` (`MeshLoaders.defaults().load(...)`)
- `Create ms (median)`: median of 3 timing passes for mesh runtime prep (`Pipelines.realtimeFast(...)` + `MeshPacker.pack(..., Packers.realtime())`)
- `Load ms / 1M verts`: normalized loader cost by vertex count
- `Create ms / 1M tris`: normalized creation cost by triangle count

Snapshot (February 22, 2026, local machine run):

| Fixture | Load ms (median) | Create ms (median) | Load ms / 1M verts | Create ms / 1M tris | Vertices | Triangles |
|---|---:|---:|---:|---:|---:|---:|
| `beast.obj` | 3.340 | 0.881 | 103.370 | 13.634 | 32311 | 64618 |
| `cow.obj` | 0.293 | 0.076 | 100.939 | 13.046 | 2903 | 5804 |
| `lucy.obj` | 6.046 | 1.381 | 120.961 | 13.810 | 49987 | 99970 |
| `nefertiti.obj` | 5.420 | 1.111 | 108.463 | 11.114 | 49971 | 99938 |
| `RevitHouse.obj` | 70.450 | 10.547 | 56.715 | 25.592 | 1242180 | 412119 |
| `stanford-bunny.obj` | 3.302 | 0.833 | 91.870 | 11.992 | 35947 | 69451 |
| `suzanne.obj` | 0.065 | 0.022 | 128.944 | 22.701 | 507 | 968 |
| `teapot.obj` | 0.303 | 0.078 | 83.054 | 12.367 | 3644 | 6320 |
| `xyzrgb_dragon.obj` | 15.200 | 3.090 | 121.540 | 12.367 | 125066 | 249882 |

These are fixture-level throughput indicators and will vary by CPU/JVM/load.

## Phase-Split Fixture Timings

Granular breakdown per fixture (parse / pipeline / pack / total), with median and p95 over 7 timed runs after 3 warmup runs.

Command:

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--legacy" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --pack-minimal" exec:java
# parse-only profiling loop for loader optimization work (Part 2)
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --parse-only --fixture=RevitHouse --warmup=5 --runs=15" exec:java
# include parser sub-phase breakdown (scan/float/face) in console + CSV
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --parse-only --profile-parse --fixture=RevitHouse --warmup=5 --runs=15" exec:java
```

Latest fast-loader snapshot (February 22, 2026, local machine run):

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

Key observations:
- Specialized hot loops in `MeshPacker` reduced pack costs dramatically across fixtures.
- RevitHouse total is now ~87 ms median with pack around ~5 ms.
- Parse is the dominant stage; pipeline and pack are secondary.

Legacy vs fast total-time deltas (same fixture set):

| Fixture | Legacy Total (median / p95) | Fast Total (median / p95) | Speedup |
|---|---:|---:|---:|
| `beast.obj` | 48 ms / 50 ms | 4.411 ms / 10.910 ms | 10.88x |
| `cow.obj` | 2 ms / 3 ms | 430 us / 504 us | 4.65x |
| `lucy.obj` | 62 ms / 76 ms | 7.447 ms / 7.479 ms | 8.33x |
| `nefertiti.obj` | 53 ms / 57 ms | 6.499 ms / 6.806 ms | 8.15x |
| `RevitHouse.obj` | 830 ms / 863 ms | 87.089 ms / 89.072 ms | 9.53x |
| `stanford-bunny.obj` | 32 ms / 32 ms | 3.976 ms / 4.034 ms | 8.05x |
| `suzanne.obj` | 0 ms / 0 ms | 85 us / 125 us | n/a (legacy rounded to 0 ms) |
| `teapot.obj` | 2 ms / 3 ms | 380 us / 414 us | 5.26x |
| `xyzrgb_dragon.obj` | 170 ms / 204 ms | 17.362 ms / 18.697 ms | 9.79x |

### Pack Breakdown Timings

Isolate `MeshPacker.pack(..., Packers.realtime())` and capture sub-phases:
- resolve attributes
- layout/stride computation
- vertex write loop
- index packing
- submesh copy/finalization

Command:

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast --pack-minimal" exec:java
```

Shortcut from phase-split runner:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --profile-pack" exec:java
```

CSV output is written to `perf/results/pack-breakdown-*.csv` with per-fixture medians/p95 and normalized totals.
`--pack-minimal` uses `Packers.realtimeMinimal()` (position-only pack target) for quick deltas.

Latest pack-breakdown snapshot (`--fast`, realtime spec, February 22, 2026):

| Fixture | Pack Total (median / p95) | Vertex (median) | Position (median) | Normal (median) | Index (median) |
|---|---:|---:|---:|---:|---:|
| `RevitHouse.obj` | 55.117 ms / 59.575 ms | 54.564 ms | 17.091 ms | 10.332 ms | 531 us |

The current pack hotspot is still the vertex write/format conversion loop; layout/resolve/submesh remain small.
Note: sub-phase mode includes per-section instrumentation (`System.nanoTime`) and is intended for hotspot ranking, not absolute cross-mode throughput comparison.

CSV outputs are written to `perf/results/phase-split-legacy-<timestamp>.csv` and `perf/results/phase-split-fast-<timestamp>.csv`.
For very small fixtures, millisecond rounding can show `0 ms`; use larger iteration counts if you need finer phase resolution.

---

# Current Scope (v1)

* Editable mesh model (`MeshData`)
* Core processing ops (weld, normals, tangents, bounds)
* Interleaved packing
* Basic compression policies
* Immutable runtime mesh
* glTF IO module (initial support)
