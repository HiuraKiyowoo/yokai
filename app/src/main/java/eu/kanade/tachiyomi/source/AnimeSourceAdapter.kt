// Adaptor: sumber anime generasi baru → pipeline manga milik Yokai.
//
// Kenapa begini, bukan menulis ulang UI: seluruh layar Yokai (Beranda, pustaka, riwayat,
// pencarian) berbicara dalam model manga (SManga / SChapter / Page). Daripada mengubah
// puluhan layar, kita bungkus sumber anime supaya tampil sebagai CatalogueSource biasa.
// Hasilnya: katalog 248 ekstensi bisa dipakai tanpa menyentuh UI.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers

/** Satu halaman pembaca yang sebenarnya adalah satu varian stream (hoster + kualitas). */
class VideoPage(
    index: Int,
    url: String,
    val video: Video?,
    val headers: Headers? = null,
) : Page(index = index, url = url)

/**
 * Bungkus [AnimeCatalogueSource] sebagai [CatalogueSource] (manga).
 *
 * Catatan tahap ini:
 * - Filter: UI Yokai mengirim [FilterList] (manga), sedangkan ekstensi anime meminta
 *   [AnimeFilterList]. Untuk sementara dikirim daftar kosong, jadi pencarian berjalan
 *   tanpa filter lanjutan. Ini batasan yang disengaja, bukan kelalaian.
 * - Stream: semua hoster × semua varian video dipetakan menjadi halaman, sehingga pemutar
 *   bisa memilih; pemilihan kualitas di UI menyusul di tahap kedua.
 */
class AnimeSourceAdapter(
    private val anime: AnimeCatalogueSource,
) : CatalogueSource {

    override val id: Long get() = anime.id
    override val name: String get() = anime.name
    override val lang: String get() = anime.lang
    override val supportsLatest: Boolean get() = anime.supportsLatest

    override fun getFilterList(): FilterList = FilterList()

    override suspend fun getPopularManga(page: Int): MangasPage =
        anime.getPopularAnime(page).toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        anime.getLatestUpdates(page).toMangasPage()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        anime.getSearchAnime(page, query, AnimeFilterList()).toMangasPage()

    override suspend fun getMangaDetails(manga: SManga): SManga =
        anime.getAnimeDetails(manga.toSAnime()).toSManga()

    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        anime.getEpisodeList(manga.toSAnime()).map { it.toSChapter() }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val episode = chapter.toSEpisode()
        val hoster = anime.getHosterList(episode)
        val halaman = mutableListOf<Page>()
        for (h in hoster) {
            // `videoList` boleh kosong saat hoster bersifat lazy: ambil saat dibutuhkan.
            val videos = h.videoList ?: runCatching { anime.getVideoList(h) }.getOrDefault(emptyList())
            for (v in videos) {
                if (v.videoUrl.isBlank() || v.videoUrl == "null") continue
                halaman += VideoPage(
                    index = halaman.size,
                    url = v.videoUrl,
                    video = v,
                    headers = v.headers ?: episodeHeader(chapter),
                )
            }
        }
        return halaman
    }

    /** Header yang menempel pada episode (kalau ada) — dipakai bila video tidak membawa sendiri. */
    private fun episodeHeader(chapter: SChapter): Headers? = null

    // ---------- pemetaan model ----------

    private fun eu.kanade.tachiyomi.animesource.model.AnimesPage.toMangasPage() =
        MangasPage(animes.map { it.toSManga() }, hasNextPage)

    private fun SAnime.toSManga(): SManga = SManga.create().also { m ->
        m.url = url
        m.title = title
        m.thumbnail_url = thumbnail_url
        m.artist = artist
        m.author = author
        m.description = description
        m.genre = genre
        m.status = status
        m.initialized = initialized
    }

    private fun SManga.toSAnime(): SAnime = SAnime.create().also { a ->
        a.url = url
        a.title = title
        a.thumbnail_url = thumbnail_url
        a.artist = artist
        a.author = author
        a.description = description
        a.genre = genre
        a.status = status
        a.initialized = initialized
    }

    private fun SEpisode.toSChapter(): SChapter = SChapter.create().also { c ->
        c.url = url
        c.name = name
        c.chapter_number = episode_number
        c.date_upload = date_upload
        c.scanlator = scanlator
    }

    private fun SChapter.toSEpisode(): SEpisode = SEpisode.create().also { e ->
        e.url = url
        e.name = name
        e.episode_number = chapter_number
        e.date_upload = date_upload
        e.scanlator = scanlator
    }
}