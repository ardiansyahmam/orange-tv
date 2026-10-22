#!/usr/bin/env python3
"""Ambil jadwal pertandingan untuk kartu jadwal Orange TV.

Sumber: TheSportsDB (API gratis, kunci tes publik "123").
Cakupan: Timnas Indonesia + liga top Eropa (Premier League, La Liga,
Serie A, Bundesliga, Liga Champions UEFA).

Hasil: ../jadwal/jadwal.json dengan skema:
  {"diperbarui": ISO8601, "jadwal": [
      {"tim": ..., "lawan": ..., "kompetisi": ..., "kandang": bool,
       "kickoff_utc": ISO8601, "sumber": "thesportsdb"}, ...]}

Daftar terurut prioritas: (1) Timnas, (2) big match (dua klub besar),
(3) sisanya — masing-masing menurut waktu kick-off terdekat.
Hanya pertandingan yang belum kick-off, maks 20 entri.

Jalankan: python3 scripts/ambil-jadwal.py
"""
import json
import sys
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

BASE = "https://www.thesportsdb.com/api/v1/json/123"
TIMNAS_ID = "140164"  # Indonesia (dari searchteams.php?t=Indonesia)

# id liga TheSportsDB -> nama tampil
LIGA = {
    "4328": "Premier League",
    "4335": "La Liga",
    "4332": "Serie A",
    "4331": "Bundesliga",
    "4480": "Liga Champions",
}

# Klub besar (fragmen nama, dicocokkan case-insensitive sebagai substring)
KLUB_BESAR = [
    "liverpool", "arsenal", "chelsea", "manchester city", "man city",
    "manchester united", "man united", "tottenham",
    "real madrid", "barcelona", "atlético", "atletico madrid",
    "bayern", "dortmund", "inter", "internazionale", "milan", "ac milan",
    "juventus", "napoli", "paris saint-germain", "paris sg", "psg",
]

MAKS_ENTRI = 20


def ambil(url):
    req = urllib.request.Request(url, headers={"User-Agent": "OrangeTV-Jadwal/1.0"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def kickoff_iso(ev):
    """strTimestamp TheSportsDB adalah UTC ('2026-10-23T19:00:00')."""
    ts = (ev.get("strTimestamp") or "").strip()
    if ts:
        return ts + "Z" if not ts.endswith("Z") else ts
    tanggal = (ev.get("dateEvent") or "").strip()
    jam = (ev.get("strTime") or "00:00:00").strip()
    if tanggal:
        return f"{tanggal}T{jam}Z"
    return None


def parse_iso(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def klub_besar(nama):
    n = (nama or "").lower()
    return any(frag in n for frag in KLUB_BESAR)


def main():
    sekarang = datetime.now(timezone.utc)
    hasil = []  # (prioritas, kickoff_dt, entri)

    # --- Timnas Indonesia ---
    try:
        data = ambil(f"{BASE}/eventsnext.php?id={TIMNAS_ID}")
        for ev in data.get("events") or []:
            ko = kickoff_iso(ev)
            if not ko or parse_iso(ko) <= sekarang:
                continue
            home, away = ev.get("strHomeTeam", ""), ev.get("strAwayTeam", "")
            kandang = home.strip().lower() == "indonesia"
            hasil.append((0, parse_iso(ko), {
                "tim": "Timnas Indonesia",
                "lawan": away if kandang else home,
                "kompetisi": ev.get("strLeague") or "Timnas Indonesia",
                "kandang": kandang,
                "kickoff_utc": ko,
                "sumber": "thesportsdb",
            }))
    except Exception as e:  # noqa: BLE001 - laporkan, jangan gagal total
        print(f"!! Timnas: gagal ambil ({e})", file=sys.stderr)

    # --- Liga top Eropa ---
    # eventsnextleague di kunci gratis hanya memberi 1 pertandingan, jadi
    # pakai eventsround (per ronde) dan saring jendela 16 hari ke depan.
    musim = "2026-2027"
    batas = sekarang + timedelta(days=16)
    terlihat = set()
    for id_liga, nama_liga in LIGA.items():
        jumlah = 0
        try:
            for ronde in range(1, 15):
                data = ambil(f"{BASE}/eventsround.php?id={id_liga}"
                             f"&r={ronde}&s={musim}")
                for ev in data.get("events") or []:
                    ko = kickoff_iso(ev)
                    if not ko:
                        continue
                    ko_dt = parse_iso(ko)
                    if ko_dt <= sekarang or ko_dt > batas:
                        continue
                    home = ev.get("strHomeTeam", "")
                    away = ev.get("strAwayTeam", "")
                    kunci = (home, away, ko)
                    if kunci in terlihat:
                        continue
                    terlihat.add(kunci)
                    big = klub_besar(home) and klub_besar(away)
                    hasil.append((1 if big else 2, ko_dt, {
                        "tim": home,
                        "lawan": away,
                        "kompetisi": nama_liga,
                        "kandang": True,
                        "kickoff_utc": ko,
                        "sumber": "thesportsdb",
                    }))
                    jumlah += 1
            print(f"   liga {nama_liga} ({id_liga}): {jumlah} pertandingan "
                  f"dalam 16 hari")
        except Exception as e:  # noqa: BLE001
            print(f"!! Liga {nama_liga} ({id_liga}): gagal ambil ({e})",
                  file=sys.stderr)

    hasil.sort(key=lambda x: (x[0], x[1]))
    jadwal = [entri for _, _, entri in hasil[:MAKS_ENTRI]]

    keluar = Path(__file__).resolve().parent.parent / "jadwal" / "jadwal.json"
    keluar.parent.mkdir(parents=True, exist_ok=True)
    dokumen = {
        "diperbarui": sekarang.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "jadwal": jadwal,
    }
    keluar.write_text(json.dumps(dokumen, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")

    print(f"\n== {len(jadwal)} pertandingan ditulis ke {keluar} ==")
    label = {0: "TIMNAS", 1: "BIGMATCH", 2: "liga"}
    for prio, ko, entri in hasil[:MAKS_ENTRI]:
        print(f"[{label[prio]:8}] {entri['tim']} vs {entri['lawan']} | "
              f"{entri['kompetisi']} | {entri['kickoff_utc']} | "
              f"{'Kandang' if entri['kandang'] else 'Tandang'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
