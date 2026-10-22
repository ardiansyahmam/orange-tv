# Orange TV

> Buat TV lamamu segar kembali, sesegar memakan jeruk di siang yang panas.

Orange TV adalah launcher Android TV yang sederhana dan ringan — ditulis dari
nol, tanpa pustaka tambahan, hanya memakai framework Android bawaan. Tampilannya
meniru kesederhanaan Apple TV: satu layar utama, ubin-ubin kaca, dan tidak ada
yang berlebihan. Warna oranye hanya hadir sebagai sentuhan tipis — cincin fokus,
logo kecil, dan garis aksen — selebihnya kaca netral yang bersih.

## Fitur

- **Sederhana ala Apple TV** — satu layar: bar atas (logo, jam, tanggal Bahasa
  Indonesia), baris **Favorit**, kisi **Semua Aplikasi**, dan baris **Pengaturan**.
- **Mode Siang & Malam** — ganti mode dari chip di bar atas atau ubin Pengaturan;
  pilihan tersimpan di perangkat. Bawaan: Mode Malam.
- **Favorit milikmu sendiri** — tekan lama tombol OK pada aplikasi mana pun di
  "Semua Aplikasi" untuk menambahkannya ke Favorit; tekan lama pada ubin Favorit
  untuk menghapusnya (maksimal 8). Pada lari pertama, Favorit disemai otomatis
  dari aplikasi streaming populer yang terpasang (YouTube, Netflix, WeTV, Vidio,
  dan lain-lain), sesudah itu sepenuhnya terserah pemilik TV.
- **Fokus remote yang jelas** — ubin terfokus membesar halus dengan cincin oranye
  tipis; navigasi D-pad bergerak wajar antar baris.
- **Ringan** — satu APK kecil (tanpa AndroidX, tanpa pustaka eksternal),
  minSdk 26 (Android 8) sehingga cocok untuk TV lama ber-RAM kecil.

## Pasang lewat ADB

```bash
adb install -r orange-tv.apk
```

Setelah terpasang, tekan tombol Home di remote lalu pilih **Orange TV** sebagai
launcher bawaan (atau tetapkan lewat Pengaturan TV → Aplikasi → Aplikasi bawaan).

Menu Pengaturan di dalam Orange TV menyediakan jalan pintas ke **Pengaturan TV**
(setelan Android) dan **Muat Ulang Aplikasi** untuk menyegarkan daftar setelah
memasang/mencopot aplikasi.

## Membangun

Dua jalur yang didukung:

1. **Gradle biasa** (di mesin dengan Android SDK): struktur proyek standar —
   `settings.gradle`, `app/build.gradle`, modul `app`. Buka di Android Studio
   atau jalankan `gradle assembleRelease`.
2. **Pipeline manual** (yang dipakai untuk APK di repo ini, tanpa Gradle):
   `aapt2 → javac → d8 → zipalign → apksigner`. Perintah persisnya terdokumentasi
   di [`build-manual.sh`](build-manual.sh).

Struktur kode (semua di `app/src/main/java/id/orangetv/launcher/`):

| Berkas | Isi |
| --- | --- |
| `MainActivity.java` | Layar utama, jam, baris Pengaturan, kelola Favorit |
| `UiTheme.java` | Seluruh warna mode Siang/Malam + aksen oranye |
| `AppCatalog.java` | Daftar aplikasi terpasang + semaian Favorit pertama |
| `AppEntry.java` | Model satu aplikasi |
| `TileFactory.java` | Ubin kaca + perilaku fokus |

## Lisensi

MIT © 2026 Ardiansyah Martha — lihat berkas [`LICENSE`](LICENSE).
