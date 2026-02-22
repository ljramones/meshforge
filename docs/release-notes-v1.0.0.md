# MeshForge v1.0.0 Release Notes

## Summary

MeshForge v1.0.0 is the stable baseline release for pure Java mesh manipulation:
- mesh authoring (`MeshData`, `VertexSchema`, builders/writers)
- mesh processing pipeline (`Ops`, `Pipelines`)
- immutable runtime packing (`PackedMesh`, `PackSpec`)
- meshlet clustering/ordering/descriptor packing
- pure-Java glTF/glb loader path with meshopt decode support (common modes)

## SIMD Status

SIMD acceleration is available in `pack` hot paths via the Vector API:
- octa normal packing (`OCTA_SNORM16x2`)
- snorm normal packing (`SNORM8x4`)

Runtime toggle:

```bash
-Dmeshforge.pack.simd.enabled=true|false
```

Default is `true`. Further SIMD tuning is intentionally deferred unless profiler data shows mesh packing as a dominant bottleneck.

## Boundary Statement

MeshForge remains renderer-agnostic and focused on mesh manipulation only. Rendering API integration and runtime dispatch belong in consumer engines.
