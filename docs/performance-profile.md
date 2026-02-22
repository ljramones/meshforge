# MeshForge v1 Performance Profile

This document defines the implementation priority and realtime packing defaults for MeshForge v1.

## Implementation Priority

1. Canonical authoring model
- `MeshData` with schema + SoA-by-attribute arrays
- `MeshBuilder` + `MeshWriter`
- indexed triangle lists + `Submesh`

2. Mandatory correctness and cleanup ops
- `RemoveDegeneratesOp`
- `ValidateIndicesOp`
- `ComputeBoundsOp` (AABB + sphere)

3. Weld + compaction
- `WeldVerticesOp(epsilon)`
- `CompactVerticesOp`

4. Normals + tangents (rendering-ready)
- `RecalculateNormalsOp(angleThresholdDeg)`
- `RecalculateTangentsOp()` (v1 consistent basis; MikkTSpace-style can follow)

5. Vertex cache optimization
- `OptimizeVertexCacheOp` (Forsyth-style)
- optional v1.1: `OptimizeOverdrawOp`

6. Packing v1
- interleaved AoS `PackedMesh`
- `u16` vs `u32` index selection
- 16-byte stride alignment/padding

7. Compression v1
- normal/tangent -> `SNORM8x4` (or `SNORM16x4`)
- uv -> `F16x2`
- color -> `UNORM8x4`
- weights -> `UNORM8x4`
- joints -> `U8x4` or `U16x4`

8. Nice-to-have after v1
- multi-stream packing
- octahedral normals
- meshlet/cluster generation
- LOD generation

## `PackSpec.realtime()` Default

### Layout
- `INTERLEAVED` single stream by default
- `alignmentBytes = 16`
- optional stricter stride modes later (32/48/64)

### Indices
- prefer 16-bit indices
- do not force 32-bit by default
- fallback to 32-bit when any submesh exceeds 65535 unique vertices

### Attribute policy
Assuming float32 authoring attributes:
- `POSITION` -> `F32x3` (keep uncompressed in v1)
- `NORMAL` -> `SNORM8x4`
- `TANGENT` -> `SNORM8x4` (`w` = handedness)
- `UV0` -> `F16x2`
- `COLOR0` -> `UNORM8x4` (if present)
- `JOINTS0` -> `U8x4` or `U16x4` by policy
- `WEIGHTS0` -> `UNORM8x4` (renormalize on write)

### Derived and removed attributes
- do not store `BITANGENT`; reconstruct in shader from normal + tangent + sign
- drop unknown attributes by default unless `preserveUnknown` is enabled

### Pre-pack safety hooks
`PackSpec.realtime()` assumes ops already ran, but should remain safe:
- compute bounds when missing (`computeBoundsIfMissing = true`)
- normals required for lit material paths (default fail fast if missing)
- tangents required for normal-mapped paths (default fail fast if missing)

## Suggested `PackSpec` Shape

- `LayoutMode { INTERLEAVED, MULTI_STREAM }`
- `int alignmentBytes`
- `IndexPolicy { AUTO_16_IF_POSSIBLE, FORCE_32 }`
- `Map<AttributeKey, VertexFormat> targetFormats`
- `boolean dropUnknownAttributes`
- `boolean preserveUnknownAttributes`
- `boolean requireNormals`
- `boolean requireTangents`
- `boolean computeBoundsIfMissing`

## Realtime Preset Outcome

`PackSpec.realtime()` should produce a `PackedMesh` that is:
- bandwidth efficient
- cache friendly
- correct for PBR shading
- directly uploadable to rendering backends

## Non-Priorities (v1)

- half-edge topology
- advanced memory pooling
- default multithreaded packing
- strip-focused primitive workflows

## Microbenchmarks (JMH)

Run meshforge microbenchmarks:

```bash
mvn -pl meshforge -Pbench test-compile exec:java
```

Run a subset (example: packer benchmarks only):

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshPackerBenchmark.*'
```

Current benchmark classes:
- `org.meshforge.bench.OptimizeVertexCacheBenchmark`
- `org.meshforge.bench.MeshPackerBenchmark`
- `org.meshforge.bench.MeshPipelineBenchmark`
- `org.meshforge.bench.MeshOpsBenchmark`
- `org.meshforge.bench.MeshSizeScalingBenchmark`

Per-op hotspot pass:

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshOpsBenchmark.*'
```

Size scaling matrix (`cells` = 64, 128, 256):

```bash
mvn -pl meshforge -Pbench test-compile exec:java -Djmh.filter='.*MeshSizeScalingBenchmark.*'
```

## Optimize Cache Timeline

`OptimizeVertexCacheBenchmark.optimizeAndMeasureAcmr` trend on the same fixture:

- initial baseline: ~850 ms/op
- incremental cache-position update: ~400 ms/op
- heap candidate selection + score lookup tables + push gating: ~294-302 ms/op

Use this as a directional performance guardrail; exact values vary by machine/JVM.
