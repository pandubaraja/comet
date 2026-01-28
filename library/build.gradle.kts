import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)

    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.pandubaraja"
version = "0.1.0"

kotlin {
    androidLibrary {
        namespace = "io.pandu.comet"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget.set(
                    JvmTarget.JVM_11
                )
            }
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutine.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutine.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("io.github.pandubaraja", "comet", version.toString())

    pom {
        name = "Comet"
        description = "Lightweight KMP coroutine telemetry library — trace, observe, and visualize Kotlin coroutines."
        inceptionYear = "2025"
        url = "https://github.com/pandubaraja/comet/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "pandubaraja"
                name = "Pandu Baraja"
                url = "https://github.com/pandubaraja"
            }
        }
        scm {
            url = "https://github.com/pandubaraja/comet"
            connection = "scm:git:git://github.com/pandubaraja/comet.git"
            developerConnection = "scm:git:ssh://git@github.com/pandubaraja/comet.git"
        }
    }
}
