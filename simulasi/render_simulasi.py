#!/usr/bin/env python3
"""Render simulasi Orange TV terpasang di TV — Mode Malam & Mode Siang.
Menggambar adegan ruang (dinding + TV) dan antarmuka Orange TV dengan PIL.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1920, 1080
NOTO = "/usr/share/fonts/truetype/noto/"
def F(name, size): return ImageFont.truetype(NOTO + name, size)
BOLD = "NotoSans-Bold.ttf"; REG = "NotoSans-Regular.ttf"; MED = "NotoSans-Medium.ttf" if False else "NotoSans-Regular.ttf"

ORANGE = (255, 138, 30, 255)

def vgrad(size, top, bottom):
    w, h = size
    base = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        base.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return base.resize((w, h))

def rr(d, box, r, **kw): d.rounded_rectangle(box, radius=r, **kw)

def draw_orange_glyph(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 149, 0, 255))
    d.ellipse([cx - r + 5, cy - r + 4, cx + r - 16, cy + r - 20], fill=(255, 176, 64, 160))
    d.polygon([(cx + 2, cy - r - 2), (cx + r + 4, cy - r - 10), (cx + r - 2, cy - r + 6), (cx + 4, cy - r + 6)], fill=(74, 160, 74, 255))

def draw_ui(mode):
    """Gambar antarmuka Orange TV pada kanvas 1600x900."""
    night = mode == "night"
    if night:
        img = vgrad((1600, 900), (11, 13, 18), (26, 20, 15)).convert("RGBA")
        text = (242, 243, 245, 255); sub = (242, 243, 245, 165)
        tile_fill = (255, 255, 255, 18); tile_rim = (255, 255, 255, 58)
        tile_focus_fill = (255, 255, 255, 34)
    else:
        img = vgrad((1600, 900), (253, 243, 227), (238, 243, 247)).convert("RGBA")
        text = (35, 39, 46, 255); sub = (35, 39, 46, 165)
        tile_fill = (255, 255, 255, 140); tile_rim = (255, 255, 255, 235)
        tile_focus_fill = (255, 255, 255, 215)
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    pad = 60; inner_w = 1600 - 2 * pad

    # --- top bar ---
    logo_box = [pad, 42, pad + 46, 88]
    rr(d, logo_box, 14, fill=(255, 154, 32, 255))
    draw_orange_glyph(d, pad + 23, 65, 13)
    d.text((pad + 62, 40), "Orange TV", font=F(BOLD, 34), fill=text)
    d.text((pad + 63, 80), "Buat TV lamamu segar kembali", font=F(REG, 15), fill=sub)
    # clock right
    clk = "19:30"; f_clk = F(BOLD, 38)
    tw = d.textlength(clk, font=f_clk)
    d.text((1600 - pad - tw, 36), clk, font=f_clk, fill=text)
    dt = "Jumat, 23 Oktober 2026"; f_dt = F(REG, 16)
    tw2 = d.textlength(dt, font=f_dt)
    d.text((1600 - pad - tw2, 82), dt, font=f_dt, fill=sub)
    chip = "Mode Malam" if night else "Mode Siang"; f_chip = F(BOLD, 15)
    cw = d.textlength(chip, font=f_chip) + 30
    chip_box = [1600 - pad - cw, 110, 1600 - pad, 140]
    rr(d, chip_box, 15, outline=ORANGE, width=2)
    d.text((chip_box[0] + 15, 117), chip, font=f_chip, fill=ORANGE)

    def tile(x, y, w, h, icon_fn, label, focused=False):
        if not night:  # bayangan lembut untuk mode siang
            sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
            ImageDraw.Draw(sh).rounded_rectangle([x + 3, y + 7, x + w + 3, y + h + 7], radius=20, fill=(120, 90, 40, 42))
            ov.alpha_composite(sh.filter(ImageFilter.GaussianBlur(6)))
        rr(d, [x, y, x + w, y + h], 20, fill=tile_focus_fill if focused else tile_fill, outline=tile_rim, width=1)
        d.line([x + 14, y + 1.5, x + w - 14, y + 1.5], fill=(255, 255, 255, 90 if night else 160), width=1)
        if focused:
            rr(d, [x - 2, y - 2, x + w + 2, y + h + 2], 22, outline=ORANGE, width=3)
        icon_fn(x + w // 2, y + h // 2 - 16)
        f_lab = F(MED, 20)
        lw = d.textlength(label, font=f_lab)
        d.text((x + (w - lw) / 2, y + h - 42), label, font=f_lab, fill=text)

    def icon_box(color, glyph=None, glyph_color=(255, 255, 255, 255), big=True, triangle=False, gear=False):
        s = 64 if big else 48
        def fn(cx, cy):
            box = [cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2]
            rr(d, box, 16 if big else 13, fill=color)
            if triangle:
                r = 13 if big else 10
                d.polygon([(cx - r + 3, cy - r), (cx - r + 3, cy + r), (cx + r + 2, cy)], fill=(255, 255, 255, 255))
            elif gear:
                r = 15 if big else 12
                for a in range(0, 360, 45):
                    import math
                    x2 = cx + int((r + 5) * math.cos(math.radians(a))); y2 = cy + int((r + 5) * math.sin(math.radians(a)))
                    d.line([cx, cy, x2, y2], fill=(255, 255, 255, 255), width=5)
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 255))
                d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=color)
            elif glyph:
                f = F(BOLD, 30 if big else 22)
                gw = d.textlength(glyph, font=f)
                d.text((cx - gw / 2, cy - (19 if big else 15)), glyph, font=f, fill=glyph_color)
        return fn

    # --- FAVORIT ---
    y_sec = 196
    d.text((pad, y_sec), "F A V O R I T", font=F(BOLD, 19), fill=sub)
    favs = [
        (icon_box((229, 50, 45, 255), triangle=True), "YouTube", True),
        (icon_box((20, 20, 20, 255), glyph="N", glyph_color=(229, 9, 20, 255)), "Netflix", False),
        (icon_box((14, 58, 93, 255), glyph="We"), "WeTV", False),
        (icon_box((228, 0, 70, 255), glyph="V"), "Vidio", False),
        (icon_box((29, 185, 84, 255), glyph="S"), "Spotify", False),
    ]
    tw_, th_, gap = 280, 164, 20
    x = pad
    for fn, lab, foc in favs:
        tile(x, y_sec + 34, tw_, th_, fn, lab, focused=foc)
        x += tw_ + gap

    # --- SEMUA APLIKASI ---
    y_sec2 = 470
    d.text((pad, y_sec2), "S E M U A   A P L I K A S I", font=F(BOLD, 19), fill=sub)
    apps = [
        (icon_box((27, 42, 107, 255), glyph="D+", big=False), "Disney+"),
        (icon_box((0, 168, 225, 255), glyph="P", big=False), "Prime Video"),
        (icon_box((229, 50, 45, 255), triangle=True, big=False), "YT Music"),
        (icon_box((91, 100, 112, 255), gear=True, big=False), "Pengaturan TV"),
    ]
    x = pad
    for fn, lab in apps:
        tile(x, y_sec2 + 34, tw_, 128, fn, lab)
        x += tw_ + gap

    # --- hint ---
    hint = "Tekan lama OK untuk tambah/hapus Favorit  ·  Ganti Mode Siang/Malam di Pengaturan"
    f_hint = F(REG, 16)
    hw = d.textlength(hint, font=f_hint)
    d.text(((1600 - hw) / 2, 852), hint, font=f_hint, fill=sub)
    img.alpha_composite(ov)
    return img

def scene(mode):
    night = mode == "night"
    if night:
        canvas = vgrad((W, H), (24, 25, 31), (13, 13, 16)).convert("RGBA")
    else:
        canvas = vgrad((W, H), (214, 205, 190), (178, 168, 152)).convert("RGBA")
    d = ImageDraw.Draw(canvas)
    # lantai
    d.rectangle([0, 1000, W, H], fill=(10, 10, 12, 255) if night else (150, 140, 124, 255))
    # bayangan TV di dinding & lantai
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([150, 84, 1790, 1006], radius=30, fill=(0, 0, 0, 110 if night else 70))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)))
    d = ImageDraw.Draw(canvas)
    # kaki TV
    d.rectangle([920, 986, 1000, 1028], fill=(16, 16, 18, 255))
    d.rounded_rectangle([800, 1024, 1120, 1042], radius=9, fill=(16, 16, 18, 255))
    d.ellipse([760, 1036, 1160, 1058], fill=(0, 0, 0, 90))
    # bezel
    d.rounded_rectangle([136, 54, 1784, 990], radius=26, fill=(8, 8, 10, 255))
    d.rounded_rectangle([136, 54, 1784, 990], radius=26, outline=(52, 52, 58, 255), width=2)
    # layar
    ui = draw_ui(mode)
    canvas.alpha_composite(ui, (160, 78))
    # pantulan cahaya halus di pojok layar
    refl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(refl)
    rd.polygon([(160, 78), (760, 78), (420, 978), (160, 978)], fill=(255, 255, 255, 14 if night else 22))
    canvas.alpha_composite(refl.filter(ImageFilter.GaussianBlur(30)))
    # merek kecil di bezel bawah
    f = F(REG, 15)
    label = "ORANGE TV"
    lw = d.textlength(label, font=f)
    d.text(((W - lw) / 2, 1000 - 34), label, font=f, fill=(140, 140, 148, 255))
    return canvas.convert("RGB")

for m in ("night", "day"):
    out = f"/home/hatch/workspace/orange-tv/simulasi/simulasi-tv-{m}.png"
    scene(m).save(out)
    print("saved", out)
