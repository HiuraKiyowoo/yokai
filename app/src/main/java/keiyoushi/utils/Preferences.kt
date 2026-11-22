// Kelas bantuan yang dipakai ekstensi generasi Keiyoushi (mis. `by getPreferencesLazy()`).
//
// CATATAN KEJUJURAN: tanda tangan asli kelas ini berasal dari plugin Gradle
// `io.github.keiyoushi.gradle` yang menyuntiknya sebagai compileOnly. Sumber terbukanya
// tidak dipublikasikan di repo mana pun yang bisa kuakses, jadi implementasi di bawah
// disusun dari POLA PEMAKAIAN di 40 berkas ekstensi (receiver = sumber anime,
// kembalian = Lazy<SharedPreferences>). Kalau saat build/uiji muncul
// NoSuchMethodError, tanda tangannya harus disesuaikan — jangan dianggap benar sebelum diuji.
package keiyoushi.utils

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.animesource.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Preferensi per-ekstensi, dimuat malas. Dipakai ekstensi sebagai `private val prefs by getPreferencesLazy()`. */
fun AnimeSource.getPreferencesLazy(): Lazy<SharedPreferences> = lazy {
    PreferenceManager.getDefaultSharedPreferences(Injekt.get<Application>())
}

/** Varian untuk sumber yang tidak mewarisi AnimeSource langsung. */
fun Any.getPreferencesLazyAny(): Lazy<SharedPreferences> = lazy {
    PreferenceManager.getDefaultSharedPreferences(Injekt.get<Application>())
}