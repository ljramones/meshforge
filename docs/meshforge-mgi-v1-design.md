# MeshForge MGI v1 Design

## 1. Purpose

MGI (MeshForge Geometry Ingest) is the canonical MeshForge ingest format between source import and runtime packing.

MGI goals:

- fast load
- simple validation
- deterministic structure
- stable, versioned, binary contract
- straightforward conversion from common source formats

## 2. Non-goals

MGI v1 is not intended to be:

- full scene serialization
- material/shader graph serialization
- animation system container
- editor project save format
- final GPU-ready packed-only blob

## 3. Pipeline Position

```text
OBJ / glTF / source formats
  -> meshforge-loader import adapters
  -> MGI writer
  -> MGI file
  -> MGI reader
  -> MeshForge runtime prep/pack
  -> GPU upload plan
```

## 4. Why MGI Exists

Recent runtime profiling shows full-path TTFU is dominated by source load/parse, while runtime-only prep is significantly lower.

MGI exists to:

- remove repeated source parse tax from runtime paths
- provide a deterministic engine-owned ingest boundary
- enable future precomputed metadata without source-format coupling

## 5. Format Principles

MGI v1 principles:

- binary
- chunked
- little-endian
- versioned from day one
- forward-compatible via chunk directory
- bulk payloads contiguous/aligned
- low-allocation reader path

## 6. v1 Scope

MGI v1 supports static triangle meshes with:

- positions
- normals
- tangents (if present)
- UV0 (and optional extra UV sets)
- optional vertex colors
- 16-bit or 32-bit indices
- submesh ranges
- bounds
- material slot IDs
- optional mesh/source names

## 7. Canonical Import Rules

Import-time canonicalization (enforced before writing MGI):

- winding order
- handedness convention
- unit scale policy
- coordinate basis expectations
- index width policy
- attribute semantic mapping

## 8. File Layout (v1)

```text
HEADER
CHUNK_DIRECTORY
MESH_TABLE
ATTRIBUTE_SCHEMA
VERTEX_STREAMS
INDEX_DATA
SUBMESH_TABLE
BOUNDS
METADATA
OPTIONAL_EXTENSION_CHUNKS
```

## 9. Header

Required header fields:

- magic (`MGI1`)
- format major/minor version
- flags
- chunk count
- chunk directory offset
- mesh count

## 10. Chunk Types

Required chunks:

- mesh table
- attribute schema
- vertex stream payload(s)
- index payload
- submesh table

Optional chunks:

- names/IDs
- source provenance
- import settings hash
- extension chunks (unknown chunks skipped by reader)

## 11. Attribute Schema

Each attribute descriptor includes:

- semantic
- set index
- component type
- component count
- normalized flag
- stream index
- stride bytes
- offset bytes

## 12. Validation Rules

Reader validates:

- header magic/version
- chunk bounds and overlap safety
- mesh/submesh index ranges
- attribute descriptor consistency
- index bounds against vertex counts

Validation failures are explicit and non-recoverable for that file.

## 13. Runtime Loading Model

MGI runtime read should be close to:

1. read header and chunk directory
2. validate structural invariants
3. read/map required bulk chunks
4. build canonical MeshForge runtime input
5. pass to runtime prep/pack and bridge

## 14. Module Plan

Recommended module split:

- `meshforge-loader`: source format import adapters
- `meshforge-mgi`: MGI schema, reader/writer, validation
- `meshforge-tools` (or CLI): conversion + inspect commands (phase 2)

## 15. Tooling Plan (v1)

Initial tooling targets:

- `obj -> mgi` converter
- `gltf -> mgi` converter
- `mgi-info` summary tool
- `mgi-validate` structural validator
- `mgi-dump` metadata/chunk inspector

## 16. Compatibility Strategy

- major version: breaking format changes
- minor version: additive chunk/schema changes
- unknown chunk IDs: ignored when marked optional

## 17. Future Extension Areas (Not v1)

- meshlets
- cluster metadata
- LOD chains
- adjacency streams
- skinning payloads
- GPU-driven draw metadata

These are additive extension chunks for future versions.

## 18. Initial Recommendation

Implement MGI v1 as:

- uncompressed, little-endian, chunked binary
- static mesh ingest first
- conversion path from OBJ first, glTF second
- benchmark full-path TTFU reduction against source-format ingestion

Status: DRAFT (Design Approved for Implementation Start)
