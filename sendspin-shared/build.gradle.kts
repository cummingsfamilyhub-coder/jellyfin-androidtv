plugins {
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sendspindroid.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/commonMain/kotlin",
                "src/androidMain/kotlin",
                "src/jvmShared/kotlin",
            )
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xmulti-platform",
            "-Xexpect-actual-classes",
        )
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-websockets:3.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.1.1")
}
