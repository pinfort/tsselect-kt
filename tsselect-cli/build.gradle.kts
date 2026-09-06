plugins {
    kotlin("jvm")
    application
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":tsselect-core"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("me.pinfort.tsselect.cli.MainKt")
    // keep the installed launcher named `tsselect`, not `tsselect-cli`
    // (this also names the distribution archives `tsselect-<version>.*`)
    applicationName = "tsselect"
}

// `distZip`/`distTar` build the archives attached to a GitHub release, so the
// tarball is gzipped rather than left as a bare 2 MB `.tar`.
tasks.distTar {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}
