plugins {
    kotlin("jvm") version "2.4.10"
    application
}

group = "me.pinfort"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("me.pinfort.tsselect.MainKt")
}

tasks.test {
    useJUnitPlatform()
}