# MeshForge MGI Meshlet Extension Plan

## Purpose
Add API-agnostic meshlet metadata to MGI so one asset can support:
- classic indexed rendering
- CPU/compute meshlet culling
- future mesh/task-shader style pipelines

without changing file format semantics per render backend.

## Why Now
- MGI solved source ingest tax
- trusted fast path solved runtime topology canonicalization tax
- Task 6 showed copy floor is already sub-millisecond even for RevitHouse-scale payloads
- meshlet prototype showed meaningful culling signal (~45-50% triangle reduction) but expensive generation (~19-21 ms), so generation belongs at import time

## Core Principle
Store meshlets as stable cluster metadata, not API command blobs.

MGI meshlet extension should store:
- cluster descriptors
- local vertex remap data
- local triangle data
- cluster bounds

and remain independent of Vulkan/OpenGL/mesh-shader command formats.

## Proposed Optional Chunk Set
- `MESHLET_DESCRIPTORS`
- `MESHLET_VERTEX_REMAP`
- `MESHLET_TRIANGLES`
- `MESHLET_BOUNDS` (optional if bounds are inline)
- `MESHLET_METADATA` (optional future extras: cone culling, LOD group, flags)

## Descriptor Minimum Fields
Each descriptor should minimally include:
- `submeshIndex`
- `materialSlot` (or stable material reference)
- `vertexRemapOffset`
- `vertexCount`
- `triangleOffset`
- `triangleCount`
- `boundsRef` (or inline bounds)
- `flags`

## Non-Goals (Initial Extension)
- backend-specific dispatch command storage
- full mesh shader pipeline integration
- hierarchical meshlet trees
- full LOD graph design
- runtime meshlet generation on hot path

## Compatibility Strategy
- meshlet chunks are optional
- files without meshlet chunks remain valid and use classic path
- runtime selects meshlet path only when meshlet chunks validate
- canonical geometry remains present as fallback/debug path

## Runtime Selection Model
At activation:
1. If meshlet chunks are present and valid -> meshlet-aware path may be used
2. Else -> classic canonical path

This preserves backward compatibility and avoids brittle coupling.

## Validation Requirements
Reader/validator should enforce:
- descriptor offsets/counts in bounds
- remap payload ranges valid
- triangle payload ranges valid
- local triangle indices within meshlet-local vertex count
- submesh/material references valid

## Implementation Order
1. Define chunk schemas + validation rules (docs/spec)
2. Import-time generation + storage of descriptors/remap/triangles/bounds
3. Runtime load prototype with frustum culling and visible-triangle metrics
4. Renderer integration (classic/indirect first)
5. Optional later metadata (cones, LOD relations, streaming hints)

## Success Criteria
- meshlet metadata round-trips in MGI without breaking classic path
- runtime can consume meshlets without runtime generation
- measurable visibility reduction on representative fixtures (RevitHouse, dragon)
- no API-specific command format leak into MGI

## Relationship to Packed Payload Note
This plan complements, but does not require, optional packed runtime payload chunks.
- packed payload targets activation overhead
- meshlet metadata targets representation/culling efficiency

Both can coexist as optional extensions while canonical MGI remains baseline.
