# Stress Guarantees

This document captures fail-fast guardrails validated by stress/fuzz tests.

## Exception Contract

- Loader and parser stress paths are expected to fail with `IOException` or `IllegalArgumentException`.
- Unexpected exception types (for example `ArithmeticException`, `IndexOutOfBoundsException`) are treated as bugs.

## Large-Count Guardrails

- OFF/PLY loaders reject unsupported extreme `vertexCount` values before vertex buffer allocation.
- Binary STL rejects oversized triangle-count derived allocations with a clear `IOException`.
- glTF morph-target delta allocation uses overflow-safe sizing and reports overflow as `IOException`.
- glTF accessor metadata is validated before reads:
  - `count >= 0`
  - `byteOffset >= 0`
  - `stride >= packed element size`
  - accessor byte range must fit inside bufferView data
- `MeshWriter` rejects extreme `vertexCount`/`indexCount` sentinels (`Integer.MAX_VALUE`) before allocation.
- `MeshData` rejects pathological submesh-list sizes before any list copy (`submeshCount` hard limit).
- `MeshPacker` rejects missing `POSITION` targets and guards direct-buffer size multiplication with overflow-safe checks.

## Determinism

- Fuzz tests use fixed seeds for reproducibility.
- Any produced output from stress inputs is sanity-checked (non-negative counts/indices, bounded submesh ranges).

## Test Coverage Entry Points

- `meshforge-loader/src/test/java/org/meshforge/loader/LoaderStressFuzzTest.java`
- `meshforge/src/test/java/org/meshforge/test/MeshStressFuzzTest.java`
