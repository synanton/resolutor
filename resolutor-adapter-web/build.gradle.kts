plugins {
    id("resolutor.java-conventions")
    alias(libs.plugins.spring.dependency.management)
}

description = "Resolutor - REST web adapter (Spring MVC)."

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":resolutor-application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.springdoc.openapi)

    testImplementation(testFixtures(project(":resolutor-application")))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}
