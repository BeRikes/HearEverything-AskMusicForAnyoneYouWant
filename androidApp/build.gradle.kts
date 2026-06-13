plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
        }

        androidInstrumentedTest.dependencies {
            implementation(project(":shared"))
            implementation(kotlin("test"))
            implementation("androidx.compose.foundation:foundation:1.7.5")
            implementation("androidx.compose.material3:material3:1.3.1")
            implementation("androidx.compose.ui:ui:1.7.5")
            implementation("androidx.compose.ui:ui-test-junit4:1.7.5")
            implementation("androidx.activity:activity-compose:1.9.3")
        }
    }
}

android {
    namespace = "com.example.aimusicplayer"
    compileSdk = 35

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }

    defaultConfig {
        applicationId = "com.example.aimusicplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
