plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "com.yourserver"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.dmulloy2.net/repository/public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://jitpack.io") // Add this for MockBukkit
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.github.johnrengelman.shadow")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(21)
        }

        test {
            useJUnitPlatform()
        }
    }

    dependencies {
        // Testing dependencies for all modules
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testImplementation"("org.mockito:mockito-core:5.11.0")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.11.0")
    }
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}