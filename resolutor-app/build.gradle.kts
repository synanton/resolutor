plugins {
    id("resolutor.java-conventions")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "Resolutor - Spring Boot application wiring. Composition root."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

dependencies {
    implementation(project(":resolutor-domain"))
    implementation(project(":resolutor-application"))
    implementation(project(":resolutor-adapter-persistence-jpa"))
    implementation(project(":resolutor-adapter-web"))
    implementation(project(":resolutor-adapter-resource-http"))
    implementation(project(":resolutor-adapter-kafka"))
    implementation(project(":resolutor-adapter-metrics"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation(libs.logstash.logback.encoder)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(testFixtures(project(":resolutor-application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("resolutor-app.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}
