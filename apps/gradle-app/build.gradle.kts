plugins {
    java
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.17.0")
}

// Lets Trivy see the dependencies (it reads gradle.lockfile).
// After changing dependencies, run: ./gradlew dependencies --write-locks
dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile> {
    options.release = 21
}

// Build a single runnable build/libs/app.jar with dependencies bundled in
tasks.jar {
    archiveFileName = "app.jar"
    manifest {
        attributes["Main-Class"] = "com.example.App"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } })
}
