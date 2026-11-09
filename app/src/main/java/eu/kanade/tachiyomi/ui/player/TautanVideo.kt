package eu.kanade.tachiyomi.ui.player

/**
 * Menentukan apakah sebuah "halaman" sebenarnya tautan video, bukan gambar.
 *
 * Dipakai supaya aplikasi otomatis membuka pemutar video alih-alih pembaca
 * gambar. Ada dua penanda, karena sebagian host tidak memakai ekstensi:
 *
 *  1. ekstensi berkas di URL (.mp4/.mkv/.webm/.m3u8/.mov)
 *  2. nama host yang memang penyedia video
 *
 * Kalau kelak ada provider baru yang memakai host lain, tambahkan di sini —
 * satu tempat, tidak perlu menyentuh alur pembaca.
 */
object TautanVideo {

    private val EKSTENSI = listOf(".mp4", ".mkv", ".webm", ".m3u8", ".mov", ".m4v")

    private val HOST_VIDEO = listOf(
        "pixeldrain.com",
        "drive.usercontent.google.com",
        "mega.nz",
        "ok.ru",
        "/kdrive/",
        "workers.dev",
    )

    fun apakahVideo(url: String): Boolean {
        if (url.isBlank()) return false
        val tanpaParam = url.substringBefore('?').lowercase()
        if (EKSTENSI.any { tanpaParam.endsWith(it) }) return true
        val utuh = url.lowercase()
        return HOST_VIDEO.any { utuh.contains(it) }
    }
}