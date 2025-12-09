plugins {
    java
    id("com.github.johnrengelman.shadow")
    jacoco // Code coverage
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
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.9.0") // Use v1.20 (stable)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    // For integration tests with H2 in-memory database
    testImplementation("com.h2database:h2:2.2.224")
}

tasks {
    test {
        useJUnitPlatform()

        // Show test output
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }

        // Fail build if tests fail
        ignoreFailures = false

        // Generate report
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

    jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "0.70".toBigDecimal() // 70% coverage minimum
                }
            }
        }
    }

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

    // Custom task to run tests and open coverage report
    register("testWithReport") {
        dependsOn(test, jacocoTestReport)
        doLast {
            val reportFile = layout.buildDirectory.file("reports/jacoco/index.html").get().asFile
            if (reportFile.exists()) {
                println("Coverage report: ${reportFile.absolutePath}")
            }
        }
    }
}