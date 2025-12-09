plugins {
    java
}

dependencies {
    // Only pure Java dependencies - no Bukkit/Paper
    compileOnly("org.jetbrains:annotations:24.1.0")
}

// This is a library module, not a plugin
tasks {
    jar {
        archiveBaseName.set("api-module")
    }
}