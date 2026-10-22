#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
tv.py — Alat Debloat Orange TV (kode orisinal, Python 3, hanya pustaka standar).

Menonaktifkan aplikasi bawaan (bloatware) Android TV / Google TV secara aman
dan SEPENUHNYA BISA DIBALikkan:

    python3 tv.py --probe   --tv 192.168.1.10     # baca kondisi TV (read-only)
    python3 tv.py --backup  --tv 192.168.1.10     # simpan cadangan keadaan TV
    python3 tv.py --disable --tv 192.168.1.10 --list lists/umum-bloat.txt
    python3 tv.py --status  --tv 192.168.1.10
    python3 tv.py --restore --tv 192.168.1.10     # aktifkan lagi semua / --package X

Prinsip keamanan (ditanam di kode, bukan sekadar dokumentasi):
  1. TIDAK PERNAH memakai `pm uninstall`. Yang dipakai hanya
     `pm disable-user --user 0` dan `pm enable --user 0` untuk memulihkan.
  2. Ada DAFTAR LARANGAN KERAS (denylist) di bawah: paket inti sistem,
     launcher, keyboard, layanan remote, kerangka input TV/HDMI, Play
     Store/Services, WebView, dan lain-lain tidak akan pernah dinonaktifkan,
     dari berkas daftar mana pun asalnya.
  3. --disable MENOLAK berjalan tanpa cadangan (--backup) yang masih segar
     untuk perangkat yang sama.
  4. Paket dari lists/hati-hati.txt butuh konfirmasi ekstra
     (--confirm-hati-hati atau jawaban eksplisit di prompt).
  5. Penonaktifan berjalan per kelompok kecil (batch); di antara batch ada
     jeda verifikasi (Home, aplikasi teratas, Wi-Fi) dan prompt lanjut.
  6. Setiap paket yang berhasil dinonaktifkan langsung dicatat ke berkas
     state, sehingga --restore tahu persis apa yang harus diaktifkan lagi.
  7. Semua perintah mendukung --dry-run: mencetak persis perintah yang
     AKAN dijalankan tanpa menyentuh perangkat apa pun.

Konsep alur (probe → backup → batch kecil → verifikasi → restore) terinspirasi
praktik aman yang terdokumentasi di amirulmzlan/android-tv-debloat (MIT) —
lihat README.md di folder ini untuk kredit lengkap. Kode ini ditulis sendiri.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

TOOL_VERSION = "1.0.0"
TOOLS_DIR = Path(__file__).resolve().parent
LISTS_DIR = TOOLS_DIR / "lists"
DEFAULT_BACKUP_ROOT = TOOLS_DIR / "backup"
DEFAULT_STATE_FILE = TOOLS_DIR / "state" / "state.json"
HATI_HATI_FILE = LISTS_DIR / "hati-hati.txt"
DEFAULT_BATCH_SIZE = 5
DEFAULT_MAX_BACKUP_AGE_HOURS = 24.0

# ---------------------------------------------------------------------------
# DAFTAR LARANGAN KERAS — jangan pernah dinonaktifkan, apa pun alasannya.
# Sengaja dibuat longgar/konservatif: lebih baik menolak paket yang ternyata
# aman daripada menonaktifkan paket yang menopang TV.
# ---------------------------------------------------------------------------

DENY_EXACT = {
    "android": "paket framework Android",
    "com.android.systemui": "SystemUI (antarmuka sistem)",
    "com.android.settings": "Pengaturan Android",
    "com.android.tv.settings": "Pengaturan TV",
    "com.android.providers.tv": "database kanal/program TV (dipakai kerangka input TV)",
    "com.android.packageinstaller": "pemasang paket sistem",
    "com.google.android.packageinstaller": "pemasang paket Google",
    "com.android.permissioncontroller": "pengontrol izin",
    "com.google.android.permissioncontroller": "pengontrol izin Google",
    "com.android.vending": "Google Play Store",
    "com.google.android.gms": "Google Play Services — banyak aplikasi bergantung padanya",
    "com.google.android.gsf": "Google Services Framework",
    "com.google.android.tv.remote.service": "layanan remote Google TV",
    "com.google.android.leanback.ime": "keyboard Leanback (metode input)",
    "com.google.android.inputmethod.latin": "keyboard Latin/Gboard (metode input)",
    "com.google.android.tungsten.setupwraith":
        "wizard penyiapan Google TV — bisa memegang jaringan Wi-Fi yang tersimpan saat setup",
    "com.google.android.tvlauncher": "launcher bawaan Android TV (penggantian launcher adalah langkah terpisah)",
    "com.google.android.apps.tv.launcherx": "launcher bawaan Google TV (penggantian launcher adalah langkah terpisah)",
    "com.google.android.leanbacklauncher": "launcher Leanback lama",
    "com.android.launcher": "launcher bawaan",
    "com.android.launcher2": "launcher bawaan",
    "com.android.launcher3": "launcher bawaan",
    "id.orangetv.launcher": "Orange TV itu sendiri",
    "com.google.android.webview": "Android System WebView — dipakai banyak aplikasi",
    "com.android.webview": "WebView AOSP — dipakai banyak aplikasi",
    # Xiaomi: terlihat seperti bloat, ternyata menopang remote/TV (terverifikasi di repo sumber)
    "com.xiaomi.android.tvsetup.partnercustomizer": "berisi layar pairing remote Bluetooth (Xiaomi)",
    "com.xiaomi.mitv.tvmanager": "layanan sistem TV Xiaomi",
    "mitv.service": "layanan inti Mi TV",
    "com.mstar.netflixobserver": "penjaga tombol Netflix di remote",
    # Sony
    "com.sony.dtv.tvx": "aplikasi TV/Live TV Sony",
}

DENY_PREFIXES = (
    ("com.android.providers.", "penyedia data sistem (providers.*)"),
    ("com.google.android.inputmethod.", "metode input/keyboard Google"),
    ("com.android.inputdevices", "perangkat input"),
)

# Token yang bila muncul DI MANA PUN dalam nama paket -> tolak (konservatif).
DENY_SUBSTRINGS = (
    ("tvinput", "kerangka input TV/HDMI"),
    ("tvcenter", "pusat Live TV/HDMI vendor"),
    ("livetv", "Live TV"),
    ("hdmi", "HDMI"),
    (".cec", "HDMI-CEC"),
    ("tuner", "tuner TV"),
    ("dtv", "layanan TV digital vendor"),
    ("systemui", "SystemUI"),
    ("keyboard", "keyboard/metode input"),
    ("inputmethod", "metode input"),
    (".ime", "metode input (paket *.ime)"),
    ("bluetooth", "Bluetooth / pairing remote"),
    ("remote", "layanan remote"),
    ("setupwizard", "wizard penyiapan"),
    ("setupwraith", "wizard penyiapan"),
    ("tvsetup", "penyiapan TV vendor"),
    ("hotkey", "penangan tombol pintas remote"),
    ("keyguard", "keyguard"),
    ("tvmanager", "manajer sistem TV vendor"),
    ("packageinstaller", "pemasang paket"),
    ("permissioncontroller", "pengontrol izin"),
)


def denylist_reason(pkg):
    """Kembalikan alasan penolakan bila paket masuk daftar larangan, else None."""
    p = pkg.strip().lower()
    if p in DENY_EXACT:
        return DENY_EXACT[p]
    for prefix, reason in DENY_PREFIXES:
        if p.startswith(prefix):
            return reason
    for token, reason in DENY_SUBSTRINGS:
        if token in p:
            return "mengandung '%s' — %s" % (token, reason)
    return None


# ---------------------------------------------------------------------------
# Lapisan ADB — satu-satunya jalan bicara ke perangkat.
# Dalam mode dry-run, tidak ada proses yang benar-benar dijalankan.
# ---------------------------------------------------------------------------

class AdbError(Exception):
    pass


class Adb:
    def __init__(self, path, serial=None, dry_run=False):
        self.path = path            # boleh None pada dry-run
        self.serial = serial        # endpoint "ip:5555" atau serial USB
        self.dry_run = dry_run

    def _base(self):
        cmd = [self.path or "adb"]
        if self.serial:
            cmd += ["-s", self.serial]
        return cmd

    def run(self, args):
        """Jalankan perintah adb (bukan shell). Kembalikan (rc, stdout)."""
        full = self._base() + list(args)
        if self.dry_run:
            print("    [dry-run] $ " + " ".join(full))
            return 0, ""
        try:
            proc = subprocess.run(full, capture_output=True, text=True, timeout=120)
        except FileNotFoundError:
            raise AdbError("adb tidak ditemukan.")
        except subprocess.TimeoutExpired:
            raise AdbError("adb timeout: " + " ".join(full))
        return proc.returncode, (proc.stdout or "") + (proc.stderr or "")

    def shell(self, *args):
        """Jalankan perintah di shell perangkat. Kembalikan stdout (str)."""
        rc, out = self.run(["shell"] + [str(a) for a in args])
        if self.dry_run:
            return ""
        if rc != 0:
            raise AdbError("perintah gagal (%s): %s" % (rc, " ".join(str(a) for a in args)))
        return out.replace("\r\n", "\n")

    def connect(self, endpoint):
        if self.dry_run:
            print("    [dry-run] $ adb connect " + endpoint)
            return
        rc, out = self.run(["connect", endpoint])
        print("    adb connect %s -> %s" % (endpoint, out.strip().splitlines()[0] if out.strip() else "?"))

    def get_state(self):
        if self.dry_run:
            print("    [dry-run] $ " + " ".join(self._base() + ["get-state"]))
            return "device"
        rc, out = self.run(["get-state"])
        return out.strip()


def find_adb(explicit=None):
    if explicit:
        return explicit
    found = shutil.which("adb") or shutil.which("adb.exe")
    if found:
        return found
    for cand in (
        Path.home() / "Library/Android/sdk/platform-tools/adb",
        Path.home() / "Android/Sdk/platform-tools/adb",
        Path("/opt/homebrew/bin/adb"),
        Path("/usr/local/bin/adb"),
        Path("/usr/bin/adb"),
    ):
        if cand.exists():
            return str(cand)
    return None


ADB_INSTALL_HINT = (
    "adb tidak ditemukan. Pasang dulu Android platform-tools:\n"
    "  macOS  : brew install --cask android-platform-tools\n"
    "  Linux  : sudo apt install adb   (atau paket android-tools di distro Anda)\n"
    "  Windows: winget install Google.PlatformTools  (lalu buka terminal baru)\n"
    "atau tunjuk manual dengan --adb /path/ke/adb"
)


def make_adb(args):
    """Siapkan objek Adb; hubungkan bila --tv diberikan. None bila adb tak ada (mode nyata)."""
    path = find_adb(args.adb)
    if not args.dry_run and not path:
        print(ADB_INSTALL_HINT)
        return None
    serial = args.serial
    endpoint = None
    if args.tv:
        endpoint = args.tv if ":" in args.tv else args.tv + ":5555"
        serial = endpoint
    adb = Adb(path, serial=serial, dry_run=args.dry_run)
    if endpoint:
        adb.connect(endpoint)
    return adb


def require_device(adb):
    if adb.dry_run:
        adb.get_state()
        return True
    state = adb.get_state()
    if state != "device":
        print("!! Perangkat tidak terhubung/terotorisasi (state=%s)." % (state or "?"))
        print("   Lihat TV: prompt 'Allow USB debugging?' harus disetujui (centang 'Always allow').")
        print("   Pastikan USB debugging / Network debugging aktif di Opsi Pengembang TV.")
        return False
    return True


# ---------------------------------------------------------------------------
# Utilitas berkas daftar, state, dan cadangan
# ---------------------------------------------------------------------------

def read_list_file(path):
    """Baca berkas daftar: satu paket per baris, '#' = komentar (termasuk di akhir baris).

    Kembalikan list[(paket, catatan)] — catatan dari komentar sebaris bila ada.
    """
    entries = []
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        note = ""
        if "#" in line:
            before, _, after = line.partition("#")
            note = after.strip()
            line = before.strip()
            if not line:
                continue  # baris komentar penuh
        pkg = line.split()[0]
        entries.append((pkg, note))
    return entries


def load_hati_hati():
    if HATI_HATI_FILE.exists():
        return {pkg for pkg, _ in read_list_file(HATI_HATI_FILE)}
    return set()


def load_state(path):
    if Path(path).exists():
        try:
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            if isinstance(data, dict) and isinstance(data.get("disabled"), list):
                return data
        except (ValueError, OSError):
            pass
        print("!! Berkas state %s tidak bisa dibaca / rusak." % path)
        print("   Demi keamanan, berhenti — periksa berkas itu manual sebelum lanjut.")
        sys.exit(2)
    return {"tool": "orange-tv tv.py", "version": TOOL_VERSION, "disabled": []}


def save_state(path, state):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def read_manifest(backup_dir):
    mf = Path(backup_dir) / "manifest.json"
    if not mf.exists():
        return None
    try:
        return json.loads(mf.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return None


def find_latest_backup(args):
    """Kembalikan (Path, manifest) cadangan terbaru, atau (None, None)."""
    if args.backup_dir:
        d = Path(args.backup_dir)
        return d, read_manifest(d)
    root = DEFAULT_BACKUP_ROOT
    if not root.exists():
        return None, None
    best, best_mf = None, None
    for child in root.iterdir():
        if not child.is_dir():
            continue
        mf = read_manifest(child)
        if mf and isinstance(mf.get("created_epoch"), (int, float)):
            if best_mf is None or mf["created_epoch"] > best_mf["created_epoch"]:
                best, best_mf = child, mf
    return best, best_mf


def check_backup(args, current_serial=None):
    """Terapkan aturan 'wajib ada cadangan segar'. Kembalikan (dir, manifest) atau None."""
    bdir, mf = find_latest_backup(args)
    if bdir is None or mf is None:
        print("!! DITOLAK: belum ada cadangan yang valid (manifest.json tidak ditemukan).")
        print("   Jalankan dulu:  python3 tv.py --backup --tv <ip-tv>")
        return None
    age_h = (time.time() - float(mf["created_epoch"])) / 3600.0
    if age_h > args.max_backup_age:
        print("!! DITOLAK: cadangan terakhir sudah %.1f jam (batas %.0f jam) di %s."
              % (age_h, args.max_backup_age, bdir))
        print("   Buat cadangan baru:  python3 tv.py --backup --tv <ip-tv>")
        return None
    bserial = mf.get("serial")
    if bserial and current_serial and bserial != current_serial:
        print("!! DITOLAK: cadangan itu untuk perangkat lain (%s), bukan %s." % (bserial, current_serial))
        return None
    print("== Cadangan dipakai: %s (umur %.1f jam, model %s)" % (bdir, age_h, mf.get("model", "?")))
    return bdir, mf


def parse_packages(out):
    pkgs = []
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("package:"):
            pkgs.append(line[len("package:"):].strip())
    return pkgs


def parse_getprop(out):
    props = {}
    for line in out.splitlines():
        m = re.match(r"\[([^\]]+)\]: \[(.*)\]", line.strip())
        if m:
            props[m.group(1)] = m.group(2)
    return props


def device_identity(adb):
    """(serial, props) — serial hanya diketahui di mode nyata."""
    serial = None
    if not adb.dry_run:
        rc, out = adb.run(["get-serialno"])
        serial = out.strip() if rc == 0 else None
        if serial in ("", "unknown"):
            serial = adb.serial
    props = parse_getprop(adb.shell("getprop"))
    return serial, props


def home_packages(adb):
    """Paket-paket yang mendaftarkan aktivitas HOME (launcher). Mode nyata saja."""
    if adb.dry_run:
        return set()
    out = adb.shell("cmd", "package", "query-activities", "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.HOME")
    return set(re.findall(r"packageName=([A-Za-z0-9_.]+)", out))


def default_ime_package(adb):
    if adb.dry_run:
        return None
    out = adb.shell("settings", "get", "secure", "default_input_method").strip()
    if out and out != "null":
        return out.split("/")[0]
    return None


def screensaver_packages(adb):
    if adb.dry_run:
        return set()
    out = adb.shell("settings", "get", "secure", "screensaver_components").strip()
    pkgs = set()
    if out and out != "null":
        for comp in out.split(","):
            comp = comp.strip()
            if "/" in comp:
                pkgs.add(comp.split("/")[0])
            elif comp:
                pkgs.add(comp)
    return pkgs


def verification_snapshot(adb, label):
    """Cek cepat pasca-batch: aktivitas teratas + SSID Wi-Fi."""
    print("  -- verifikasi (%s):" % label)
    if adb.dry_run:
        adb.shell("input", "keyevent", "KEYCODE_HOME")
        adb.shell("dumpsys", "activity", "activities")
        adb.shell("dumpsys", "wifi")
        print("     [dry-run] (di mode nyata: tombol Home ditekan, lalu aktivitas teratas & SSID dibaca)")
        return
    adb.shell("input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.0)
    acts = adb.shell("dumpsys", "activity", "activities")
    m = re.search(r"(?:topResumedActivity|mResumedActivity)[^\n]*", acts)
    print("     aktivitas teratas: %s" % (m.group(0).strip() if m else "(tidak terbaca)"))
    wifi = adb.shell("dumpsys", "wifi")
    m = re.search(r"SSID: ([^,\n]+)", wifi)
    print("     Wi-Fi SSID       : %s" % (m.group(1).strip() if m else "(tidak terbaca)"))


# ---------------------------------------------------------------------------
# Subcommand: --probe  (read-only)
# ---------------------------------------------------------------------------

def cmd_probe(adb, args):
    if not require_device(adb):
        return 1
    serial, props = device_identity(adb)
    model = props.get("ro.product.model") or props.get("ro.product.marketname") or "?"
    brand = props.get("ro.product.manufacturer") or props.get("ro.product.brand") or "?"
    android = props.get("ro.build.version.release", "?")
    sdk = props.get("ro.build.version.sdk", "?")

    mem_total = "?"
    meminfo = adb.shell("cat", "/proc/meminfo")
    m = re.search(r"MemTotal:\s+(\d+)\s*kB", meminfo)
    if m:
        mem_total = "%.2f GB" % (int(m.group(1)) / 1024.0 / 1024.0)
    df = adb.shell("df", "-h", "/data")
    df_line = df.strip().splitlines()[-1] if df.strip() else "?"

    all_pkgs = parse_packages(adb.shell("pm", "list", "packages"))
    sys_pkgs = parse_packages(adb.shell("pm", "list", "packages", "-s"))
    user_pkgs = parse_packages(adb.shell("pm", "list", "packages", "-3"))
    dis_pkgs = parse_packages(adb.shell("pm", "list", "packages", "-d"))
    ime = default_ime_package(adb)
    saver = sorted(screensaver_packages(adb))
    home_cur = adb.shell("cmd", "package", "resolve-activity", "-a", "android.intent.action.MAIN",
                         "-c", "android.intent.category.HOME").strip().splitlines()
    home_cur = home_cur[-1] if home_cur else "?"

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    lines = []
    lines.append("LAPORAN PROBE — Orange TV tv.py v%s" % TOOL_VERSION)
    lines.append("waktu            : %s" % datetime.now().isoformat(timespec="seconds"))
    lines.append("serial           : %s" % (serial or "(dry-run / tidak diketahui)"))
    lines.append("merek / model    : %s / %s" % (brand, model))
    lines.append("Android          : %s (SDK %s)" % (android, sdk))
    lines.append("RAM total        : %s" % mem_total)
    lines.append("penyimpanan /data: %s" % df_line)
    lines.append("paket total      : %d (sistem %d, pihak ketiga %d, sudah nonaktif %d)"
                 % (len(all_pkgs), len(sys_pkgs), len(user_pkgs), len(dis_pkgs)))
    lines.append("HOME saat ini    : %s" % home_cur)
    lines.append("keyboard bawaan  : %s" % (ime or "?"))
    lines.append("screensaver aktif: %s" % (", ".join(saver) if saver else "(tidak terbaca / tidak ada)"))
    lines.append("")
    lines.append("== SEMUA PAKET (nama | asal) ==")
    sys_set = set(sys_pkgs)
    for p in sorted(all_pkgs):
        lines.append("%s | %s" % (p, "system" if p in sys_set else "user"))
    if dis_pkgs:
        lines.append("")
        lines.append("== SUDAH NONAKTIF SEBELUM PROBE ==")
        lines.extend(sorted(dis_pkgs))
    report = "\n".join(lines) + "\n"

    if adb.dry_run:
        print(report)
        print("[dry-run] laporan di atas TIDAK ditulis ke berkas dan TV tidak disentuh.")
        return 0
    out_dir = DEFAULT_BACKUP_ROOT / ("probe-" + stamp)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "probe.txt").write_text(report, encoding="utf-8")
    print(report)
    print("== Laporan probe ditulis ke %s" % (out_dir / "probe.txt"))
    return 0


# ---------------------------------------------------------------------------
# Subcommand: --backup
# ---------------------------------------------------------------------------

def cmd_backup(adb, args):
    if not require_device(adb):
        return 1
    serial, props = device_identity(adb)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    bdir = DEFAULT_BACKUP_ROOT / stamp
    if adb.dry_run:
        print("[dry-run] folder cadangan yang AKAN dibuat: %s" % bdir)
    else:
        bdir.mkdir(parents=True, exist_ok=True)

    dumps = {
        "packages-all.txt": ("pm", "list", "packages"),
        "packages-with-paths.txt": ("pm", "list", "packages", "-f"),
        "packages-disabled.txt": ("pm", "list", "packages", "-d"),
        "packages-system.txt": ("pm", "list", "packages", "-s"),
        "packages-thirdparty.txt": ("pm", "list", "packages", "-3"),
        "packages-enabled.txt": ("pm", "list", "packages", "-e"),
        "getprop.txt": ("getprop",),
        "settings-system.txt": ("settings", "list", "system"),
        "settings-secure.txt": ("settings", "list", "secure"),
        "settings-global.txt": ("settings", "list", "global"),
        "home-activities.txt": ("cmd", "package", "query-activities", "-a",
                                "android.intent.action.MAIN", "-c", "android.intent.category.HOME"),
        "home-current.txt": ("cmd", "package", "resolve-activity", "-a",
                             "android.intent.action.MAIN", "-c", "android.intent.category.HOME"),
        "meminfo.txt": ("cat", "/proc/meminfo"),
    }
    counts = {}
    for fname, cmd in dumps.items():
        out = adb.shell(*cmd)
        if fname.startswith("packages-") and not fname.endswith("with-paths.txt"):
            counts[fname] = len(parse_packages(out))
        if not adb.dry_run:
            (bdir / fname).write_text(out, encoding="utf-8")
        print("    [backup] %-26s %s" % (fname, "(dry-run)" if adb.dry_run else "tersimpan"))

    saver = adb.shell("settings", "get", "secure", "screensaver_components").strip()
    ime = adb.shell("settings", "get", "secure", "default_input_method").strip()
    if not adb.dry_run:
        (bdir / "screensaver-components.txt").write_text(saver + "\n", encoding="utf-8")
        (bdir / "default-input-method.txt").write_text(ime + "\n", encoding="utf-8")

    manifest = {
        "tool": "orange-tv tv.py",
        "version": TOOL_VERSION,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "created_epoch": time.time(),
        "serial": serial,
        "model": props.get("ro.product.model"),
        "manufacturer": props.get("ro.product.manufacturer"),
        "android": props.get("ro.build.version.release"),
        "sdk": props.get("ro.build.version.sdk"),
        "counts": counts,
        "screensaver_components": saver if not adb.dry_run else "(dry-run)",
        "default_input_method": ime if not adb.dry_run else "(dry-run)",
    }
    if not adb.dry_run:
        (bdir / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
                                            encoding="utf-8")
    print("== Cadangan %s: %s" % ("(dry-run) selesai" if adb.dry_run else "selesai", bdir))
    print("   paket total %s | sistem %s | nonaktif %s"
          % (counts.get("packages-all.txt", "?"), counts.get("packages-system.txt", "?"),
             counts.get("packages-disabled.txt", "?")))
    return 0


# ---------------------------------------------------------------------------
# Subcommand: --disable
# ---------------------------------------------------------------------------

def cmd_disable(adb, args):
    if not args.list_files:
        print("!! --disable butuh --list <berkas> (boleh diulang untuk beberapa berkas).")
        return 2
    if args.batch_size < 1:
        print("!! --batch-size minimal 1.")
        return 2

    # 1) Kumpulkan kandidat dari semua berkas daftar.
    entries = []          # (pkg, note, sumber)
    seen = set()
    for lf in args.list_files:
        if not Path(lf).exists():
            print("!! Berkas daftar tidak ditemukan: %s" % lf)
            return 2
        for pkg, note in read_list_file(lf):
            if pkg not in seen:
                seen.add(pkg)
                entries.append((pkg, note, lf))
    if not entries:
        print("!! Tidak ada paket di berkas daftar.")
        return 2

    # 2) Saring daftar larangan keras (lokal, selalu jalan — juga di dry-run).
    blocked, candidates = [], []
    for pkg, note, src in entries:
        reason = denylist_reason(pkg)
        if reason:
            blocked.append((pkg, "LARANGAN KERAS: " + reason))
        else:
            candidates.append((pkg, note, src))

    # 3) Wajib ada cadangan segar SEBELUM apa pun disentuh.
    if args.dry_run:
        serial_now = None
    else:
        if not require_device(adb):
            return 1
        serial_now, _ = device_identity(adb)
    backup = check_backup(args, current_serial=serial_now)
    if backup is None:
        return 2
    bdir, _mf = backup

    # 4) Penjaga runtime dari perangkat (mode nyata): HOME, keyboard, screensaver aktif.
    if not adb.dry_run:
        guards = {}
        for p in home_packages(adb):
            guards[p] = "terdaftar sebagai aplikasi HOME/launcher di TV ini"
        ime = default_ime_package(adb)
        if ime:
            guards[ime] = "keyboard yang sedang aktif di TV ini"
        for p in screensaver_packages(adb):
            guards[p] = "screensaver yang SEDANG AKTIF di TV ini (TV akan tidur sendiri bila dinonaktifkan)"
        kept = []
        for pkg, note, src in candidates:
            if pkg in guards:
                blocked.append((pkg, "PENJAGA PERANGKAT: " + guards[pkg]))
            else:
                kept.append((pkg, note, src))
        candidates = kept

        # 5) Lewati yang tidak terpasang / sudah nonaktif.
        installed = set(parse_packages(adb.shell("pm", "list", "packages")))
        disabled_now = set(parse_packages(adb.shell("pm", "list", "packages", "-d")))
        kept = []
        for pkg, note, src in candidates:
            if pkg not in installed:
                print("   - %s : tidak terpasang, dilewati" % pkg)
            elif pkg in disabled_now:
                print("   - %s : sudah nonaktif, dilewati" % pkg)
            else:
                kept.append((pkg, note, src))
        candidates = kept

    # 6) Paket hati-hati butuh konfirmasi ekstra.
    hati = load_hati_hati()
    hh = [c for c in candidates if c[0] in hati]
    if hh:
        print("== %d paket HATI-HATI terdeteksi:" % len(hh))
        for pkg, note, _ in hh:
            print("   ! %s — %s" % (pkg, note or "perlu keyakinan ekstra"))
        if args.confirm_hati_hati:
            print("   (diterima lewat --confirm-hati-hati)")
        elif adb.dry_run:
            print("   [dry-run] di mode nyata paket ini butuh --confirm-hati-hati / konfirmasi prompt.")
        elif sys.stdin.isatty() and not args.yes:
            jawab = input("   Lanjutkan TERMASUK paket hati-hati? [y/N] ").strip().lower()
            if jawab not in ("y", "ya"):
                print("   Paket hati-hati dikeluarkan dari rencana.")
                candidates = [c for c in candidates if c[0] not in hati]
        else:
            print("   DITOLAK untuk paket hati-hati (mode non-interaktif tanpa --confirm-hati-hati);")
            print("   paket lain tetap diproses.")
            candidates = [c for c in candidates if c[0] not in hati]

    # 7) Tampilkan rencana.
    print("== Rencana: %d paket dalam batch ukuran %d; %d diblokir/ditolak."
          % (len(candidates), args.batch_size, len(blocked)))
    for pkg, reason in blocked:
        print("   DIBLOKIR %s — %s" % (pkg, reason))
    if not candidates:
        print("== Tidak ada yang bisa dinonaktifkan.")
        return 0
    batches = [candidates[i:i + args.batch_size] for i in range(0, len(candidates), args.batch_size)]
    for bi, batch in enumerate(batches, 1):
        print("   batch %d: %s" % (bi, ", ".join(p for p, _, _ in batch)))

    # 8) Eksekusi per batch + verifikasi + jeda.
    state = load_state(args.state_file)
    known = {e["package"] for e in state["disabled"]}
    done = []
    for bi, batch in enumerate(batches, 1):
        print("== Batch %d/%d" % (bi, len(batches)))
        for pkg, note, src in batch:
            marker = " [HATI-HATI]" if pkg in hati else ""
            if adb.dry_run:
                adb.shell("pm", "disable-user", "--user", "0", pkg)
                adb.shell("pm", "list", "packages", "-d")
                print("     [dry-run] %s%s akan dicatat ke state bila terverifikasi nonaktif" % (pkg, marker))
                done.append(pkg)
                continue
            out = adb.shell("pm", "disable-user", "--user", "0", pkg)
            check = set(parse_packages(adb.shell("pm", "list", "packages", "-d")))
            if pkg in check:
                print("     nonaktif: %s%s" % (pkg, marker))
                if pkg not in known:
                    state["disabled"].append({
                        "package": pkg,
                        "disabled_at": datetime.now().isoformat(timespec="seconds"),
                        "batch": "%s-%d" % (Path(src).stem, bi),
                        "list_file": src,
                        "backup": str(bdir),
                    })
                    known.add(pkg)
                    save_state(args.state_file, state)  # catat SEGERA, satu per satu
                done.append(pkg)
            else:
                print("     !! GAGAL menonaktifkan %s (keluaran: %s)" % (pkg, out.strip() or "?"))
        verification_snapshot(adb, "setelah batch %d" % bi)
        if bi < len(batches) and not adb.dry_run and not args.yes and sys.stdin.isatty():
            print("   CEK TV DULU: apakah Home tampil normal, remote berfungsi, Wi-Fi tersambung?")
            jawab = input("   Lanjut ke batch berikutnya? [y/N] ").strip().lower()
            if jawab not in ("y", "ya"):
                print("   Berhenti di sini. %d paket sudah dinonaktifkan & tercatat di state." % len(done))
                break
    print("== Selesai. %d paket dinonaktifkan sesi ini. State: %s" % (len(done), args.state_file))
    print("   Urungkan kapan pun dengan: python3 tv.py --restore --tv <ip-tv>")
    return 0


# ---------------------------------------------------------------------------
# Subcommand: --restore
# ---------------------------------------------------------------------------

def cmd_restore(adb, args):
    state = load_state(args.state_file)
    recorded = [e["package"] for e in state["disabled"]]
    if args.package:
        targets = list(dict.fromkeys(args.package))
    else:
        targets = list(recorded)
    if not targets:
        print("== Tidak ada paket tercatat di state; tidak ada yang dipulihkan.")
        return 0
    if not adb.dry_run and not require_device(adb):
        return 1
    print("== Memulihkan %d paket:" % len(targets))
    ok = []
    for pkg in targets:
        if adb.dry_run:
            adb.shell("pm", "enable", "--user", "0", pkg)
            ok.append(pkg)
            continue
        out = adb.shell("pm", "enable", "--user", "0", pkg)
        if "enabled" in out.lower() or pkg not in parse_packages(adb.shell("pm", "list", "packages", "-d")):
            print("   aktif lagi: %s" % pkg)
            ok.append(pkg)
        else:
            print("   !! belum aktif: %s (keluaran: %s)" % (pkg, out.strip() or "?"))
    if not adb.dry_run:
        state["disabled"] = [e for e in state["disabled"] if e["package"] not in ok]
        save_state(args.state_file, state)
        sisa = parse_packages(adb.shell("pm", "list", "packages", "-d"))
        print("== Selesai. Masih nonaktif di TV: %d paket." % len(sisa))
    else:
        print("== [dry-run] %d paket AKAN diaktifkan lagi; state tidak diubah." % len(ok))
    return 0


# ---------------------------------------------------------------------------
# Subcommand: --status
# ---------------------------------------------------------------------------

def cmd_status(adb, args):
    state = load_state(args.state_file)
    print("== Status Orange TV tv.py v%s" % TOOL_VERSION)
    print("   state: %d paket dinonaktifkan oleh alat ini (%s)"
          % (len(state["disabled"]), args.state_file))
    bdir, mf = find_latest_backup(args)
    if mf:
        age_h = (time.time() - float(mf["created_epoch"])) / 3600.0
        print("   cadangan terakhir: %s (umur %.1f jam, model %s)" % (bdir, age_h, mf.get("model", "?")))
    else:
        print("   cadangan terakhir: (belum ada — jalankan --backup)")
    if adb.dry_run:
        adb.shell("getprop", "ro.product.model")
        adb.shell("dumpsys", "activity", "activities")
        adb.shell("dumpsys", "wifi")
        adb.shell("pm", "list", "packages", "-d")
        print("   [dry-run] perintah di atas yang AKAN dijalankan untuk status perangkat.")
        return 0
    if not require_device(adb):
        return 1
    _serial, props = device_identity(adb)
    print("   perangkat: %s (Android %s)" % (props.get("ro.product.model", "?"),
                                            props.get("ro.build.version.release", "?")))
    acts = adb.shell("dumpsys", "activity", "activities")
    m = re.search(r"(?:topResumedActivity|mResumedActivity)[^\n]*", acts)
    print("   aktivitas teratas: %s" % (m.group(0).strip() if m else "(tidak terbaca)"))
    wifi = adb.shell("dumpsys", "wifi")
    m = re.search(r"SSID: ([^,\n]+)", wifi)
    print("   Wi-Fi SSID: %s" % (m.group(1).strip() if m else "(tidak terbaca)"))
    dis = parse_packages(adb.shell("pm", "list", "packages", "-d"))
    print("   paket nonaktif di TV saat ini: %d" % len(dis))
    up = adb.shell("uptime").strip()
    print("   uptime: %s" % up)
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser():
    p = argparse.ArgumentParser(
        prog="tv.py",
        description="Alat Debloat Orange TV — nonaktifkan bloatware Android TV secara aman "
                    "(hanya pm disable-user, selalu bisa dipulihkan, wajib backup dulu).",
        epilog="Contoh:\n"
               "  python3 tv.py --probe --tv 192.168.1.10\n"
               "  python3 tv.py --backup --tv 192.168.1.10\n"
               "  python3 tv.py --disable --tv 192.168.1.10 --list lists/umum-bloat.txt\n"
               "  python3 tv.py --disable --tv 192.168.1.10 --list lists/xiaomi.txt --batch-size 4\n"
               "  python3 tv.py --disable --tv 192.168.1.10 --list lists/hati-hati.txt --confirm-hati-hati\n"
               "  python3 tv.py --status --tv 192.168.1.10\n"
               "  python3 tv.py --restore --tv 192.168.1.10 [--package com.contoh.paket]\n"
               "  python3 tv.py --disable --dry-run --list lists/umum-bloat.txt   (tanpa TV, hanya cetak rencana)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    aksi = p.add_mutually_exclusive_group(required=True)
    aksi.add_argument("--probe", action="store_true", help="baca kondisi TV, tulis laporan probe (read-only)")
    aksi.add_argument("--backup", action="store_true", help="simpan cadangan keadaan TV ke tools/backup/<waktu>/")
    aksi.add_argument("--disable", action="store_true", help="nonaktifkan paket dari --list, per batch, dengan verifikasi")
    aksi.add_argument("--restore", action="store_true", help="aktifkan lagi paket yang tercatat di state")
    aksi.add_argument("--status", action="store_true", help="ringkasan status perangkat + state + cadangan")

    p.add_argument("--tv", metavar="IP[:PORT]", help="alamat TV; port bawaan 5555 (adb connect dilakukan dulu)")
    p.add_argument("--serial", help="serial/perangkat adb tertentu (alternatif --tv, mis. untuk USB)")
    p.add_argument("--adb", help="path ke binary adb (bawaan: cari di PATH & lokasi umum)")
    p.add_argument("--list", dest="list_files", action="append", default=[], metavar="BERKAS",
                   help="berkas daftar paket untuk --disable; boleh diulang")
    p.add_argument("--package", action="append", default=[], metavar="PAKET",
                   help="untuk --restore: hanya paket ini (boleh diulang); bawaan: semua di state")
    p.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE,
                   help="paket per batch saat --disable (bawaan %d)" % DEFAULT_BATCH_SIZE)
    p.add_argument("--backup-dir", help="pakai folder cadangan tertentu (bawaan: yang terbaru di tools/backup/)")
    p.add_argument("--max-backup-age", type=float, default=DEFAULT_MAX_BACKUP_AGE_HOURS, metavar="JAM",
                   help="umur cadangan maksimum untuk --disable (bawaan %.0f jam)" % DEFAULT_MAX_BACKUP_AGE_HOURS)
    p.add_argument("--state-file", default=str(DEFAULT_STATE_FILE),
                   help="lokasi berkas state (bawaan: tools/state/state.json)")
    p.add_argument("--confirm-hati-hati", action="store_true",
                   help="setujui paket dari lists/hati-hati.txt tanpa prompt")
    p.add_argument("--yes", action="store_true",
                   help="lewati prompt jeda antar-batch (penjaga keamanan lain tetap berlaku)")
    p.add_argument("--dry-run", action="store_true",
                   help="cetak semua perintah yang AKAN dijalankan; tidak menyentuh perangkat")
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    adb = make_adb(args)
    if adb is None:
        return 1
    try:
        if args.probe:
            return cmd_probe(adb, args)
        if args.backup:
            return cmd_backup(adb, args)
        if args.disable:
            return cmd_disable(adb, args)
        if args.restore:
            return cmd_restore(adb, args)
        if args.status:
            return cmd_status(adb, args)
    except AdbError as e:
        print("!! %s" % e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
