import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.profynus.music"
    ndkVersion = "27.1.12297006"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.profynus.music"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
        externalNativeBuild {
            cmake { arguments += "-DANDROID_STL=c++_static" }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildTypes.create("preview") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        versionNameSuffix = "-preview"
        matchingFallbacks += "release"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("androidTest").assets.directories += "src/test/js"
    sourceSets.getByName("main").res.directories += layout.buildDirectory.dir("generated/brand/res").get().asFile.path
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// Copy the supplied bitmap into generated resources; the original artwork stays in assets/.
val generateBrandResources by tasks.registering(Copy::class) {
    from(rootProject.file("assets/profynus.png"))
    into(layout.buildDirectory.dir("generated/brand/res/drawable-nodpi"))
    rename { "profynus_logo.png" }
}
tasks.named("preBuild").configure { dependsOn(generateBrandResources) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.media3:media3-transformer:1.11.1")
    // Retained only for the on-device A/B benchmark; excluded from the app APK.
    androidTestImplementation("de.sciss:jump3r:1.0.5")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}