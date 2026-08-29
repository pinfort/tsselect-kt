plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

kotlin {
    jvmToolchain(25)
    // The published surface is small and deliberate; make widening it a
    // compile error rather than an oversight.
    explicitApi()
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
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                        distribution.set("repo")
                        comments.set(
                            "MIT applies to this Kotlin port. It is a derivative work of tsselect 0.1.8 " +
                                "by Mogi Kazuhiro; see NOTICE for the original terms.",
                        )
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
