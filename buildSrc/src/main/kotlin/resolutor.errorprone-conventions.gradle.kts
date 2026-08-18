import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "errorprone"(
        "com.google.errorprone:error_prone_core:" +
            libs.findVersion("errorprone-core").get().requiredVersion,
    )
    "errorprone"(
        "com.uber.nullaway:nullaway:" +
            libs.findVersion("nullaway").get().requiredVersion,
    )
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        // NullAway analyses the Resolutor packages; other packages are ignored.
        option("NullAway:AnnotatedPackages", "org.synanton.resolutor")
    }
}

tasks.named<JavaCompile>("compileJava").configure {
    options.errorprone.error("NullAway")
}

// Keep test compilation strict on real errors but do not fail on NullAway there -
// tests deliberately construct null-heavy fixtures.
tasks.matching { it.name.startsWith("compileTest") }
    .withType<JavaCompile>()
    .configureEach {
        options.errorprone.disable("NullAway")
    }
