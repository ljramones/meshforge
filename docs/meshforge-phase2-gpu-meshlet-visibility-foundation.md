# MeshForge Phase 2 GPU Meshlet Visibility Foundation

## Purpose
Introduce the minimal data and upload-preparation seam required for future GPU-driven meshlet visibility, without implementing compute visibility or draw integration yet.

## Phase 1 Baseline
Phase 1 is complete and provides:
- CPU frustum culling over prebaked meshlet bounds
- fixture harness and results (`RevitHouse`, `dragon`)
- measured triangle reduction with low culling overhead

This Phase 2 foundation builds on that baseline and only adds GPU-ready payload preparation.

## What This Phase Introduces

### 1) GPU Visibility Payload Model
`GpuMeshletVisibilityPayload` stores the minimal GPU-culling input payload metadata and bytes contract:
- `meshletCount`
- `boundsOffsetFloats`
- `boundsStrideFloats`
- `boundsPayload` (flattened float array)

Derived helpers:
- `boundsStrideBytes()`
- `boundsByteSize()`
- `toBoundsByteBuffer()` (little-endian direct buffer)

### 2) Upload-Prep Seam
`MeshletVisibilityUploadPrep.fromMeshletBounds(List<Aabbf>)` packs meshlet bounds into a GPU-uploadable flat representation.

Current layout (v1):
- per-meshlet element: 6 floats
- order: `minX, minY, minZ, maxX, maxY, maxZ`
- `boundsOffsetFloats = 0`
- `boundsStrideFloats = 6`

This gives a deterministic, mechanical bridge from runtime meshlet bounds to upload-ready payload bytes.

## Invariants
- zero meshlets -> empty payload, count=0, byte size=0
- non-empty -> payload length equals `meshletCount * boundsStrideFloats` (with offset accounted for)
- all entries are packed in strict meshlet index order
- no format/schema changes required

## Tests Added
The foundation includes focused tests for:
- payload model invariant checks
- zero/one/multiple meshlet packing
- layout correctness and byte-size/stride consistency

## Explicitly Deferred
This phase does **not** implement:
- compute visibility pass
- GPU-generated visible meshlet list
- indirect draw command integration
- cone/occlusion culling
- hierarchy/LOD
- streaming policy

## Next Step
Use this payload seam as the input contract for a minimal compute visibility prototype:
1. upload flattened bounds payload
2. run frustum visibility compute pass
3. produce visible meshlet index list on GPU
4. compare against CPU baseline before draw integration
