plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":tsselect-core"))
    testImplementation(kotlin("test"))
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
