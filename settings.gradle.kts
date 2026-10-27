pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.springframework.boot") version providers.gradleProperty("springBootVersion").get()
        id("io.spring.dependency-management") version "1.1.7"
        id("com.diffplug.spotless") version "6.25.0"
    }
}

rootProject.name = "hapi-fhir-jpaserver-starter"
