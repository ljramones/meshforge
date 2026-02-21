# Fixture Assets

This folder stores local mesh files used for loader integration and demo validation.

Structure:
- `obj/`, `stl/`, `ply/`, `off/`, `gltf/`
- each with `small/`, `medium/`, and `broken/`

Guidelines:
- `small`: tiny deterministic files for quick checks
- `medium`: representative real assets
- `broken`: malformed files to validate parser errors

See `docs/mesh-fixtures.md` for recommended public datasets.
