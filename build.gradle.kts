import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

allprojects {
    group = "me.pinfort"
    version = "1.0-SNAPSHOT"
}

// Aggregate coverage across both modules; reports land in build/reports/kover.
dependencies {
    kover(project(":tsselect-core"))
    kover(project(":tsselect-cli"))
}

// Each module opts in with `id("org.jlleitschuh.gradle.ktlint")` in its own
// plugins block; the shared configuration lives here.
subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<KtlintExtension> {
            version.set("1.5.0")
            reporters {
                reporter(ReporterType.PLAIN)
                reporter(ReporterType.CHECKSTYLE)
            }
        }
    }
}
