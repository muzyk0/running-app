import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    jacoco
}

val localTrainingApiBaseUrl = "http://10.0.2.2:8080/"
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
fun String?.nullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
val trainingApiBaseUrlOverride = providers.gradleProperty("runningAppTrainingApiBaseUrl").orNull.nullIfBlank()
val releaseTrainingApiBaseUrl = listOfNotNull(
    trainingApiBaseUrlOverride,
    providers.gradleProperty("runningAppReleaseTrainingApiBaseUrl").orNull.nullIfBlank(),
    providers.environmentVariable("RUNNING_APP_RELEASE_TRAINING_API_BASE_URL").orNull.nullIfBlank(),
    localProperties.getProperty("runningAppReleaseTrainingApiBaseUrl").nullIfBlank(),
).firstOrNull()
val releaseTrainingApiBaseUrlErrorMessage =
    "Release builds require runningAppReleaseTrainingApiBaseUrl " +
        "in android-app/local.properties, a RUNNING_APP_RELEASE_TRAINING_API_BASE_URL " +
        "environment variable, or an explicit -PrunningAppTrainingApiBaseUrl override."
val releaseTrainingApiBaseUrlFallback = "https://invalid.running-app.example/"
val releaseKeystorePath = listOfNotNull(
    providers.gradleProperty("runningAppReleaseKeystoreFile").orNull.nullIfBlank(),
    providers.environmentVariable("RUNNING_APP_RELEASE_KEYSTORE_FILE").orNull.nullIfBlank(),
    keystoreProperties.getProperty("storeFile").nullIfBlank(),
).firstOrNull()
val releaseKeystorePassword = listOfNotNull(
    providers.gradleProperty("runningAppReleaseKeystorePassword").orNull.nullIfBlank(),
    providers.environmentVariable("RUNNING_APP_RELEASE_KEYSTORE_PASSWORD").orNull.nullIfBlank(),
    keystoreProperties.getProperty("storePassword").nullIfBlank(),
).firstOrNull()
val releaseKeyAlias = listOfNotNull(
    providers.gradleProperty("runningAppReleaseKeyAlias").orNull.nullIfBlank(),
    providers.environmentVariable("RUNNING_APP_RELEASE_KEY_ALIAS").orNull.nullIfBlank(),
    keystoreProperties.getProperty("keyAlias").nullIfBlank(),
).firstOrNull()
val releaseKeyPassword = listOfNotNull(
    providers.gradleProperty("runningAppReleaseKeyPassword").orNull.nullIfBlank(),
    providers.environmentVariable("RUNNING_APP_RELEASE_KEY_PASSWORD").orNull.nullIfBlank(),
    keystoreProperties.getProperty("keyPassword").nullIfBlank(),
).firstOrNull()
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }
val releaseSigningErrorMessage =
    "Release signing requires storeFile, storePassword, keyAlias, and keyPassword " +
        "in android-app/keystore.properties, matching RUNNING_APP_RELEASE_* environment " +
        "variables, or explicit -PrunningAppRelease* Gradle properties."
val androidCoverageThreshold = "0.80".toBigDecimal()
val androidCoverageIncludes = listOf(
    "com/vladislav/runningapp/activity/service/ActivityDistanceCalculator*",
    "com/vladislav/runningapp/activity/service/ActivityPointFilter*",
    "com/vladislav/runningapp/activity/service/ActivityTrackingCalculatorKt*",
    "com/vladislav/runningapp/ai/data/remote/TrainingGenerationMappersKt*",
    "com/vladislav/runningapp/core/datastore/DefaultDisclaimerPreferenceStore*",
    "com/vladislav/runningapp/core/navigation/RunningAppNavigationState*",
    "com/vladislav/runningapp/core/navigation/TopLevelDestination*",
    "com/vladislav/runningapp/core/permissions/*",
    "com/vladislav/runningapp/core/startup/AppBootstrapper*",
    "com/vladislav/runningapp/core/startup/AppStartupViewModel*",
    "com/vladislav/runningapp/core/startup/DefaultStartupDestinationResolver*",
    "com/vladislav/runningapp/core/storage/ProfileTypeConverters*",
    "com/vladislav/runningapp/profile/DefaultProfileRepository*",
    "com/vladislav/runningapp/profile/ProfileFormKt*",
    "com/vladislav/runningapp/profile/ProfileFormValidator*",
    "com/vladislav/runningapp/session/DefaultWorkoutSessionController*",
    "com/vladislav/runningapp/session/WorkoutSessionEngine*",
    "com/vladislav/runningapp/session/audio/DefaultSessionCuePlayer*",
    "com/vladislav/runningapp/training/data/local/WorkoutEntityKt*",
    "com/vladislav/runningapp/training/domain/WorkoutTransportModelsKt*",
    "com/vladislav/runningapp/training/ui/WorkoutEditorReducer*",
    "com/vladislav/runningapp/training/ui/WorkoutEditorStateKt*",
    "com/vladislav/runningapp/training/ui/WorkoutEditorValidator*",
)
val androidCoverageExcludes = listOf(
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*\$Companion.class",
    "**/*ComposableSingletons*",
    "**/*\$inlined$*",
    "**/*Hilt*.*",
    "**/*_Factory*",
    "**/*_MembersInjector*",
)

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.vladislav.runningapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vladislav.runningapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "TRAINING_API_BASE_URL",
                "\"${trainingApiBaseUrlOverride ?: localTrainingApiBaseUrl}\"",
            )
        }

        getByName("release") {
            buildConfigField(
                "String",
                "TRAINING_API_BASE_URL",
                "\"${releaseTrainingApiBaseUrl ?: releaseTrainingApiBaseUrlFallback}\"",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

jacoco {
    toolVersion = "0.8.13"
}

kapt {
    correctErrorTypes = true
}

val validateReleaseTrainingApiBaseUrl = tasks.register("validateReleaseTrainingApiBaseUrl") {
    group = "verification"
    description = "Ensures release builds define a training API base URL."

    doLast {
        check(releaseTrainingApiBaseUrl != null) {
            releaseTrainingApiBaseUrlErrorMessage
        }
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Ensures release builds define signing credentials."

    doLast {
        check(releaseSigningConfigured) {
            releaseSigningErrorMessage
        }
    }
}

tasks.matching { task ->
    task.name == "preReleaseBuild" || task.name == "generateReleaseBuildConfig"
}.configureEach {
    dependsOn(validateReleaseTrainingApiBaseUrl)
    dependsOn(validateReleaseSigning)
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val debugKotlinClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile
val debugJavaClassesDir = layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile
val androidCoverageClassDirectories = files(
    fileTree(debugKotlinClassesDir) {
        include(androidCoverageIncludes)
        exclude(androidCoverageExcludes)
    },
    fileTree(debugJavaClassesDir) {
        include(androidCoverageIncludes)
        exclude(androidCoverageExcludes)
    },
)
val androidCoverageSourceDirectories = files("src/main/java")
val androidCoverageExecutionData = fileTree(layout.buildDirectory.asFile.get()) {
    include(
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        "jacoco/testDebugUnitTest.exec",
    )
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(androidCoverageClassDirectories)
    sourceDirectories.setFrom(androidCoverageSourceDirectories)
    executionData.setFrom(androidCoverageExecutionData)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoDebugUnitTestCoverageVerification") {
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(androidCoverageClassDirectories)
    sourceDirectories.setFrom(androidCoverageSourceDirectories)
    executionData.setFrom(androidCoverageExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = androidCoverageThreshold
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.google.gson)
    implementation(libs.google.material)
    implementation(libs.google.play.services.location)
    implementation(libs.hilt.android)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.gson)
    kapt(libs.androidx.room.compiler)
    kapt(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.squareup.okhttp.mockwebserver)
}
