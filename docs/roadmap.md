# MeshForge Roadmap

Last updated: 2026-02-23

## Completed (v1 foundation)

- Core authoring model (`MeshData`, schema, builder/writer)
- Loader hardening for OBJ/STL/PLY/OFF and glTF/glb geometry path
- Skinning + morph target ingestion for glTF
- Boundary-safe weld with regression coverage
- Submesh-safe index mutation across affected ops
- Realtime pack fast path and validated perf gate
- Stress/fuzz suites and large-count guardrails

## Near-term follow-ups

- Multi-stream pack mode implementation (`PackSpec.LayoutMode.MULTI_STREAM`)
- Additional benchmark coverage for loader/parser variants
- Optional weld key policy extensions (for seam-preserving workflows)

## v1.x quality upgrades

- Tangent basis compatibility mode improvements
- Optional overdraw optimization pass tuning
- Additional pack profiles (`mobile`, content-class specific presets)

## Later

- Meshlet/cluster runtime export enhancements
- LOD/decimation integration
- Loader expansion for currently unsupported planned formats
- Optional off-heap/arena-backed packed storage ownership modes
