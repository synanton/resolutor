// Root build script. Configuration lives in convention plugins under buildSrc/
// so per-module scripts stay minimal.

tasks.register("printModules") {
    doLast {
        subprojects.forEach { println(it.path) }
    }
}
