plugins {
    kotlin("multiplatform")
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sendspindroid.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("io.ktor:ktor-client-websockets:3.1.1")
            }
        }

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.bouncycastle:bcprov-jdk18on:1.80")
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.1.1")
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
