# MeshForge Release Plan

## Scope

- Old Java package root: `org.meshforge`
- New Java package root: `org.dynamisengine.meshforge`
- Target Maven groupId: `org.dynamisengine`
- Parent adoption target: `org.dynamisengine:dynamis-parent:1.0.0`
- Primary published artifacts: `meshforge`, `meshforge-loader`
- Internal-only artifact: `meshforge-demo` (deploy skipped)
- No push rule: do not push any branch/tag until all validation checks in this document pass.

## Repository Inventory (2026-03-07)

- Build layout: Maven multi-module (`meshforge-parent` aggregator)
- Modules:
  - `meshforge`
  - `meshforge-loader`
  - `meshforge-demo`
- Current package roots in source:
  - `org.meshforge.api`
  - `org.meshforge.core`
  - `org.meshforge.ops`
  - `org.meshforge.pack`
  - `org.meshforge.loader`
  - `org.meshforge.demo`
- `module-info.java`: not present
- `build.sh`/`deploy.sh`: not present at audit time (added in Phase 1)
- Tests/benchmarks:
  - Unit/integration tests present in `meshforge` and `meshforge-loader`
  - Benchmarks present under `meshforge/src/test/java/org/meshforge/bench`

## Dependency Audit

### DynamisCore

Current status:

- No `org.dynamisengine.core` dependencies.
- No direct imports or contracts from DynamisCore.

Adopt Core candidates:

- IDs for stable asset/model handles.
- Lifecycle/resource contract interfaces if MeshForge adopts shared runtime ownership semantics.
- Config/result abstractions only if they reduce duplicate local patterns.

Conclusion:

- Do not adopt DynamisCore in this migration pass.
- Revisit only when adding cross-library contracts that are already standardized in Core.

### Vectrix

Current status:

- Active usage in production code (`org.dynamisengine.vectrix.core.Vector3f`, `org.dynamisengine.vectrix.gpu.Half`, `org.dynamisengine.vectrix.gpu.OctaNormal`, `org.dynamisengine.vectrix.gpu.PackedNorm`).
- Usage spans tangent generation, normal packing, mesh packing, and meshopt decode paths.

Keep Vectrix candidates:

- Vector math for geometry/tangent workflows.
- Packing/conversion utilities (`Half`, octahedral normal pack/unpack).

Remove Vectrix candidates:

- None identified without replacing core mesh math/packing behavior.

Conclusion:

- Keep Vectrix as a required dependency.

### DynamisGPU

Current status:

- No `org.dynamisengine.gpu` dependencies.
- Demo module uses LWJGL/Vulkan directly, not DynamisGPU APIs.

Conclusion:

- Do not add DynamisGPU dependency in current release prep.
- Reassess only if MeshForge begins emitting/consuming DynamisGPU-native buffer/layout contracts.

## Phase Plan

## Phase 0

- [x] Create this plan document.
- [x] Inventory modules, package roots, parent/coordinates, scripts, tests/benchmarks.
- [x] Complete dependency audit with Core/Vectrix/GPU conclusions.

## Phase 1

- [x] Adopt `dynamis-parent` in root `pom.xml`.
- [x] Move Maven coordinates to `org.dynamisengine`.
- [x] Add Central-ready metadata (`name`, `description`, `url`, `licenses`, `developers`, `scm`).
- [x] Mark `meshforge-demo` as non-deployable (`maven.deploy.skip=true`).
- [x] Add `build.sh` and `deploy.sh` scripts.
- [x] Validate:
  - `mvn -q clean test`
  - `mvn -q -DskipTests package`
  - `mvn -q -Prelease -DskipTests package`

## Phase 2

- [x] Rename Java package root to `org.dynamisengine.meshforge` across main/test/bench sources.
- [x] Move source directories to match updated package declarations.
- [x] Update all imports/usages.
- [x] Update `module-info.java` (if introduced).
- [x] Validate and grep leftovers:
  - `mvn -q clean test`
  - `mvn -q -DskipTests package`
  - `rg "org\.meshforge|org\.dynamis\.meshforge"`
  - Note: remaining `org.meshforge` matches are limited to historical references (for example perf baselines) and migration notes.

## Phase 3

- [ ] Apply any justified Core adoption (smallest slice only).
- [ ] Keep/remove Vectrix explicitly with rationale.
- [ ] Keep DynamisGPU out unless GPU layout API coupling appears.
- [ ] Validate:
  - `mvn -q clean test`
  - `mvn -q -DskipTests package`

## Phase 4

- [x] Update `README.md` for new namespace/coordinates/release flow.
- [x] Add `docs/namespace-migration-note.md`.
- [x] Refresh tooling/docs references (`AGENTS.md`, scripts, checks).

## Phase 5

- [ ] Publish-readiness verification:
  - `mvn -q clean test`
  - `mvn -q -DskipTests package`
  - `mvn -q -Prelease clean verify`
- [ ] If needed, isolate javadoc/plugin issues:
  - `mvn -q -DskipTests -Dmaven.javadoc.skip=true package`
- [ ] Final staging deploy:
  - `./deploy.sh`

## Deliverables

- [x] `docs/meshforge-release-plan.md`
- [x] `docs/namespace-migration-note.md`
- [x] `README.md` update
- [x] `build.sh`
- [x] `deploy.sh`
- [ ] optional: `docs/meshforge-dependency-audit.md`

## Suggested Commit Boundaries

1. Planning + dependency audit docs.
2. Maven parent/coordinate/release metadata + scripts.
3. Namespace migration (packages/imports/layout).
4. Dependency realignment decisions.
5. Docs refresh.
6. Final publish-readiness fixes and verification notes.
