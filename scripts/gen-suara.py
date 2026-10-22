#!/usr/bin/env python3
"""Generator efek suara navigasi Orange TV.

Semua suara DISINTESIS dari nol dengan gelombang sinus di skrip ini —
tidak ada berkas audio dari produk/proyek lain yang dipakai atau ditiru.
Karakter yang dicari: lembut, lapang ("airy"), dan pelan, seperti bunyi
navigasi antarmuka TV modern — centang halus saat berpindah fokus,
lonceng dua nada saat memilih, dan blip rendah saat kembali.

Hasilnya berkas WAV mungil (22050 Hz, mono, 16-bit) di
app/src/main/res/raw/:
  - suara_pilih.wav    : lonceng dua nada ~210 ms (pilih / luncurkan)
  - suara_kembali.wav  : blip tunggal lebih rendah ~90 ms (kembali)

Catatan: suara pindah fokus (suara_pindah.mp3) adalah berkas audio yang
disediakan langsung oleh pemilik proyek dan disimpan apa adanya di
res/raw — skrip ini TIDAK membuatnya dan tidak boleh menimpanya.

Jalankan ulang skrip ini bila ingin mengubah karakter suaranya:
    python3 scripts/gen-suara.py
"""
import math
import os
import struct
import wave

SR = 22050  # Hz
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "app", "src", "main", "res", "raw")


def envelope(t, attack=0.004, tau=0.02):
    """Amplop: naik cepat (attack) lalu meluruh eksponensial (tau)."""
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) / tau)


def sweep(freq_awal, freq_akhir, durasi, tau, gain=0.55, harmonik=0.18):
    """Satu sapuan sinus dari freq_awal ke freq_akhir (eksponensial)."""
    n = int(SR * durasi)
    out = []
    fase = 0.0
    for i in range(n):
        t = i / SR
        frac = t / durasi
        # Interpolasi frekuensi secara eksponensial terasa lebih natural.
        f = freq_awal * (freq_akhir / freq_awal) ** frac
        fase += 2.0 * math.pi * f / SR
        env = envelope(t, tau=tau)
        # Rona lapang: sedikit harmonik kedua yang ikut meluruh lebih cepat.
        s = math.sin(fase) + harmonik * math.sin(2.0 * fase) * math.exp(-t / (tau * 0.6))
        out.append(gain * env * s)
    return out


def nada(freq, mulai, durasi_total, tau, gain=0.5, harmonik=0.25):
    """Satu nada lonceng yang dimulai pada detik `mulai`."""
    n = int(SR * durasi_total)
    out = [0.0] * n
    n_mulai = int(SR * mulai)
    for i in range(n_mulai, n):
        t = (i - n_mulai) / SR
        env = envelope(t, attack=0.003, tau=tau)
        s = math.sin(2.0 * math.pi * freq * t) \
            + harmonik * math.sin(2.0 * math.pi * freq * 2.0 * t) * math.exp(-t / (tau * 0.5)) \
            + 0.08 * math.sin(2.0 * math.pi * freq * 3.0 * t) * math.exp(-t / (tau * 0.3))
        out[i] += gain * env * s
    return out


def campur(*lapisan):
    n = max(len(l) for l in lapisan)
    out = [0.0] * n
    for l in lapisan:
        for i, v in enumerate(l):
            out[i] += v
    return out


def normalisasi(samples, puncak=0.8):
    maks = max(abs(s) for s in samples) or 1.0
    skala = puncak / maks if maks > puncak else 1.0
    return [s * skala for s in samples]


def tulis_wav(nama, samples):
    path = os.path.join(OUT_DIR, nama)
    os.makedirs(OUT_DIR, exist_ok=True)
    data = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767))
                    for s in samples)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)
    print(f"{nama}: {len(samples)} sampel, {os.path.getsize(path)} byte")


def main():
    # Pilih: dua nada lonceng (E6 lalu A6) yang saling menyusul, total ~210 ms.
    pilih = campur(
        nada(1318.51, 0.00, 0.21, tau=0.045, gain=0.42),
        nada(1760.00, 0.07, 0.21, tau=0.065, gain=0.5),
    )
    tulis_wav("suara_pilih.wav", normalisasi(pilih))

    # Kembali: satu blip lebih rendah, 520 -> 360 Hz, ~90 ms.
    kembali = sweep(520.0, 360.0, 0.09, tau=0.028, gain=0.5, harmonik=0.12)
    tulis_wav("suara_kembali.wav", normalisasi(kembali))


if __name__ == "__main__":
    main()
