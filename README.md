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

Command run outside sandbox on February 21, 2026:

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

Results (JMH `avgt`, forked, `Cnt=20`):

| Benchmark | Units | Score | Error | Cnt |
|---|---|---:|---:|---:|
| `MeshOpsBenchmark.computeBounds` | `ms/op` | 0.110 | ±0.001 | 20 |
| `MeshOpsBenchmark.optimizeVertexCache` | `ms/op` | 293.849 | ±4.544 | 20 |
| `MeshOpsBenchmark.recalculateNormals` | `ms/op` | 0.501 | ±0.007 | 20 |
| `MeshOpsBenchmark.recalculateTangents` | `ms/op` | 1.111 | ±0.003 | 20 |
| `MeshOpsBenchmark.removeDegenerates` | `ms/op` | 0.312 | ±0.015 | 20 |
| `MeshOpsBenchmark.validate` | `ms/op` | 0.073 | ±0.003 | 20 |
| `MeshOpsBenchmark.weld` | `ms/op` | 3.372 | ±0.064 | 20 |
| `MeshPackerBenchmark.packDebug` | `ms/op` | 7.210 | ±0.643 | 20 |
| `MeshPackerBenchmark.packRealtime` | `ms/op` | 7.394 | ±0.739 | 20 |
| `MeshPipelineBenchmark.realtimePipeline` | `ms/op` | 165.614 | ±1.540 | 20 |
| `OptimizeVertexCacheBenchmark.optimizeAndMeasureAcmr` | `ms/op` | 302.115 | ±4.410 | 20 |

Focused packer-only run (`JMH_FILTER='.*MeshPackerBenchmark.*'`, `-f 3 -wi 8 -i 15 -prof gc`) produced a stable `packRealtime` of `5.356 ± 0.015 ms/op`.

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

Snapshot (February 21, 2026, local machine run):

| Fixture | Load ms (median) | Create ms (median) | Load ms / 1M verts | Create ms / 1M tris | Vertices | Triangles |
|---|---:|---:|---:|---:|---:|---:|
| `beast.obj` | 6.453 | 3.834 | 199.707 | 59.333 | 32311 | 64618 |
| `cow.obj` | 0.572 | 0.354 | 197.092 | 60.961 | 2903 | 5804 |
| `lucy.obj` | 13.327 | 5.845 | 266.603 | 58.469 | 49987 | 99970 |
| `nefertiti.obj` | 9.459 | 5.161 | 189.280 | 51.639 | 49971 | 99938 |
| `RevitHouse.obj` | 183.373 | 93.068 | 147.622 | 225.828 | 1242180 | 412119 |
| `stanford-bunny.obj` | 6.385 | 3.096 | 177.610 | 44.580 | 35947 | 69451 |
| `suzanne.obj` | 0.114 | 0.052 | 224.178 | 53.857 | 507 | 968 |
| `teapot.obj` | 0.613 | 0.324 | 168.306 | 51.323 | 3644 | 6320 |
| `xyzrgb_dragon.obj` | 28.267 | 10.806 | 226.016 | 43.245 | 125066 | 249882 |

These are fixture-level throughput indicators and will vary by CPU/JVM/load.

## Phase-Split Fixture Timings

Granular breakdown per fixture (parse / pipeline / pack / total), with median and p95 over 7 timed runs after 3 warmup runs.

Command:

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--legacy" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast" exec:java
```

Latest snapshot (February 21, 2026, local machine run):

| Fixture | Legacy Total (median / p95) ms | Fast Total (median / p95) ms | Speedup |
|---|---:|---:|---:|
| `beast.obj` | 48 / 50 | 10 / 16 | 4.80x |
| `cow.obj` | 2 / 3 | 0 / 1 | ~2x+ |
| `lucy.obj` | 62 / 76 | 19 / 19 | 3.26x |
| `nefertiti.obj` | 53 / 57 | 15 / 16 | 3.53x |
| `RevitHouse.obj` | 830 / 863 | 267 / 284 | 3.11x |
| `stanford-bunny.obj` | 32 / 32 | 9 / 9 | 3.56x |
| `suzanne.obj` | 0 / 0 | 0 / 0 | n/a |
| `teapot.obj` | 2 / 3 | 0 / 1 | ~2x+ |
| `xyzrgb_dragon.obj` | 170 / 204 | 38 / 41 | 4.47x |

Fast-loader parse phase examples (`--fast`):
- `RevitHouse.obj`: parse `180 / 183 ms` (from legacy `729 / 737 ms`)
- `xyzrgb_dragon.obj`: parse `28 / 28 ms` (from legacy `160 / 190 ms`)
- `lucy.obj`: parse `13 / 13 ms` (from legacy `58 / 70 ms`)

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
