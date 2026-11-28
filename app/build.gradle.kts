import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val firebaseConfig = layout.projectDirectory.file("google-services.json").asFile
val hasUsageConfig = firebaseConfig.isFile
if (hasUsageConfig) {
    val config = JsonSlurper().parse(firebaseConfig) as Map<*, *>
    val project = config["project_info"] as? Map<*, *>
    val clients = config["client"] as? List<*>
    val hasAndroidApp = clients.orEmpty().filterIsInstance<Map<*, *>>().any { client ->
        val info = client["client_info"] as? Map<*, *>
        (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.tokipin.app"
    }
    check(project?.get("project_id") == "tokipin-app" && hasAndroidApp) {
        "app/google-services.json must be the com.tokipin.app Android config from Firebase project tokipin-app."
    }
    apply(plugin = "com.google.gms.google-services")
}

// A source checkout may run debug builds offline. A release must target the real project.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(hasUsageConfig) {
            "Download google-services.json for com.tokipin.app from Firebase project tokipin-app into app/ before building a release."
        }
    }
}

android {
    namespace = "com.tokipin.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Confirmed by the owner (2026-11-26). It cannot change after the first Play Console upload.
        applicationId = "com.tokipin.app"
        buildConfigField("boolean", "USAGE_SHARING_AVAILABLE", hasUsageConfig.toString())
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Each device test runs in its own process with fresh app data (no state carried over).
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        /**
         * Performance checks (:benchmark): release code and R8, profileable, debug-signed, with the
         * debug sample data so a benchmark can seed the large journal. Never shipped.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            proguardFiles("benchmark-rules.pro")
        }
    }
    sourceSets.getByName("benchmark") {
        java.directories.add("src/debug/java")
        kotlin.directories.add("src/debug/java")
        assets.directories.add("src/debug/assets")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Native UX v0.4 shared with iOS: one copy lives in the web repo (../tokipin/native-spec/v0.4), not
 * in this repository. Tests read it from there; the SHA-256 pin makes a changed or missing spec fail
 * the build instead of silently testing against another version.
 */
// -PtokipinSpecDir=<dir> points at another checkout of the same folder (e.g. while the sibling
// web repo is not on the commit that has it); the SHA-256 pin below still applies.
val uxSpecDir = providers.gradleProperty("tokipinSpecDir").orNull?.let(::file)
    ?: rootProject.file("../tokipin/native-spec/v0.4")
val uxSpecSha256 = "81a9e6fd8e8aa0b70d99389214e5914f4d28a472120e8786e2b7679b2f167203"

val verifyUxSpec by tasks.registering {
    group = "verification"
    val spec = uxSpecDir.resolve("Tokipin-Native-UX-v0.4.json")
    inputs.file(spec)
    doLast {
        if (!spec.isFile) throw GradleException("UX spec not found at $spec (clone elpalaiso/tokipin next to this repo)")
        val digest = MessageDigest.getInstance("SHA-256").digest(spec.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (digest != uxSpecSha256) throw GradleException("UX spec SHA-256 mismatch: $digest (pinned $uxSpecSha256)")
    }
}

android.sourceSets.getByName("test").resources.directories.add(uxSpecDir.path)
android.sourceSets.getByName("androidTest").assets.directories.add(uxSpecDir.path)

tasks.matching { it.name.startsWith("test") || it.name.startsWith("connected") }.configureEach {
    dependsOn(verifyUxSpec)
}

/**
 * Debug sample data is a port of the homepage generator (../tokipin/trial.js). This dumps the web
 * generator's output with node so SampleDataTest can compare every day; without node the test skips.
 */
val webSamplesDir = layout.buildDirectory.dir("generated/webSamples")
val dumpWebSamples by tasks.registering {
    val out = webSamplesDir.map { it.file("web-samples.json") }
    val web = rootProject.file("../tokipin")
    val script = rootProject.file("tools/web-samples.js")
    outputs.upToDateWhen { false }
    doLast {
        val target = out.get().asFile
        target.parentFile.mkdirs()
        target.delete()
        runCatching {
            val process = ProcessBuilder("node", script.path, web.path).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val text = process.inputStream.readBytes()
            if (process.waitFor() == 0) target.writeBytes(text)
        }.onFailure { logger.warn("node not available; SampleDataTest will skip the web comparison") }
    }
}
android.sourceSets.getByName("test").resources.directories.add(webSamplesDir.get().asFile.path)
tasks.matching { it.name.startsWith("process") && it.name.endsWith("UnitTestJavaRes") }.configureEach { dependsOn(dumpWebSamples) }
tasks.matching { it.name == "testDebugUnitTest" || it.name == "testReleaseUnitTest" }.configureEach { dependsOn(dumpWebSamples) }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.window)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestUtil(libs.androidx.test.orchestrator)
}
