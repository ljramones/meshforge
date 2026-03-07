# MeshForge API

Last updated: 2026-02-23

This is the current API shape for MeshForge v1.
Canonical top-level API reference: `API.md`.

## Layers

1. Authoring: editable `MeshData`
2. Processing: `MeshPipeline` + `MeshOp`
3. Packing: immutable `PackedMesh`

## Core Types

### Authoring (`org.dynamisengine.meshforge.core.*`)
- `MeshData`
- `Topology`
- `Submesh`
- `VertexSchema`
- `VertexAttributeView`
- `MeshBuilder`
- `MeshWriter`
- `MorphTarget`

### Processing (`org.dynamisengine.meshforge.ops.*`)
- `MeshOp`
- `MeshPipeline`
- `MeshContext`

Common operation families:
- validate/repair
- generate (normals, tangents, bounds)
- modify (weld, compact, triangulate)
- optimize (cache reorder, meshlet cluster/order)

### Packing (`org.dynamisengine.meshforge.pack.*`)
- `PackSpec`
- `MeshPacker`
- `PackedMesh`
- `VertexLayout`
- `VertexBufferView`
- `IndexBufferView`

Implementation note:
- `PackSpec.LayoutMode.MULTI_STREAM` is declared.
- `MeshPacker` currently supports `INTERLEAVED` output only and fails fast for other layout modes.

## Convenience Facade (`org.dynamisengine.meshforge.api`)

- `Meshes`
- `Ops`
- `Pipelines`
- `Packers`

## Typical Flow

```java
MeshData mesh = Meshes.builder(Topology.TRIANGLES)
    .schema(VertexSchema.standardLit())
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

## Loader Notes

- `MeshLoaders.defaults()` uses the fast OBJ parser plus STL/PLY/OFF.
- glTF/glb loading is available through `MeshLoaders.planned()` and direct `GltfMeshLoader`.
- glTF skinning (`JOINTS_0`/`WEIGHTS_0`) and morph targets are supported.

## Stability Notes

- MeshForge core is renderer-API agnostic.
- `meshforge-demo` is an integration sandbox, not a compatibility contract.
