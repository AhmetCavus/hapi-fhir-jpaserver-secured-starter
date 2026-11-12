# Private Playground Resource Server policy

This fork implements the narrowly scoped, local/private OAuth learning exercise. It is not
a general FHIR permission system, SMART implementation or production security certification.
Only `GET /fhir/Basic/playground-demo` is an allowed data operation. The existing HAPI server
and DAO provide the resource; there is no second Resource Server.

## Required trust configuration

The corrected Spring Boot 3.5 OAuth2 Resource Server starter is retained. Security is always
active; the former placeholder `enable: false` has been removed. Missing or invalid trust
settings fail application startup. Configure both URLs from the same trusted Keycloak origin:

```sh
export FHIR_OIDC_ISSUER=https://keycloak.example/realms/epa-demo
export FHIR_OIDC_JWKS_URI=https://keycloak.example/realms/epa-demo/protocol/openid-connect/certs
```

Obtain `jwks_uri` from the trusted issuer's discovery document. No token-provided URL/key,
cross-origin JWKS or redirect is accepted. URLs cannot contain credentials, query strings,
fragments or traversal. The standard properties
`spring.security.oauth2.resourceserver.jwt.issuer-uri`, `jwk-set-uri` and `audiences` bind to
the validated trust record. The configured audience must be exactly `[playground-fhir]`;
it cannot be weakened to another audience. Decoder trust is checked at startup without
requiring a provider network call. Requests needing unavailable signing keys fail closed.
JWKS transport has five-second connect/read bounds and does not follow redirects.

HTTPS is supported; loopback HTTP is permitted for local tests. RFC1918 IPv4 HTTP requires
the explicit `PLAYGROUND_SECURITY_ALLOW_PRIVATE_HTTP=true` development setting. The parent
Compose definition maps existing `OIDC_ISSUER`, explicit `RES_SERVER_JWKS_URI` and the existing
private-HTTP opt-in to these HAPI inputs. Rebuilding source does not update existing containers.
No application cookie, HTTP Basic credential or browser session authenticates to HAPI.

## Supported Access Token and operation

The real Nimbus/Spring decoder checks a trusted RSA signature with RS256, exact issuer,
`playground-fhir` audience, required `exp`, and `nbf` when present. Timestamp tolerance is zero;
the local servers need synchronized clocks. Keycloak's signed payload `typ` must be `Bearer`,
and `scope`, if present, must be a string. ID Tokens, missing-purpose tokens, unsigned/HMAC/
other-algorithm tokens, unknown/forged keys and unsuitable ePA audiences are rejected.
An ePA client token without the resource audience grants no access.

The exact permission `demo.fhir.read` in `scope` becomes the required authority
`SCOPE_demo.fhir.read`. Missing permission is 403 after valid authentication. Only the literal
GET path, with no query string, is authorized. Other resources, search, writes, vread/history,
batch/transaction, operations, alternate encodings in paths, MCP/SSE, CDS hooks, tester/UI,
metadata/Swagger and other Actuator routes are denied. Malformed/noncanonical URLs can be
rejected by the servlet container/firewall before application authorization.

The application chain is stateless and accepts only bearer authentication. It does not use
cookie/form authentication, create sessions, cache saved requests or process logout. CSRF is
disabled only in this bearer-only chain; no data operation is opened as a result. All ordinary
unauthenticated data requests return 401 and authenticated undeclared operations return 403.

The existing dedicated health chain still permits only exact GET/HEAD
`/actuator/health/liveness` and `/actuator/health/readiness`. It contains no JWT filter, so
invalid/stale bearer headers cannot break probes. Responses contain status only. Other
methods and `/actuator/health` do not receive an anonymous exception.

## Explicit synthetic setup

`src/main/resources/demo/playground_demo.json` is an R4 Basic with a fictional code under
`example.invalid`, a non-clinical explanation, and no subject, author or patient content.
Set `PLAYGROUND_DEMO_SEED=true` only for deliberate local/test initialization. It uses HAPI's
internal DAO, not an HTTP write or anonymous seed endpoint. It creates the fixed logical ID
only when absent; identical content creates no new version and conflicting content fails
without overwriting data. Seeding is disabled by default and requires R4.

The parent's separate `epa-lab-integration` profile enables this seed against its disposable
tmpfs PostgreSQL database. It never mounts the existing HAPI database volume. Follow the
parent infrastructure guide for loopback-only publication and targeted cleanup; never use a
volume reset to initialize or roll back this exercise.

## Safe Resource Server decision report

| HTTP/security outcome                                 | X-Playground-Decision | X-Playground-Reason       |
| ----------------------------------------------------- | --------------------- | ------------------------- |
| Allowed demo read returning 200                       | `allowed`             | `demo-read-allowed`       |
| No bearer credentials, 401                            | `denied`              | `missing-token`           |
| Invalid credentials, 401                              | `denied`              | `invalid-token`           |
| Valid token without permission on the exact read, 403 | `denied`              | `insufficient-permission` |
| Valid token on an undeclared operation, 403           | `denied`              | `operation-not-allowed`   |

Headers report this Resource Server's own decision. They do not publish claims, exceptions,
internal check lists or a fabricated observation time. The allowed report is written only
for the authorized read returning 200; unrelated/error responses cannot acquire it. Health
probes have no resource-decision report. Servlet-container rejections may have no report;
the firewall reports only the fixed operation-denial reason. Denial bodies are empty and
the 401 challenge is the fixed `Bearer` value, without raw exception descriptions.

Reports use fixed enum values and no-store. Routine HAPI access logging uses constant messages
without request headers, URLs/queries, bodies or exception dumps. Security/web debug logging
is disabled in the checked-in logging configuration. Tokens, secrets and identity claims
are never teaching evidence; a caller must validate these bounded headers before projecting
them. A BFF's later observation is distinct from this explicit Resource Server report.

## Reproduce the focused checks

Use Java 21 from this repository directory. On checkouts where the wrapper lacks its execute
bit, invoke it through `sh` without changing file modes:

```sh
sh ./gradlew --no-daemon dependencyInsight --configuration compileClasspath --dependency spring-boot-starter-oauth2-resource-server compileJava
sh ./gradlew --no-daemon test --tests '*security.*Test' --tests '*demo.*Test' --tests 'HealthCheckTest*'
sh ./gradlew --no-daemon integrationTest --tests '*PlaygroundResourceSecurityIT'
sh ./gradlew --no-daemon bootWar
```

Tests use actual signed JWTs, trusted test JWKS and the production decoder/filter chain.
The integration suite runs embedded Tomcat, the real HAPI servlet and a disposable H2
database on loopback, including alternative-route and sensitive-log checks. The existing
health-only suite retains a mocked decoder to prove probes never invoke it; it is not the
evidence for JWT validation.

For the separate real-Keycloak check, start the parent's isolated integration profile first,
then run from the parent repository root with Node 24, Docker and its existing private `.env`:

```sh
PRIVATE_FHIR_BASE_URL=http://127.0.0.1:18083 node --env-file=.env services/res-server/scripts/verify-private-security.mjs
```

The script creates/deletes one fully fictional Keycloak identity, obtains actual standard
code/PKCE tokens with and without permission and an ePA token through PAR, and calls HAPI
directly. It never follows callbacks to the deployed BFF/SPA, changes client policy or prints
credentials. It verifies resource/probe responses and scans isolated HAPI stdout/stderr for
sensitive values. It requires the existing registered clients and private admin settings;
there is no mock fallback. This does not implement BFF resource access or SPA integration.
