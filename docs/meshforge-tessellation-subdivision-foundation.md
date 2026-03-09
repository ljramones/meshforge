# MeshForge Tessellation/Subdivision Foundation

Date: 2026-03-09

## Purpose

This phase adds a MeshForge-side data foundation for tessellation/subdivision so later systems can consume prepared metadata without recomputing geometry structure at runtime.

Scope is intentionally limited to metadata and runtime handoff shape.

## Data Model

Added optional MGI tessellation metadata:

- `MgiTessellationRegion`
- `MgiTessellationData`

Per region fields:

- `submeshIndex`
- `firstIndex`
- `indexCount`
- `patchControlPoints`
- `tessLevel`
- `flags`

Validation rules include:

- non-empty region list
- strictly increasing `submeshIndex`
- valid index ranges
- `patchControlPoints >= 3`
- finite positive `tessLevel`

## MGI Extension

Added optional chunk:

- `TESSELLATION_REGIONS` (`0x1301`)

`MgiStaticMeshCodec` now round-trips this chunk and validates ranges against mesh index/submesh data.

Backward compatibility is preserved because the chunk is optional.

## Runtime Handoff

`MgiMeshDataCodec.RuntimeDecodeResult` now exposes:

- raw optional MGI metadata: `tessellationDataOrNull`
- runtime metadata mapping: `tessellationMetadataOrNull()`

Added runtime handoff metadata types:

- `TessellationRegionMetadata`
- `TessellationMetadata`

Added CPU-side GPU handoff seam:

- `GpuTessellationPayload`
- `TessellationUploadPrep`

Payload layout v1 (int32 words per region):

1. `submeshIndex`
2. `firstIndex`
3. `indexCount`
4. `patchControlPoints`
5. `tessLevelBits` (`Float.floatToRawIntBits`)
6. `flags`

## Deferred Work

Explicitly deferred in this pass:

- GPU tessellation/subdivision execution
- adaptive tessellation policy
- renderer/frame-graph integration
- shader/material policy

This pass only establishes prepared-data contracts and runtime handoff shape.
