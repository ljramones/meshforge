# MeshForge v1 Internal Storage

This document defines the v1 internal representation for `MeshData` and its relationship to packing.

## Canonical Decision
Use **SoA-by-attribute** as the canonical authoring layout.

- `MeshData` storage: one primitive array per attribute
- `PackedMesh` storage: interleaved or multi-stream per `PackSpec`

Rationale:
- maps well to mesh ops (normals, tangents, bounds, weld)
- avoids per-vertex object churn
- supports vectorization-friendly loops
- keeps packing conversion explicit at the authoring/runtime boundary

## MeshData Internal Shape

`MeshData` owns:
- `Topology topology`
- `VertexSchema schema`
- `int vertexCount`
- `IndexBuffer indices` (optional)
- `List<Submesh> submeshes`
- cached `Boundsf` + dirty flags
- `AttributeStore attributes`

### AttributeStore
Attributes are keyed by `(AttributeSemantic, setIndex)`.

Key recommendation:
- compact integer key: `(semanticId << 8) | setIndex` (v1 assumes `setIndex < 256`)
- map implementation: fast int-key map (for example fastutil) or equivalent internal map

## Attribute Storage Kinds

Use a small fixed set of backing types driven by `VertexFormat`:
- `FloatStorage` (`float[]`)
- `IntStorage` (`int[]`)
- `ShortStorage` (`short[]`)
- `ByteStorage` (`byte[]`)

Each storage defines:
- primitive array
- component count
- format metadata

Example (`F32x3`):
- `float[] data`
- `components = 3`
- element addressing: `data[vertexIndex * 3 + component]`

## VertexFormat Responsibilities

`VertexFormat` should describe:
- scalar kind (`FLOAT`/`INT`/`SHORT`/`BYTE`)
- component count (1..4)
- normalized flag (for integer encodings)
- packing semantics (snorm/unorm/half, etc.)
- byte size per element

v1 recommendation:
- keep mesh ops canonical on float formats
- defer compression/quantization to pack stage

## Canonical Authoring Formats (v1)
- `POSITION`: `F32x3`
- `NORMAL`: `F32x3`
- `TANGENT`: `F32x4` (`w` = handedness)
- `UVn`: `F32x2`
- `COLOR0`: `F32x4` (or `UNORM8x4` if required)
- `JOINTS0`: `I32x4` (authoring)
- `WEIGHTS0`: `F32x4` (authoring)

## Index Storage

Use `int[]` as authoring canonical index storage.

```java
final class IndexBuffer {
  final int[] data;
  final int count;
}
```

Pack stage decides `u16` vs `u32` for runtime buffers.

## VertexAttributeView Contract

Public API should expose views, not raw storage internals.

Expected methods:
- typed get/set (`get2f`, `get3f`, `get4f`, `set...`)
- optional advanced raw access (`rawFloatArray`, `rawIntArray`) clearly marked expert-use

## Dirty Flags and Caches

Track mutability/cached state in `MeshData`:
- `boundsDirty`
- optional `normalsDirty`
- optional `tangentsDirty`
- optional `indicesDirty`
- `vertexOrderDirty` (weld/reindex/optimize)

Ops are responsible for setting/clearing relevant flags.

## Optional Internal Kernel Views

For hot paths, expose internal lightweight wrappers (not separate storage), e.g.:
- `Positions3f` view over `float[]`
- `Normals3f` view over `float[]`

These avoid repeated format checks in tight loops while preserving a single backing store.

## Class Placement Guidance

Internal/package-private:
- `AttributeStore`
- key helper(s) for semantic/set keys
- `AttributeStorage` + typed implementations

Public:
- `MeshData`
- `VertexSchema`
- `VertexFormat`
- `VertexAttributeView`

## Boundary Summary

- `MeshData`: SoA-by-attribute authoring model
- `PackedMesh`: render-oriented packed model
- conversion occurs explicitly in pack stage
