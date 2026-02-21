Excellent. This is exactly the right time to align the two libraries conceptually.

Below is an updated **MeshForge README** that:

* Mirrors the clarity and structure of the Vectrix guide
* Shows how MeshForge and Vectrix fit together
* Uses the same tone and modular documentation style
* Reinforces the authoring → ops → pack flow
* Explicitly explains representation boundaries

You can drop this in as your new `README.md`.

---

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
mvn clean test        # compile and run tests
mvn clean package     # build JAR in target/
mvn -Pbench exec:exec # run JMH benchmarks
```

---

# Current Scope (v1)

* Editable mesh model (`MeshData`)
* Core processing ops (weld, normals, tangents, bounds)
* Interleaved packing
* Basic compression policies
* Immutable runtime mesh
* glTF IO module (initial support)

---

If you'd like next, I can:

* Create a matching **API Guide for MeshForge** in the same style as Vectrix (module-by-module with usage snippets), or
* Help you design the first concrete `MeshData` + `VertexSchema` implementation skeleton so you can start coding immediately.

