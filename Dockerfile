FROM docker.io/library/eclipse-temurin:21-jdk AS build-hapi
WORKDIR /tmp/hapi-fhir-jpaserver-starter

ARG OPENTELEMETRY_JAVA_AGENT_VERSION=2.24.0
RUN curl -LSsO https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OPENTELEMETRY_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ ./gradle/
RUN chmod +x gradlew && ./gradlew --no-daemon help

COPY src/ /tmp/hapi-fhir-jpaserver-starter/src/
RUN ./gradlew --no-daemon bootWar -Djdk.lang.Process.launchMechanism=vfork

FROM build-hapi AS build-distroless
RUN mkdir /app && cp /tmp/hapi-fhir-jpaserver-starter/build/libs/ROOT.war /app/main.war

COPY src/main/java/HealthCheck.java /app/HealthCheck.java
RUN javac /app/HealthCheck.java


########### distroless brings focus on security and runs on plain spring boot - this is the default image
FROM gcr.io/distroless/java21-debian13:nonroot AS default
# 65532 is the nonroot user's uid
# used here instead of the name to allow Kubernetes to easily detect that the container
# is running as a non-root (uid != 0) user.
USER 65532:65532
WORKDIR /app

COPY --chown=nonroot:nonroot --from=build-distroless /app /app
COPY --chown=nonroot:nonroot --from=build-hapi /tmp/hapi-fhir-jpaserver-starter/opentelemetry-javaagent.jar /app

ENTRYPOINT ["java", "--class-path", "/app/main.war", "-Dloader.path=main.war!/WEB-INF/classes/,main.war!/WEB-INF/,/app/extra-classes", "org.springframework.boot.loader.PropertiesLauncher"]
