# Docs Index

Last updated: 2026-03-09

## Core

- `API.md` - top-level public API reference
- `TECHNOLOGY_EXPLAINER.md` - architecture rationale and integration guide
- `docs/meshforge-v1-architecture.md` - v1 runtime geometry subsystem summary and canonical contracts
- `docs/api.md` - current API shape and usage flow
- `docs/meshforge-architecture.md` - authoring/runtime model split and boundaries
- `docs/internal-storage.md` - internal data representation
- `docs/boundaries.md` - cross-library boundary rules
- `docs/adr/0001-library-boundaries.md` - accepted boundary ADR
- `docs/adr/0002-runtime-geometry-layout.md` - accepted runtime layout ADR

## Performance

- `docs/performance-profile.md` - measurement rules and profiler workflow
- `docs/perf-baseline.md` - baseline policy and tracked benchmark values
- `perf/baseline.csv` - perf-gate input

## Loaders and Fixtures

- `docs/loader-roadmap.md` - implemented vs placeholder loader formats
- `docs/mesh-fixtures.md` - fixture sources and repository layout
- `docs/runtime-geometry-cache-lifecycle.md` - `.mfgc` placement, validation, and rebuild policy
- `docs/runtime-geometry-flow.md` - canonical loader -> payload -> upload-plan engine flow
- `docs/stress-guarantees.md` - stress/fuzz guardrails and failure contract

## Planning / History

- `docs/meshforge-mgi-v1-design.md` - canonical MeshForge Geometry Ingest (MGI) v1 format design
- `docs/meshforge-mgi-performance-results.md` - measured OBJ vs MGI vs runtime-only activation results and conclusions
- `docs/meshforge-meshlets-v1-plan.md` - meshlets v1 prototype goals, non-goals, and benchmark plan
- `docs/meshforge-mgi-fastpath-plan.md` - trusted runtime fast path design and phased rollout
- `docs/meshforge-mgi-meshlet-extension-plan.md` - meshlet extension architecture and chunk model
- `docs/meshforge-mgi-meshlet-benchmark-results.md` - runtime generation vs prebaked meshlet load results
- `docs/meshforge-capability-priority.md` - geometry subsystem capability priority roadmap

- `docs/roadmap.md` - active and next priorities
- `docs/plan-v1.md` - historical v1 completion snapshot
- `docs/release-notes-v1.0.0.md` - v1 release notes
- `docs/v2-clustered-runtime-contract.md` - v2 clustered runtime contract draft
- `docs/v2-clustered-cache-strategy.md` - v2 clustered cache strategy draft
- `docs/v2-lod-chunking-outline.md` - v2 LOD/chunking phased outline
