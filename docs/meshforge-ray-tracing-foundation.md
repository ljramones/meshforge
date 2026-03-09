# MeshForge Ray Tracing Foundation

This pass introduces a minimal MeshForge-side data foundation for roadmap item #7 (ray tracing support).

## Scope

Added only prepared-data and runtime-handoff metadata:

- RT-relevant geometry region metadata model
- GPU-ready RT metadata payload shape
- optional MGI chunk support for preserving RT region metadata
- runtime decode exposure for downstream systems

Deferred:

- BLAS/TLAS construction
- DynamisGPU-side acceleration-structure execution
- renderer integration and RT policy
- shader binding/material system expansion

## MeshForge Data Model

Added:

- `RayTracingGeometryRegionMetadata`
  - `submeshIndex`
  - `firstIndex`
  - `indexCount`
  - `materialSlot`
  - `flags`
  - flags include:
    - `FLAG_OPAQUE`
    - `FLAG_DOUBLE_SIDED`
- `RayTracingGeometryMetadata`
  - ordered, non-overlapping list of RT geometry regions

This keeps RT metadata aligned with existing submesh/index-range representation.

## Runtime Handoff Metadata

Added:

- `GpuRayTracingGeometryPayload`
  - v1 layout: 5 `int32` values per region
  - order: `submeshIndex`, `firstIndex`, `indexCount`, `materialSlot`, `flags`
- `RayTracingGeometryUploadPrep`
  - flattens `RayTracingGeometryMetadata` into GPU-ready payload bytes

This is a handoff seam only; no RT execution behavior is introduced.

## MGI Extension Hook

Added optional MGI support:

- new chunk type: `RAY_TRACING_REGIONS`
- new models:
  - `MgiRayTracingRegion`
  - `MgiRayTracingData`

Codec updates:

- `MgiStaticMeshCodec` encodes/decodes optional RT region chunk
- validation enforces:
  - submesh index bounds
  - index range bounds
  - ordered/non-overlapping regions

Mapping updates:

- `MgiMeshDataCodec` now includes optional RT metadata in `RuntimeDecodeResult`
- conversion path builds canonical RT region metadata from submesh ranges

## Tests Added/Updated

- `RayTracingGeometryMetadataTest`
- `RayTracingGeometryUploadPrepTest`
- `MgiRayTracingModelTest`
- `MgiStaticMeshCodecTest` (RT chunk round-trip)
- `MgiMeshDataCodecTest` (runtime decode exposes RT metadata)

## Outcome

MeshForge now preserves RT-relevant geometry metadata through:

`MeshData -> MGI -> runtime decode -> GPU-ready payload prep`

without introducing execution or policy concerns outside the intended subsystem boundary.

