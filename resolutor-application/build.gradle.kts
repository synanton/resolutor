plugins {
    id("resolutor.java-conventions")
    `java-test-fixtures`
    alias(libs.plugins.jmh)
}

description = "Resolutor - application services and ports. Depends on domain only."

dependencies {
    api(project(":resolutor-domain"))
    api(libs.jspecify)

    testImplementation(libs.archunit.junit5)
}
