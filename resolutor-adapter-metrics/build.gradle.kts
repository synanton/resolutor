plugins {
    id("resolutor.java-conventions")
    alias(libs.plugins.spring.dependency.management)
}

description = "Resolutor - Micrometer/Prometheus metrics adapter."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":resolutor-application"))

    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-observation")

    testImplementation(testFixtures(project(":resolutor-application")))
}
