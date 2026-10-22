package id.orangetv.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Kartu jadwal pertandingan Orange TV (v2.2).
 *
 * Data diterbitkan sebagai jadwal.json di repo GitHub proyek ini
 * (dibuat oleh scripts/ambil-jadwal.py dari TheSportsDB) dan diambil
 * lewat raw.githubusercontent.com saat launcher dibuka/dilanjutkan.
 * Bila pengambilan gagal, dipakai salinan cache terakhir di penyimpanan
 * internal; bila tidak ada data sama sekali, kartu disembunyikan.
 *
 * Daftar di jadwal.json sudah terurut prioritas (Timnas dulu, lalu big
 * match, lalu sisanya menurut kick-off). Kelas ini memilih entri PERTAMA
 * yang kick-off-nya masuk jendela tampil:
 *   [kick-off − 2 hari , kick-off + 3 jam].
 */
public final class Jadwal {

    private static final String URL_JADWAL =
            "https://raw.githubusercontent.com/ardiansyahmam/orange-tv/main/jadwal/jadwal.json";
    private static final String BERKAS_CACHE = "jadwal-cache.json";
    private static final long JENDELA_SEBELUM_MS = 2L * 24 * 3600 * 1000; // H-2
    private static final long JENDELA_SESUDAH_MS = 3L * 3600 * 1000;       // +3 jam
    private static final Locale ID = new Locale("id", "ID");

    /** Satu pertandingan yang siap ditampilkan. */
    public static final class Match {
        public final String tim;
        public final String lawan;
        public final String kompetisi;
        public final boolean kandang;
        public final long kickoffMs;

        Match(String tim, String lawan, String kompetisi, boolean kandang, long kickoffMs) {
            this.tim = tim;
            this.lawan = lawan;
            this.kompetisi = kompetisi;
            this.kandang = kandang;
            this.kickoffMs = kickoffMs;
        }
    }

    public interface Callback {
        /** Dipanggil di utas utama; match null = kartu disembunyikan. */
        void onMatch(Match match);
    }

    private Jadwal() {
    }

    /** Ambil jadwal di latar belakang; hasilnya lewat {@link Callback}. */
    public static void muat(final Context context, final Callback callback) {
        final Handler utama = new Handler(Looper.getMainLooper());
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String json = ambilDariServer(context);
                if (json == null) {
                    json = bacaCache(context);
                }
                final Match m = (json != null) ? pilih(json, System.currentTimeMillis()) : null;
                utama.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onMatch(m);
                    }
                });
            }
        }, "jadwal-ambil");
        t.setDaemon(true);
        t.start();
    }

    private static String ambilDariServer(Context context) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(URL_JADWAL).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "OrangeTV/2.2");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            String isi = bacaSemua(conn.getInputStream());
            if (isi != null && isi.length() > 2) {
                simpanCache(context, isi);
                return isi;
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String bacaSemua(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String baris;
        while ((baris = r.readLine()) != null) {
            sb.append(baris).append('\n');
        }
        r.close();
        return sb.toString();
    }

    private static void simpanCache(Context context, String isi) {
        try {
            FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), BERKAS_CACHE));
            out.write(isi.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) {
            // Cache gagal simpan bukan masalah fatal; kartu tetap tampil dari server.
        }
    }

    private static String bacaCache(Context context) {
        try {
            File f = new File(context.getFilesDir(), BERKAS_CACHE);
            if (!f.exists()) {
                return null;
            }
            return bacaSemua(new FileInputStream(f));
        } catch (Exception e) {
            return null;
        }
    }

    /** Pilih entri pertama (daftar terurut prioritas) yang masuk jendela tampil. */
    static Match pilih(String json, long nowMs) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray daftar = root.optJSONArray("jadwal");
            if (daftar == null) {
                return null;
            }
            for (int i = 0; i < daftar.length(); i++) {
                JSONObject o = daftar.getJSONObject(i);
                long ko = parseKickoff(o.optString("kickoff_utc", ""));
                if (ko <= 0) {
                    continue;
                }
                if (nowMs >= ko - JENDELA_SEBELUM_MS && nowMs <= ko + JENDELA_SESUDAH_MS) {
                    return new Match(
                            o.optString("tim", ""),
                            o.optString("lawan", ""),
                            o.optString("kompetisi", ""),
                            o.optBoolean("kandang", true),
                            ko);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseKickoff(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
            // Cadangan: format tanpa zona / detik sebagian.
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String bersih = s.replace("Z", "").trim();
            Date d = f.parse(bersih);
            return d != null ? d.getTime() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Format tampilan (Bahasa Indonesia, zona waktu perangkat)
    // ------------------------------------------------------------------

    /** "Sabtu · 23:30 WIB" — hari & jam lokal + singkatan zona perangkat. */
    public static String formatHariJam(long kickoffMs) {
        Date d = new Date(kickoffMs);
        String hariJam = new SimpleDateFormat("EEEE · HH:mm", ID).format(d);
        String zona = new SimpleDateFormat("zzz", ID).format(d);
        return hariJam + " " + zona;
    }

    /**
     * Label hitung mundur menurut hari kalender lokal:
     * &gt;1 hari "dalam N hari", besok "BESOK", hari yang sama "MALAM INI"
     * (kick-off ≥ 18:00) atau "HARI INI", sesudah kick-off "SEDANG BERLANGSUNG".
     */
    public static String hitungMundur(long kickoffMs, long nowMs) {
        ZoneId zona = ZoneId.systemDefault();
        LocalDate hariIni = new Date(nowMs).toInstant().atZone(zona).toLocalDate();
        LocalDate hariKo = new Date(kickoffMs).toInstant().atZone(zona).toLocalDate();
        long selisih = ChronoUnit.DAYS.between(hariIni, hariKo);
        if (selisih > 1) {
            return "dalam " + selisih + " hari";
        }
        if (selisih == 1) {
            return "BESOK";
        }
        if (selisih == 0) {
            if (nowMs > kickoffMs) {
                return "SEDANG BERLANGSUNG";
            }
            int jam = new Date(kickoffMs).toInstant().atZone(zona).getHour();
            return jam >= 18 ? "MALAM INI" : "HARI INI";
        }
        return "SEDANG BERLANGSUNG";
    }
}
