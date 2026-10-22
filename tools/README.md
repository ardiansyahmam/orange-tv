# Alat Debloat Orange TV (`tools/`)

Alat pendamping launcher Orange TV: menonaktifkan aplikasi bawaan (bloatware)
Android TV / Google TV yang tidak terpakai — telemetri vendor, baris iklan dan
rekomendasi, pendorong aplikasi otomatis, dan layanan latar yang tidak perlu —
supaya TV lama yang RAM-nya pas-pasan kembali ringan.

Ditulis dari nol dengan Python 3 (hanya pustaka standar), satu berkas:
[`tv.py`](tv.py). Alat ini **tidak pernah meng-uninstall apa pun**; yang dipakai
hanya `pm disable-user --user 0` (nonaktifkan) dan `pm enable --user 0`
(aktifkan lagi), jadi **semua perubahan bisa dibalikkan kapan pun**.

> Hanya untuk Android TV / Google TV. Samsung (Tizen), LG (webOS), Roku,
> VIDAA, dan sejenisnya bukan Android — tidak ada ADB dan tidak ada paket
> yang bisa dinonaktifkan di sana.

## Prasyarat

1. **Python 3** di komputer Anda (Windows/macOS/Linux).
2. **adb** (Android platform-tools) di PATH:
   - macOS: `brew install --cask android-platform-tools`
   - Linux: `sudo apt install adb` (atau paket `android-tools` di distro Anda)
   - Windows: `winget install Google.PlatformTools`, lalu buka terminal baru
3. **Komputer dan TV di jaringan yang sama** (Wi-Fi/LAN yang sama).
4. **Debugging aktif di TV**: Pengaturan → Preferensi Perangkat → Tentang →
   ketuk **Nomor bentukan (Build)** 7 kali → kembali ke Opsi Pengembang →
   nyalakan **USB debugging** (dan **Network/ADB debugging** / **Wireless
   debugging** bila ada). Catat alamat IP TV (Pengaturan → Jaringan).
   Saat pertama kali terhubung, TV menampilkan prompt *"Allow USB
   debugging?"* — centang *Always allow from this computer* lalu OK.

## Alur pemakaian

Selalu ikuti urutan ini. Jangan melompat langsung ke langkah 3.

### 1. Probe — lihat dulu isi TV (read-only, tidak mengubah apa pun)

```bash
python3 tv.py --probe --tv 192.168.1.10
```

Menulis laporan ke `tools/backup/probe-<waktu>/probe.txt`: model TV, versi
Android, RAM, penyimpanan, dan **seluruh paket beserta asal sistem/pengguna** —
plus fakta keselamatan: launcher (HOME) saat ini, keyboard yang aktif, dan
screensaver yang aktif. Dari laporan inilah daftar paket untuk TV Anda disusun.

### 2. Backup — wajib, dan harus masih segar

```bash
python3 tv.py --backup --tv 192.168.1.10
```

Menyimpan potret keadaan TV ke `tools/backup/<waktu>/`: semua daftar paket
(terpasang, nonaktif, sistem, pihak ketiga, aktif), `getprop`, isi setelan
(system/secure/global), aktivitas HOME, screensaver dan keyboard aktif, plus
`manifest.json`. `--disable` **menolak berjalan** bila cadangan tidak ada,
sudah lebih dari 24 jam (atur dengan `--max-backup-age`), atau berasal dari
perangkat yang berbeda.

### 3. Disable — per batch kecil, dengan verifikasi

```bash
python3 tv.py --disable --tv 192.168.1.10 --list lists/umum-bloat.txt
python3 tv.py --disable --tv 192.168.1.10 --list lists/xiaomi.txt --batch-size 4
```

Paket diproses dalam batch (bawaan 5). Setelah **setiap** batch, alat menekan
tombol Home dan membaca aktivitas teratas serta SSID Wi-Fi, lalu **berhenti
dan bertanya**: cek TV Anda — apakah Home tampil normal, remote berfungsi,
Wi-Fi tersambung, HDMI/Live TV bisa dibuka? Jawab `y` hanya bila semua normal.
Batch kecil membuat sumber masalah mudah dilacak bila ada yang terganggu.
Setiap paket yang berhasil dinonaktifkan **langsung dicatat** ke
`tools/state/state.json` (paket, waktu, batch, berkas daftar, cadangan acuan).

### 4. Status — cek kapan pun

```bash
python3 tv.py --status --tv 192.168.1.10
```

Ringkasan: model & versi Android, aktivitas teratas, SSID Wi-Fi, jumlah paket
nonaktif di TV, isi state alat ini, dan umur cadangan terakhir.

### 5. Restore — urungkan kapan pun

```bash
python3 tv.py --restore --tv 192.168.1.10                  # semua yang tercatat di state
python3 tv.py --restore --tv 192.168.1.10 --package com.contoh.paket   # satu paket saja
```

Tanpa komputer pun bisa: di TV, Pengaturan → Aplikasi → Lihat semua aplikasi →
Tampilkan aplikasi sistem → pilih aplikasi → **Aktifkan**.

### Dry-run — lihat rencana tanpa menyentuh TV

Semua perintah menerima `--dry-run`: semua perintah adb yang *akan* dijalankan
dicetak, tanpa ada yang dieksekusi dan tanpa butuh TV/adb:

```bash
python3 tv.py --disable --dry-run --list lists/umum-bloat.txt
```

## Model keamanan

1. **Tidak pernah uninstall.** System app hanya dinonaktifkan; data dan APK-nya
   tetap di TV dan bisa diaktifkan lagi dalam hitungan detik.
2. **Daftar larangan keras (denylist) di dalam `tv.py`.** Paket-paket ini
   ditolak dari berkas daftar mana pun, dengan alasan yang dicetak:
   Play Store / Play Services / GSF, SystemUI, Pengaturan, semua
   `com.android.providers.*`, database kanal TV, pemasang paket, pengontrol
   izin, WebView, keyboard/metode input apa pun, layanan remote, kerangka
   input TV (`tvinput`, `tvcenter`, `livetv`, `hdmi`, `cec`, `tuner`, `dtv`),
   Bluetooth, wizard penyiapan, launcher bawaan, dan Orange TV sendiri —
   termasuk paket vendor yang kelihatannya bloat padahal menopang remote,
   mis. `com.xiaomi.android.tvsetup.partnercustomizer` (layar pairing remote
   Bluetooth Xiaomi) dan `mitv.service`.
3. **Penjaga dari perangkat (runtime).** Selain denylist statis, `--disable`
   membaca keadaan TV dan menolak: paket apa pun yang terdaftar sebagai
   aplikasi HOME, paket keyboard yang sedang aktif, dan **paket screensaver
   yang sedang aktif** — menonaktifkan screensaver aktif membuat TV langsung
   tidur setelah beberapa menit diam (kejadian nyata, lihat Kredit).
4. **Wajib backup segar** untuk perangkat yang sama (lihat langkah 2).
5. **Konfirmasi ekstra untuk daftar hati-hati.** Paket di
   [`lists/hati-hati.txt`](lists/hati-hati.txt) (Chromecast, Asisten Google,
   screensaver, layanan update, dll.) hanya diterapkan dengan
   `--confirm-hati-hati` atau jawaban eksplisit di prompt.
6. **Batch kecil + verifikasi + jeda** (langkah 3), dan semua tercatat di
   state untuk restore yang persis.
7. **Matikan debugging sesudah selesai.** Port ADB yang terbuka membuat TV
   bisa dikendalikan perangkat lain di jaringan Anda. Setelah semua beres:
   Opsi Pengembang → matikan USB/Network debugging, pilih *Revoke USB
   debugging authorisations*, lalu restart TV.

## Berkas daftar (`lists/`)

Satu paket per baris; `#` untuk komentar/alasan. Nama paket adalah fakta yang
dikurasi dari referensi di bagian Kredit — organisasinya pilihan alat ini.

| Berkas | Isi | Jumlah |
| --- | --- | --- |
| [`lists/umum-bloat.txt`](lists/umum-bloat.txt) | Bloat lintas merek (paket Google umum): baris rekomendasi, pengirim feedback/bug report, sinkronisasi kalender/kontak, print spooler, shell uji Chromium | 8 paket |
| [`lists/hati-hati.txt`](lists/hati-hati.txt) | Hanya dengan konfirmasi ekstra: Chromecast built-in, Asisten Google & suara, Play Games, Play Movies, YouTube Music, screensaver Google & Xiaomi, layanan update Xiaomi | 9 paket |
| [`lists/xiaomi.txt`](lists/xiaomi.txt) | Xiaomi/Mi TV (PatchWall, firmware global): telemetri, iklan & baris konten, auto-install, aplikasi bawaan tak terpakai | 9 paket |

Catatan kejujuran cakupan:

- Daftar Xiaomi **terverifikasi di satu TV asli** (MiTV-MSSP2, Android TV 9,
  firmware global). Untuk model/firmware Xiaomi lain, cocokkan dulu dengan
  hasil `--probe` TV Anda.
- **Belum ada daftar terverifikasi untuk merek lain** (TCL, Sony, Hisense,
  Changhong, dll.) — disengaja: nama paket tidak ditebak. Susun daftar vendor
  Anda dari hasil `--probe`, dengan pola klasifikasi yang teruji dari repo
  sumber: nama mengandung `analytics`, `statistic`, `report`, `feedback`,
  `bugreport`, `tracker`, `ad`, `promo`, `recommend`, `channel`,
  `autoinstall`, `appstore`/`market` (toko vendor), `cleaner`/`booster`,
  `demo`/`retail`/`shop` *biasanya* aman dinonaktifkan **setelah** membaca
  aktivitas/layanan paket itu (`adb shell dumpsys package <paket>`); nama
  mengandung `tv`, `input`, `hdmi`, `cec`, `tuner`, `dtv`, `remote`,
  `bluetooth`, `audio`, `picture`, `setup`, `ota`/`update`, `hotkey`
  **biarkan dulu** sampai yakin. Bila ragu: jangan nonaktifkan, atau masukkan
  ke daftar hati-hati Anda sendiri.
- Paket *Play Auto Install* bernama `android.autoinstalls.config.<merek>` —
  nama persisnya berbeda tiap merek; contoh Xiaomi ada di daftar Xiaomi.

## Kredit & lisensi

Alat di folder ini adalah **kode orisinal Orange TV** (MIT, © 2026 Ardiansyah
Martha — lihat `LICENSE` di akar repo), ditulis dari nol untuk proyek ini.

Kurasi nama paket, klasifikasi aman/berisiko, dan beberapa pelajaran keamanan
(batch kecil dengan verifikasi, larangan menonaktifkan screensaver aktif,
wizard penyiapan yang memegang Wi-Fi, paket pairing remote Xiaomi) didasarkan
pada dokumentasi dan contoh teruji dari:

- **[amirulmzlan/android-tv-debloat](https://github.com/amirulmzlan/android-tv-debloat)**
  oleh Amirul Mazlan — lisensi **MIT** (Copyright © 2026 Amirul Mazlan untuk
  modifikasinya), yang sendiri merupakan adaptasi dari
  **[Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack)** oleh
  Gathin (MIT, Copyright © 2026 Gathin). Terima kasih — pergilah beri bintang
  pada repo aslinya.
- Konsep keselamatan umum proyek **Glass TV** (backup dulu, `disable-user`
  bukan uninstall, batch kecil, selalu bisa dipulihkan).

Tidak ada kode dari repo-repo tersebut yang disalin ke alat ini; yang diadopsi
adalah fakta (nama paket dan perilaku TV) serta praktik amannya, dengan kredit
sebagaimana mestinya. Anda mengubah setelan perangkat Anda sendiri atas risiko
Anda; proyek ini tidak berafiliasi dengan Google maupun produsen TV mana pun.
