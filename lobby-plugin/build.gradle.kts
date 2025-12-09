plugins {
    java
    id("com.gradleup.shadow")
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Core Plugin dependency - THIS IS WHAT'S MISSING!
    compileOnly(project(":core-plugin"))

    // API Module
    implementation(project(":api-module"))

    // Paper API 1.21.4 (update to 1.21.8 when available)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Adventure API (modern text components)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    // Configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Soft dependencies (optional plugins)
    compileOnly("net.luckperms:api:5.4")

    // Testing
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.95.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks {
    test {
        useJUnitPlatform()

        // Exclude ItemBuilderTest until MockBukkit fully supports 1.21.8
        exclude("**/ItemBuilderTest.class")

        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
        ignoreFailures = false
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
            html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
        }
    }

    shadowJar {
        archiveBaseName.set("LobbyPlugin")
        archiveClassifier.set("")

        // Relocate dependencies to avoid conflicts
        relocate("org.spongepowered.configurate", "com.yourserver.lobby.libs.configurate")
        relocate("net.kyori", "com.yourserver.lobby.libs.kyori")

        // Exclude unnecessary files
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "name" to project.name,
                "main" to "com.yourserver.lobby.LobbyPlugin"
            )
        }
    }
}