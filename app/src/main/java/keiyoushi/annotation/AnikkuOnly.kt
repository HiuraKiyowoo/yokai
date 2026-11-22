// Anotasi penanda khusus app. Sumber: modul extensions-lib milik ekosistem Keiyoushi
// dipakai oleh ekstensi Yuzono. Dipakai untuk menandai API yang hanya ada di fork tertentu
// (Anikku / Animiru). Di app ini keduanya hanya penanda — tidak mengubah perilaku.
package keiyoushi.annotation

/** API yang hanya tersedia di Anikku. Implementasi di app ini: tidak ada penanganan khusus. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class AnikkuOnly

/** API yang hanya tersedia di Animiru. Implementasi di app ini: tidak ada penanganan khusus. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class AnimiruOnly