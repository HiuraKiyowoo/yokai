# RENCANA PORT — Yokai menerima ekstensi generasi baru (lib 12–16)

Status: **Tahap 1 dimulai**. Semua angka di bawah hasil pengukuran dari repo, bukan perkiraan.

## Yang sudah dilakukan

| Langkah | Hasil |
|---|---|
| Unduh API anime resmi dari `yuzono/anime-extensions/extensions-lib` (Apache-2.0) | **34 berkas · 61 KB** |
| Pindahkan ke modul `app` (bukan modul terpisah) | **30 berkas** masuk: `animesource/` **22**, `keiyoushi/` **5**, `torrentutils/` **3** |
| Buang duplikat yang berbahaya | `AppInfo`, `network/*`, `util/*` dari lib **tidak dipakai** — fork sudah punya versi asli; menyalin stub akan membuat ekstensi memanggil `Exception("Stub!")` |
| Buat kelas `keiyoushi.*` yang hilang | `annotation/AnikkuOnly`, `annotation/AnimiruOnly`, `annotation/Source`, `utils/Preferences` (`getPreferencesLazy`), `utils/Parse` (`parseAs`) |

Kenapa di modul `app`: ekstensi memuat kelas dari classloader APK. Kalau API ini ditaruh di modul
library terpisah, ia tidak bisa melihat `NetworkHelper`/`AppInfo` milik fork dan hanya akan memakai
stub — ekstensi lalu gagal saat dipanggil, bukan saat dimuat (lebih sulit dilacak).

## Sisa pekerjaan Tahap 1

### a. Ganti 60 titik `Stub!` menjadi delegasi nyata (paling kritis)

Sebaran terukur:

| Berkas | Jumlah `Stub!` |
|---|---|
| `animesource/online/AnimeHttpSource.kt` | 37 |
| `animesource/online/ParsedAnimeHttpSource.kt` | 9 |
| `animesource/AnimeCatalogueSource.kt` | 7 |
| `animesource/AnimeSource.kt` | 4 |
| `animesource/model/{SAnime,SEpisode,HttpServer}.kt` | 1 tiap berkas |
| `torrentutils/*` | 1 tiap berkas |

Sebagian besar `open fun ... = throw Exception("Stub!")` **aman** karena memang wajib di-override
ekstensi. Yang **wajib diperbaiki** adalah anggota yang disuntik app, terutama:

```kotlin
// AnimeHttpSource.kt:31 — harus jadi DI, bukan stub
protected val network: NetworkHelper = throw Exception("Stub!")
```

Rencana: ganti menjadi `Injekt.get<NetworkHelper>()` (fork sudah punya `NetworkHelper` di
`core/main/.../network/`), dan beri nilai bawaan wajar untuk `versionId` (1) serta `getHomeUrl()` (baseUrl),
supaya ekstensi yang tidak meng-override tidak meledak.

### b. Dependensi yang perlu ditambahkan

| Kebutuhan | Dipakai oleh | Status di fork |
|---|---|---|
| `nanohttpd` | `model/HttpServer.kt` | **belum ada** → tambah, atau keluarkan berkas itu |
| `okhttp3`, `jsoup`, `injekt`, `androidx.preference`, `kotlinx-serialization` | seluruh modul | sudah ada |

### c. Loader ekstensi

1. Jendela versi: `1.3–1.6` → **`12–16`** (`ExtensionLoader.kt:51-52`), plus pesan galat yang menyebut rentang baru.
2. Dukung `index.pb` (gzip + protobuf) seperti yang sudah ada di `ExtensionIndexDto`.
3. Jangan lupa: filter yang sama ada di **dua** jalur (`ExtensionApi.kt:122-126` dan `:160-161`).

### d. Adaptor anime → pipeline manga milik Yokai

Yokai membaca katalog lewat antarmuka manga (`SManga`/`SChapter`/`Page`). Pemetaan:

| Anime (baru) | Manga (Yokai) |
|---|---|
| `SAnime` | `SManga` |
| `SEpisode` | `SChapter` |
| `AnimesPage` | `MangasPage` |
| `AnimeFilterList` / `AnimeFilter` | `FilterList` / `Filter` |
| `Video` (hoster) | "page" dengan URL stream → `VideoActivity` (media3) |

Adaptor didaftarkan sebagai `CatalogueSource` supaya seluruh UI yang ada (Beranda, pustaka, riwayat)
bekerja tanpa diubah.

### e. Verifikasi

1. Build di GitHub Actions (workflow `apk.yml` sudah ada di fork).
2. Pasang APK sebagai update, tambahkan repo Yuzono, buka 3 ekstensi.
3. Uji: daftar anime muncul → buka detail → daftar episode → putar satu stream.

## Ganti nama ke Maple (diminta user, dikerjakan setelah port beres)

| Perubahan | Dampak |
|---|---|
| Label aplikasi (`app_name` → "Maple") | aman: data, pustaka, riwayat, ekstensi utuh |
| `applicationId` (mis. `app.maple`) | app lama & baru **berdampingan**; data tidak ikut (wajib ekspor/impor backup); ekstensi muncul **dobel** di kedua app |
| Keystore app | tidak wajib diganti; ekstensi tidak terpengaruh karena diverifikasi tanda tangan ekstensinya, bukan tanda tangan app |

Urutan aman: label dulu → port stabil → backup → baru ganti `applicationId`.

## Risiko yang sudah terlihat

- **Tanda tangan `keiyoushi.utils`** disusun dari pola pemakaian, bukan dari sumber resmi (tidak
  dipublikasikan). Kalau muncul `NoSuchMethodError` saat uji, itu penyebabnya — bukan logika adaptor.
- **`AnimeHttpSource` 37 stub** berarti beberapa fungsi dasar memang harus diimplementasi fork;
  daftarnya harus diperiksa satu per satu, bukan diasumsikan aman.
- **Bentrok nama** dengan kelas lama harus dijaga: `eu.kanade.tachiyomi.network.*` dan `AppInfo`
  sudah ada di fork dan **tidak** boleh ditimpa versi stub.