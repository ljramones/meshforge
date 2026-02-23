# MeshForge API Guide

Last updated: 2026-02-23

This document is the top-level API reference for MeshForge v1.

## Layered Model

1. Authoring: editable `MeshData`
2. Processing: `MeshPipeline` + `MeshOp`
3. Packing: immutable `PackedMesh`

## Public Facade (`org.meshforge.api`)

- `Meshes`: mesh creation helpers (`builder`, `writer`, sample primitives)
- `Ops`: operation factories (`validate`, `weld`, `normals`, `tangents`, `optimizeVertexCache`, etc.)
- `Pipelines`: opinionated operation chains (`realtime`, `realtimeFast`)
- `Packers`: pack presets that return `PackSpec`

## Core Authoring (`org.meshforge.core.*`)

- `MeshData`: mutable mesh model with schema, attributes, topology, indices, submeshes, morph targets, optional bounds
- `VertexSchema`: semantic and format contract for attributes
- `VertexAttributeView`: typed attribute access and write surface
- `MorphTarget`: per-vertex delta streams (`POSITION` required, `NORMAL`/`TANGENT` optional)
- `Submesh`: index-range partition and material binding
- `Topology`: primitive topology enum

## Ops (`org.meshforge.ops.*`)

- `MeshOp`: operation interface
- `MeshPipeline.run(mesh, ops...)`: deterministic left-to-right composition
- `MeshContext`: operation-scoped context object

Common op families:

- Validation and repair
- Normal/tangent/bounds generation
- Degenerate removal and weld/compact
- Index reorder and meshlet clustering/order

## Packing (`org.meshforge.pack.*`)

- `PackSpec`: pack contract (layout, index policy, target formats, meshlet options)
- `MeshPacker.pack(mesh, spec)`: authoring-to-runtime transform
- `PackedMesh`: immutable packed output buffers + layout metadata
- `VertexLayout`: packed vertex layout metadata

Current behavior notes:

- `PackSpec.LayoutMode.MULTI_STREAM` exists in the contract.
- v1 pack implementation currently supports interleaved output (`INTERLEAVED`) only and fails fast for unsupported layout modes.
- Morph targets are preserved in `MeshData`; packed output in v1 is base-mesh only.

## Loader Surface (`meshforge-loader`)

- `MeshLoaders.defaults()`: OBJ/FastOBJ, STL, PLY, OFF
- `MeshLoaders.planned()`: includes glTF/glb path while rollout continues
- glTF support includes skinning attributes (`JOINTS_0`, `WEIGHTS_0`) and morph targets

## Canonical Flow

```java
MeshData mesh = Meshes.builder(Topology.TRIANGLES)
    .schema(VertexSchema.standardSkinned())
    .build();

mesh = MeshPipeline.run(mesh,
    Ops.validate(),
    Ops.removeDegenerates(),
    Ops.weld(1e-6f),
    Ops.normals(60f),
    Ops.tangents(),
    Ops.optimizeVertexCache(),
    Ops.bounds()
);

PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
```

## Related Documentation

- `TECHNOLOGY_EXPLAINER.md`
- `README.md`
- `docs/meshforge-architecture.md`
- `docs/stress-guarantees.md`
