plugins {
    alias(libs.plugins.android.library)
    // KSP runs Room's annotation processor (generates the DAO/database code).
    alias(libs.plugins.ksp)
}

android {
    // Same package as app-pos's data layer: the two apps are separate APKs with
    // different applicationIds, and the sources are a deliberate copy (like :core-domain).
    namespace = "com.example.app_pos.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        // Room schemas are exported so migrations can be diffed later (phase 4+).
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    compileOptions {
        // Java 21 to match :app and :core-domain (the JBR, jlink-capable, already installed).
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Domain models (Customer, Transaction, User, SellerDebt…) live in the pure module.
    implementation(project(":core-domain"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)          // Flow-returning DAO queries + suspend
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)    // Flow used across the Repository API
}
