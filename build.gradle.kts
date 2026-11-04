import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.intellijPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// The IDE compiled against and run by runIde: an installed one with -PplatformPath=<install folder> (or platformPath in
// local.properties, not committed, which IDE syncs read since they can't pass -P), or an IntelliJ IDEA downloaded with
// -PplatformVersion=<version>, e.g. on CI.
val platformPath: String? = providers.gradleProperty("platformPath").orNull?.takeIf { it.isNotBlank() }
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("platformPath")?.takeIf { it.isNotBlank() }
    }
val platformVersion: String? = providers.gradleProperty("platformVersion").orNull?.takeIf { it.isNotBlank() }
if (platformPath == null && platformVersion == null) {
    error("Set the IDE: -PplatformPath=<IDE install folder> (installed) or -PplatformVersion=<version> (downloaded)")
}

// IDEs verifyPlugin checks compatibility with, from -PverifyIdes: installed IDE folders separated by ';', and/or
// "recommended" for the versions JetBrains recommends for the since/until range (downloaded).
val verifyIdes: List<String> = providers.gradleProperty("verifyIdes").orNull.orEmpty()
    .split(';').map { it.trim() }.filter { it.isNotEmpty() }

// Compose Multiplatform ships inside the plugin, with Skiko's native library for each desktop OS.
val desktopTargets = listOf("windows-x64", "windows-arm64", "macos-x64", "macos-arm64", "linux-x64", "linux-arm64")

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Interface default methods stay in the interfaces, so classes implementing platform interfaces, such as
        // ToolWindowFactory, don't get compiler-generated overrides of their experimental default methods.
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

repositories {
    google()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.fast.compose)
    implementation(libs.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    val composeVersion = libs.versions.composeMultiplatform.get()
    desktopTargets.forEach { implementation("org.jetbrains.compose.desktop:desktop-jvm-$it:$composeVersion") }

    intellijPlatform {
        if (platformPath != null) local(platformPath) else intellijIdea(platformVersion!!)
        pluginVerifier()
        zipSigner()
    }
}

configurations.runtimeClasspath {
    // The IDE provides Kotlin and coroutines.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    // Kotlin reflection, which the engine's JarResolver uses.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-swing")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-slf4j")
    // Images load through Coil's OkHttp fetcher (see ImageLoading); Ktor needs a newer coroutines than the IDE's.
    exclude(group = "io.ktor")
    exclude(group = "io.coil-kt.coil3", module = "coil-network-ktor3")
    exclude(group = "io.coil-kt.coil3", module = "coil-network-ktor3-jvm")
}

tasks.processResources {
    // Plugin and engine versions shown in issue reports.
    val versions = mapOf("pluginVersion" to version.toString(), "engineVersion" to libs.versions.fastCompose.get())
    inputs.properties(versions)
    filesMatching("fast-compose-versions.properties") { expand(versions) }
}

// Project opened by the run tasks, from -PrunIdeProject=<path>.
val runIdeProject = providers.gradleProperty("runIdeProject").orNull?.takeIf { it.isNotBlank() }

tasks.runIde {
    runIdeProject?.let { args(it) }
    // runIde starts the IDE in internal mode, where Android Studio's analytics throw on startup ("call to
    // AnalyticsSettings before initialization"). Added last, so it overrides the default.
    jvmArgumentProviders += CommandLineArgumentProvider { listOf("-Didea.is.internal=false") }
}

// Change notes of this version: its "## <version>" section of CHANGELOG.md, as an HTML list. Built in run { } so the
// mapping lambda captures only the version string, not the build script, as the configuration cache requires.
val releaseNotes = run {
    val versionName = version.toString()
    providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.map { changelog ->
        val lines = changelog.lines()
        val start = lines.indexOfFirst { it.trim() == "## $versionName" }
        check(start >= 0) { "CHANGELOG.md has no \"## $versionName\" section" }
        val items = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }.filter { it.startsWith("- ") }
        items.joinToString("", "<ul>", "</ul>") { item ->
            val text = item.removePrefix("- ").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<li>" + text.replace(Regex("`([^`]+)`"), "<code>$1</code>") + "</li>"
        }
    }
}

intellijPlatform {
    // The plugin has no settings to search, and indexing them starts the IDE.
    buildSearchableOptions = false

    pluginConfiguration {
        changeNotes = releaseNotes
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    // Never from the repository: the certificate chain and private key as contents from the environment (CI), or as
    // files with -PcertificateChainFile=<chain.crt> -PprivateKeyFile=<private.pem> (local, any shell); the key's
    // password from PRIVATE_KEY_PASSWORD.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        providers.gradleProperty("certificateChainFile").orNull?.let { certificateChainFile = file(it) }
        providers.gradleProperty("privateKeyFile").orNull?.let { privateKeyFile = file(it) }
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // A pre-release version such as 0.2.0-beta.1 goes to its channel (beta); others to the default channel.
        channels = providers.gradleProperty("pluginVersion").map { version ->
            listOf(version.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            verifyIdes.forEach { if (it == "recommended") recommended() else local(it) }
        }
    }
}
