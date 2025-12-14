plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Annotations only
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks {
    shadowJar {
        archiveBaseName.set("APIModule")
        archiveClassifier.set("")

        // Don't shade anything - this is a pure API module
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version
            )
        }
    }
}