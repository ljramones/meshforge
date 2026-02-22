# MeshForge v1 Plan (with Vectrix)

## Goal
Authoring `MeshData` -> Ops -> immutable `PackedMesh` suitable for high-throughput downstream consumption.

## Milestone 1: Authoring Core (MeshData)
### Tasks
- [x] `VertexSchema` (immutable, deterministic order)
- [x] `AttributeKey` (semantic + setIndex)
- [x] `VertexFormat` (minimal v1 set)
- [x] `VertexAttributeView` + primitive-array backed storage
- [x] `MeshData` (SoA-by-attribute, `int[]` indices, submeshes)
- [x] `MeshBuilder` (small procedural)
- [x] `MeshWriter` (bulk)

### Acceptance
- Can build a triangle and cube with position + indices
- Can add normal/uv/tangent attributes without per-vertex allocations
- Unit tests verify schema, attribute array sizing, and index preservation

## Milestone 2: Ops Pipeline
### Tasks
- [x] `MeshOp` interface + `MeshPipeline.run(...)`
- [x] `ValidateOp` (indices range, submesh range)
- [x] `ComputeBoundsOp` (Vectrix-compatible bounds model)
- [x] `RecalculateNormalsOp` (smooth)
- [x] `RecalculateTangentsOp` (basic, `w` handedness)

### Acceptance
- Bounds computed correctly for cube fixtures
- Normals are unit length within epsilon for cube/sphere fixtures
- Tangents exist and `w` is `+/-1`

## Milestone 3: First Runtime Pack
### Tasks
- [x] `PackSpec.debug()` (`F32` everything)
- [x] `PackSpec.realtime()` (compressed normal/tangent, half uv)
- [x] `VertexLayout` (stride + offsets)
- [x] `PackedMesh` (immutable runtime shape)
- [x] `MeshPacker.pack(...)` interleaved output

### Acceptance
- Packed stride/offsets match expected layout
- Vertex/index buffers are direct `ByteBuffer`
- Index type uses `UINT16` when possible, `UINT32` otherwise
- Integration test verifies packed cube layout/indices and deterministic output

## Milestone 4: Throughput and Size
### Tasks
- [x] `WeldVerticesOp` (epsilon, remap)
- [x] `CompactVerticesOp`
- [x] `OptimizeVertexCacheOp` (Forsyth)
- [x] Full realtime compression defaults enabled

### Acceptance
- ACMR improves on medium mesh benchmark fixture
- Vertex count drops after weld + compact where expected
- Realtime packed size is smaller than debug packed size

## Default Realtime Pipeline
`Validate -> RemoveDegenerates -> Weld -> Normals -> Tangents -> OptimizeVertexCache -> Bounds -> Pack(realtime)`
