# MeshForge

MeshForge core is a pure Java runtime geometry subsystem for Dynamis, focused on authoring, processing, packing, cache-backed loading, and GPU upload-plan handoff.

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

MeshForge supports meshlet-related data modeling and packing workflows where enabled by pack specs; runtime dispatch and rendering policy remain outside MeshForge.

---

## PackSpec

Defines how a mesh is packed.

Why use it:
Control layout (interleaved/multi-stream), compression, index width.

In practice:
`MeshPacker.pack(mesh, Packers.realtime())`.

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

# Performance and Baselines

For detailed benchmark methodology and historical profiling snapshots, use:

- `docs/perf-baseline.md`
- `docs/performance-profile.md`
- `docs/runtime-geometry-regression-baseline.md`
- `tools/benchmarks/meshforge/BaselineFixtureTiming.java`

Perf gate:

```bash
./scripts/perf-gate.sh
```

## End-to-End Runtime Geometry Baseline

| Fixture | Old Total ms | New OBJ Total ms | Cache Total ms | Cache Speedup vs Old Total |
| --- | ---: | ---: | ---: | ---: |
| RevitHouse.obj | 80.997 | 81.538 | 3.995 | 20.27x |
| xyzrgb_dragon.obj | 18.290 | 17.929 | 1.243 | 14.71x |
| lucy.obj | 7.427 | 6.994 | 0.422 | 17.60x |

Interpretation:

- Cold OBJ path is roughly in line with prior totals.
- The dominant cold-load cost is source parse/import.
- Cache path bypasses import/prep/pack and is now the preferred runtime load path.

## Runtime Geometry Doctrine

- `pack(...)`: friendly path
- `packInto(...)`: runtime workspace path
- `packPlannedInto(...)`: preferred repeated-runtime path

Canonical loader usage:

```java
RuntimeGeometryLoader loader = new RuntimeGeometryLoader(MeshLoaders.defaultsFast(), Packers.realtime());
RuntimeGeometryLoader.Result loaded = loader.load(assetPath);
RuntimeGeometryLoader.PrebuildResult prebuilt = loader.prebuild(assetPath);
GpuGeometryUploadPlan plan = MeshForgeGpuBridge.buildUploadPlan(loaded.payload());
```

Runtime flow references:

- `docs/runtime-geometry-flow.md`
- `docs/runtime-geometry-cache-lifecycle.md`
- `docs/meshforge-v1-architecture.md`

v2 planning references:

- `docs/v2-clustered-runtime-contract.md`
- `docs/v2-clustered-cache-strategy.md`
- `docs/v2-lod-chunking-outline.md`

---

# Current Scope (v1)

- Editable mesh model (`MeshData`)
- Core processing ops (weld, normals, tangents, bounds, topology cleanup)
- Packing tiers (`pack`, `packInto`, `packPlannedInto`)
- Runtime geometry cache (`.mfgc`) with validation + load-or-build lifecycle
- Canonical runtime loader + prebuild support
- MeshForge -> DynamisGPU bridge (`RuntimeGeometryPayload` -> `GpuGeometryUploadPlan`)
- Multi-module structure (`meshforge`, `meshforge-loader`, `meshforge-dynamisgpu`, `meshforge-demo`)

## Status

MeshForge v1 runtime geometry infrastructure is complete and stable.
Primary next work shifts to DynamisGPU/renderer consumption and real upload/streaming integration.
