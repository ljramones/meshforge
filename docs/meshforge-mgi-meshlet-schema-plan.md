# MeshForge MGI Meshlet Schema Plan

## Purpose
Define an implementation-ready, API-agnostic schema for storing prebaked meshlet metadata in MGI.

This schema must support:
- classic geometry path (no meshlet usage)
- meshlet-aware loading/culling paths
- future GPU-driven render paths

without embedding backend-specific command structures.

## Chunk Set
Meshlet support is introduced via optional chunks.

### `MESHLET_DESCRIPTORS`
Fixed-size descriptor array. One record per meshlet.

Responsibilities:
- cluster ownership/reference (submesh/material)
- offsets/counts into remap and triangle chunks
- bounds reference
- flags

### `MESHLET_VERTEX_REMAP`
Packed vertex remap payload.

Responsibilities:
- maps each meshlet-local vertex index to parent mesh vertex index
- supports local triangle indexing independent of backend draw API

### `MESHLET_TRIANGLES`
Packed local triangle payload.

Responsibilities:
- local triangle indices for each meshlet
- indexed against meshlet-local vertex list from remap chunk

### `MESHLET_BOUNDS`
Packed bounds payload for meshlets.

Responsibilities:
- per-meshlet bounds for frustum/visibility culling
- first implementation stores AABB min/max

### `MESHLET_METADATA` (optional, reserved)
Future extension placeholder.

Responsibilities (future):
- cull cones
- lod group ids
- parent/child cluster links
- streaming hints

Not required for first implementation.

## Descriptor Layout (Initial)
Recommended descriptor fields and primitive types:

- `uint32 submeshIndex`
- `uint32 materialSlot`
- `uint32 vertexRemapOffset`
- `uint32 vertexCount`
- `uint32 triangleOffset`
- `uint32 triangleCount`
- `uint32 boundsIndex`
- `uint32 flags`

Notes:
- `vertexRemapOffset` indexes into `MESHLET_VERTEX_REMAP` entries.
- `triangleOffset` indexes into `MESHLET_TRIANGLES` entry units.
- `boundsIndex` indexes into `MESHLET_BOUNDS` entries.
- `flags` reserved for future per-cluster options.

## Validation Rules
Reader/validator requirements:

### Chunk-level
- chunk sizes are aligned to expected element sizes
- required meshlet chunks for meshlet mode are all present together
- no chunk out-of-bounds or overlaps (existing global validation still applies)

### Descriptor-level
- descriptor count > 0 when meshlet chunks are present
- `submeshIndex` is within classic submesh table bounds
- `vertexCount > 0`
- `triangleCount > 0`

### Remap range checks
- `vertexRemapOffset + vertexCount <= remapEntryCount`
- each remap entry references a valid parent vertex index

### Triangle range checks
- `triangleOffset + triangleCount <= triangleEntryCount`
- each local triangle index < `vertexCount` for the owning descriptor

### Bounds checks
- `boundsIndex` within bounds entry count
- bounds data finite and valid (`min <= max` per axis)
- bounds entry count consistent with descriptor references

## Runtime Loading Model
### Classic path
- If meshlet chunks are absent: load canonical mesh only.
- Classic runtime remains unchanged.

### Meshlet-aware path
- If meshlet chunks are present and validate: expose meshlet metadata through decode/load helpers.
- Consumer decides whether to use meshlet-aware culling/render path.

No backend-specific command realization is stored in file format.

## Compatibility and Versioning
- Meshlet chunks are optional additions.
- Existing MGI assets without meshlet chunks remain valid.
- Reader behavior:
  - no meshlet chunks -> classic-only decode
  - valid meshlet chunks -> classic + meshlet metadata available
  - malformed meshlet chunks -> validation failure for that asset

Versioning guidance:
- keep chunk-based extension additive
- reserve `MESHLET_METADATA` for forward-compatible optional extras

## Non-Goals (First Implementation)
- no cone culling requirement
- no meshlet hierarchy / parent-child graph
- no LOD tree metadata
- no API-specific draw or dispatch structs
- no coupling to optional packed-payload path

## Implementation Sequence (Schema Scope)
1. Add meshlet model types (descriptor/bounds/container)
2. Add chunk read/write support for descriptor/remap/triangles/bounds
3. Add validation enforcing rules above
4. Add import-time generation/export path
5. Add meshlet-aware load path and benchmark comparisons

## Success Criteria
- Meshlet-enabled assets round-trip with descriptor/remap/triangle/bounds data intact.
- Classic MGI assets remain fully backward compatible.
- Validation catches malformed meshlet payloads deterministically.
- Runtime can detect meshlet metadata presence without requiring renderer coupling.
