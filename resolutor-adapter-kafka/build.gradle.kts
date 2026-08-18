plugins {
    id("resolutor.java-conventions")
    alias(libs.plugins.spring.dependency.management)
}

description = "Resolutor - optional Kafka DispatcherPort adapter."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":resolutor-application"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(libs.resilience4j.circuitbreaker)

    testImplementation(testFixtures(project(":resolutor-application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
