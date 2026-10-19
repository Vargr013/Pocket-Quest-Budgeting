import com.google.gms.googleservices.GoogleServicesTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val firebaseDemoSource = rootProject.layout.projectDirectory.file("config/firebase/google-services.demo.json")
val firebaseDemoTarget = layout.projectDirectory.file("src/debug/google-services.json")
val firebaseCommonConfigs = listOf("google-services.json", "src/google-services.json", "src/Debug/google-services.json")
    .map { layout.projectDirectory.file(it).asFile }
    .filter { it.canonicalFile != firebaseDemoTarget.asFile.canonicalFile }

tasks.register("prepareFirebaseDemo") {
    group = "firebase"
    description = "Prepares the synthetic debug Firebase client for builds and emulator tests."
    doLast {
        check(firebaseCommonConfigs.none { it.exists() }) {
            "A Firebase client is already selected. Keep it; demo setup will not replace or override it. See README.md."
        }
        val source = firebaseDemoSource.asFile
        val target = firebaseDemoTarget.asFile
        check(!target.exists() || target.readBytes().contentEquals(source.readBytes())) {
            "The debug Firebase client differs. Keep it; demo setup will not replace it. See README.md."
        }
        if (!target.exists()) {
            target.parentFile.mkdirs()
            source.copyTo(target)
        }
        logger.lifecycle("Prepared demo-pocket-quest for debug builds/emulator tests only. Normal cloud login needs the team's real client configuration.")
    }
}

tasks.withType<GoogleServicesTask>().configureEach {
    doFirst {
        if (googleServicesJsonFiles.get().none { it.isFile }) {
            throw GradleException(
                "Firebase client configuration is missing. Put the team's google-services.json in app/google-services.json. " +
                    "For debug builds/emulator tests only, run :app:prepareFirebaseDemo first. " +
                    "The demo client does not support normal cloud login or release builds. See README.md: Firebase setup.",
            )
        }
    }
}

android {
    namespace = "com.example.pocketquestbudgeting"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.pocketquestbudgeting"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // I kept the main modules on one BoM without changing the Kotlin compiler (Google, 2026m; Google, 2026af).
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.foundation:foundation-layout:1.12.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    // I added the 2.10 network-constraint fixes without upgrading existing dependencies (Google, 2026an).
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
