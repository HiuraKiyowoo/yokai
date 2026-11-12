package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.withIOContext
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.base.models.Version

class AppUpdateChecker(
    private val json: Json = Injekt.get(),
    private val networkService: NetworkHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) {

    suspend fun checkForUpdate(context: Context, isUserPrompt: Boolean = false, doExtrasAfterNewUpdate: Boolean = true): AppUpdateResult {
        // Limit checks to once a day at most
        if (!isUserPrompt && Date().time < preferences.lastAppCheck().get() + TimeUnit.DAYS.toMillis(1)) {
            return AppUpdateResult.NoNewUpdate
        }

        return withIOContext {
            val result = runCatching { periksaRilis(context) }
                .getOrDefault(AppUpdateResult.NoNewUpdate)
            preferences.lastAppCheck().set(Date().time)

            if (doExtrasAfterNewUpdate && result is AppUpdateResult.NewUpdate) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    preferences.appShouldAutoUpdate().get() != AppDownloadInstallJob.NEVER
                ) {
                    AppDownloadInstallJob.start(context, null, false, waitUntilIdle = true)
                }
                AppUpdateNotifier(context.localeContext).promptUpdate(result.release)
            }

            result
        }
    }

    /**
     * Memeriksa rilis aplikasi.
     *
     * Repo ini menerbitkan APK ke SATU rilis bertag tetap (`apk-terbaru`) dan tiap build hijau
     * menimpa asetnya (`gh release upload --clobber`). Updater bawaan membandingkan `tag_name`
     * dengan `VERSION_NAME`, jadi di repo ini ia selamanya berhenti di "tidak ada pembaruan".
     *
     * Yang dibandingkan di sini: **waktu unggah aset APK di GitHub** vs **waktu paket terpasang**
     * (`lastUpdateTime`). Kelonggaran 2 menit diberikan supaya APK yang baru dipasang dari rilis
     * yang sama tidak langsung dianggap usang.
     */
    private suspend fun periksaRilis(context: Context): AppUpdateResult {
        val rilis = networkService.client
            .newCall(GET("https://api.github.com/repos/$GITHUB_REPO/releases/tags/$RELEASE_TAG"))
            .await()
            .parseAs<GithubRelease>()

        val waktuAset = rilis.waktuAset
        if (waktuAset <= 0L || rilis.downloadLink.isBlank()) return AppUpdateResult.NoNewUpdate

        @Suppress("DEPRECATION")
        val terpasang = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .lastUpdateTime

        return if (waktuAset > terpasang + TimeUnit.MINUTES.toMillis(2)) {
            AppUpdateResult.NewUpdate(rilis)
        } else {
            AppUpdateResult.NoNewUpdate
        }
    }

    @VisibleForTesting
    fun isNewVersion(newVersion: String, currentVersion: String = BuildConfig.VERSION_NAME): Boolean =
        try {
            Version.parse(newVersion) > Version.parse(currentVersion)
        } catch (e: IllegalArgumentException) {
            false
        }
}

/** Repo tempat APK aplikasi diterbitkan. */
val GITHUB_REPO: String by lazy { "HiuraKiyowoo/yokai" }

/**
 * Tag rilis tetap. Asetnya ditimpa tiap build hijau, jadi tag ini tidak pernah berubah —
 * perbandingan versi memakai waktu unggah aset (lihat [GithubRelease.waktuAset]).
 */
val RELEASE_TAG: String by lazy { "apk-terbaru" }

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
