import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ─────────────────────────────────────────────────────────────────────────────
// Подпись релиза постоянным ключом.
// Порядок поиска ключа:
//   1. переменные окружения SPOTY_KEYSTORE_FILE / SPOTY_KEYSTORE_PASSWORD /
//      SPOTY_KEY_ALIAS / SPOTY_KEY_PASSWORD (так CI подставляет ключ из секретов);
//   2. файл signing/signing.properties + signing/spoty-release.p12 из репозитория.
// См. signing/README.md.
// ─────────────────────────────────────────────────────────────────────────────
val signingDir: File = rootProject.file("signing")
val signingProps = Properties().apply {
    val f = File(signingDir, "signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun envOrProp(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)

val releaseStoreFile: File? = (
    System.getenv("SPOTY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: signingProps.getProperty("storeFile")?.let { File(signingDir, it) }
    )?.takeIf { it.exists() }

// ─────────────────────────────────────────────────────────────────────────────
// Версия. versionCode растёт со временем коммита, поэтому любая более новая сборка
// устанавливается поверх старой (даже если файл workflow когда-нибудь пересоздадут).
// ─────────────────────────────────────────────────────────────────────────────
fun gitOutput(vararg args: String): String? = try {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
} catch (e: Exception) {
    null
}

val commitEpochSeconds: Long = gitOutput("log", "-1", "--format=%ct")?.toLongOrNull() ?: 0L
val appVersionCode: Int = if (commitEpochSeconds > 0) {
    ((commitEpochSeconds / 60L) - 29_000_000L).toInt().coerceAtLeast(1)
} else {
    1
}
val runNumber: String = System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotBlank() } ?: "0"
val gitSha: String = (System.getenv("GITHUB_SHA")?.take(7)
    ?: gitOutput("rev-parse", "--short=7", "HEAD")
    ?: "local")
val jamendoClientId: String = System.getenv("JAMENDO_CLIENT_ID")?.trim().orEmpty()

android {
    namespace = "io.github.zigorminsk.spoty"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zigorminsk.spoty"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "1.0.$runNumber"

        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "REPO_URL", "\"https://github.com/zigorminsk-debug/spoty\"")
        buildConfigField("String", "DEFAULT_JAMENDO_CLIENT_ID", "\"$jamendoClientId\"")
    }

    androidResources {
        localeFilters.addAll(listOf("ru", "en"))
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = envOrProp("SPOTY_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = envOrProp("SPOTY_KEY_ALIAS", "keyAlias")
                keyPassword = envOrProp("SPOTY_KEY_PASSWORD", "keyPassword")
                storeType = envOrProp("SPOTY_KEYSTORE_TYPE", "storeType") ?: "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // Отладочная сборка подписывается тем же постоянным ключом, чтобы
            // обновляться поверх релизной и наоборот, но ставится как отдельное приложение.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
