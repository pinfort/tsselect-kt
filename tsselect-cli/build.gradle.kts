plugins {
    kotlin("jvm")
    application
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
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
    applicationName = "tsselect"
}
