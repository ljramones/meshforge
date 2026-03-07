# Namespace Migration Note

Date: 2026-03-07

## Summary

MeshForge Java packages were migrated from:

- `org.meshforge`

to:

- `org.dynamisengine.meshforge`

Maven coordinates are now aligned to the Dynamis ecosystem:

- GroupId: `org.dynamisengine`
- Artifacts: `meshforge`, `meshforge-loader`, `meshforge-demo`

## Rationale

- Align MeshForge with sibling libraries (`DynamisCore`, `Vectrix`, `DynamisGPU`) under a shared namespace strategy.
- Standardize release flow with `org.dynamisengine:dynamis-parent`.
- Remove namespace ambiguity before Central publication.

## Scope

Completed changes:

- Package declarations/imports updated across `meshforge`, `meshforge-loader`, and `meshforge-demo` source trees.
- Source directories moved to `org/dynamisengine/meshforge/...` to match package declarations.
- Build/test/demo class references updated to new package names.
- Documentation and scripts updated to use the new namespace.

## Compatibility Impact

- This is a source-level breaking namespace change for downstream imports.
- Runtime behavior and algorithmic behavior are unchanged by the rename.

## Benchmarks and Historical Data

- Existing baseline files may still contain historical benchmark identifiers under `org.meshforge...`.
- Those entries are retained as historical labels and do not affect build or runtime behavior.

## Migration Guidance for Consumers

- Replace imports from `org.meshforge...` to `org.dynamisengine.meshforge...`.
- Update any tooling/scripts that reference old fully-qualified demo class names.
- Use Maven coordinates under `org.dynamisengine`.
