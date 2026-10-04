plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.sendspindroid.shared"
        compileSdk = 36
        minSdk = 26
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmShared = create("jvmShared") {
            dependsOn(commonMain.get())
            dependencies {
                implementation("org.bouncycastle:bcprov-jdk18on:1.80")
            }
        }
        androidMain.get().dependsOn(jvmShared)

        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.1.1")
            implementation("io.ktor:ktor-client-websockets:3.1.1")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.1.1")
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvmToolchain(21)
}
