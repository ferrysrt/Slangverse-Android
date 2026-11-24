plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stagingUrl = providers.environmentVariable("SLANGVERSE_SUPABASE_URL").orElse("").get().trim().trimEnd('/')
val stagingKey = providers.environmentVariable("SLANGVERSE_SUPABASE_PUBLISHABLE_KEY").orElse("").get().trim()
require((stagingUrl.isEmpty() && stagingKey.isEmpty()) ||
    (stagingUrl.matches(Regex("https://[a-z0-9]+\\.supabase\\.co")) && stagingKey.matches(Regex("sb_publishable_[A-Za-z0-9_-]+")))) {
    "Set a Supabase HTTPS project URL and publishable key together for the staging build"
}

android {
    namespace = "id.slangverse.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "id.slangverse.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.8.3-participant-preview"
        manifestPlaceholders["slangverseAppLabel"] = "Slangverse"
        buildConfigField("String", "STAGING_SUPABASE_URL", "\"\"")
        buildConfigField("String", "STAGING_SUPABASE_PUBLISHABLE_KEY", "\"\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes.getByName("debug") {
        if (stagingUrl.isNotEmpty()) {
            applicationIdSuffix = ".staging"
            manifestPlaceholders["slangverseAppLabel"] = "Slangverse Staging"
        }
        buildConfigField("String", "STAGING_SUPABASE_URL", "\"$stagingUrl\"")
        buildConfigField("String", "STAGING_SUPABASE_PUBLISHABLE_KEY", "\"$stagingKey\"")
    }
    if (stagingUrl.isNotEmpty()) {
        sourceSets.getByName("debug").assets.srcDir("../../question-bank")
    }
    sourceSets.getByName("main").res.srcDir("../../assets/profile-pictures/android-res")
    // Robolectric renders the real composables on the JVM; this device's ROM cannot resume an
    // instrumentation activity, so a device-hosted Compose rule would hang instead of testing.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    testImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.withType<Test>().configureEach {
    systemProperty("slangverse.repositoryRoot", rootProject.projectDir.parentFile.absolutePath)
    systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
}
