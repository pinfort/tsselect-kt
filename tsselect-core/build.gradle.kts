plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

kotlin {
    jvmToolchain(25)
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "tsselect"
            pom {
                name.set("tsselect")
                description.set("Kotlin port of tsselect - MPEG-2 TS stream(pid) analyzer and selector")
                url.set("https://github.com/pinfort/tsselect-kt")
                licenses {
                    license {
                        name.set("tsselect original terms (Mogi Kazuhiro)")
                        url.set("https://github.com/pinfort/tsselect-kt/blob/main/README.md#credits-and-license")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("pinfort")
                        name.set("pinfort")
                    }
                }
                scm {
                    url.set("https://github.com/pinfort/tsselect-kt")
                    connection.set("scm:git:https://github.com/pinfort/tsselect-kt.git")
                    developerConnection.set("scm:git:git@github.com:pinfort/tsselect-kt.git")
                }
            }
        }
    }
}
