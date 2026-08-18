plugins {
    id("resolutor.java-conventions")
    alias(libs.plugins.spring.dependency.management)
}

description = "Resolutor - JPA persistence adapter (PostgreSQL)."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
        mavenBom(libs.testcontainers.bom.get().toString())
    }
}

dependencies {
    api(project(":resolutor-application"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.shedlock.spring)
    implementation(libs.shedlock.jdbc.template)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(testFixtures(project(":resolutor-application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
}
