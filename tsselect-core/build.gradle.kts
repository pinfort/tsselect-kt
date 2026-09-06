import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

kotlin {
    jvmToolchain(25)
    // Compile with the JDK 25 toolchain but emit Java 17 bytecode: this is a
    // published library, so it must not force consumers onto a newer JVM than
    // they run. 17 is the oldest LTS still in wide use; targeting it covers
    // consumers on 17, 21 and 25.
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // jvmTarget only sets the class-file version; it does not stop the JDK 25
        // toolchain from resolving APIs that exist only on 18+. -Xjdk-release is
        // the Kotlin equivalent of `javac --release`: it checks calls against the
        // JDK 17 signature set, so a newer API is a compile error here rather than
        // a NoSuchMethodError for a consumer on 17.
        freeCompilerArgs.add("-Xjdk-release=17")
        // jvmTarget/-Xjdk-release only pin the class-file version; they say
        // nothing about the `mv` (metadata version) field the Kotlin compiler
        // stamps into every class's @Metadata annotation, which defaults to
        // whatever languageVersion this compiler treats as current. A consumer
        // on Java 17 but an older Kotlin compiler would still be rejected
        // (or need -Xskip-metadata-version-check) without this pin. 2.0 is the
        // oldest languageVersion this Kotlin compiler still accepts.
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
    }
    // The published surface is small and deliberate; make widening it a
    // compile error rather than an oversight.
    explicitApi()
}

java {
    // Drives `org.gradle.jvm.version` in the published Gradle module metadata so
    // consumers building on Java 17 resolve the artifact instead of being rejected.
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    // Registers `publishToMavenCentral` (uploads a signed bundle and leaves it
    // validated, so the release is a button in the Portal UI) and
    // `publishAndReleaseToMavenCentral` (uploads and releases in one step).
    publishToMavenCentral()
    // Central rejects unsigned artifacts, but `signAllPublications()` signs
    // every publication including the local one, so declaring it
    // unconditionally would break `publishToMavenLocal` on any machine without
    // a GPG key. Gate it on the key actually being supplied — CI sets
    // ORG_GRADLE_PROJECT_signingInMemoryKey (see RELEASING.md), and a release
    // without it fails at upload rather than shipping unsigned artifacts.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    // Central also requires a -javadoc.jar to exist. The sources here are pure
    // Kotlin, so `javadoc` would produce an empty jar anyway; declare that
    // explicitly rather than shipping an accident. Swap for JavadocJar.Dokka()
    // if real API docs are ever wanted.
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    coordinates(artifactId = "tsselect")

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
