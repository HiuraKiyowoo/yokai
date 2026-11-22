// Sumber pengganti ketika ID sumber belum/tidak bisa ditemukan.
//
// Kenapa perlu: `SourceManager` mengisi peta sumber secara asinkron dari
// `installedExtensionsFlow`. Saat app dibuka langsung ke layar jelajah (navigasi yang
// dipulihkan), `get(id)` masih kosong → `BrowseSourcePresenter` dulu melakukan `?: return`,
// sehingga `lateinit var source` tidak pernah terisi dan app CRASH dengan
// "lateinit property source has not been initialized" (terbukti di log perangkat).
//
// Dengan sumber ini layar tetap terbuka dan hanya kosong; tidak ada crash.
// Begitu ekstensi selesai dimuat, layar yang dibuka ulang akan memakai sumber aslinya.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

class UnavailableSource(override val id: Long) : CatalogueSource {
    override val name: String = "Sumber tidak tersedia"
    override val lang: String = ""
    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)

    override suspend fun getSearchManga(page: Int, query: String, filters: eu.kanade.tachiyomi.source.model.FilterList): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(manga, chapters)

    override suspend fun getMangaDetails(manga: SManga): SManga = manga

    override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()

    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}