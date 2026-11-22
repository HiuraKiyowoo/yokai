package eu.kanade.tachiyomi.extension.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.source.AnimeSourceAdapter
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import yokai.domain.extension.interactor.TrustExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Class that handles the loading of the extensions installed in the system.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object ExtensionLoader {

    private val preferences: PreferencesHelper by injectLazy()
    private val trustExtension: TrustExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSources().get()
    }

    private const val EXTENSION_FEATURE = "tachiyomi.extension"

    /**
     * Nama `uses-feature` yang menandai sebuah paket sebagai ekstensi.
     *
     * Ekstensi generasi baru (repo Yuzono / ekosistem Aniyomi-Keiyoushi) memakai
     * `aniyomix.extension`, bukan `tachiyomi.extension`. Kalau hanya nama lama yang diterima,
     * ekstensinya tetap terpasang di Android tetapi TIDAK PERNAH muncul di daftar aplikasi —
     * gejalanya persis "sudah di-install tapi tidak ada".
     */
    private val EXTENSION_FEATURES = setOf(
        EXTENSION_FEATURE,
        // Nama yang BENAR-BENAR dipakai ekstensi anime (diverifikasi dengan membongkar
        // AndroidManifest.xml biner, bukan dari dugaan): <uses-feature name="tachiyomi.animeextension">
        "tachiyomi.animeextension",
        "aniyomix.extension",
        "aniyomi.extension",
        "keiyoushi.extension",
    )
    private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
    private const val METADATA_SOURCE_CLASS_ANIME = "tachiyomi.animeextension.class"
    private const val METADATA_SOURCE_FACTORY = "tachiyomi.extension.factory"
    private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
    private const val METADATA_NSFW_ANIME = "tachiyomi.animeextension.nsfw"
    private const val METADATA_HAS_README = "tachiyomi.extension.hasReadme"
    private const val METADATA_HAS_CHANGELOG = "tachiyomi.extension.hasChangelog"
    private const val METADATA_NAME = "tachiyomix.name"
    private const val METADATA_NAME_ANIME = "aniyomix.name"
    private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
    private const val METADATA_EXTENSION_LIB_ANIME = "aniyomix.extensionLib"
    private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
    private const val METADATA_CONTENT_WARNING_ANIME = "aniyomix.contentWarning"
    /**
     * Jendela versi library ekstensi yang diterima.
     *
     * Sebelumnya 1.3–1.6 (generasi lama). Dinaikkan ke 12–16 supaya ekstensi generasi baru
     * (repo Yuzono: 189 entri ber-lib 14, 59 entri ber-lib 16) bisa dimuat.
     *
     * PENTING: angka ini hanya GERBANG. Ekstensi baru juga membutuhkan kelas
     * `eu.kanade.tachiyomi.animesource.*` + `keiyoushi.*` yang sudah dimasukkan ke modul app,
     * dan adaptor ke pipeline manga. Menaikkan angka tanpa itu hanya akan membuat entri
     * muncul lalu gagal saat dibuka.
     */
    const val LIB_VERSION_MIN = 12
    const val LIB_VERSION_MAX = 16

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "ext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "exts")

    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        val extension = context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
            ?.takeIf { isPackageAnExtension(it) } ?: return false
        val currentExtension = getExtensionPackageInfoFromPkgName(context, extension.packageName)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                Logger.e { "Installed extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                Logger.e { "Extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                Logger.e { "Installed extension signature is not matched." }
                return false
            }
        }

        val target = File(getPrivateExtensionDir(context), "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
        return try {
            file.copyTo(target, overwrite = true)
            if (currentExtension != null) {
                ExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                ExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            Logger.e { "Failed to copy extension file." }
            target.delete()
            false
        }
    }

    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    private fun getExtensionsPackages(context: Context): List<ExtensionInfo> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        // Diagnostik: tanpa ini, "daftar ekstensi kosong" tidak bisa dibedakan antara
        // (a) paket tidak terlihat app, (b) fitur tidak dikenali, (c) ditolak versi lib.
        run {
            val kandidat = installedPkgs.filter {
                it.packageName.contains(".extension") || it.packageName.contains("animeextension")
            }
            android.util.Log.w("YOKAI_EXT", "pkg total=${installedPkgs.size} kandidat=${kandidat.size}")
            kandidat.forEach { p ->
                val fitur = p.reqFeatures.orEmpty().joinToString(",") { it.name }
                android.util.Log.w(
                    "YOKAI_EXT",
                    "  ${p.packageName} | fitur=[$fitur] | cocok=${isPackageAnExtension(p)}",
                )
            }
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageAnExtension(it) }
            .map { ExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                it.setReadOnly()
                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageAnExtension(it) }
            ?.map { ExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        return (sharedExtPkgs + privateExtPkgs)
            // Remove duplicates. Shared takes priority than private by default
            .distinctBy { it.packageInfo.packageName }
            // Compare version number
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()
    }

    /**
     * Return a list of all the installed extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadExtensions(context: Context): List<LoadResult> {
        val extPkgs = getExtensionsPackages(context)
        // Load each extension concurrently and wait for completion
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it) }
            }
            deferred.awaitAll()
        }
    }

    suspend fun loadExtensionAsync(context: Context): List<LoadResult> {
        val extPkgs = getExtensionsPackages(context)
        // Load each extension concurrently and wait for completion
        return withIOContext {
            val deferred = extPkgs.map {
                async { loadExtension(context, it) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): LoadResult {
        val extensionPackage = getExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            Logger.e { "Extension package is not found ($pkgName)" }
            return LoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }
    fun isExtensionPrivate(context: Context, pkgName: String): Boolean =
        getExtensionInfoFromPkgName(context, pkgName)?.isShared == false

    fun extensionInstallDate(context: Context, extension: Extension.Installed): Long {
        return try {
            if (!extension.isShared) {
                val file = ExtensionLoader.privateExtensionFile(context, extension.pkgName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    attr.creationTime().toMillis()
                } else {
                    file.lastModified()
                }
            } else {
                context.packageManager.getPackageInfo(extension.pkgName, 0).firstInstallTime
            }
        } catch (e: java.lang.Exception) {
            0
        }
    }

    fun extensionUpdateDate(context: Context, extension: Extension.Installed): Long {
        return try {
            if (!extension.isShared) {
                ExtensionLoader.privateExtensionFile(context, extension.pkgName).lastModified()
            } else {
                context.packageManager.getPackageInfo(extension.pkgName, 0).lastUpdateTime
            }
        } catch (e: java.lang.Exception) {
            0
        }
    }

    private fun privateExtensionFile(context: Context, pkgName: String): File =
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): ExtensionInfo? {
        val privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        val privatePkg = if (privateExtensionFile.isFile) {
            privateExtensionFile.setReadOnly()
            context.packageManager.getPackageArchiveInfo(privateExtensionFile.absolutePath, PACKAGE_FLAGS)
                ?.takeIf { isPackageAnExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageAnExtension(it) }
                ?.let {
                    ExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    @Suppress("DEPRECATION")
    fun isExtensionInstalledByApp(context: Context, pkgName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(pkgName).installingPackageName
            } else {
                context.packageManager.getInstallerPackageName(pkgName)
            } == BuildConfig.APPLICATION_ID
        } catch (e: java.lang.Exception) {
            false
        }
    }

    /**
     * Loads an extension
     *
     * @param context The application context.
     * @param extensionInfo The extension to load.
     */
    private suspend fun loadExtension(context: Context, extensionInfo: ExtensionInfo): LoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName
        val metaData = appInfo.metaData ?: return LoadResult.Error

        val extName = metaData.getString(METADATA_NAME)
            ?: metaData.getString(METADATA_NAME_ANIME)
            ?: pkgManager.getApplicationLabel(appInfo).toString()
                .substringAfter("Tachiyomi: ")
                .substringAfter("Aniyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Logger.w { "Missing versionName for extension $extName" }
            return LoadResult.Error
        }

        // Repositories publish this metadata as either a String or Float. Reading it through
        // Bundle.get() avoids Android's ClassCastException warning on Float-valued extensions.
        val libVersion = metaData.get(METADATA_EXTENSION_LIB)?.toString()?.toDoubleOrNull()
            ?: metaData.get(METADATA_EXTENSION_LIB_ANIME)?.toString()?.toDoubleOrNull()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            android.util.Log.w(
                "YOKAI_EXT",
                "TOLAK $pkgName lib=$libVersion (jendela $LIB_VERSION_MIN..$LIB_VERSION_MAX) vers=$versionName",
            )
            Logger.w {
                "Lib version is $libVersion, while only versions $LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return LoadResult.Error
        }
        android.util.Log.w("YOKAI_EXT", "LOLOS $pkgName lib=$libVersion vers=$versionName")

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            Logger.w { "Package $pkgName isn't signed" }
            return LoadResult.Error
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = Extension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            Logger.w { "Extension $pkgName isn't trusted" }
            return LoadResult.Untrusted(extension)
        }

        // Repositories publish this metadata as either a String or Int.
        val contentWarning = metaData.get(METADATA_CONTENT_WARNING)?.toString()?.toIntOrNull()
            ?: metaData.get(METADATA_CONTENT_WARNING_ANIME)?.toString()?.toIntOrNull()
            ?: 0
        val isNsfw = contentWarning > 0 ||
            metaData.getInt(METADATA_NSFW) == 1 ||
            metaData.getInt(METADATA_NSFW_ANIME) == 1
        if (!loadNsfwSource && isNsfw) {
            Logger.w { "NSFW extension $pkgName not allowed" }
            return LoadResult.Error
        }

        val hasReadme = metaData.getInt(METADATA_HAS_README, 0) == 1
        val hasChangelog = metaData.getInt(METADATA_HAS_CHANGELOG, 0) == 1

        val classLoader = try {
            ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Exception) {
            Logger.e(e) { "Extension load error: $extName ($pkgName)" }
            return LoadResult.Error
        }

        val sources = (
            metaData.getString(METADATA_SOURCE_CLASS)
                ?: metaData.getString(METADATA_SOURCE_CLASS_ANIME)
                ?: run {
                    Logger.w { "No source class metadata for $pkgName" }
                    return LoadResult.Error
                }
            )
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    val hasil: List<Source> = when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
                        is Source -> listOf(obj)
                        is SourceFactory -> obj.createSources()

                        // Ekstensi generasi baru (lib 12–16) mengekspor antarmuka anime,
                        // bukan Source manga. Bungkus dengan adaptor supaya masuk ke pipeline
                        // UI yang sudah ada, tanpa mengubah layar mana pun.
                        is AnimeCatalogueSource -> {
                            android.util.Log.w("YOKAI_EXT", "ADAPTOR ${obj.javaClass.name} (katalog)")
                            listOf<Source>(AnimeSourceAdapter(obj))
                        }
                        is AnimeSourceFactory -> {
                            val daftar = obj.createSources()
                            android.util.Log.w(
                                "YOKAI_EXT",
                                "ADAPTOR pabrik ${obj.javaClass.name} → ${daftar.size} sumber anime",
                            )
                            daftar.mapNotNull { a ->
                                when (a) {
                                    is AnimeCatalogueSource -> AnimeSourceAdapter(a) as Source
                                    else -> {
                                        android.util.Log.w(
                                            "YOKAI_EXT",
                                            "LEWAT ${a.javaClass.name} (bukan katalog)",
                                        )
                                        null
                                    }
                                }
                            }
                        }

                        else -> throw Exception("Unknown source class type! ${obj.javaClass}")
                    }
                    hasil
                } catch (e: Throwable) {
                    Logger.e(e) { "Extension load error: $extName." }
                    return LoadResult.Error
                }
            }

        val langs = sources.filterIsInstance<CatalogueSource>()
            .map { it.lang }
            .toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = Extension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = metaData.getString(METADATA_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return LoadResult.Success(extension)
    }

    /**
     * Choose which extension package to use based on version code
     *
     * @param shared extension installed to system
     * @param private extension installed to data directory
     */
    private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    fun isPackageNameAnExtension(packageManager: PackageManager, pkgName: String): Boolean =
        isPackageAnExtension(packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS))

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(pkgInfo: PackageInfo): Boolean =
        pkgInfo.reqFeatures.orEmpty().any { it.name in EXTENSION_FEATURES }

    /**
     * Returns the signatures of the package or null if it's not signed.
     *
     * @param pkgInfo The package info of the application.
     * @return List SHA256 digest of the signatures
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    /**
     * On Android 13+ the ApplicationInfo generated by getPackageArchiveInfo doesn't
     * have sourceDir which breaks assets loading (used for getting icon here).
     */
    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class ExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}
