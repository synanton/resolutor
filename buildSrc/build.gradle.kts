plugins {
    `kotlin-dsl`
}

// Only include plugins that are applied *from* convention plugins. Application-level
// plugins (Spring Boot, dependency-management) are applied directly by the app module.
dependencies {
    implementation(
        "com.diffplug.spotless:com.diffplug.spotless.gradle.plugin:" +
            libs.versions.spotless.get(),
    )
    implementation(
        "net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:" +
            libs.versions.errorprone.plugin.get(),
    )
}
