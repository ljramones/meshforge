# MeshForge Meshlet LOD Foundation

## Purpose

This document defines the MeshForge-side data foundation for roadmap item #3: Meshlet LOD.

Scope is intentionally limited to prepared-data representation and runtime handoff metadata.
It does not introduce LOD selection policy, GPU-side LOD execution, streaming, or renderer integration.

## Architectural Boundary

- MeshForge: owns geometry preparation and runtime-ready payload definition.
- DynamisGPU: owns upload/orchestration/execution.
- DynamisLightEngine: will own render-planning policy.

This pass keeps LOD in MeshForge as a prepared-data concept only.

## Data Model Added

MGI-side optional meshlet LOD metadata:

- `MgiMeshletLodLevel`
  - `lodLevel`
  - `meshletStart`
  - `meshletCount`
  - `geometricError`
- `MgiMeshletLodData`
  - ordered level list with invariant checks (strictly increasing levels, non-overlapping ordered ranges)

MeshForge runtime handoff metadata:

- `MeshletLodLevelMetadata`
- `MeshletLodMetadata`

These types describe how meshlets are partitioned into LOD levels and carry precomputed error metrics for later selection.

## Runtime Handoff Metadata

Runtime decode now exposes optional LOD metadata via `MgiMeshDataCodec.RuntimeDecodeResult`:

- raw optional `MgiMeshletLodData`
- derived optional MeshForge handoff `MeshletLodMetadata` via `meshletLodMetadataOrNull()`
- `meshletLodLevelCount()` convenience count

This keeps LOD metadata available without introducing runtime LOD policy logic in MeshForge.

## MGI Extension Hook

A new optional MGI chunk type is introduced:

- `MESHLET_LOD_LEVELS` (`0x1105`)

Chunk payload v1 is fixed-width per level:

1. `lodLevel` (int32)
2. `meshletStart` (int32)
3. `meshletCount` (int32)
4. `geometricError` (float32)

Rules:

- optional chunk
- valid only when meshlet descriptor payload exists
- ranges must remain within descriptor count

## GPU-Ready Upload Prep Seam (MeshForge-side only)

For later DynamisGPU consumption, MeshForge now includes a minimal LOD payload seam:

- `GpuMeshletLodPayload`
- `MeshletLodUploadPrep`

Layout v1 uses 4 int32 words per level:

1. `lodLevel`
2. `meshletStart`
3. `meshletCount`
4. `Float.floatToRawIntBits(geometricError)`

This is a handoff contract only; no upload or execution is performed here.

## Deferred Work

This pass intentionally defers:

- LOD selection policy
- GPU-side LOD selection execution
- renderer/frame-graph integration
- geometry streaming integration
- occlusion/hierarchy redesign

## Outcome

MeshForge now has a clear, tested, minimal Meshlet LOD data and runtime handoff foundation.

This sets up later DynamisGPU and renderer-side LOD selection/execution work without changing subsystem boundaries.
