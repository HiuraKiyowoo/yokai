// Implementasi konkret untuk antarmuka SEpisode.
//
// Sama seperti SAnimeImpl: `SEpisode.create()` di lib adalah stub, sedangkan ekstensi
// memakainya untuk membangun daftar episode. Tanpa ini, daftar episode selalu gagal.
package eu.kanade.tachiyomi.animesource.model

data class SEpisodeImpl(
    override var url: String = "",
    override var name: String = "",
    override var date_upload: Long = 0L,
    override var episode_number: Float = -1f,
    override var fillermark: Boolean = false,
    override var scanlator: String? = null,
    override var summary: String? = null,
    override var preview_url: String? = null,
) : SEpisode