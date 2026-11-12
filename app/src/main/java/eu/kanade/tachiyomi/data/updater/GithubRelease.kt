package eu.kanade.tachiyomi.data.updater

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Release object.
 * Contains information about the latest release from GitHub.
 *
 * @param version tag rilis. Di repo ini tag selalu tetap (`apk-terbaru`), jadi JANGAN dipakai
 *   sebagai penanda versi — yang berubah tiap build adalah [waktuAset].
 * @param info log of latest release.
 * @param assets assets of latest release.
 */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val version: String = "",
    @SerialName("body") val info: String = "",
    @SerialName("html_url") val releaseLink: String = "",
    @SerialName("prerelease") val preRelease: Boolean? = null,
    @SerialName("assets") val assets: List<Assets> = emptyList(),
) {

    /** Aset APK yang cocok dengan ABI perangkat (CI menamainya `app-standard-<abi>-nightly.apk`). */
    val asetAbi: Assets?
        get() {
            val variasi = when (Build.SUPPORTED_ABIS.getOrNull(0)) {
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a" -> "armeabi-v7a"
                "x86" -> "x86"
                "x86_64" -> "x86_64"
                else -> ""
            }
            return assets.firstOrNull { it.nama.contains("app-standard-$variasi-") }
                ?: assets.firstOrNull { it.nama.contains("universal") }
                ?: assets.firstOrNull()
        }

    /**
     * Get download link of latest release from the assets.
     * @return download link of latest release.
     */
    val downloadLink: String
        get() = asetAbi?.downloadLink.orEmpty()

    /**
     * Waktu unggah aset APK (milidetik).
     *
     * Dipakai sebagai pengganti perbandingan versi: CI mengunggah ulang aset ke satu tag tetap
     * (`--clobber`), jadi `tag_name` tidak pernah berubah sedangkan waktu unggah aset selalu.
     */
    val waktuAset: Long
        get() {
            val teks = asetAbi?.diunggahPada.orEmpty()
            if (teks.isBlank()) return 0L
            return runCatching {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(teks)?.time ?: 0L
            }.getOrDefault(0L)
        }

    /**
     * Assets class containing download url.
     * @param downloadLink download url.
     */
    @Serializable
    data class Assets(
        @SerialName("name") val nama: String = "",
        @SerialName("browser_download_url") val downloadLink: String = "",
        @SerialName("updated_at") val diunggahPada: String = "",
        @SerialName("size") val ukuran: Long = 0L,
    )
}
