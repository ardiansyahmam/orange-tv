# Orange TV

> Buat TV lamamu segar kembali, sesegar memakan jeruk di siang yang panas.

Orange TV adalah launcher Android TV yang sederhana dan ringan — ditulis dari
nol, tanpa pustaka tambahan, hanya memakai framework Android bawaan. Sejak
v2.0 tampilannya bergaya layar utama konsol modern (ala PS5): satu strip ikon
di dekat atas layar, latar sinematik layar penuh yang mengikuti aplikasi yang
sedang dipilih, dan nama aplikasi tampil besar di kiri bawah. Warna oranye
hanya hadir sebagai sentuhan tipis — cincin fokus, logo kecil, dan garis
aksen — selebihnya kaca netral yang bersih.

> **v2.2.3 (hotfix)** — penanda fokus dibuat jauh lebih kentara. Di TV asli
> animasi membesar ubin ternyata terpotong kliping wadahnya, sehingga dari
> jarak sofa tak terlihat ubin mana yang sedang dipilih. Kini kliping anak
> dimatikan pada strip dan seluruh wadah di atasnya (ubin terfokus meluber
> keluar selnya, menimpa tetangga — pop ala PS5), skala fokus ubin naik
> 1,25x → 1,42x, cincin oranye fokus menebal 2,5dp → 4dp, elevation fokus
> naik ke 24, dan ubin yang tak terfokus lebih redup (alpha 0,70) demi
> kontras. Tombol bundar klaster ikut diperjelas (skala 1,28x, cincin 4dp)
> dan chip mode juga (skala 1,15x, cincin 4dp). Graf fokus v2.2.2 tidak
> berubah. Masih di v2.2.3: glif tombol Input digambar ulang menjadi
> simbol sumber klasik (layar persegi membulat + panah tebal masuk dari
> kiri) karena glif lama terbaca sebagai "Pengaturan"; wordmark kiri atas
> diperbesar 21sp → 26sp dan dibuat dua warna ("Orange" warna teks utama,
> "TV" oranye aksen) agar terbaca tegas; dan tombol Input tidak lagi
> diam-diam jatuh ke Pengaturan — bila intent VIEW_INPUT tidak ditangani
> TV, tombol ini kini mencoba membuka aplikasi TV milik penyedia
> TvInputService (vendor didahulukan), lalu aktivitas TV bawaan Android,
> dan hanya menampilkan pesan "Pemilih input tidak tersedia di TV ini"
> bila semuanya gagal.

> **v2.2.2 (hotfix)** — navigasi remote kini memakai graf fokus 4 arah yang
> eksplisit: Kiri/Kanan di strip dan di klaster kanan atas dirantai satu
> per satu, Bawah pada ubin menahan fokus di tempat, dan wadah (strip,
> kartu, bar atas) tidak bisa difokus. Di v2.2.1 hanya Atas/Bawah yang
> dirantai dan Kiri/Kanan mengandalkan pencarian fokus geometris Android,
> yang di TV asli membuat fokus tersangkut berputar di sekitar klaster
> kanan atas dan ubin-ubin awal strip. Tanpa perubahan fitur lain.

> **v2.2.1 (hotfix)** — intent-filter `MainActivity` sekarang mencantumkan
> kategori `android.intent.category.HOME`. Kategori ini hilang di v2.2
> sehingga launcher tidak pernah bisa dipilih sebagai aplikasi Beranda
> bawaan; v2.2.1 hanya memperbaiki itu, tanpa perubahan fitur lain.

## Fitur

- **Strip ikon ala konsol** — satu baris horizontal berisi **Favorit**, lalu
  **Semua Aplikasi**, lalu ubin alat (Mode, Efek Suara, Izin Waktu Layar,
  Muat Ulang). Ubin hanya berisi ikon; yang terfokus membesar ~1,42x dengan
  cincin oranye tebal dan cahayanya sendiri. Satu baris berarti navigasi
  remote cukup kiri/kanan — paling mudah ditebak. Titik-titik kecil di kiri
  bawah menandai seksi yang sedang aktif.
- **Latar sinematik dinamis** — setiap aplikasi punya latar layar penuh.
  Sejak **v2.1**, aplikasi populer yang terkurasi memakai **artwork
  sinematik original yang dibundel** di dalam APK (lihat daftar di bawah) —
  karya orisinal yang dibuat khusus untuk Orange TV: tanpa teks, tanpa
  logo, tanpa tokoh atau adegan dari film mana pun. Aplikasi lain memakai
  latar yang digambar langsung dari kode (gradien dalam + cahaya radial +
  vignette dari palet warna khas aplikasi: Netflix merah tua, WeTV biru
  dalam, Spotify hijau gelap, dan seterusnya; aplikasi tak dikenal
  mendapat palet turunan dari nama paketnya) — gradien yang sama juga
  menjadi lapisan bawah/cadangan di balik artwork. Latar berganti dengan
  crossfade halus mengikuti fokus, dan di Mode Siang artwork maupun
  gradien sama-sama diberi cucian terang agar tetap terbaca.
- **Klaster kanan atas** — tombol bundar **Input** (pemilih input TV),
  **Wi-Fi**, dan **Pengaturan**, chip mode Siang/Malam, serta jam dan
  tanggal Bahasa Indonesia.
- **Kartu info kaca** — di kanan bawah: **Waktu Layar Minggu Ini** (total
  pemakaian 7 hari dari UsageStats; butuh izin "akses penggunaan" — tanpa
  izin, kartu menampilkan "—" dan launcher tetap normal) dan
  **Penyimpanan Terpakai** (angka nyata dari StatFs).
- **Kartu jadwal pertandingan** (sejak **v2.2**) — kartu kaca di bawah
  strip, sisi kanan: titik merah + nama tim, "vs Lawan" besar, kompetisi
  dan status Kandang/Tandang, lalu hari · jam WIB · hitung mundur
  ("dalam 2 hari", "BESOK", "MALAM INI"). Cakupannya **Timnas Indonesia +
  liga top Eropa** (Premier League, La Liga, Serie A, Bundesliga, Liga
  Champions). Kartu hanya muncul bila pertandingan terdekat masuk
  jendela **H-2 sampai 3 jam sesudah kick-off**; di luar itu layar bersih
  seperti biasa. Datanya dari berkas `jadwal/jadwal.json` di repo ini
  (lihat bagian Jadwal di bawah) — launcher mengambilnya saat dibuka dan
  menyimpan cache terakhir untuk berjaga-jaga bila TV sedang offline.
- **Efek suara navigasi** — bunyi lembut saat fokus berpindah (berkas audio
  yang disediakan pemilik proyek), lonceng dua nada hasil sintesis saat
  memilih aplikasi, dan blip rendah saat menekan Kembali. Volume pelan,
  diberi throttle, dan bisa dimatikan dari ubin alat **Efek Suara**
  (tersimpan di perangkat; bawaan: nyala). Suara pelengkap dibuat dengan
  `scripts/gen-suara.py`.
- **Mode Siang & Malam** — ganti mode dari chip di kanan atas atau ubin
  Mode di strip; di Siang, latar sinematik yang sama diberi cucian terang
  agar tetap terbaca. Pilihan tersimpan di perangkat. Bawaan: Mode Malam.
- **Favorit milikmu sendiri** — tekan lama tombol OK pada ubin aplikasi mana
  pun untuk menambah/menghapusnya dari Favorit (maksimal 8). Pada lari
  pertama, Favorit disemai otomatis dari aplikasi streaming populer yang
  terpasang (YouTube, Netflix, WeTV, Vidio, dan lain-lain), sesudah itu
  sepenuhnya terserah pemilik TV. Format simpanan Favorit sama dengan v1,
  jadi upgrade berjalan di tempat.
- **Ringan** — satu APK kecil (tanpa AndroidX, tanpa pustaka eksternal),
  minSdk 26 (Android 8) sehingga cocok untuk TV lama ber-RAM kecil.

## Artwork latar (v2.1)

Tujuh artwork original dibundel di `app/src/main/res/drawable-nodpi/`
(sumber mentahnya di folder `artwork/`), masing-masing 1280px, JPEG ±80,
di bawah 250 KB:

| Berkas | Aplikasi | Suasana |
| --- | --- | --- |
| `latar_netflix.jpg` | Netflix | Anak-anak bersepeda dengan senter di hutan gelap, langit merah badai berpetir — nuansa petualangan retro |
| `latar_youtube.jpg` | YouTube / YouTube TV | Lounge kreator modern di malam hari, panel cahaya merah lembut yang menyala |
| `latar_wetv.jpg` | WeTV | Siluet pasangan di gang kota tua berlampion dan sakura, palet biru-hijau malam |
| `latar_vidio.jpg` | Vidio | Cakrawala Jakarta saat senja, langit magenta-jingga, lampu kota mulai menyala |
| `latar_disney.jpg` | Disney+ / Disney+ Hotstar | Kastil dongeng generik di bukit, langit biru berbintang, aurora, dan kunang-kunang |
| `latar_prime.jpg` | Prime Video | Jalan kota hujan bergaya film noir, neon biru, sosok berpayung |
| `latar_spotify.jpg` | Spotify | Konser gelap, siluet penonton, berkas cahaya panggung hijau, gitaris di panggung |

Semua artwork adalah karya original yang dihasilkan untuk proyek ini —
bukan poster, tangkapan layar, atau materi berhak cipta dari layanan mana
pun. Aplikasi di luar daftar ini tetap mendapat latar gradien tergambar.

## Jadwal pertandingan (v2.2)

Kartu jadwal membaca `jadwal/jadwal.json` dari repo ini
(`raw.githubusercontent.com/ardiansyahmam/orange-tv/main/jadwal/jadwal.json`).
Berkas itu dibuat oleh `scripts/ambil-jadwal.py` (Python murni, tanpa
dependensi) dari **TheSportsDB** — Timnas Indonesia lewat
`eventsnext`, liga-liga Eropa lewat `eventsround` — lalu diurutkan
menurut prioritas: Timnas dulu, big match (dua klub besar) berikutnya,
sisanya menurut waktu kick-off. Perbarui datanya kapan pun dengan:

```bash
python3 scripts/ambil-jadwal.py
git add jadwal/jadwal.json && git commit -m "Perbarui jadwal" && git push
```

Catatan: kunci API gratis TheSportsDB hanya memberi data terbatas
(mis. `eventsnextleague` hanya 1 pertandingan), karena itu skrip memakai
endpoint per-ronde. Bila suatu kompetisi kosong di API, kartunya cukup
tidak tampil — tidak ada jadwal yang dikarang.

## Pasang lewat ADB

```bash
adb install -r orange-tv.apk
```

Setelah terpasang, tekan tombol Home di remote lalu pilih **Orange TV** sebagai
launcher bawaan (atau tetapkan lewat Pengaturan TV → Aplikasi → Aplikasi bawaan).

Dari dalam Orange TV, klaster kanan atas membuka pemilih **Input**,
**Pengaturan Wi-Fi**, dan **Pengaturan TV**; ubin alat di ujung strip
menyediakan **Mode Siang/Malam**, **Efek Suara**, **Izin Waktu Layar**
(untuk kartu waktu layar), dan **Muat Ulang Aplikasi** untuk menyegarkan
daftar setelah memasang/mencopot aplikasi.

## Alat Debloat

Launcher yang ringan akan terasa percuma bila TV tetap dibebani bloatware.
Karena itu Orange TV dilengkapi alat debloat sendiri di folder
[`tools/`](tools/) — Python 3, satu berkas `tv.py`, tanpa pustaka tambahan.

Prinsipnya: **backup dulu, nonaktifkan per batch kecil, verifikasi setiap
batch, dan semua bisa dipulihkan**. Alat ini tidak pernah meng-uninstall;
yang dipakai hanya `pm disable-user --user 0`, dengan daftar larangan keras
untuk paket yang menopang TV (Play Services, SystemUI, kerangka HDMI/input
TV, keyboard, layanan remote, screensaver aktif, dan lain-lain).

```bash
python3 tools/tv.py --probe   --tv 192.168.1.10   # lihat isi TV (read-only)
python3 tools/tv.py --backup  --tv 192.168.1.10   # simpan cadangan (wajib)
python3 tools/tv.py --disable --tv 192.168.1.10 --list tools/lists/umum-bloat.txt
python3 tools/tv.py --restore --tv 192.168.1.10   # urungkan kapan pun
```

Panduan lengkap, model keamanan, daftar paket yang dikurasi (umum, hati-hati,
Xiaomi), dan kredit sumber kurasi: **[`tools/README.md`](tools/README.md)**.

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
| `MainActivity.java` | Layar utama v2: strip ikon, klaster kanan atas, kartu info, kelola Favorit |
| `Backdrop.java` | Latar sinematik per aplikasi: artwork original terbundel (v2.1) + gradien tergambar & palet warna |
| `UiTheme.java` | Seluruh warna mode Siang/Malam + aksen oranye |
| `AppCatalog.java` | Daftar aplikasi terpasang + semaian Favorit pertama |
| `AppEntry.java` | Model satu aplikasi |
| `TileFactory.java` | Ubin strip kaca, tombol bundar, kartu info + perilaku fokus |
| `Glyphs.java` | Ikon Input/Wi-Fi/roda gigi/bulan/speaker/dll. yang digambar dari kode |
| `SoundPlayer.java` | Efek suara navigasi via SoundPool (pelan, fail-safe) |

Suara pilih/kembali disintesis oleh `scripts/gen-suara.py` ke
`app/src/main/res/raw/`; suara pindah fokus adalah berkas MP3 yang
disediakan pemilik proyek di folder yang sama.

## Lisensi

MIT © 2026 Ardiansyah Martha — lihat berkas [`LICENSE`](LICENSE).
