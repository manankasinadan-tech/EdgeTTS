plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
}

abstract class RustBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    abstract val rustDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val rustDirFile = rustDir.orNull?.asFile ?: return
        if (!rustDirFile.isDirectory) return

        val hasCargo = try {
            ProcessBuilder("which", "cargo").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
        if (!hasCargo) {
            println("Cargo not found in PATH, using prebuilt JNI libraries.")
            return
        }

        val outPath = outputDir.get().asFile.absolutePath
        val targets = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        targets.forEach { abi ->
            println("Building Rust library for $abi with cargo ndk...")
            val pb = ProcessBuilder("bash", "-lc", "cargo ndk -t $abi --platform 24 -o \"$outPath\" build --release")
                .directory(rustDirFile)
                .inheritIO()
            val exitCode = pb.start().waitFor()
            if (exitCode != 0) {
                println("Warning: cargo ndk exited with code $exitCode")
            }
        }
    }
}

val rustBuildJniLibs by tasks.registering(RustBuildTask::class) {
    group = "build"
    description = "Build Rust JNI libraries for all supported Android ABIs with cargo ndk."
    rustDir.set(layout.projectDirectory.dir("../rust"))
    outputDir.set(layout.projectDirectory.dir("src/main/jniLibs"))
}

android {
    namespace = "top.initsnow.edge_tts_android"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "top.initsnow.edge_tts_android"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.matching {
    it.name.startsWith("merge") && it.name.endsWith("JniLibFolders")
}.configureEach {
    dependsOn(rustBuildJniLibs)
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    // Android Views & Material components used by Edge TTS Settings & Voice Picker
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation(libs.junit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
