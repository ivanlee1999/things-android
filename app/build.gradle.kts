import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "us.liyifan.things"
    compileSdk = 37

    defaultConfig {
        applicationId = "us.liyifan.things"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Preview builds carry the build time in their name so the "next" release on GitHub can
        // be told apart from the last one at a glance on the device's app info screen.
        if ((project.findProperty("IS_NEXT") as String?)?.toBoolean() == true) {
            versionName = "$versionName-next-${SimpleDateFormat("dd.MM.yyyy-HH:mm").format(Date())}"
        }

        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    signingConfigs {
        create("release") {
            // CI decodes the keystore from a secret and exports these. A local build has none,
            // and produces an unsigned APK rather than failing.
            System.getenv("STORE_FILE")?.let {
                storeFile = file(it)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        // Debug is signed with the release key when CI has one, so the nightly "next" APK and a
        // tagged release can update over each other instead of demanding an uninstall.
        getByName("debug") {
            if (System.getenv("STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.androidx.work.testing)
}

// `./gradlew -q printVersionName` — the release workflow names the APK with it. The value is
// read at configuration time but printed in the action, so it does not pollute other builds.
tasks.register("printVersionName") {
    val name = android.defaultConfig.versionName
    doLast { println(name) }
}
