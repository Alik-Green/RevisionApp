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

            // The packaged app runs on a jlink image built from exactly these
            // modules. Compose's default set is java.base, java.desktop,
            // java.logging and jdk.crypto.ec - no java.sql - while SQLDelight's
            // desktop driver opens its connection through
            // java.sql.DriverManager. The installer therefore died at startup
            // with NoClassDefFoundError: java/sql/DriverManager while
            // :desktopApp:run, which uses the full JDK, worked fine.
            modules("java.sql")

            linux {
                packageName = "revisionapp"
            }
            windows {
                packageName = "RevisionApp"
                upgradeUuid = "6f1c1e3a-9d2b-4b7a-8f31-2c0d5a4e7b91"
                // Both of these default to false, which produced an installer
                // that created no Start Menu entry and no desktop shortcut: the
                // app was invisible to Windows Search and could only be launched
                // by digging out the executable in its install folder.
                menu = true
                menuGroup = "RevisionApp"
                shortcut = true
            }
        }
    }
}

ktlint {
    filter {
        exclude("**/build/**")
    }
}
