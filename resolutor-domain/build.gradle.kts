plugins {
    id("resolutor.java-conventions")
}

description = "Resolutor - pure domain model. No Spring, no I/O, no framework leakage."

dependencies {
    api(libs.jspecify)
}
