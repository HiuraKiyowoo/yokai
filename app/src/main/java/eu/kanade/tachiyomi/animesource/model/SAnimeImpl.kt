// Implementasi konkret untuk antarmuka SAnime.
//
// Kenapa berkas ini wajib ada: di API ekstensi generasi baru, `SAnime` hanya berupa
// interface, dan `SAnime.create()` di lib masih stub (`throw Exception("Stub!")`).
// Ekstensi memanggil `SAnime.create()` untuk membangun objek hasil scraping — jadi
// tanpa implementasi ini setiap ekstensi baru gagal di baris pertamanya.
package eu.kanade.tachiyomi.animesource.model

data class SAnimeImpl(
    override var url: String = "",
    override var title: String = "",
    override var artist: String? = null,
    override var author: String? = null,
    override var description: String? = null,
    override var genre: String? = null,
    override var status: Int = SAnime.UNKNOWN,
    override var thumbnail_url: String? = null,
    override var background_url: String? = null,
    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    override var fetch_type: FetchType = FetchType.Episodes,
    override var season_number: Double = -1.0,
    override var initialized: Boolean = false,
) : SAnime