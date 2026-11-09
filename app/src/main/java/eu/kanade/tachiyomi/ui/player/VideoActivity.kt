package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private fun siapkan(url: String) {
        val p = ExoPlayer.Builder(this).build()
        pemutar = p
        binding.pemutar.player = p

        p.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Host kadang menolak permintaan tanpa header tertentu. Biarkan
                    // kontrol tetap tampil supaya pengguna bisa memilih kualitas lain
                    // dari daftar episode, bukan layar mati tanpa penjelasan.
                    binding.pemutar.controllerShowTimeoutMs = 0
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

        fun intent(context: Context, url: String): Intent =
            Intent(context, VideoActivity::class.java).putExtra(KUNCI_URL, url)
    }
}