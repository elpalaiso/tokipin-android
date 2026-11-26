import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Native UX v0.1 shared with iOS: one copy lives in the web repo (../tokipin/native-spec/v0.1), not
 * in this repository. Tests read it from there; the SHA-256 pin makes a changed or missing spec fail
 * the build instead of silently testing against another version.
 */
val uxSpecDir = rootProject.file("../tokipin/native-spec/v0.1")
val uxSpecSha256 = "33c5f8d64e76b345fbc49ad7a76fc5ac5cc6556b6f0a7db061bfb2540e37c452"

val verifyUxSpec by tasks.registering {
    group = "verification"
    val spec = uxSpecDir.resolve("Tokipin-Native-UX-v0.1.json")
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
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
