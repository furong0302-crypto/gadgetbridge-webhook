import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import tasks.BuildGitChangelogTask
import tasks.GitMetadataExtension
import java.nio.file.Files

plugins {
    id("gadgetbridge.git")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kaitai)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val generatedGbDaoConf = configurations.create("generatedGbDao") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val generatedFitConf = configurations.create("generatedFit") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val generatedKaitaiAuxConf = configurations.create("generatedKaitaiAux") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val wireGbDao = tasks.register<Sync>("wireGbDao") {
    from(generatedGbDaoConf)
    into(layout.buildDirectory.dir("generated/sources/gbdao/synced"))
}

val wireFit = tasks.register<Sync>("wireFit") {
    from(generatedFitConf)
    into(layout.buildDirectory.dir("generated/sources/fit/synced"))
}

val wireKaitaiAux = tasks.register<Sync>("wireKaitaiAux") {
    from(generatedKaitaiAuxConf)
    into(layout.buildDirectory.dir("generated/sources/kaitaiaux/synced"))
}

val git = extensions.getByType<GitMetadataExtension>()

val changelogGen = tasks.register<BuildGitChangelogTask>("buildGitChangelog") {
    outputDirectory.set(layout.buildDirectory.dir("generated/res/changelog"))
    versionCode.set(git.commitCount)
    gitFiles.set(git.gitFiles)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "nodomain.freeyourgadget.gadgetbridge"

    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        manifestPlaceholders["oauthRedirectScheme"] = "nodomain.freeyourgadget.gadgetbridge"
        applicationId = "nodomain.freeyourgadget.gadgetbridge"
        // Note: always bump BOTH versionCode and versionName!
        versionName = "2026.11.1"
        versionCode = 257
        buildConfigField("boolean", "INTERNET_ACCESS", "false")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        lintConfig = layout.projectDirectory.file("src/main/lint.xml").asFile
        baseline = layout.projectDirectory.file("lint-baseline.xml").asFile
    }

    val nightlyStoreFile = providers.systemProperty("nightly_store_file")
    val nightlyStorePassword = providers.systemProperty("nightly_store_password")
    val nightlyKeyAlias = providers.systemProperty("nightly_key_alias")
    val nightlyKeyPassword = providers.systemProperty("nightly_key_password")

    signingConfigs {
        create("nightly") {
            if (nightlyStoreFile.isPresent) {
                storeFile = file(nightlyStoreFile.get())
                storePassword = nightlyStorePassword.orNull
                keyAlias = nightlyKeyAlias.orNull
                keyPassword = nightlyKeyPassword.orNull
            }
        }
    }

    flavorDimensions.add("device_type")
    productFlavors {
        create("mainline") {
            isDefault = true
            dimension = "device_type"
        }

        create("banglejs") {
            dimension = "device_type"
            applicationId = "com.espruino.gadgetbridge"
            applicationIdSuffix = ".banglejs"
            versionNameSuffix = "-banglejs"
            buildConfigField("boolean", "INTERNET_ACCESS", "true")
        }
    }

    sourceSets {
        getByName("test") {
            (resources as PatternFilterable).exclude("FIT-test-files-main/**")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        val nightlySigning = if (nightlyStoreFile.isPresent)
            signingConfigs.getByName("nightly")
        else
            signingConfigs.getByName("debug")

        create("nightly") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly"
            signingConfig = nightlySigning
        }

        create("nopebble") {
            initWith(getByName("release"))
            applicationIdSuffix = ".nightly_nopebble"
            signingConfig = nightlySigning
        }
    }
}

val defaultConfigVersionName: Provider<String> = providers.provider {
    android.defaultConfig.versionName?.takeIf { it.isNotEmpty() } ?: "1.0.0"
}
val flavorVersionNameSuffixes: Provider<Map<String, String>> = providers.provider {
    android.productFlavors.associate { flavor ->
        flavor.name to (flavor.versionNameSuffix?.takeIf { s -> s.isNotEmpty() } ?: "")
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.resValues.put(
            variant.makeResValueKey("string", "applicationId"),
            variant.applicationId.map { ResValue(it, null) }
        )

        val oauthRedirectScheme = variant.applicationId.map { it.replace('_', '-') }
        variant.manifestPlaceholders.put("oauthRedirectScheme", oauthRedirectScheme)
        variant.buildConfigFields?.put(
            "OAUTH_REDIRECT_SCHEME",
            oauthRedirectScheme.map { BuildConfigField("String", "\"$it\"", null) }
        )

        val gitHashShort: Provider<String> = git.gitHashShort
        val gitDirtyFlag: Provider<String> = git.gitDirtyFlag

        variant.buildConfigFields?.put(
            "GIT_HASH_SHORT",
            gitHashShort.map { hash -> BuildConfigField("String", "\"$hash\"", null) }
        )
        variant.buildConfigFields?.put(
            "GIT_DIRTY_STATUS",
            gitDirtyFlag.map { flag -> BuildConfigField("String", "\"$flag\"", null) }
        )

        variant.sources.java?.addGeneratedSourceDirectory(wireGbDao) { it.destinationDirectory }

        variant.sources.java?.addGeneratedSourceDirectory(wireFit) { it.destinationDirectory }

        variant.sources.kotlin?.addGeneratedSourceDirectory(wireKaitaiAux) { it.destinationDirectory }

        variant.sources.res?.addGeneratedSourceDirectory(changelogGen) { it.outputDirectory }

        if (variant.buildType == "nightly" || variant.buildType == "nopebble") {
            variant.outputs.forEach { output ->
                output.versionCode.set(git.commitCount)

                val baseVersion = defaultConfigVersionName
                val flavorSuffixes = variant.productFlavors.map { (_, flavorName) ->
                    flavorVersionNameSuffixes.get()[flavorName] ?: ""
                }.joinToString("")
                val finalVersionName = baseVersion.zip(git.gitHashShort) { version, hash ->
                    "${version}${flavorSuffixes}-${hash}"
                }
                output.versionName.set(finalVersionName)
                output.outputFileName.set(
                    variant.applicationId.zip(finalVersionName) { appId, versionName ->
                        "${appId}_${versionName}.apk"
                    }
                )
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    val miFirmwareDir = providers.systemProperty("MiFirmwareDir")
    if (miFirmwareDir.isPresent) {
        systemProperty("MiFirmwareDir", miFirmwareDir.get())
    }

    systemProperty("logback.configurationFile", layout.projectDirectory.file("src/main/assets/logback.xml").asFile.absolutePath)

    doFirst {
        systemProperty("GB_LOGFILES_DIR", Files.createTempDirectory("gblog").toString())
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX & UI
    implementation(libs.androidx.constraintlayout)
    implementation(libs.bundles.androidx.camera)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.material)
    implementation(libs.flexbox)

    // DI & Annotation Processing
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Third-party Libraries & Utilities
    implementation(libs.gson)
    implementation(libs.dfu)
    implementation(libs.logback.android)
    implementation(libs.slf4j.api)
    implementation(libs.mpandroidchart)
    implementation(libs.durationformatter)
    implementation(libs.ckchangelog)
    implementation(libs.coil)
    implementation(libs.cbor)
    implementation(libs.greendao)
    implementation(libs.apache.commons.lang3)
    implementation(libs.cyanogenmod.sdk)
    implementation(libs.colorpicker)
    implementation(libs.bundles.android.emojify)
    implementation(libs.protobuf.lite)
    implementation(libs.okhttp)
    implementation(libs.msgpack)
    implementation(libs.searchpreference)
    implementation(libs.bundles.mapsforge)
    implementation(libs.androidsvg)
    implementation(libs.jsoup)
    implementation(libs.kaitai.struct.runtime)
    implementation(libs.zxing.core)
    implementation(libs.ahocorasick)

    // Generator Subprojects (via consumable configurations)
    "generatedGbDao"(project(path = ":GBDaoGenerator", configuration = "generatedGbDao"))
    "generatedFit"(project(path = ":FitCodeGenerator", configuration = "generatedFit"))
    "generatedKaitaiAux"(project(path = ":KaitaiAuxGenerator", configuration = "generatedKaitaiAux"))

    // Protocol Buffer subproject
    implementation(project(":GBProtobufGenerator"))

    // Testing
    testImplementation(libs.bundles.unit.test)
}

kaitai {
    javaPackage = "nodomain.freeyourgadget.gadgetbridge.kaitai"
    readWrite = true
}
