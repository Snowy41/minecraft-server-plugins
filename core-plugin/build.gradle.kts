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

repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // API Module
    implementation(project(":api-module"))
    compileOnly(project(":api-module"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")

    // Adventure API (modern text components)
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")

    // Database - HikariCP connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // MySQL Driver (MariaDB driver is compatible with MySQL)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")

    // Alternative: MySQL official driver
    // implementation("mysql:mysql-connector-java:8.0.33")

    // Redis
    implementation("io.lettuce:lettuce-core:6.2.7.RELEASE")

    // Caching - Caffeine is faster than Guava
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.6")

    // Configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Gson for JSON (if needed for migration)
    implementation("com.google.code.gson:gson:2.10.1")

    compileOnly("eu.cloudnetservice.cloudnet:driver:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:wrapper-jvm:4.0.0-RC10")
    compileOnly("eu.cloudnetservice.cloudnet:common:4.0.0-RC10")

    // Testing
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.95.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("com.h2database:h2:2.2.224")
}

tasks {
    test {
        useJUnitPlatform()
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
        archiveBaseName.set("CorePlugin")
        archiveClassifier.set("")

        dependencies {
            // Don't shade API module (loaded separately)
            exclude(dependency("com.yourserver:api-module"))
        }

        // Relocate dependencies to avoid conflicts with other plugins
        relocate("com.zaxxer.hikari", "com.yourserver.core.libs.hikari")
        relocate("org.mariadb", "com.yourserver.core.libs.mariadb")
        relocate("io.lettuce", "com.yourserver.core.libs.lettuce")
        relocate("com.github.benmanes.caffeine", "com.yourserver.core.libs.caffeine")
        relocate("org.spongepowered.configurate", "com.yourserver.core.libs.configurate")
        relocate("com.google.gson", "com.yourserver.core.libs.gson")

        // Minimize JAR size by removing unused classes
        minimize {
            // Keep these dependencies fully
            exclude(dependency("org.mariadb.jdbc:.*"))
            exclude(dependency("com.zaxxer:HikariCP:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    register("testWithReport") {
        dependsOn(test, jacocoTestReport)
        doLast {
            val reportFile = layout.buildDirectory.file("reports/jacoco/index.html").get().asFile
            if (reportFile.exists()) {
                println("Coverage report: ${reportFile.absolutePath}")
            }
        }
    }

    processResources {
        filesMatching(listOf("plugin.yml", "database.yml", "redis.yml")) {
            expand(
                "version" to project.version,
                "name" to project.name
            )
        }
    }
}