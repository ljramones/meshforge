# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven Java library (`org.dynamisengine:meshforge`). Keep production code under `*/src/main/java/org/dynamisengine/meshforge` and tests under `*/src/test/java/org/dynamisengine/meshforge`.

- `src/test/resources/meshes/`: sample mesh fixtures (for example `cube.obj`).
- `target/`: build output, generated sources, and test reports; do not edit manually.

The codebase is organized as modules (`meshforge`, `meshforge-loader`, `meshforge-demo`), each with standard Maven source roots.

## Build, Test, and Development Commands
Use Maven from the repository root:

- `mvn clean test`: compile and run unit tests with preview features enabled.
- `mvn clean package`: build the JAR in `target/`.
- `mvn test -Dtest=ClassNameTest`: run a single test class.
- `mvn -Pbench test`: compile benchmark classes.
- `mvn -Pbench exec:exec`: run JMH benchmarks via the `bench` profile.

The project targets Java 25 with `--enable-preview`; match that JDK locally.

## Coding Style & Naming Conventions
Follow standard Java conventions:

- 4-space indentation, UTF-8, one public class per file.
- `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- Keep package names lowercase under `org.dynamisengine.meshforge` (for example `org.dynamisengine.meshforge.core`).
- Name tests as `*Test.java`; name benchmarks as `*Benchmark.java` in `.../bench`.

No formatter/linter plugin is currently configured in `pom.xml`; keep style consistent with surrounding code.

## Testing Guidelines
JUnit 5 (`junit-jupiter`) is the test framework. Prefer small, deterministic tests and reuse fixtures from `src/test/resources`.

- Place behavior/unit tests under `*/src/test/java/org/dynamisengine/meshforge/.../test`.
- Keep benchmark code under `*/src/test/java/org/dynamisengine/meshforge/.../bench` (JMH).
- Run `mvn clean test` before opening a PR.

No coverage gate is configured; aim to cover core algorithms and parsing paths.

## Commit & Pull Request Guidelines
Git history is not available in this workspace snapshot, so use Conventional Commits as the default:

- `feat: add OBJ face triangulation`
- `fix: handle empty vertex groups`
- `test: add SoA conversion edge cases`

For PRs, include:

- concise summary of behavior changes,
- linked issue/task,
- test evidence (command + result),
- benchmark comparison when performance-sensitive code changes.
