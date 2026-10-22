#!/usr/bin/env python3
"""Render simulasi Orange TV v2 (gaya konsol modern) terpasang di TV.

Perubahan dari simulasi v1: tata letak ala PS5 —
  - latar sinematik layar penuh mengikuti aplikasi terfokus (di sini
    Netflix: gradien merah tua + cahaya radial + bidang diagonal +
    vignette; TANPA orang dan TANPA poster berhak cipta, murni bentuk
    dan cahaya yang digambar dari kode),

Varian v2.1 (simulasi-tv-night-v21.png): latar Mode Malam memakai
ARTWORK sinematik original yang sungguhan dibundel di aplikasi
(artwork/netflix.jpg), dikomposit ke area layar dan digelapkan sedikit
di bawah strip/klaster/kartu yang sama persis.
  - strip ikon horizontal di dekat atas; ikon terfokus (Netflix) lebih
    besar dengan cincin oranye tipis + cahaya,
  - klaster kanan atas: tombol bundar Input / Wi-Fi / Pengaturan + jam
    + tanggal,
  - kiri bawah: nama aplikasi besar ("Netflix") + deretan titik seksi,
  - kanan bawah: kartu kaca statistik (nilai ilustratif).

Mode Siang: latar yang sama diberi cucian terang, kaca jadi terang.
Menghasilkan simulasi-tv-night-v2.png & simulasi-tv-day-v2.png
(berkas v1 tidak ditimpa).
"""
import json
import math
import os
from datetime import datetime, timedelta, timezone

from PIL import Image, ImageDraw, ImageEnhance, ImageFont, ImageFilter

try:
    from zoneinfo import ZoneInfo
    WIB = ZoneInfo("Asia/Jakarta")
except Exception:  # noqa: BLE001 - cadangan bila tzdata tidak ada
    WIB = timezone(timedelta(hours=7))

HARI_ID = ["Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"]

W, H = 1920, 1080
NOTO = "/usr/share/fonts/truetype/noto/"
def F(name, size): return ImageFont.truetype(NOTO + name, size)
BOLD = "NotoSans-Bold.ttf"; REG = "NotoSans-Regular.ttf"

ORANGE = (255, 138, 30, 255)
SW, SH = 1600, 900  # ukuran "layar" di dalam bezel TV


def vgrad(size, top, bottom):
    w, h = size
    base = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        base.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return base.resize((w, h))


def rr(d, box, r, **kw):
    d.rounded_rectangle(box, radius=r, **kw)


def radial_alpha(w, h, inner=0.55, max_alpha=175):
    """Peta alpha vignette kecil: 0 di tengah, max_alpha di sudut."""
    small = Image.new("L", (200, 113), 0)
    px = small.load()
    cx, cy = 100, 56
    maxd = math.hypot(cx, cy)
    for y in range(113):
        for x in range(200):
            d = math.hypot(x - cx, y - cy) / maxd
            a = 0
            if d > inner:
                a = int(max_alpha * min(1.0, (d - inner) / (1.0 - inner)))
            px[x, y] = a
    return small.resize((w, h), Image.BICUBIC)


def draw_backdrop(night):
    """Latar sinematik Netflix: merah tua nyaris hitam + cahaya lembut."""
    # Gradien diagonal dasar
    img = Image.new("RGBA", (SW, SH), (10, 3, 4, 255))
    grad = vgrad((SW, SH), (24, 5, 9), (8, 2, 3)).convert("RGBA")
    img.alpha_composite(grad)

    # Cahaya radial utama (kanan-atas)
    glow = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([SW * 0.34, -SH * 0.42, SW * 1.14, SH * 0.62], fill=(196, 10, 22, 120))
    gd.ellipse([-SW * 0.24, SH * 0.52, SW * 0.30, SH * 1.24], fill=(140, 8, 18, 66))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(130)))

    # Bidang cahaya diagonal sangat halus
    shapes = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shapes)
    sd.polygon([(SW * 0.60, -80), (SW * 0.68, -80), (SW * 0.40, SH + 80), (SW * 0.30, SH + 80)],
               fill=(255, 255, 255, 14))
    sd.polygon([(SW * 0.74, -80), (SW * 0.92, -80), (SW * 0.60, SH + 80), (SW * 0.44, SH + 80)],
               fill=(255, 90, 90, 12))
    img.alpha_composite(shapes.filter(ImageFilter.GaussianBlur(6)))

    # Vignette
    black = Image.new("RGBA", (SW, SH), (0, 0, 0, 255))
    black.putalpha(radial_alpha(SW, SH, max_alpha=175 if night else 100))
    img.alpha_composite(black)

    if not night:
        # Cucian terang hangat (mode Siang)
        img.alpha_composite(Image.new("RGBA", (SW, SH), (244, 236, 221, 208)))
    return img


def draw_backdrop_artwork():
    """Latar v2.1 Mode Malam: artwork original Netflix yang dibundel
    (artwork/netflix.jpg), di-center-crop ke 1600x900, digelapkan
    sedikit, lalu diberi vignette seperti di aplikasi."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "artwork", "netflix.jpg")
    src = Image.open(path).convert("RGB")
    scale = max(SW / src.width, SH / src.height)
    nw, nh = round(src.width * scale), round(src.height * scale)
    src = src.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - SW) // 2, (nh - SH) // 2
    img = src.crop((left, top, left + SW, top + SH))
    img = ImageEnhance.Brightness(img).enhance(0.82).convert("RGBA")
    black = Image.new("RGBA", (SW, SH), (0, 0, 0, 255))
    black.putalpha(radial_alpha(SW, SH, max_alpha=150))
    img.alpha_composite(black)
    return img


def glyph_tile(d, cx, cy, s, color, glyph=None, glyph_color=(255, 255, 255, 255),
               triangle=False, gear=False):
    box = [cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2]
    rr(d, box, 15, fill=color)
    if triangle:
        r = s // 5
        d.polygon([(cx - r + 3, cy - r), (cx - r + 3, cy + r), (cx + r + 2, cy)], fill=(255, 255, 255, 255))
    elif gear:
        r = s // 4
        for a in range(0, 360, 45):
            x2 = cx + int((r + 5) * math.cos(math.radians(a)))
            y2 = cy + int((r + 5) * math.sin(math.radians(a)))
            d.line([cx, cy, x2, y2], fill=(255, 255, 255, 255), width=5)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
        d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=color)
    elif glyph:
        f = F(BOLD, int(s * 0.44))
        gw = d.textlength(glyph, font=f)
        d.text((cx - gw / 2, cy - s * 0.30), glyph, font=f, fill=glyph_color)


def pilih_kartu_jadwal(sekarang_wib):
    """Replikasi logika kartu di aplikasi: entri pertama jadwal.json yang
    kick-off-nya dalam jendela [kick-off - 2 hari, kick-off + 3 jam]."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "jadwal", "jadwal.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for entri in data.get("jadwal", []):
        ko = datetime.fromisoformat(entri["kickoff_utc"].replace("Z", "+00:00"))
        if ko - timedelta(days=2) <= sekarang_wib <= ko + timedelta(hours=3):
            return entri, ko
    return None, None


def teks_kartu_jadwal(sekarang_wib):
    """Susun 4 baris kartu dari data asli + hitung mundur ala aplikasi."""
    entri, ko = pilih_kartu_jadwal(sekarang_wib)
    if entri is None:
        return None
    ko_wib = ko.astimezone(WIB)
    selisih = (ko_wib.date() - sekarang_wib.date()).days
    if selisih > 1:
        mundur = f"dalam {selisih} hari"
    elif selisih == 1:
        mundur = "BESOK"
    elif selisih == 0:
        mundur = "MALAM INI" if ko_wib.hour >= 18 else "HARI INI"
    else:
        mundur = "SEDANG BERLANGSUNG"
    kapan = f"{HARI_ID[ko_wib.weekday()]} · {ko_wib:%H:%M} WIB"
    return (
        f"{entri['tim'].upper()} · PERTANDINGAN BERIKUTNYA",
        f"vs {entri['lawan']}",
        f"{entri['kompetisi']} · {'Kandang' if entri['kandang'] else 'Tandang'}",
        kapan,
        mundur,
    )


def draw_match_card(d, night, text, sub, tile_rim, card_fill):
    """Kartu jadwal v2.2: di bawah strip, sisi kanan, di atas kartu info."""
    baris = teks_kartu_jadwal(datetime(2026, 10, 23, 19, 30, tzinfo=WIB))
    if baris is None:
        return
    mc_w, mc_h = 500, 196
    mx1, my1 = SW - 64 - mc_w, 268
    rr(d, [mx1, my1, mx1 + mc_w, my1 + mc_h], 20, fill=card_fill,
       outline=tile_rim, width=1)
    px = mx1 + 26
    # Baris 1: titik merah + tim
    d.ellipse([px, my1 + 27, px + 11, my1 + 38], fill=(229, 50, 45, 255))
    d.text((px + 20, my1 + 20), baris[0], font=F(BOLD, 17), fill=text)
    # Baris 2: vs lawan (besar)
    d.text((px, my1 + 52), baris[1], font=F(BOLD, 40), fill=text)
    # Baris 3: kompetisi
    d.text((px, my1 + 108), baris[2], font=F(REG, 19), fill=sub)
    # Baris 4: hari · jam (utama) + hitung mundur (sekunder)
    f_when = F(BOLD, 20)
    d.text((px, my1 + 140), baris[3], font=f_when, fill=text)
    w1 = d.textlength(baris[3], font=f_when)
    d.text((px + w1 + 14, my1 + 140), "·  " + baris[4], font=f_when, fill=sub)


def draw_ui(mode, match=False):
    """Gambar antarmuka Orange TV v2 pada kanvas 1600x900."""
    night = mode == "night"
    # v2.1: Mode Malam memakai artwork terbundel; Mode Siang tetap gradien.
    img = draw_backdrop_artwork() if night else draw_backdrop(night)
    ov = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)

    if night:
        text = (245, 246, 248, 255); sub = (245, 246, 248, 170)
        tile_fill = (255, 255, 255, 26); tile_rim = (255, 255, 255, 64)
        btn_fill = (255, 255, 255, 30)
        card_fill = (18, 18, 22, 150); dot_off = (255, 255, 255, 90)
    else:
        text = (36, 34, 30, 255); sub = (36, 34, 30, 175)
        tile_fill = (255, 255, 255, 150); tile_rim = (255, 255, 255, 235)
        btn_fill = (255, 255, 255, 165)
        card_fill = (255, 255, 255, 165); dot_off = (36, 34, 30, 110)

    pad = 64

    # ---------- klaster kanan atas: tombol bundar + jam + tanggal ----------
    clock = "19:30"; f_clk = F(BOLD, 36)
    cw = d.textlength(clock, font=f_clk)
    clock_x = SW - pad - cw
    d.text((clock_x, 30), clock, font=f_clk, fill=text)
    date = "JUMAT, 23 OKTOBER 2026"; f_date = F(BOLD, 14)
    dw = d.textlength(date, font=f_date)
    d.text((SW - pad - dw, 76), date, font=f_date, fill=sub)

    def circle_btn(cx, cy, kind):
        r = 25
        rr(d, [cx - r, cy - r, cx + r, cy + r], r, fill=btn_fill, outline=tile_rim, width=1)
        if kind == "input":
            rr(d, [cx - 11, cy - 8, cx + 11, cy + 8], 3, outline=text, width=2)
            d.polygon([(cx - 4, cy - 5), (cx - 4, cy + 5), (cx + 6, cy)], fill=text)
        elif kind == "wifi":
            for i, rad in enumerate((8, 14, 20)):
                d.arc([cx - rad, cy - rad + 4, cx + rad, cy + rad + 4], 218, 322, fill=text, width=2)
            d.ellipse([cx - 2.5, cy + 6, cx + 2.5, cy + 11], fill=text)
        elif kind == "gear":
            glyph_tile(d, cx, cy, 30, (0, 0, 0, 0), gear=True)

    cy_btn = 52
    circle_btn(clock_x - 190, cy_btn, "input")
    circle_btn(clock_x - 128, cy_btn, "wifi")
    circle_btn(clock_x - 66, cy_btn, "gear")

    # ---------- strip ikon (Favorit + semua aplikasi, satu baris) ----------
    strip_y = 108
    tile = 96
    gap = 18
    apps = [
        ("yt", (229, 50, 45, 255), {"triangle": True}),
        ("wetv", (14, 58, 93, 255), {"glyph": "We"}),
        ("netflix", None, {}),
        ("vidio", (228, 0, 70, 255), {"glyph": "V"}),
        ("disney", (27, 42, 107, 255), {"glyph": "D+"}),
        ("prime", (0, 168, 225, 255), {"glyph": "P"}),
        ("spotify", (29, 185, 84, 255), {"glyph": "S"}),
    ]
    x = pad
    center_y = strip_y + tile // 2
    for name, color, kw in apps:
        if name == "netflix":
            big = 122
            bx, by = x, center_y - big // 2
            # cahaya oranye lembut di belakang ubin terfokus
            glow = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
            ImageDraw.Draw(glow).rounded_rectangle(
                [bx - 16, by - 16, bx + big + 16, by + big + 16], radius=30,
                fill=(255, 138, 30, 80))
            ov.alpha_composite(glow.filter(ImageFilter.GaussianBlur(16)))
            d = ImageDraw.Draw(ov)
            rr(d, [bx, by, bx + big, by + big], 24, fill=(246, 246, 246, 255))
            rr(d, [bx - 3, by - 3, bx + big + 3, by + big + 3], 27, outline=ORANGE, width=4)
            f_nf = F(BOLD, 23)
            tw = d.textlength("NETFLIX", font=f_nf)
            d.text((bx + (big - tw) / 2, by + big / 2 - 15), "NETFLIX", font=f_nf,
                   fill=(200, 8, 20, 255))
            x += big + gap
        else:
            if not night:
                sh = Image.new("RGBA", (SW, SH), (0, 0, 0, 0))
                ImageDraw.Draw(sh).rounded_rectangle(
                    [x + 3, strip_y + 7, x + tile + 3, strip_y + tile + 7], radius=22,
                    fill=(120, 90, 40, 40))
                ov.alpha_composite(sh.filter(ImageFilter.GaussianBlur(6)))
                d = ImageDraw.Draw(ov)
            rr(d, [x, strip_y, x + tile, strip_y + tile], 22,
               fill=tile_fill, outline=tile_rim, width=1)
            glyph_tile(d, x + tile // 2, center_y, 54, color, **kw)
            x += tile + gap

    # ---------- kiri bawah: nama besar + titik seksi ----------
    f_name = F(BOLD, 58)
    d.text((pad, SH - 168), "Netflix", font=f_name, fill=text)
    dot_y = SH - 74
    dx = pad
    for i in range(7):
        if i == 2:
            rr(d, [dx, dot_y, dx + 26, dot_y + 9], 4, fill=text)
            dx += 26 + 10
        else:
            d.ellipse([dx, dot_y, dx + 9, dot_y + 9], fill=dot_off)
            dx += 9 + 10

    # ---------- kartu jadwal (v2.2): di bawah strip, sisi kanan ----------
    if match:
        draw_match_card(d, night, text, sub, tile_rim, card_fill)

    # ---------- kanan bawah: kartu info kaca ----------
    card_w, card_h = 452, 106
    cx0, cy0 = SW - pad - card_w, SH - 56 - card_h
    rr(d, [cx0, cy0, cx0 + card_w, cy0 + card_h], 20, fill=card_fill,
       outline=tile_rim, width=1)
    f_lab = F(REG, 15); f_val = F(BOLD, 31)
    d.text((cx0 + 24, cy0 + 18), "Waktu Layar Minggu Ini", font=f_lab, fill=sub)
    d.text((cx0 + 24, cy0 + 44), "3,5 jam", font=f_val, fill=text)
    mid = cx0 + card_w // 2
    d.line([mid, cy0 + 18, mid, cy0 + card_h - 18], fill=tile_rim, width=1)
    d.text((mid + 24, cy0 + 18), "Penyimpanan Terpakai", font=f_lab, fill=sub)
    d.text((mid + 24, cy0 + 44), "180 MB", font=f_val, fill=text)

    img.alpha_composite(ov)
    return img


def scene(mode, match=False):
    night = mode == "night"
    if night:
        canvas = vgrad((W, H), (24, 25, 31), (13, 13, 16)).convert("RGBA")
    else:
        canvas = vgrad((W, H), (214, 205, 190), (178, 168, 152)).convert("RGBA")
    d = ImageDraw.Draw(canvas)
    d.rectangle([0, 1000, W, H], fill=(10, 10, 12, 255) if night else (150, 140, 124, 255))
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([150, 84, 1790, 1006], radius=30,
                                          fill=(0, 0, 0, 110 if night else 70))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)))
    d = ImageDraw.Draw(canvas)
    d.rectangle([920, 986, 1000, 1028], fill=(16, 16, 18, 255))
    d.rounded_rectangle([800, 1024, 1120, 1042], radius=9, fill=(16, 16, 18, 255))
    d.ellipse([760, 1036, 1160, 1058], fill=(0, 0, 0, 90))
    d.rounded_rectangle([136, 54, 1784, 990], radius=26, fill=(8, 8, 10, 255))
    d.rounded_rectangle([136, 54, 1784, 990], radius=26, outline=(52, 52, 58, 255), width=2)
    ui = draw_ui(mode, match=match)
    canvas.alpha_composite(ui, (160, 78))
    refl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(refl)
    rd.polygon([(160, 78), (760, 78), (420, 978), (160, 978)],
               fill=(255, 255, 255, 14 if night else 22))
    canvas.alpha_composite(refl.filter(ImageFilter.GaussianBlur(30)))
    f = F(REG, 15)
    label = "ORANGE TV"
    lw = d.textlength(label, font=f)
    d.text(((W - lw) / 2, 1000 - 34), label, font=f, fill=(140, 140, 148, 255))
    return canvas.convert("RGB")


if __name__ == "__main__":
    # Render utama skrip ini sekarang: simulasi v2.1 Mode Malam.
    out = "/home/hatch/workspace/orange-tv/simulasi/simulasi-tv-night-v21.png"
    scene("night").save(out)
    print("saved", out)
    # v2.2: Mode Malam + kartu jadwal (data asli jadwal.json).
    out22 = "/home/hatch/workspace/orange-tv/simulasi/simulasi-tv-night-v22.png"
    scene("night", match=True).save(out22)
    print("saved", out22)
