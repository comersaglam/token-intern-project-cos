plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mock_pos"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.mock_pos"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // Java 21 = the Android Studio JBR, which ships jlink. This keeps JdkImageTransform
        // on a jlink-capable JDK (fixes the jlink-not-found error) AND avoids Gradle trying to
        // download a JDK we don't have (we have 11 and 21 installed, not 17). minSdk (24) is
        // unchanged, so device compatibility is unaffected — compile-time language level only
        // (D8 desugars to Dalvik regardless). Kept in sync with app-pos.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}