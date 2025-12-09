plugins {
    java
    id("com.github.johnrengelman.shadow")
}

dependencies {
    // API Module
    implementation(project(":api-module"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Adventure API (modern text components)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Testing
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.9.0")
}

tasks {
    shadowJar {
        archiveBaseName.set("CorePlugin")
        archiveClassifier.set("")

        // Relocate dependencies to avoid conflicts
        relocate("com.zaxxer.hikari", "com.yourserver.core.libs.hikari")
        relocate("io.lettuce", "com.yourserver.core.libs.lettuce")
        relocate("com.github.benmanes.caffeine", "com.yourserver.core.libs.caffeine")
        relocate("org.spongepowered.configurate", "com.yourserver.core.libs.configurate")

        // Minimize jar size by excluding unused classes
        minimize {
            exclude(dependency("com.mysql:mysql-connector-j:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}