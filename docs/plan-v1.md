# MeshForge v1 Plan (with Vectrix)

## Goal
Authoring `MeshData` -> Ops -> `PackedMesh` suitable for real-time rendering.

## Milestone 1: Authoring Core (MeshData)
### Tasks
- [ ] `VertexSchema` (immutable, deterministic order)
- [ ] `AttributeKey` (semantic + setIndex)
- [ ] `VertexFormat` (minimal v1 set)
- [ ] `VertexAttributeView` + primitive-array backed storage
- [ ] `MeshData` (SoA-by-attribute, `int[]` indices, submeshes)
- [ ] `MeshBuilder` (small procedural)
- [ ] `MeshWriter` (bulk)

### Acceptance
- Can build a triangle and cube with position + indices
- Can add normal/uv/tangent attributes without per-vertex allocations
- Unit tests verify schema, attribute array sizing, and index preservation

## Milestone 2: Ops Pipeline
### Tasks
- [ ] `MeshOp` interface + `MeshPipeline.run(...)`
- [ ] `ValidateOp` (indices range, submesh range)
- [ ] `ComputeBoundsOp` (Vectrix AABB)
- [ ] `RecalculateNormalsOp` (smooth)
- [ ] `RecalculateTangentsOp` (basic, `w` handedness)

### Acceptance
- Bounds computed correctly for cube fixtures
- Normals are unit length within epsilon for cube/sphere fixtures
- Tangents exist and `w` is `+/-1`

## Milestone 3: First Renderer-Ready Pack
### Tasks
- [ ] `PackSpec.debug()` (`F32` everything)
- [ ] `PackSpec.realtime()` (compressed normal/tangent, half uv)
- [ ] `VertexLayout` (stride + offsets)
- [ ] `PackedMesh` (immutable runtime shape)
- [ ] `MeshPacker.pack(...)` interleaved output

### Acceptance
- Packed stride/offsets match expected layout
- Vertex/index buffers are direct `ByteBuffer`
- Index type uses `UINT16` when possible, `UINT32` otherwise
- Integration test uploads packed cube and renders correctly

## Milestone 4: Throughput and Size
### Tasks
- [ ] `WeldVerticesOp` (epsilon, remap)
- [ ] `CompactVerticesOp`
- [ ] `OptimizeVertexCacheOp` (Forsyth)
- [ ] Full realtime compression defaults enabled

### Acceptance
- ACMR improves on medium mesh benchmark fixture
- Vertex count drops after weld + compact where expected
- Realtime packed size is smaller than debug packed size

## Default Realtime Pipeline
`Validate -> RemoveDegenerates -> Weld -> Normals -> Tangents -> OptimizeVertexCache -> Bounds -> Pack(realtime)`
