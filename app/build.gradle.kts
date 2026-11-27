import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

android {
    namespace = "com.luiscoronel.halley"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.luiscoronel.halley"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Los dos teléfonos de prueba son arm64-v8a: se descartan las librerías nativas de las otras ABIs
        // (Vosk, JNA y sherpa-onnx traen cuatro o más).
        ndk {
            abiFilters += "arm64-v8a"
        }

        // Selector de motor de palabra clave (1.2c), temporal hasta decidir el motor: se borra junto con el que pierda.
        // ./gradlew installDebug -PwakeEngine=vosk (por defecto sherpa).
        val wakeEngine = providers.gradleProperty("wakeEngine").getOrElse("sherpa")
        val wakeEngines = setOf("sherpa", "vosk")
        if (wakeEngine !in wakeEngines) {
            throw GradleException("wakeEngine=$wakeEngine no es válido; usar uno de $wakeEngines")
        }
        buildConfigField("String", "WAKE_ENGINE", "\"$wakeEngine\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    buildFeatures {
        compose = true
        // Solo para WAKE_ENGINE (selector temporal del 1.2c).
        buildConfig = true
    }
    lint {
        // targetSdk 36 con compileSdk 37.1 es intencional: ver "Decisiones tomadas" en CLAUDE.md.
        // Revisar esta exclusión al probar en Android 17 (cuando se suba targetSdk a 37).
        disable += "OldTargetApi"
        // Solo arm64-v8a (abiFilters): ChromeOS no es un objetivo. Revisar si se vuelve a sumar x86_64.
        disable += "ChromeOsAbiSupport"
    }
}

/**
 * Descarga un archivo, verifica su SHA-256 fijado y lo deja en una caché local (~/.gradle/jervy-models/,
 * fuera de caches/ para que la limpieza automática de Gradle no lo borre); con la caché llena el build
 * funciona sin red. La descarga pasa solo al compilar: la app no tiene permiso INTERNET (NF3).
 */
abstract class DownloadVerifiedTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:Internal
    abstract val offline: Property<Boolean>

    protected fun cachedFile(extension: String): File {
        val expected = sha256.get().lowercase()
        val dir = cacheDir.get().asFile.apply { mkdirs() }
        val cached = dir.resolve("$expected.$extension")
        if (cached.isFile && sha256Of(cached) == expected) return cached

        if (offline.get()) {
            throw GradleException("${url.get()} no está en la caché ($cached) y Gradle está en modo offline.")
        }
        val partial = File(cached.path + ".part")
        logger.lifecycle("Descargando ${url.get()}")
        val connection = URI(url.get()).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw GradleException("No se pudo descargar ${url.get()}: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }

        val actual = sha256Of(partial)
        if (actual != expected) {
            partial.delete()
            throw GradleException("SHA-256 no coincide para ${url.get()}: esperado $expected, obtenido $actual")
        }
        Files.move(partial.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return cached
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Descarga un modelo (.zip o .tar.bz2) verificado y lo descomprime como assets del APK. */
abstract class DownloadModelTask : DownloadVerifiedTask() {
    /** Carpeta dentro de assets/ donde queda el modelo descomprimido. */
    @get:Input
    abstract val assetPath: Property<String>

    /** Archivos del modelo que se empaquetan (patrones sobre la ruta sin la carpeta raíz); vacío = todos. */
    @get:Input
    abstract val includes: ListProperty<String>

    /** Escribe el archivo `uuid` que usa vosk-android (StorageService.sync) para saber si ya copió el modelo. */
    @get:Input
    abstract val writeVoskUuid: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun run() {
        val zip = url.get().endsWith(".zip")
        val archive = cachedFile(if (zip) "zip" else "tar.bz2")
        val tree = if (zip) archives.zipTree(archive) else archives.tarTree(archives.bzip2(archive))
        val target = outputDir.get().dir(assetPath.get()).asFile
        val patterns = includes.get()

        fileSystem.delete { delete(outputDir) }
        fileSystem.copy {
            from(tree)
            into(target)
            // Saca la carpeta raíz del archivo (p. ej. vosk-model-small-es-0.42/).
            eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
            if (patterns.isNotEmpty()) include(patterns.map { "*/$it" })
            includeEmptyDirs = false
        }
        if (writeVoskUuid.get()) target.resolve("uuid").writeText(sha256.get().lowercase())
    }
}

/** Descarga una librería (.aar) verificada, para usarla como dependencia de archivo. */
abstract class DownloadAarTask : DownloadVerifiedTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        cachedFile("aar").copyTo(outputFile.get().asFile, overwrite = true)
    }
}

// Nombre de la época de "Jervy": se mantiene para no volver a descargar el modelo de Vosk.
val downloadCacheDir = File(gradle.gradleUserHomeDir, "jervy-models")

val downloadVoskModel =
    tasks.register<DownloadModelTask>("downloadVoskModel") {
        group = "halley"
        description = "Descarga y verifica el modelo de Vosk (español) para la palabra clave."
        url.set(libs.versions.voskModelUrl)
        sha256.set(libs.versions.voskModelSha256)
        assetPath.set("models/vosk-es")
        includes.set(emptyList())
        writeVoskUuid.set(true)
        cacheDir.set(downloadCacheDir)
        offline.set(gradle.startParameter.isOffline)
    }

val downloadSherpaKwsModel =
    tasks.register<DownloadModelTask>("downloadSherpaKwsModel") {
        group = "halley"
        description = "Descarga y verifica el modelo KWS de sherpa-onnx (inglés) para la palabra clave."
        url.set(libs.versions.sherpaKwsModelUrl)
        sha256.set(libs.versions.sherpaKwsModelSha256)
        assetPath.set("models/sherpa-kws-en")
        // Solo int8 y tokens (~5 MB de los 17): sin los fp32, bpe.model, keywords de ejemplo ni test_wavs.
        includes.set(listOf("encoder-*.int8.onnx", "decoder-*.int8.onnx", "joiner-*.int8.onnx", "tokens.txt"))
        writeVoskUuid.set(false)
        cacheDir.set(downloadCacheDir)
        offline.set(gradle.startParameter.isOffline)
    }

val downloadSherpaAar =
    tasks.register<DownloadAarTask>("downloadSherpaAar") {
        group = "halley"
        description = "Descarga y verifica el AAR de sherpa-onnx (releases de GitHub)."
        url.set(libs.versions.sherpaOnnxAarUrl)
        sha256.set(libs.versions.sherpaOnnxAarSha256)
        cacheDir.set(downloadCacheDir)
        offline.set(gradle.startParameter.isOffline)
        outputFile.set(layout.buildDirectory.file("libs/sherpa-onnx-${libs.versions.sherpaOnnx.get()}.aar"))
    }

tasks.register("downloadWakeWordModels") {
    group = "halley"
    description = "Descarga y verifica todos los modelos de palabra clave."
    dependsOn(downloadVoskModel, downloadSherpaKwsModel)
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)
    implementation(files(downloadSherpaAar.flatMap { it.outputFile }))
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadVoskModel, DownloadModelTask::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(downloadSherpaKwsModel, DownloadModelTask::outputDir)
    }
}
