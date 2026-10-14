plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false
    // I used the supported Google services plugin for client configuration (Google, 2026m).
    id("com.google.gms.google-services") version "4.5.0" apply false
}
