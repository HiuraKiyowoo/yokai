// parseAs: pembantu deserialisasi respons OkHttp ke tipe Kotlin.
//
// `inline` + `reified` = fungsi ini DI-INLINE ke bytecode ekstensi saat kompilasi,
// sehingga app tidak wajib menyediakannya saat runtime. Tetap disediakan supaya
// signature-nya jelas dan bisa dipakai kode app sendiri.
package keiyoushi.utils

import kotlinx.serialization.json.Json
import okhttp3.Response

inline fun <reified T> Response.parseAs(
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
): T = json.decodeFromString(body.string())