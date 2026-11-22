// Anotasi metadata sumber yang dipakai ekstensi generasi Keiyoushi.
//
// PENTING: anotasi ini di ekosistem aslinya dipakai oleh plugin Gradle untuk menghasilkan
// metadata (id, lang, nama) saat build. Untuk RUNTIME, app hanya perlu kelasnya ada supaya
// ekstensi bisa dimuat; isinya tidak dibaca app ini. Retensi BINARY supaya tidak ikut
// tersimpan sebagai runtime annotation di APK ekstensi.
package keiyoushi.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class Source(
    val name: String = "",
    val id: Long = 0,
    val lang: String = "en",
    val baseUrl: String = "",
    val versionId: Int = 0,
)