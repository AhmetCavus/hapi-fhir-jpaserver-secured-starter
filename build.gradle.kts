import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.springframework.boot.gradle.tasks.bundling.BootWar
import org.springframework.boot.gradle.tasks.run.BootRun
import org.springframework.boot.loader.tools.LoaderImplementation

plugins {
    java
    war
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.diffplug.spotless")
}

val hapiFhirVersion: String by project
val starterRevision: String by project
val springBootVersion: String by project
val clinicalReasoningVersion: String by project
val kotlinRuntimeVersion: String by project
val kotlinxSerializationVersion: String by project
val springAiVersion: String by project
val awaitilityVersion: String by project
val micrometerVersion: String by project
val commonsLoggingVersion: String by project
val postgresqlVersion: String by project
val tomcatVersion: String by project
val logbackVersion: String by project
val jacocoVersion: String by project
val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt)
val cloudSqlPostgres = providers.gradleProperty("cloudSqlPostgres").map(String::toBooleanStrict)

group = "ca.uhn.hapi.fhir"
version = "$hapiFhirVersion-$starterRevision"
description = "HAPI FHIR JPA Server - Starter Project"

java {
    toolchain {
        languageVersion.set(javaVersion.map(JavaLanguageVersion::of))
    }
}

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        name = "centralPortalSnapshots"
        mavenContent {
            snapshotsOnly()
        }
    }
}

// Import HAPI's dependency management without inheriting its Maven build lifecycle.
dependencyManagement {
    imports {
        mavenBom("ca.uhn.hapi.fhir:hapi-fhir:$hapiFhirVersion") {
            bomProperty("spring_boot_version", springBootVersion)
            bomProperty("logback_version", logbackVersion)
        }
        // CQL requires newer Kotlin runtime APIs than Spring Boot's BOM defaults.
        mavenBom("org.jetbrains.kotlin:kotlin-bom:$kotlinRuntimeVersion")
        mavenBom("org.jetbrains.kotlinx:kotlinx-serialization-bom:$kotlinxSerializationVersion")
    }
    dependencies {
        dependency("org.glassfish.jaxb:jaxb-runtime:2.3.8")
        dependency("org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion")
        dependency("org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion")
        dependency("org.apache.tomcat.embed:tomcat-embed-el:$tomcatVersion")
        dependency("ch.qos.logback:logback-classic:$logbackVersion")
        dependency("ch.qos.logback:logback-core:$logbackVersion")
        dependency("org.postgresql:postgresql:$postgresqlVersion")
        dependency("io.micrometer:micrometer-core:$micrometerVersion")
        dependency("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
        dependency("io.micrometer:micrometer-registry-prometheus-simpleclient:$micrometerVersion")
    }
}

val testerOverlay by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

// HAPI test utilities also pull in Jetty; keep a single servlet container in every classpath.
configurations.configureEach {
    exclude(group = "org.eclipse.jetty")
    exclude(group = "org.eclipse.jetty.ee10")
    exclude(group = "org.eclipse.jetty.ee10.websocket")
    exclude(group = "org.eclipse.jetty.websocket")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("co.elastic.clients:elasticsearch-java")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.microsoft.sqlserver:mssql-jdbc")
    implementation("org.simplejavamail:simple-java-mail") {
        exclude(group = "jakarta.annotation", module = "jakarta.annotation-api")
    }

    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:$hapiFhirVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-subscription:$hapiFhirVersion") {
        exclude(group = "com.zaxxer", module = "HikariCP-java7")
    }
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-base:$hapiFhirVersion") {
        exclude(group = "org.springframework", module = "spring-jcl")
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.opencds.cqf.fhir:cqf-fhir-cr-hapi:$clinicalReasoningVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-mdm:$hapiFhirVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-server-cds-hooks:$hapiFhirVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-converter:$hapiFhirVersion")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-server-openapi:$hapiFhirVersion") {
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    testerOverlay("ca.uhn.hapi.fhir:hapi-fhir-testpage-overlay:$hapiFhirVersion@war")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-testpage-overlay:$hapiFhirVersion:classes")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-ips:$hapiFhirVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.interceptor:jakarta.interceptor-api")
    compileOnly("commons-logging:commons-logging:$commonsLoggingVersion")
    implementation("org.thymeleaf:thymeleaf")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-test")
    implementation("org.apache.commons:commons-dbcp2") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("com.h2database:h2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus-simpleclient:$micrometerVersion")
    implementation("org.springframework.ai:spring-ai-mcp:$springAiVersion")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server:$springAiVersion")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.webjars:Eonasdan-bootstrap-datetimepicker")
    implementation("org.webjars:font-awesome")
    implementation("org.webjars.bower:awesome-bootstrap-checkbox")
    implementation("org.webjars:jstimezonedetect")
    implementation("org.webjars:select2")
    implementation("org.webjars.bower:jquery")
    implementation("org.webjars.bower:moment")

    if (cloudSqlPostgres.get()) {
        runtimeOnly("com.google.cloud.sql:postgres-socket-factory:1.28.0")
    }

    testImplementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-test-utilities:$hapiFhirVersion") {
        exclude(group = "com.sun.xml.bind", module = "jaxb-impl")
    }
    testImplementation("ca.uhn.hapi.fhir:hapi-fhir-test-utilities:$hapiFhirVersion")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-elasticsearch")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

springBoot {
    mainClass.set("ca.uhn.fhir.jpa.starter.Application")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val tokens = mapOf("project.artifactId" to project.name, "project.version" to project.version.toString())
    inputs.properties(tokens)
    filteringCharset = "UTF-8"
    filesMatching(listOf("**/*.yaml", "**/*.yml", "**/*.properties")) {
        // Spring's ${...} expressions must reach runtime unchanged.
        filter<ReplaceTokens>("tokens" to tokens)
    }
}

val prepareWebapp by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("webapp"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Local templates and assets take precedence over the upstream WAR overlay.
    from("src/main/webapp")
    from(provider { zipTree(testerOverlay.singleFile) }) {
        exclude("META-INF/**", "WEB-INF/classes/**", "WEB-INF/lib/**")
    }
}

tasks.named<BootWar>("bootWar") {
    archiveFileName.set("ROOT.war")
    webAppDirectory.set(layout.buildDirectory.dir("webapp"))
    dependsOn(prepareWebapp)
    loaderImplementation.set(LoaderImplementation.CLASSIC)
}

tasks.war {
    enabled = false
}

// The executable WAR retains the FHIR Tester's servlet resources.
tasks.named("bootJar") {
    enabled = false
}

tasks.jar {
    enabled = false
}

tasks.named<BootRun>("bootRun") {
    val executableWar = tasks.named<BootWar>("bootWar")
    dependsOn(executableWar)
    // The tester resolves WEB-INF/templates through ServletContext, including during development.
    classpath = files(executableWar.flatMap { it.archiveFile })
    mainClass.set("org.springframework.boot.loader.WarLauncher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "2g"
    systemProperty("file.encoding", "UTF-8")
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        events("failed", "skipped")
    }
}

tasks.test {
    include("**/*Test.class")
    exclude("**/*StressTest*.class")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs the *IT integration tests. Some suites require Docker."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IT.class")
    forkEvery = 1
    shouldRunAfter(tasks.test)
}

jacoco {
    toolVersion = jacocoVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(tasks.test.get(), integrationTest.get())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestReport)
}

spotless {
    java {
        target("src/main/java/**/*.java")
        palantirJavaFormat("2.38.0")
        indentWithTabs(4)
        removeUnusedImports()
        importOrder("", "java|javax", "\\#").wildcardsLast()
    }
    format("misc") {
        target("*.md", ".gitignore", "*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.wrapper {
    gradleVersion = "8.13"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
}
