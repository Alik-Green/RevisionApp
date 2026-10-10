import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    // AGP 9 configures the Android target of a Kotlin Multiplatform library
    // through this `kotlin { android { ... } }` block instead of the legacy
    // top-level `android { ... }` + `androidTarget()` pair.
    android {
        namespace = "com.revisionapp.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // `api` so that :androidApp and :desktopApp can compose against the
            // same Compose artifacts without declaring them again.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.animation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.components.resources)
            // The full Material icon set. The brief asks for a consistent icon set
            // across the redesigned UI; material3 only brings the ~50 core icons,
            // which has no folder, book, chart or sync glyph. This accessor is
            // pinned by the Compose plugin to a frozen 1.7.3 and, unlike the other
            // compose.* accessors, is not deprecated. See docs/DECISIONS.md D33.
            api(compose.materialIconsExtended)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
        }

        // A custom-named jvm target has no generated `desktopMain` accessor,
        // so the source set has to be looked up by name.
        named("desktopTest").dependencies {
            // The in-memory SQLite driver the repository integration tests use.
            implementation(libs.sqldelight.sqlite.driver)
        }

        named("desktopMain").dependencies {
            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.revisionapp.generated.resources"
}

sqldelight {
    databases {
        create("RevisionDatabase") {
            packageName.set("com.revisionapp.data.db")
        }
    }
}

ktlint {
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
        // SQLDelight generates Kotlin into build/generated and adds it to the
        // commonMain source set, so the per-source-set ktlint tasks pick it up.
        // The path patterns above did not match those absolute paths, so the
        // generated output is filtered on the resolved file as well - linting
        // code nobody wrote only hides violations in code that was.
        exclude { element -> element.file.path.replace('\\', '/').contains("/build/") }
    }
}
