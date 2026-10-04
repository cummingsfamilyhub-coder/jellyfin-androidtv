plugins {
    kotlin("multiplatform")
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.music_assistant.sendspin.vendored"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
    }
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(rootProject.file("third_party/music-assistant-mobile/sendspin/src/commonMain/kotlin"))
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-atomicfu:0.33.0")
                implementation("io.ktor:ktor-client-core:3.5.2")
                implementation("io.ktor:ktor-client-websockets:3.5.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                implementation("co.touchlab:kermit:2.1.0")
                implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
            }
        }

        val androidMain by getting {
            kotlin.srcDir(rootProject.file("third_party/music-assistant-mobile/sendspin/src/androidMain/kotlin"))
            dependencies {
                implementation("dev.whyoleg.cryptography:cryptography-provider-jdk:0.6.0")
                implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
                implementation("io.github.jaredmdobson:concentus:1.0.2")
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
