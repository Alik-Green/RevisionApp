import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    // Same target name as in :composeApp so that the Kotlin Multiplatform
    // project dependency resolves to the desktop compilation.
    jvm("desktop")

    sourceSets {
        named("desktopMain").dependencies {
            implementation(project(":composeApp"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.revisionapp.MainKt"

        nativeDistributions {
            // Deb for Linux CI, Msi/Exe for the Windows release artifact.
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "RevisionApp"
            packageVersion = providers.gradleProperty("version_name").get()
            description = "Flashcards and question-mode revision app"
            vendor = "RevisionApp"
            copyright = "© 2026 RevisionApp contributors"

            linux {
                packageName = "revisionapp"
            }
            windows {
                packageName = "RevisionApp"
                upgradeUuid = "6f1c1e3a-9d2b-4b7a-8f31-2c0d5a4e7b91"
            }
        }
    }
}

ktlint {
    filter {
        exclude("**/build/**")
    }
}
