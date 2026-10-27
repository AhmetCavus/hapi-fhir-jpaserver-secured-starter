# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot entry point `ca.uhn.fhir.jpa.starter.Application`, resource providers, config.
- `src/main/resources`: Application YAML, search parameter bundles, capability statements packaged with the WAR.
- `src/main/webapp`: HAPI Testpage overlay shipped for the default UI.
- `src/test/java` & `src/test/resources`: JUnit 5 suites (interceptors, MCP, MDM) plus matching fixtures grouped by FHIR version.
- `charts/`, `docker-compose.yml`, `configs/`: Deployment templates for Helm, Docker, and Tomcat/server overrides.
- `Dockerfile`, `build-docker-image.sh`: Reference container build scripts used by CI/CD.

## Build, Test, and Development Commands
- `./gradlew clean build`: Compile, check formatting, run unit/integration tests, and emit `build/libs/ROOT.war`.
- `./gradlew bootRun`: Start Spring Boot with embedded Tomcat on port 8080.
- `./gradlew bootWar` then `java -jar build/libs/ROOT.war`: Build and exercise the executable WAR without tests.
- On Windows, use `.\gradlew.bat` in place of `./gradlew`.
- `docker-compose up -d --build`: Launch JPAServer + PostgreSQL using the local Dockerfile.
- `docker run -p 8080:8080 hapiproject/hapi:latest`: Compare against the upstream binary distribution.

## Coding Style & Naming Conventions
- Target Java 21, four-space indents, alphabetized imports, no wildcards.
- Keep code under `ca.uhn.fhir.jpa.starter` and mirror packages in tests.
- Prefer descriptive class suffixes (`*Provider`, `*Service`, `*Config`) and constructor injection with `final` collaborators.
- YAML keys stay kebab-case; JSON fixtures use lower_snake_case filenames.

## Testing Guidelines
- `./gradlew test`: Runs JUnit Jupiter `*Test` unit suites such as `CustomBeanTest` and `ParallelUpdatesVersionConflictTest`.
- `./gradlew integrationTest`: Runs `*IT` suites with the default H2 datasource; if you pivot to PostgreSQL, use `./gradlew bootWar` until fixtures are updated.
- `./gradlew build`: Runs both suites, formatting checks, and JaCoCo reporting. Some integration suites require Docker.
- Store integration suites as `*IT.java` and colocate datasets in `src/test/resources`.
- Leverage Testcontainers and HAPI FHIR test utilities already declared in `build.gradle.kts`.

## Commit & Pull Request Guidelines
- Follow repository precedent: imperative summary, optional scope (`Feature/mcp`), and linked issue `(#123)` when applicable.
- Keep commits narrowly scoped and include config or fixture updates with the code they support.
- PRs should describe runtime impact (profiles, ports, env vars), reference issues, and include UI screenshots when behaviour changes.
- Run `./gradlew build` or the relevant Docker workflow before review; note any skipped checks and how to reproduce the result.

## Security & Configuration Tips
- Do not commit secrets; stash overrides under `configs/` and explain required env vars in the PR.
- When enabling external services, update `src/main/resources/application.yaml` plus sample overrides and mention connection expectations for reviewers.
