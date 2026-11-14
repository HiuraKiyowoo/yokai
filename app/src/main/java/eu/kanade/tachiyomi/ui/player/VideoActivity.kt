package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import eu.kanade.tachiyomi.databinding.ActivityVideoBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity

/**
 * Pemutar video sederhana untuk bab yang halamannya berupa tautan video.
 *
 * Sengaja memakai ExoPlayer + PlayerView bawaan media3 (bukan UI sendiri):
 * kontrol putar, jeda, durasi, dan gestur layar penuh sudah tersedia, jadi
 * yang perlu dirawat cuma bagian memuat tautan.
 *
 * Format yang didukung mengikuti ExoPlayer: MP4, Matroska (MKV), WebM, HLS.
 */
class VideoActivity : BaseActivity<ActivityVideoBinding>() {

    private var pemutar: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        layarPenuh()

        // Rotasi: pemutar mengikuti sensor di KEEMPAT orientasi, termasuk saat auto-rotate
        // ponsel dimatikan — perilaku lazim pemutar video. Manifest aktivitas ini sudah
        // memuat configChanges orientasi, jadi pemutaran tidak terulang saat layar berputar.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        binding.tombolPutarLayar.setOnClickListener { putarLayar() }

        val url = intent.getStringExtra(KUNCI_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        siapkan(url)
    }

    private fun layarPenuh() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Putar layar manual: tombol ini mengunci arah berlawanan dari arah sekarang, dan
     * menekannya lagi mengembalikan pemutar ke mode sensor (ikut putaran perangkat).
     */
    private fun putarLayar() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun siapkan(url: String) {
        // Sebagian CDN (mis. storages.animein.net) membalas 403 bila permintaan tidak
        // membawa User-Agent sama sekali. DefaultHttpDataSource yang dibuat tanpa
        // context/userAgent memang tidak mengirim UA, dan itu penyebab "video tidak
        // jalan" padahal tautannya sehat saat diuji dari terminal. Jadi UA selalu
        // dipasang, dan bila extension mengirim UA sendiri lewat extra, itu menang.
        val asal = runCatching {
            Uri.parse(url).let { "${it.scheme}://${it.host}/" }
        }.getOrNull()

        val header = HashMap<String, String>()
        asal?.let { header["Referer"] = it }
        intent.getStringArrayExtra(KUNCI_HEADER)?.let { arr ->
            for (h in arr) {
                val pisah = h.indexOf(':')
                if (pisah > 0) header[h.substring(0, pisah).trim()] = h.substring(pisah + 1).trim()
            }
        }
        header.putIfAbsent("User-Agent", UA_PEMUTAR)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(header["User-Agent"] ?: UA_PEMUTAR)
            .setDefaultRequestProperties(header)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)),
            )
            .build()
        pemutar = p
        binding.pemutar.player = p

        p.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Host kadang menolak permintaan tanpa header tertentu. Biarkan
                    // kontrol tetap tampil supaya pengguna bisa memilih kualitas lain
                    // dari daftar episode, bukan layar mati tanpa penjelasan.
                    binding.pemutar.controllerShowTimeoutMs = 0
                    if (error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        val kode = (error.cause as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                            .responseCode
                        if (kode == 403 || kode == 401) {
                            Toast.makeText(
                                this@VideoActivity,
                                "Server menolak ($kode). Coba pilih kualitas lain di daftar episode.",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
        )

        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        pemutar?.pause()
    }

    override fun onDestroy() {
        pemutar?.release()
        pemutar = null
        super.onDestroy()
    }

    companion object {
        private const val KUNCI_URL = "url_video"
        private const val KUNCI_HEADER = "header_video"

        /** UA pemutar: sebagian CDN membalas 403 bila permintaan tanpa User-Agent sama sekali. */
        private const val UA_PEMUTAR = "Mozilla/5.0 (Linux; Android 14; SM-A125F) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun intent(context: Context, url: String, header: List<String> = emptyList()): Intent =
            Intent(context, VideoActivity::class.java)
                .putExtra(KUNCI_URL, url)
                .putExtra(KUNCI_HEADER, header.toTypedArray())
    }
}