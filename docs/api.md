# MeshForge API (Basic)

This document describes the planned v1 API surface at a high level. It is intentionally basic and may evolve as implementation lands.

## API Layers

MeshForge is organized around three user-facing stages:

1. Authoring: create/edit `MeshData`
2. Processing: apply `MeshOp` pipelines
3. Packing: convert to immutable `PackedMesh`

## Primary Types

### Authoring (`org.meshforge.core.*`)
- `MeshData`: editable semantic mesh model
- `Topology`: primitive mode (TRIANGLES/LINES/POINTS)
- `Submesh`: index range and material association
- `VertexSchema`: declared attribute contract
- `VertexAttributeView`: typed/bulk attribute access
- `MeshBuilder`: ergonomic mesh construction
- `MeshWriter`: bulk/high-throughput construction

### Processing (`org.meshforge.ops.*`)
- `MeshOp`: single operation contract
- `MeshPipeline`: sequential op execution
- `MeshContext`: shared scratch/config for ops

Planned op categories:
- validate/repair
- generate (normals/tangents/bounds)
- modify (weld; triangulate planned)
- optimize (cache reorder/compaction)

### Packing (`org.meshforge.pack.*`)
- `PackSpec`: packing and compression policy
- `MeshPacker`: `MeshData` -> `PackedMesh`
- `PackedMesh`: immutable runtime mesh payload
- `VertexLayout`: consumer-facing format/offset/stride description
- `VertexBufferView` / `IndexBufferView`: packed buffer views

## Convenience Facade (`org.meshforge.api`)

Front-door classes keep common workflows simple:
- `Meshes`: creation helpers
- `Ops`: operation factories/pipeline helpers
- `Pipelines`: preset processing flows (`realtime`, `realtimeFast`)
- `Packers`: packing presets/helpers

## Intended Usage Flow

```java
MeshData mesh = Meshes.builder(Topology.TRIANGLES)
    .schema(VertexSchema.standardLit())
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

Preset form:

```java
mesh = Pipelines.realtimeFast(mesh);
PackedMesh packed = MeshPacker.pack(mesh, Packers.realtimeFast());
```

## Notes
- Core MeshForge stays renderer-API agnostic.
- `MeshData` favors flexibility; `PackedMesh` favors compact immutable runtime handoff.
- For design rationale and boundaries, see `docs/meshforge-architecture.md`.
- For internal authoring storage details (SoA layout, storage kinds, dirty flags), see `docs/internal-storage.md`.
- For realtime defaults and optimization priorities, see `docs/performance-profile.md`.
