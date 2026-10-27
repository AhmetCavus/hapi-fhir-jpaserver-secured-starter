# HAPI FHIR JPA Server — Secured Starter

> **Project status: Work in progress.** The migration to Gradle (Kotlin DSL) and Java 21 is complete. The Spring Security integration described below is **planned, not yet implemented**. The current server should not be treated as a secured deployment.

This repository is a community-maintained fork of the official [HAPI FHIR JPA Server Starter](https://github.com/hapifhir/hapi-fhir-jpaserver-starter). It builds on the HAPI FHIR JPA Server to provide a starting point for FHIR applications with an additional, standards-based security layer.

The underlying HAPI FHIR framework is maintained separately in the [main HAPI FHIR repository](https://github.com/hapifhir/hapi-fhir). This fork is an independent community effort, not an official HAPI FHIR distribution.

## What's different from upstream?

| Area | This fork | Status |
| --- | --- | --- |
| Build system | Gradle with Kotlin DSL (`build.gradle.kts`) instead of Maven | Implemented |
| Java | Java 21 toolchain | Implemented |
| Security | Spring Security with OAuth 2.0 Resource Server support | Planned |
| Reusability | Security components intended for reuse beyond this starter | Design goal |

The application and test sources remain Java; Kotlin DSL is used for the Gradle build configuration.

## Project goals

The main goal is to offer a **FHIR server starter with a reusable security layer**, reducing the effort needed to add authentication and authorization to FHIR-based systems.

The planned approach separates responsibilities:

- **Authentication and token validation:** Use Spring Security's OAuth 2.0 Resource Server support to validate bearer access tokens against a configurable authorization server (for example, Keycloak).
- **FHIR-specific authorization:** Enforce resource-level access rules using HAPI FHIR's authorization mechanisms. A valid access token alone must not grant unrestricted access to patient data.
- **Reusability:** Keep the security integration modular and configurable so that its components can be adapted to other Spring-based applications and FHIR deployments, rather than being tightly coupled to this fork.

### Roadmap

1. **Phase A — Generic OAuth 2.0 protection:** Integrate Spring Security, validate access tokens, enforce access rules, and provide automated security tests and example configuration.
2. **Phase B — SMART on FHIR:** Add SMART-specific discovery, scopes, context handling, and interoperability tests.

### Security and production readiness

**Security is not implemented in this fork yet.** Do not expose the current server to untrusted networks or use it to store real patient data on the assumption that it is protected by this project. Even after security features are introduced, production deployments will require independent reviews and environment-specific hardening, auditing, and operational controls.

The starter also inherits other upstream considerations, including enterprise audit logging and multi-instance subscription infrastructure. See the [HAPI FHIR security documentation](https://hapifhir.io/hapi-fhir/docs/security/introduction.html).

## Getting started

This fork uses the checked-in Gradle wrapper and requires **JDK 21** for local builds. See the build instructions below.

**Docker note:** Commands referencing `hapiproject/hapi` use the **upstream HAPI FHIR image**, not an image published from this fork. To run this fork, build it from source or build the local Docker image.

---

## Additional starter documentation

The following sections retain the starter's setup, build, configuration, and deployment guidance, updated for Gradle where applicable.

## Prerequisites

In order to use this sample, you should have:

- [This project](https://github.com/hapifhir/hapi-fhir-jpaserver-starter) checked out. You may wish to create a GitHub Fork of the project and check that out instead so that you can customize the project and save the results to GitHub.

### and either
 - JDK 21 installed and selected through `JAVA_HOME`.
 - The checked-in Gradle wrapper; a separate Gradle installation is not required.

### or
 - Docker, as the entire project can be built using multistage Docker (with both JDK and Gradle inside Docker) or used directly from [Docker Hub](https://hub.docker.com/r/hapiproject/hapi)

## Building with Gradle

The build uses Kotlin DSL in `build.gradle.kts`. Application and test sources remain Java.
Spring Boot with embedded Tomcat is the supported runtime; no build profile is required.

On Windows, use `.\gradlew.bat` in place of `./gradlew` below.
On Linux and macOS, run `chmod +x gradlew` once after checkout if necessary.

| Command | Purpose |
| --- | --- |
| `./gradlew bootRun` | Start Spring Boot on port 8080. |
| `./gradlew clean build` | Compile, package, check formatting, and run unit and integration tests with coverage. |
| `./gradlew test` | Run `*Test` unit suites, matching the upstream build's test selection. |
| `./gradlew integrationTest` | Run `*IT` integration tests; some require Docker. |
| `./gradlew bootWar` | Build `build/libs/ROOT.war` without running tests. |
| `./gradlew jacocoTestReport` | Run both test suites and write HTML/XML coverage under `build/reports/jacoco/test`. |
| `./gradlew spotlessCheck` / `./gradlew spotlessApply` | Check or apply source formatting. |

Run the packaged application with `java -jar build/libs/ROOT.war`.
The `bootRun` task builds and launches this WAR so the complete FHIR Tester overlay is available during development.
Once startup completes, the application prints `Started successfully` and links to the UI and FHIR metadata.
The links use the actual server port, context path, and HTTP/HTTPS configuration.
Gradle uses plain console output by default; `bootRun` stays active until the server stops.
To run a single test, use `./gradlew test --tests '*CustomBeanTest'`.
To skip both test suites during a full build, use `./gradlew build -x test -x integrationTest -x jacocoTestReport`.

Dependency versions and build options are in `gradle.properties` and can be overridden using `-P`,
for example `./gradlew bootWar -PjavaVersion=21`.
The former Cloud SQL option is available as `./gradlew bootRun -PcloudSqlPostgres=true`.
Application settings still belong in `src/main/resources/application.yaml` or Spring configuration overrides.

If HTTPS inspection on Windows prevents Java from trusting the dependency repositories, use the certificates
already trusted by Windows for the current PowerShell session before running Gradle:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

## Running the upstream reference image via [Docker Hub](https://hub.docker.com/r/hapiproject/hapi)

The commands in this section run the upstream HAPI FHIR distribution, **not this fork or its planned Spring Security integration**. They are useful for comparison with upstream behavior. To run this fork, build the application or its Docker image locally.

To run the upstream image from Docker Hub:

```
docker pull hapiproject/hapi:latest
docker run -p 8080:8080 hapiproject/hapi:latest
```

This will run the docker image with the default configuration, mapping port 8080 from the container to port 8080 in the host. Once running, you can access `http://localhost:8080/` in the browser to access the HAPI FHIR server's UI or use `http://localhost:8080/fhir/` as the base URL for your REST requests.

If you change the mapped port, you need to change the configuration used by HAPI to have the correct `hapi.fhir.tester` property/value.

### Configuration via environment variables

You can customize HAPI directly from the `run` command using environment variables. For example:

```
docker run -p 8080:8080 -e hapi.fhir.default_encoding=xml hapiproject/hapi:latest
```

HAPI looks in the environment variables for properties in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file for defaults.

### CORS configuration (including ETag/If-Match)

The starter CORS configuration now supports the following configurable keys:

- `hapi.fhir.cors.allowed_origin`
- `hapi.fhir.cors.allow_Credentials`
- `hapi.fhir.cors.allowed_headers`
- `hapi.fhir.cors.exposed_headers`
- `hapi.fhir.cors.allowed_methods`

Defaults include `If-Match` in allowed headers and `ETag` in exposed headers to support browser-based optimistic locking workflows.
The `allowed_headers`, `exposed_headers`, and `allowed_methods` keys are optional; if omitted, built-in defaults are applied.
The default for `allow_Credentials` is `false`. If you set `allow_Credentials=true`, do not use `"*"` for `allowed_origin`; configure explicit origins.

Example override file:

```yaml
hapi:
  fhir:
    cors:
      allowed_origin:
        - "http://localhost:3000"
      allowed_headers:
        - Origin
        - Accept
        - Content-Type
        - Authorization
        - Cache-Control
        - If-Match
        - If-None-Match
      exposed_headers:
        - Location
        - Content-Location
        - ETag
```

### Binary storage configuration

To stream large `Binary` payloads to disk instead of the database, configure the starter with filesystem storage properties:

```
hapi:
  fhir:
    binary_storage_enabled: true
    binary_storage_mode: FILESYSTEM
    binary_storage_filesystem_base_directory: /binstore
    # inline_resource_storage_below_size: 131072   # optional override
```

When `binary_storage_mode` is set to `FILESYSTEM` and `inline_resource_storage_below_size` is omitted, the starter automatically applies a 102400 byte (100 KB) inline threshold so smaller payloads remain in the database. Ensure the directory you point to is writable by the process (for Docker builds, mount it into the container with appropriate permissions).

### Configuration via overridden application.yaml file and using Docker

You can customize HAPI by telling HAPI to look for the configuration file in a different location, e.g.:

```
docker run -p 8090:8080 -v $(pwd)/yourLocalFolder:/configs -e "--spring.config.location=file:///configs/another.application.yaml" hapiproject/hapi:latest
```
Here, the configuration file (*another.application.yaml*) is placed locally in the folder *yourLocalFolder*.

```
docker run -p 8090:8080 -e "--spring.config.location=classpath:/another.application.yaml" hapiproject/hapi:latest
```
Here, the configuration file (*another.application.yaml*) is part of the compiled set of resources.

### Configuration with additional override files

You can layer additional configuration files on top of the default application.yaml while preserving all the base settings. This approach allows you to create specific override files for different environments without duplicating the entire configuration.

```bash
# Using Gradle
./gradlew bootRun --args='--spring.config.additional-location=classpath:your-overrides.yaml'

# Using Docker
docker run -p 8080:8080 -e "--spring.config.additional-location=classpath:your-overrides.yaml" hapiproject/hapi:latest
```

Here, the additional configuration file (*your-overrides.yaml*) contains only the specific properties you want to override or add, while all default values from application.yaml remain unchanged.

### One-liner for quickly getting an Implementation Guide installed into HAPI

```
docker run -p 8080:8080 -e "hapi.fhir.implementationguides.someIg.name=com.org.something" -e "hapi.fhir.implementationguides.someIg.version=1.2.3" -e "hapi.fhir.implementationguides.someIg.packageUrl=https://build.fhir.org/ig/yourOrg/yourIg/package.tgz" -e "hapi.fhir.implementationguides.someIg.installMode=STORE_AND_INSTALL" hapiproject/hapi:latest
```

### Example using ``docker-compose.yml`` for docker-compose

```yaml
version: '3.7'

services:
  fhir:
    container_name: fhir
    image: "hapiproject/hapi:latest"
    ports:
      - "8080:8080"
    configs:
      - source: hapi
        target: /app/config/application.yaml
    depends_on:
      - db


  db:
    image: postgres
    restart: always
    environment:
      POSTGRES_PASSWORD: admin
      POSTGRES_USER: admin
      POSTGRES_DB: hapi
    volumes:
      - ./hapi.postgress.data:/var/lib/postgresql/data

configs:
  hapi:
     file: ./hapi.application.yaml
```

Provide the following content in ``./hapi.application.yaml``:

```yaml
spring:
  datasource:
    url: 'jdbc:postgresql://db:5432/hapi'
    username: admin
    password: admin
    driverClassName: org.postgresql.Driver
  jpa:
    properties:
      hibernate.dialect: ca.uhn.fhir.jpa.model.dialect.HapiFhirPostgresDialect
      hibernate.search.enabled: false
```

### Example running custom interceptor using docker-compose

This example is an extension of the above one, now adding a custom interceptor.

```yaml
version: '3.7'

services:
  fhir:
    container_name: fhir
    image: "hapiproject/hapi:latest"
    ports:
      - "8080:8080"
    configs:
      - source: hapi
        target: /app/config/application.yaml
      - source: hapi-extra-classes
        target: /app/extra-classes
    depends_on:
      - db

  db:
    image: postgres
    restart: always
    environment:
      POSTGRES_PASSWORD: admin
      POSTGRES_USER: admin
      POSTGRES_DB: hapi
    volumes:
      - ./hapi.postgress.data:/var/lib/postgresql/data

configs:
  hapi:
     file: ./hapi.application.yaml
  hapi-extra-classes:
     file: ./hapi-extra-classes
```

Provide the following content in ``./hapi.application.yaml``:

```yaml
spring:
  datasource:
    url: 'jdbc:postgresql://db:5432/hapi'
    username: admin
    password: admin
    driverClassName: org.postgresql.Driver
  jpa:
    properties:
      hibernate.dialect: ca.uhn.fhir.jpa.model.dialect.HapiFhirPostgresDialect
      hibernate.search.enabled: false
hapi:
  fhir:
    custom-bean-packages: the.package.containing.your.interceptor
    custom-interceptor-classes: the.package.containing.your.interceptor.YourInterceptor
```

The basic interceptor structure would be like this:

```java
package the.package.containing.your.interceptor;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;

@Component
@Interceptor
public class YourInterceptor
{
    @Hook(Pointcut.STORAGE_PRECOMMIT_RESOURCE_CREATED)
    public void resourceCreated(IBaseResource newResource)
    {
        System.out.println("YourInterceptor.resourceCreated");
    }
}
```

## Running locally

Run the application with Spring Boot using Gradle or the executable WAR.

### Using Spring Boot
```bash
./gradlew bootRun
```

The Server will then be accessible at http://localhost:8080/fhir and the CapabilityStatement will be found at http://localhost:8080/fhir/metadata.

If you want to run this server on a different port, you can change the port in the `src/main/resources/application.yaml` file as follows:

```yaml
server:
#  servlet:
#    context-path: /example/path
  port: 8888
```

The Server will then be accessible at http://localhost:8888/fhir and the CapabilityStatement will be found at http://localhost:8888/fhir/metadata. Remember to adjust your overlay configuration in the `application.yaml` file to the following:

```yaml
    tester:
      -
          id: home
          name: Local Tester
          server_address: 'http://localhost:8888/fhir'
          refuse_to_fetch_third_party_urls: false
          fhir_version: R4
```

### Starting from a clean build
```bash
./gradlew clean bootRun
```
Server will then be accessible at http://localhost:8080/ and e.g. http://localhost:8080/fhir/metadata. Remember to adjust you overlay configuration in the application.yaml to the following:

```yaml
    tester:
      -
          id: home
          name: Local Tester
          server_address: 'http://localhost:8080/fhir'
          refuse_to_fetch_third_party_urls: false
          fhir_version: R4
```

### Running the packaged application
```bash
./gradlew clean bootWar
java -jar build/libs/ROOT.war
```
Server will then be accessible at http://localhost:8080/ and e.g. http://localhost:8080/fhir/metadata. Remember to adjust your overlay configuration in the application.yaml to the following:

```yaml
    tester:
      -
          id: home
          name: Local Tester
          server_address: 'http://localhost:8080/fhir'
          refuse_to_fetch_third_party_urls: false
          fhir_version: R4
```
### Using Spring Boot and Google distroless
```bash
docker build -t distroless-hapi .
docker run -p 8080:8080 distroless-hapi
```
Server will then be accessible at http://localhost:8080/ and e.g. http://localhost:8080/fhir/metadata. Remember to adjust your overlay configuration in the application.yaml to the following:

```yaml
    tester:
      -
          id: home
          name: Local Tester
          server_address: 'http://localhost:8080/fhir'
          refuse_to_fetch_third_party_urls: false
          fhir_version: R4
```

### Using the Dockerfile and multistage build
```bash
./build-docker-image.sh && docker run -p 8080:8080 hapi-fhir/hapi-fhir-jpaserver-starter:latest
```
Server will then be accessible at http://localhost:8080/ and e.g. http://localhost:8080/fhir/metadata. Remember to adjust your overlay configuration in the application.yaml to the following:

```yaml
    tester:
      -
          id: home
          name: Local Tester
          server_address: 'http://localhost:8080/fhir'
          refuse_to_fetch_third_party_urls: false
          fhir_version: R4
```

## Configurations

Much of this HAPI starter project can be configured using the yaml file in _src/main/resources/application.yaml_. By default, this starter project is configured to use H2 as the database.

### MySQL configuration

HAPI FHIR JPA Server does not support MySQL as it is deprecated.

See more at https://hapifhir.io/hapi-fhir/docs/server_jpa/database_support.html

### PostgreSQL configuration

To configure the starter app to use PostgreSQL, instead of the default H2, update the application.yaml file to have the following:

```yaml
spring:
  datasource:
    url: 'jdbc:postgresql://localhost:5432/hapi'
    username: admin
    password: admin
    driverClassName: org.postgresql.Driver
  jpa:
    properties:
      hibernate.dialect: ca.uhn.fhir.jpa.model.dialect.HapiFhirPostgresDialect
      hibernate.search.enabled: false

      # Then comment all hibernate.search.backend.*
```

The test fixtures use the default H2 database configuration. After switching the local defaults to PostgreSQL, package with `./gradlew bootWar` until the fixtures are updated, or supply a separate test configuration.

### Microsoft SQL Server configuration

To configure the starter app to use MS SQL Server, instead of the default H2, update the application.yaml file to have the following:

```yaml
spring:
  datasource:
    url: 'jdbc:sqlserver://<server>:<port>;databaseName=<databasename>;sendStringParametersAsUnicode=false'
    username: admin
    password: admin
    driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
```

Always include `sendStringParametersAsUnicode=false` in the connection URL. The HAPI FHIR schema uses plain `VARCHAR` columns, but the Microsoft SQL Server JDBC driver sends string parameters as Unicode (`NVARCHAR`) by default. The resulting implicit `NVARCHAR`-to-`VARCHAR` conversions prevent SQL Server from using indexes on those columns and can severely degrade query performance (see [Microsoft JDBC driver documentation](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties) for details).

Also, make sure you are not setting the Hibernate dialect explicitly, in other words, remove any lines similar to:

```
hibernate.dialect: {some none Microsoft SQL dialect}
```


The test fixtures use the default H2 database configuration. After switching the local defaults to PostgreSQL, package with `./gradlew bootWar` until the fixtures are updated, or supply a separate test configuration.


NOTE: MS SQL Server by default uses a case-insensitive codepage. This will cause errors with some operations - such as when expanding case-sensitive valuesets (UCUM) as there are unique indexes defined on the terminology tables for codes.
It is recommended to deploy a case-sensitive database prior to running HAPI FHIR when using MS SQL Server to avoid these and potentially other issues.

## Adding custom interceptors
Custom interceptors can be registered with the server by including the property `hapi.fhir.custom-interceptor-classes`. This will take a comma separated list of fully-qualified class names which will be registered with the server.
Interceptors will be discovered in one of two ways:

1) From the Spring application context as existing Beans (can be used in conjunction with `hapi.fhir.custom-bean-packages`) or registered with Spring via other methods
2) Classes will be instantiated via reflection if no matching Bean is found

Interceptors can also be registered manually through `RestfulServer.registerInterceptor`. Take note that any interceptor registered in this way _will not fire_ for non-REST operations, e.g. creation through a DAO. To trigger in this case, you need to register your interceptors on the `IInterceptorService` bean.

## Adding custom operations(providers)
Custom operations(providers) can be registered with the server by including the property `hapi.fhir.custom-provider-classes`. This will take a comma separated list of fully-qualified class names which will be registered with the server.
Providers will be discovered in one of two ways:

1) discovered from the Spring application context as existing Beans (can be used in conjunction with `hapi.fhir.custom-bean-packages`) or registered with Spring via other methods

or

2) classes will be instantiated via reflection if no matching Bean is found

## Customizing The Web Testpage UI

The UI that comes with this server is an exact clone of the server available at [http://hapi.fhir.org](http://hapi.fhir.org). You may skin this UI if you'd like. For example, you might change the introductory text or replace the logo with your own.

The UI is customized using [Thymeleaf](https://www.thymeleaf.org/) template files. You might want to learn more about Thymeleaf, but you don't necessarily need to: they are quite easy to figure out.

Several template files that can be customized are found in the following directory: [https://github.com/hapifhir/hapi-fhir-jpaserver-starter/tree/master/src/main/webapp/WEB-INF/templates](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/tree/master/src/main/webapp/WEB-INF/templates)

## Running behind a reverse proxy

When the starter is exposed through a reverse proxy or load balancer, configure the public FHIR base URL that clients
should see in generated links, Bundle entries, and the web tester.

For a stable public URL, set `hapi.fhir.server_address` to the externally reachable FHIR endpoint:

```yaml
hapi:
  fhir:
    server_address: https://fhir.example.com/fhir
    tester:
      home:
        server_address: https://fhir.example.com/fhir
```

If your proxy forwards the original request host and scheme, enable the Apache proxy address strategy instead:

```yaml
hapi:
  fhir:
    use_apache_address_strategy: true
    use_apache_address_strategy_https: true
```

Ensure the proxy sends the usual forwarded headers, such as `X-Forwarded-Host`, `X-Forwarded-Proto`, and
`X-Forwarded-Prefix` when the server is mounted below a path prefix.


## Deploy with docker compose

Docker compose is a simple option to build and deploy containers. To deploy with docker compose, you should build the project
with `docker-compose up -d --build`; the Docker build runs Gradle inside the build container. The server can be
reached at http://localhost:8080/.

In order to use another port, change the `ports` parameter
inside `docker-compose.yml` to `8888:8080`, where 8888 is a port of your choice.

The docker compose set also includes PostgreSQL database, if you choose to use PostgreSQL instead of H2, change the following
properties in `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: 'jdbc:postgresql://hapi-fhir-postgres:5432/hapi'
    username: admin
    password: admin
    driverClassName: org.postgresql.Driver
jpa:
  properties:
    hibernate.dialect: ca.uhn.fhir.jpa.model.dialect.HapiFhirPostgresDialect
    hibernate.search.enabled: false

    # Then comment all hibernate.search.backend.*
```

## Docker Health Check

The distroless Docker image includes a built-in health check that probes the Spring Boot Actuator `/actuator/health` endpoint and exits `0` on HTTP 200, `1` otherwise. It is a standalone Java class with no external dependencies, so it works on the distroless base image which has no shell or `curl`.

To run the health check inside a running container:

```
docker exec hapi-fhir-jpaserver-start java -cp /app HealthCheck
```

The probe URL is built from the following environment variables (matching Spring Boot defaults), so the same check works for custom ports and context paths without code changes:

| Variable | Default | Notes |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Main server port. |
| `SERVER_SERVLET_CONTEXT_PATH` | _(empty)_ | Applied only when management shares the server port. |
| `MANAGEMENT_SERVER_PORT` | `SERVER_PORT` | Set to expose actuator on its own port. |
| `MANAGEMENT_SERVER_BASE_PATH` | _(empty)_ | Applied only when management uses its own port. |
| `MANAGEMENT_ENDPOINTS_WEB_BASE_PATH` | `/actuator` | Path of the actuator endpoints. |

Make sure the actuator health endpoint is exposed (e.g. `management.endpoints.web.exposure.include=health`).
To enable periodic health checks, uncomment the `healthcheck` block in `docker-compose.yml`.

## Running from IntelliJ as Spring Boot

Import the project using its Gradle build, select JDK 21 as the Gradle JVM and project SDK, and run
ca.uhn.fhir.jpa.starter.Application or the bootRun Gradle task. Use bootRun to include the complete FHIR Tester overlay.

## Enabling Subscriptions

The server may be configured with subscription support by enabling properties in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file:

- `hapi.fhir.subscription.resthook_enabled` - Enables REST Hook subscriptions, where the server will make an outgoing connection to a remote REST server

- `hapi.fhir.subscription.email.*` - Enables email subscriptions. Note that you must also provide the connection details for a usable SMTP server.

- `hapi.fhir.subscription.websocket_enabled` - Enables websocket subscriptions. With this enabled, your server will accept incoming websocket connections on the following URL (this example uses the default context path and port, you may need to tweak depending on your deployment environment): [ws://localhost:8080/websocket](ws://localhost:8080/websocket)

## Enabling Clinical Reasoning

Set `hapi.fhir.cr.enabled=true` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to enable [Clinical Quality Language](https://cql.hl7.org/) on this server.  An alternate settings file, [application-cds.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application-cds.yaml), exists with the Clinical Reasoning module enabled and default settings that have been found to work with most CDS and dQM test cases.

## Enabling CDS Hooks

Set `hapi.fhir.cdshooks.enabled=true` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to enable [CDS Hooks](https://cds-hooks.org/) on this server.  The Clinical Reasoning module must also be enabled because this implementation of CDS Hooks includes [CDS on FHIR](https://build.fhir.org/clinicalreasoning-cds-on-fhir.html).  An example CDS Service using CDS on FHIR is available in the CdsHooksServletIT test class.

## Enabling MDM (EMPI)

Set `hapi.fhir.mdm_enabled=true` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to enable MDM on this server.  The MDM matching rules are configured in [mdm-rules.json](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/mdm-rules.json).  The rules in this example file should be replaced with actual matching rules appropriate to your data. Note that MDM relies on subscriptions, so for MDM to work, subscriptions must be enabled.

## Using Elasticsearch

By default, the server will use embedded Lucene indexes for terminology and fulltext indexing purposes. You can switch this to Elasticsearch by editing the properties in [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml)

For example:

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    username: SomeUsername
    password: SomePassword
  jpa:
    properties:
      hibernate:
        search:
          enabled: true
          backend:
            type: elasticsearch
            protocol: http
            schema_management:
              minimal_required_status: YELLOW
          schema_management:
            strategy: CREATE
```

## Enabling LastN

Set `hapi.fhir.lastn_enabled=true` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to enable the $lastn operation on this server.  Note that the $lastn operation relies on Elasticsearch, so for $lastn to work, indexing must be enabled using Elasticsearch.

## Enabling Resource to be stored in Lucene Index

Set `hapi.fhir.store_resource_in_lucene_index_enabled` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to enable storing of resource json along with Lucene/Elasticsearch index mappings.

## Changing cached search results time

It is possible to change the cached search results time. The option `reuse_cached_search_results_millis` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) is 6000 milliseconds by default.
Set `reuse_cached_search_results_millis: -1` in the [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) file to ignore the cache time every search.

## Build the distroless variant of the image (for lower footprint and improved security)

The default Dockerfile contains a `default` stage to build a variant of the image
using the `gcr.io/distroless/java21-debian13:nonroot` base image:

```sh
docker build --target=default -t hapi-fhir:distroless .
```

Note that distroless images are also automatically built and pushed to the container registry,
see the `-distroless` suffix in the image tags.

## Adding custom operations

To add a custom operation, refer to the documentation in the core hapi-fhir libraries [here](https://hapifhir.io/hapi-fhir/docs/server_plain/rest_operations_operations.html).

Within `hapi-fhir-jpaserver-starter`, create a generic class (that does not extend or implement any classes or interfaces), add the `@Operation` as a method within the generic class, and then register the class as a provider using `RestfulServer.registerProvider()`.

## Runtime package install

It's possible to install a FHIR Implementation Guide package (`package.tgz`) either from a published package or from a local package with the `$install` operation, without having to restart the server. This is available for R4 and R5.

This feature must be enabled in the application.yaml (or docker command line):

```yaml
hapi:
  fhir:
    ig_runtime_upload_enabled: true
```

The `$install` operation is triggered with a POST to `[server]/ImplementationGuide/$install`, with the payload below:

```json
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "npmContent",
      "valueBase64Binary": "[BASE64_ENCODED_NPM_PACKAGE_DATA]"
    }
  ]
}
```

## Enable OpenTelemetry auto-instrumentation

The container image includes the [OpenTelemetry Java auto-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
Java agent JAR which can be used to export telemetry data for the HAPI FHIR JPA Server. You can enable it by specifying the `-javaagent` flag,
for example by overriding the `JAVA_TOOL_OPTIONS` environment variable:

```sh
docker run --rm -it -p 8080:8080 \
  -e JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar" \
  -e OTEL_TRACES_EXPORTER="jaeger" \
  -e OTEL_SERVICE_NAME="hapi-fhir-server" \
  -e OTEL_EXPORTER_JAEGER_ENDPOINT="http://jaeger:14250" \
  docker.io/hapiproject/hapi:latest
```

You can configure the agent using environment variables or Java system properties, see <https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/> for details.

## Enable MCP

MCP capabilities can be enabled by setting the `spring.ai.mcp.server.enabled` to `true`. This will enable the MCP server and expose the MCP endpoints. The default MCP endpoint is `/mcp/messages` and can be tried out by running e.g. `npx @modelcontextprotocol/inspector` and connecting to http://localhost:8080/mcp/messages using Streamable HTTP. Spring AI MCP Server Auto Configuration is currently not supported.
