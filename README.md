# MeshForge

MeshForge is a pure Java mesh manipulation library focused on authoring, processing, and packing geometry.

It provides a clear, layered workflow:

1. Build or import editable `MeshData`
2. Apply processing pipelines (`MeshOp`)
3. Pack into immutable `PackedMesh` for downstream consumers

MeshForge is renderer-agnostic and designed to pair naturally with **Vectrix** for math and numeric helpers.

Core docs:

- `API.md` (public API reference)
- `TECHNOLOGY_EXPLAINER.md` (design rationale and integration model)
- `docs/README.md` (full documentation index)

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
Optimized for compact, immutable runtime geometry handoff.

In practice:
Contains packed vertex buffers, index buffer, layout metadata, and bounds.

---

## Why Meshlets

Meshlets improve geometry scalability and runtime efficiency for downstream consumers without changing MeshForge's core responsibilities.

Why they matter:

* Smaller, local triangle clusters improve vertex reuse and memory locality.
* Coarse per-cluster bounds/cone data enables fine-grained visibility filtering.
* Descriptor-friendly structure reduces command overhead in GPU-driven pipelines.
* Cluster/page-oriented data is a strong base for streaming and compression workflows.

Typical downstream impact (engine/runtime side):

| Area | Without meshlets | With meshlets |
|---|---|---|
| Geometry scale | limited by coarse mesh granularity | much higher effective triangle budgets |
| CPU submission/culling | high per-mesh overhead | lower overhead with cluster-level work |
| Vertex/bandwidth efficiency | less local reuse | better locality and reduced bandwidth |
| Streaming readiness | coarse asset chunks | natural fine-grained paging units |

MeshForge provides the meshlet data model, clustering, ordering, and descriptor packing. Runtime dispatch and rendering policy remain outside MeshForge.

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
* Keeps runtime immutable and consumer-friendly
* Prevents rendering concerns from leaking into authoring logic

The canonical architecture reference is:

```
docs/meshforge-architecture.md
```

Future feature planning is tracked in:

```
docs/roadmap.md
```

Cross-library ownership boundaries (Vectrix, MeshForge, DynamisLightEngine) are defined in:

```
docs/adr/0001-library-boundaries.md
```

Runtime geometry layout strategy is defined in:

```
docs/adr/0002-runtime-geometry-layout.md
```

Detailed integration rules and enforcement notes are in:

```
docs/boundaries.md
```

Loader format rollout planning is tracked in:

```
docs/loader-roadmap.md
```

Stress/fuzz guarantees and fail-fast guardrails are documented in:

```
docs/stress-guarantees.md
```

---

# Quick Start

```java
import org.dynamisengine.meshforge.api.*;
import org.dynamisengine.meshforge.core.mesh.*;
import org.dynamisengine.meshforge.core.topology.*;

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
PackedMesh packedOcta = MeshPacker.pack(mesh, Packers.realtimeWithOctaNormals());
```

---

# Package Layout

Single Maven artifact:

```
org.dynamisengine:meshforge
```

Layered packages:

```
org.dynamisengine.meshforge.api
org.dynamisengine.meshforge.core.*
org.dynamisengine.meshforge.ops.*
org.dynamisengine.meshforge.pack.*
org.dynamisengine.meshforge.loader.*            (in meshforge-loader module)
org.dynamisengine.meshforge.loader.gltf.*       (in meshforge-loader module)
```

Guidelines:

* `core` contains authoring types only.
* `ops` contains processing pipeline.
* `pack` contains runtime packing and buffer logic.
* Loader modules convert external formats → `MeshData` and live in `meshforge-loader`.

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

## Library Boundaries

MeshForge sits between Vectrix and DynamisLightEngine with explicit ownership:

| Library | Owns | Must not own |
|---|---|---|
| `vectrix` | math primitives, SIMD/SoA kernels, numeric packing helpers | mesh topology/pipeline, materials, render APIs |
| `meshforge` | mesh authoring (`MeshData`), mesh ops, packing (`PackedMesh`, meshlets) | lighting/material systems, render graph/passes, direct Vulkan/OpenGL calls |
| `dynamislightengine` | scene/render orchestration, materials/lights, shader/pipeline binding | reimplementation of MeshForge mesh ops or Vectrix math kernels |

PR smell checks:

- Math duplicated outside `vectrix`.
- Mesh processing duplicated outside `meshforge`.
- Rendering API logic introduced into `meshforge`.

`meshforge-demo` is a non-contract smoke/integration harness. Demo code may use LWJGL/Vulkan/shaderc for validation, but this is intentionally outside MeshForge core API guarantees.

Guardrail command:

```bash
./scripts/ci_guardrails.sh
```

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
* Vector API SIMD usage is confined to `pack` internals (currently octa-normal hot path).
* SIMD pack path can be toggled with `-Dmeshforge.pack.simd.enabled=true|false` (default `true`).
* Prefer API entry points (`Meshes`, `Ops`, `Packers`) for user workflows.
* `MeshData` is mutable.
* `PackedMesh` is immutable and safe to pass to downstream systems.

---

# Build

From repository root:

```
mvn clean test                          # build and test all modules
mvn -pl meshforge clean test            # build/test core mesh module only
mvn -pl meshforge-loader clean test     # build/test loader module
mvn -pl meshforge-demo package          # build demo module
./scripts/run-jmh.sh                    # run JMH benchmarks for meshforge module (fork-capable)
```

---

# Demos

The `meshforge-demo` module currently provides runnable demos.
These are examples for loader/pipeline/packing validation only and are not part of MeshForge's public contract.

Prerequisite (fresh checkout or after API changes):

```bash
mvn -pl meshforge,meshforge-loader -DskipTests install
```

1. `org.dynamisengine.meshforge.demo.MeshForgeDemo` (CLI)
- Loads one mesh file through `meshforge-loader`
- Runs the fast realtime pipeline (`Pipelines.realtimeFast`)
- Packs with `Packers.realtime` and prints mesh/packing stats
- Run:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.MeshForgeDemo -Dexec.args="fixtures/obj/medium/suzanne.obj" exec:java
```

2. `org.dynamisengine.meshforge.demo.MeshViewerApp` (JavaFX viewer)
- File-open UI for `*.obj`, `*.stl`, `*.ply`, `*.off`
- Loads via `MeshLoaders.defaults()`, processes with `Pipelines.realtimeFast`, validates packing path
- Displays the mesh in a JavaFX viewport (left-drag orbit, right-drag pan, wheel zoom)
- Run:

```bash
mvn -pl meshforge-demo javafx:run
```

3. `org.dynamisengine.meshforge.demo.MeshletDispatchDemo` and `org.dynamisengine.meshforge.demo.VulkanPreflight`
- These are optional experimental harnesses under `meshforge-demo` only.
- They are intentionally non-contract and not required for using MeshForge as a mesh manipulation library.

See `docs/mesh-fixtures.md` for sample asset sources and local fixture setup.

---

# Benchmark Snapshot

Command run outside sandbox on February 22, 2026:

```bash
./scripts/run-jmh.sh
```

Reliable forked runner script (uses direct `java -cp ...` and works outside sandbox):

```bash
./scripts/run-jmh.sh
JMH_FILTER='.*MeshPackerBenchmark.*' JMH_FORKS=2 ./scripts/run-jmh.sh
JMH_FILTER='.*MeshPackerBenchmark\\.(packRealtime|packRealtimeOctaNormals).*' JMH_FORKS=1 JMH_JAVA_OPTS='-Dmeshforge.pack.simd.enabled=true' ./scripts/run-jmh.sh
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
- `MeshPipelineBenchmark.realtimePipeline` is import/preprocess cost, not a frame-runtime metric.
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
mvn -pl meshforge-demo -am -DskipTests compile dependency:build-classpath \
  -Dmdep.includeScope=runtime -Dmdep.outputFile=/tmp/mf_demo_cp.txt
CP="$(cat /tmp/mf_demo_cp.txt):meshforge-demo/target/classes"
java --enable-preview --add-modules jdk.incubator.vector \
  -cp "$CP" org.dynamisengine.meshforge.demo.BaselineFixtureTiming --repeat=16
```

Definitions:
- `Load ms`: median of 3 timing passes for file read + parse into `MeshData` (`MeshLoaders.defaultsFast().load(...)`)
- `Mode A (cold create)`: `Load -> Pipelines.realtimeFast(...) -> pack lane` measured per operation
- `Mode B (repeated create)`: load once, preprocess once, then repeat create lane (`--repeat` count) and report per-op median
- `Friendly lane`: `MeshPacker.pack(mesh, Packers.realtime())`
- `Runtime lane`: `MeshPacker.packInto(mesh, Packers.realtime(), workspace)`
- `Planned lane`: `RuntimePackPlan plan = MeshPacker.buildRuntimePlan(mesh, Packers.realtime())`, then `MeshPacker.packPlannedInto(plan, workspace)`
- `/1M tris`: normalized creation cost by triangle count

Post-runtime-hardening snapshot (March 7, 2026, local machine run):

- Commit: `1fbb7b8`
- JDK: `openjdk 25.0.1 LTS` (preview enabled)
- Flags: `--enable-preview --add-modules jdk.incubator.vector`
- Host: `Darwin 25.3.0 (arm64)`

Mode A: cold create (`Load -> Create`)

| Fixture | Load ms | Create Friendly | Create Runtime | Create Planned | Friendly /1M tris | Runtime /1M tris | Planned /1M tris | Vertices | Triangles |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `beast.obj` | 3.176 | 3.978 | 3.579 | 3.594 | 61.568 | 55.380 | 55.625 | 32311 | 64618 |
| `cow.obj` | 0.270 | 0.311 | 0.290 | 0.303 | 53.555 | 50.027 | 52.284 | 2903 | 5804 |
| `lucy.obj` | 5.476 | 5.711 | 5.413 | 5.376 | 57.126 | 54.147 | 53.780 | 49987 | 99970 |
| `nefertiti.obj` | 5.067 | 5.255 | 5.013 | 5.101 | 52.578 | 50.160 | 51.043 | 49971 | 99938 |
| `RevitHouse.obj` | 71.350 | 20.234 | 16.212 | 16.235 | 49.098 | 39.338 | 39.394 | 1242180 | 412119 |
| `stanford-bunny.obj` | 3.158 | 3.223 | 3.089 | 3.089 | 46.408 | 44.484 | 44.479 | 35947 | 69451 |
| `suzanne.obj` | 0.059 | 0.046 | 0.045 | 0.044 | 47.650 | 46.419 | 44.938 | 507 | 968 |
| `teapot.obj` | 0.292 | 0.285 | 0.275 | 0.277 | 45.137 | 43.542 | 43.784 | 3644 | 6320 |
| `xyzrgb_dragon.obj` | 14.308 | 12.840 | 12.098 | 12.085 | 51.384 | 48.416 | 48.361 | 125066 | 249882 |

Mode B: repeated create (load once, repeated create; `repeat=16`)

| Fixture | Create Friendly | Create Runtime | Create Planned | Friendly /1M tris | Runtime /1M tris | Planned /1M tris | Vertices | Triangles |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `beast.obj` | 1.590 | 0.875 | 0.777 | 24.607 | 13.533 | 12.031 | 32311 | 64618 |
| `cow.obj` | 0.133 | 0.066 | 0.068 | 22.974 | 11.382 | 11.722 | 2903 | 5804 |
| `lucy.obj` | 3.541 | 2.367 | 2.265 | 35.423 | 23.673 | 22.658 | 49987 | 99970 |
| `nefertiti.obj` | 2.406 | 1.256 | 1.151 | 24.076 | 12.568 | 11.514 | 49971 | 99938 |
| `RevitHouse.obj` | 37.005 | 16.794 | 16.760 | 89.791 | 40.751 | 40.668 | 1242180 | 412119 |
| `stanford-bunny.obj` | 1.683 | 0.863 | 0.822 | 24.239 | 12.431 | 11.833 | 35947 | 69451 |
| `suzanne.obj` | 0.027 | 0.013 | 0.012 | 28.035 | 12.959 | 11.904 | 507 | 968 |
| `teapot.obj` | 0.138 | 0.080 | 0.079 | 21.897 | 12.684 | 12.505 | 3644 | 6320 |
| `xyzrgb_dragon.obj` | 7.332 | 3.900 | 3.465 | 29.342 | 15.609 | 13.868 | 125066 | 249882 |

These are fixture-level throughput indicators and will vary by CPU/JVM/load.

## End-to-End Runtime Geometry Baseline

The original fixture table measured two separate phases:

- `Load ms` = file read + parse into `MeshData`
- `Create ms` = `Pipelines.realtimeFast(...) + MeshPacker.pack(...)`

Recent work introduced two additional end-to-end measurements:

- `New OBJ total` = OBJ parse + runtime prep + packing + MeshForge -> DynamisGPU upload-plan translation
- `Cache total` = runtime-geometry cache load + MeshForge -> DynamisGPU upload-plan translation

This makes the comparison clearer: the optimized runtime pipeline is not slower; the full OBJ path is roughly in line with the original baseline, while the cached runtime geometry path is dramatically faster.

### Baseline Comparison Table

| Fixture | Old Load ms | Old Create ms | Old Total ms | New OBJ Total ms | Cache Total ms | Cache Speedup vs Old Total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RevitHouse.obj | 70.450 | 10.547 | 80.997 | 81.538 | 3.995 | 20.27x |
| xyzrgb_dragon.obj | 15.200 | 3.090 | 18.290 | 17.929 | 1.243 | 14.71x |
| lucy.obj | 6.046 | 1.381 | 7.427 | 6.994 | 0.422 | 17.60x |

### Interpretation

- The old total and new OBJ total are comparable and remain in the same general range.
- The main remaining cold-load bottleneck in the OBJ path is text parsing, not runtime geometry preparation.
- The new runtime geometry cache bypasses parse + prep + packing and feeds the MeshForge -> DynamisGPU seam directly.
- This yields a large cold-load improvement:
  - RevitHouse: ~81.0 ms -> ~4.0 ms
  - dragon: ~18.3 ms -> ~1.2 ms
  - lucy: ~7.4 ms -> ~0.4 ms

### Current Runtime Geometry Doctrine

MeshForge now provides three packing tiers:

- `pack(...)` - friendly, ergonomic path
- `packInto(...)` - runtime path
- `packPlannedInto(...)` - lowest-overhead repeated-runtime path

The preferred repeated engine path is:

```java
RuntimePackPlan plan = MeshPacker.buildRuntimePlan(mesh, spec);
MeshPacker.packPlannedInto(plan, workspace);
```

The preferred cold-load runtime path is:

```text
runtime geometry cache -> RuntimeGeometryPayload -> GpuGeometryUploadPlan
```

Canonical loader entry point:

```java
RuntimeGeometryLoader loader = new RuntimeGeometryLoader(MeshLoaders.defaultsFast(), Packers.realtime());
RuntimeGeometryLoader.Result result = loader.load(assetPath);
```

### Runtime Geometry Pipeline

Slow path (source import):

```text
OBJ/GLTF
   ->
MeshLoaders
   ->
Pipelines.realtimeFast
   ->
MeshPacker
   ->
RuntimeGeometryPayload
   ->
meshforge-dynamisgpu
   ->
GpuGeometryUploadPlan
   ->
DynamisGPU
```

Fast path (cache):

```text
Runtime geometry cache
   ->
RuntimeGeometryPayload
   ->
meshforge-dynamisgpu
   ->
GpuGeometryUploadPlan
   ->
DynamisGPU
```

Regression guardrail snapshot:

```text
docs/runtime-geometry-regression-baseline.md
```

Cache lifecycle policy:

```text
docs/runtime-geometry-cache-lifecycle.md
```

## Next Phase

The next phase is no longer about squeezing MeshForge packing or runtime pipeline internals. Those paths are now in strong shape. The next work should focus on integration and productionization.

### Phase E - Runtime Geometry Integration and Cache Productionization

#### Track A - MeshForge -> DynamisGPU seam

Status: foundation complete

Completed:

- `meshforge-dynamisgpu` module
- `RuntimeGeometryPayload`
- `GpuGeometryUploadPlan`
- `MeshForgeGpuBridge`
- seam benchmark

Next:

- document the runtime geometry contract more formally
- decide whether payload backing should stay abstract or standardize around a single low-level carrier
- extend the seam for future multi-stream layouts and meshlet/cluster upload plans

#### Track B - Runtime geometry cache

Status: prototype complete

Completed:

- cache format draft
- serializer/deserializer prototype
- cache vs OBJ benchmark harness

Next:

- add format versioning and compatibility checks
- add schema/layout validation during load
- define cache invalidation rules
- integrate cache generation into the asset pipeline
- add background/offline cache build support

### Immediate Implementation Goals

1. Finalize cache format header
   - magic
   - version
   - endianness
   - layout/schema identifier
   - compatibility flags
2. Add cache validation
   - reject stale or incompatible cached payloads
   - fall back to source import when needed
3. Wire cache generation into import flow
   - first load from OBJ/GLTF/etc builds runtime cache
   - subsequent loads use cache directly
4. Add production fixture regression table
   - old total
   - new OBJ total
   - cache total
   - speedup
   - commit/JDK/platform metadata
5. Prepare for future GPU-side work
   - multi-stream vertex layouts
   - meshlets/clusters
   - chunked geometry caches
   - streaming residency strategy

### Short Version

MeshForge runtime geometry preparation is no longer the primary bottleneck.
The next phase is to:

- lock the MeshForge -> DynamisGPU seam
- productionize the runtime geometry cache
- shift cold-load cost away from text parsing and toward cached runtime-ready geometry

## Phase-Split Fixture Timings

Granular breakdown per fixture (parse / pipeline / pack / total), with median and p95 over 7 timed runs after 3 warmup runs.

Command:

```bash
mvn -pl meshforge-demo -DskipTests compile
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--legacy" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --pack-minimal" exec:java
# parse-only profiling loop for loader optimization work (Part 2)
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --parse-only --fixture=RevitHouse --warmup=5 --runs=15" exec:java
# include parser sub-phase breakdown (scan/float/face) in console + CSV
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --parse-only --profile-parse --fixture=RevitHouse --warmup=5 --runs=15" exec:java
# compare two phase-split CSV snapshots
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitDiff -Dexec.args="perf/results/phase-split-fast-OLD.csv perf/results/phase-split-fast-NEW.csv" exec:java
# compare with regression gate (fails if total delta > 10%)
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitDiff -Dexec.args="perf/results/phase-split-fast-OLD.csv perf/results/phase-split-fast-NEW.csv --max-regression-pct=10 --fixture=RevitHouse" exec:java
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
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast" exec:java
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PackBreakdownFixtureTiming -Dexec.args="--fast --pack-minimal" exec:java
```

Shortcut from phase-split runner:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.dynamisengine.meshforge.demo.PhaseSplitFixtureTiming -Dexec.args="--fast --profile-pack" exec:java
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
* Multi-module structure (`meshforge`, `meshforge-loader`, `meshforge-demo`)
