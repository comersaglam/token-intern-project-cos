plugins {
    // Pure Kotlin/JVM module, no Android. No version here on purpose: AGP 9 puts
    // the Kotlin Gradle Plugin on the build classpath (with an unmanaged version),
    // so re-declaring a version would clash. We just apply the one already present.
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(11) // match :app (Java 11) so the bytecode is compatible
}

// No dependencies on purpose: this module holds pure domain models
// (data classes + enums) with zero third-party or Android dependencies.
// The compiler enforces that purity — adding e.g. a Room import would not resolve.
