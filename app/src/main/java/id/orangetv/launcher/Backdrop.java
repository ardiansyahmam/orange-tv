package id.orangetv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Latar sinematik layar penuh yang mengikuti aplikasi yang sedang terfokus,
 * ala layar utama konsol modern: poster abstrak raksasa di belakang strip ikon.
 *
 * Tidak ada gambar poster sungguhan — setiap latar DIGAMBAR dari kode:
 * gradien dalam dari palet warna khas aplikasi, cahaya radial lembut,
 * beberapa bidang cahaya diagonal yang sangat halus, lalu vignette gelap
 * di tepi. Untuk aplikasi yang tidak dikenal, palet diturunkan dari hash
 * nama paket (selalu nada permata gelap di mode Malam).
 *
 * Di mode Siang latar yang sama diberi "cucian terang" (warm off-white)
 * agar antarmuka kaca terang tetap terbaca.
 *
 * Bitmap dirender paling besar 1280px lebar dan disimpan di cache kecil —
 * TV ber-RAM kecil tidak perlu latar 4K untuk efek selembut ini.
 */
public final class Backdrop {

    /** Palet satu aplikasi: warna dasar, warna tengah gradien, warna cahaya. */
    public static final class Palette {
        final int base;
        final int mid;
        final int glow;

        Palette(int base, int mid, int glow) {
            this.base = base;
            this.mid = mid;
            this.glow = glow;
        }
    }

    /** Palet netral hangat untuk ubin alat (Pengaturan &amp; kawan-kawan). */
    public static final Palette NEUTRAL =
            new Palette(0xFF171310, 0xFF0D0B09, 0xFF8A4B12);

    private static final Map<String, Palette> CURATED = new LinkedHashMap<>();

    static {
        CURATED.put("com.google.android.youtube.tv", new Palette(0xFF1C0605, 0xFF0D0202, 0xFFC2271F)); // YouTube: merah gelap
        CURATED.put("com.google.android.youtube", CURATED.get("com.google.android.youtube.tv"));
        CURATED.put("com.google.android.apps.youtube.music", new Palette(0xFF1A0505, 0xFF0A0202, 0xFFD92B1F)); // YT Music
        CURATED.put("com.netflix.mediaclient", new Palette(0xFF12040A, 0xFF050207, 0xFFB00610));       // Netflix: hitam-merah tua
        CURATED.put("com.tencent.qqlive", new Palette(0xFF04101F, 0xFF020812, 0xFF1E6FB8));          // WeTV: biru dalam
        CURATED.put("com.vidio.android", new Palette(0xFF180511, 0xFF0A0208, 0xFFC2185B));             // Vidio: magenta gelap
        CURATED.put("com.disney.disneyplus", new Palette(0xFF050B26, 0xFF02040F, 0xFF2B4BD8));         // Disney+: navy dalam
        CURATED.put("in.startv.hotstar", new Palette(0xFF050B26, 0xFF02040F, 0xFF2B4BD8));             // Disney+ Hotstar
        CURATED.put("com.amazon.avod.thirdpartyclient", new Palette(0xFF04141F, 0xFF02090F, 0xFF0E7FA8)); // Prime Video: biru
        CURATED.put("com.spotify.music", new Palette(0xFF04140A, 0xFF020A05, 0xFF1A9E48));             // Spotify: hijau gelap
        CURATED.put("com.spotify.tv.android", CURATED.get("com.spotify.music"));
    }

    /** Nada permata gelap untuk palet turunan hash. */
    private static final Palette[] JEWELS = {
            new Palette(0xFF06231F, 0xFF03120F, 0xFF0E7C6B), // teal
            new Palette(0xFF150A2E, 0xFF0A0518, 0xFF5B34C4), // ungu
            new Palette(0xFF1C1206, 0xFF0F0903, 0xFFA86F10), // ambar
            new Palette(0xFF0A1030, 0xFF05081A, 0xFF3B5BDB), // indigo
            new Palette(0xFF20060F, 0xFF100309, 0xFFB3225A), // mawar
            new Palette(0xFF071A10, 0xFF030E08, 0xFF2F8F46), // hutan
            new Palette(0xFF0B1526, 0xFF050A14, 0xFF41619C), // biru baja
    };

    private Backdrop() {
    }

    /** Palet untuk satu paket; aplikasi tak dikenal memakai turunan hash. */
    public static Palette paletteFor(String packageName) {
        if (packageName == null) {
            return NEUTRAL;
        }
        Palette p = CURATED.get(packageName);
        if (p != null) {
            return p;
        }
        int idx = (packageName.hashCode() & 0x7FFFFFFF) % JEWELS.length;
        return JEWELS[idx];
    }

    /** Cache kecil bitmap latar (kunci: paket + mode), urutan akses LRU sederhana. */
    private static final int MAX_CACHE = 10;
    private static final LinkedHashMap<String, Bitmap> CACHE =
            new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    if (size() > MAX_CACHE) {
                        eldest.getValue().recycle();
                        return true;
                    }
                    return false;
                }
            };

    /**
     * Render (atau ambil dari cache) bitmap latar untuk paket tertentu.
     * Lebar dibatasi 1280px agar hemat memori; tinggi mengikuti rasio layar.
     */
    public static Bitmap render(String packageName, int width, int height, boolean night) {
        if (width <= 0 || height <= 0) {
            width = 1280;
            height = 720;
        }
        if (width > 1280) {
            height = height * 1280 / width;
            width = 1280;
        }
        String key = (packageName == null ? "netral" : packageName) + (night ? "#m" : "#s");
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(key);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }

        Palette p = paletteFor(packageName);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1) Gradien dasar diagonal: tengah sedikit lebih terang di kiri-atas.
        paint.setShader(new LinearGradient(0, 0, width, height,
                new int[]{lighten(p.mid, 26), p.base, darken(p.mid, 10)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // 2) Cahaya radial utama (kanan-atas, seperti poster film yang menyala).
        float gx = width * 0.68f, gy = height * 0.34f, gr = width * 0.62f;
        paint.setShader(new RadialGradient(gx, gy, gr,
                withAlpha(p.glow, night ? 118 : 84), withAlpha(p.glow, 0),
                Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // 3) Cahaya kedua yang lebih redup di kiri-bawah.
        paint.setShader(new RadialGradient(width * 0.12f, height * 0.92f, width * 0.45f,
                withAlpha(p.mid == p.base ? p.glow : p.glow, night ? 46 : 30),
                withAlpha(p.glow, 0), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // 4) Bidang cahaya diagonal sangat halus (kesannya "sinematik").
        paint.setStyle(Paint.Style.FILL);
        cv.save();
        cv.rotate(-16, width / 2f, height / 2f);
        paint.setColor(withAlpha(0xFFFFFFFF, night ? 13 : 9));
        cv.drawRect(width * 0.52f, -height * 0.3f, width * 0.60f, height * 1.3f, paint);
        paint.setColor(withAlpha(0xFFFFFFFF, night ? 8 : 6));
        cv.drawRect(width * 0.66f, -height * 0.3f, width * 0.86f, height * 1.3f, paint);
        paint.setColor(withAlpha(p.glow, night ? 22 : 14));
        cv.drawRect(width * 0.18f, -height * 0.3f, width * 0.235f, height * 1.3f, paint);
        cv.restore();

        // 5) Vignette: tepi digelapkan agar fokus tetap ke tengah layar.
        paint.setShader(new RadialGradient(width / 2f, height / 2f, width * 0.72f,
                new int[]{0x00000000, 0x00000000, withAlpha(0xFF000000, night ? 168 : 92)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // 6) Mode Siang: cucian terang hangat agar kaca terang tetap terbaca.
        if (!night) {
            paint.setColor(0xCFF4ECDD); // off-white hangat, alpha ~81%
            cv.drawRect(0, 0, width, height, paint);
        }

        synchronized (CACHE) {
            CACHE.put(key, bmp);
        }
        return bmp;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int lighten(int color, int amount) {
        return Color.rgb(
                Math.min(255, Color.red(color) + amount),
                Math.min(255, Color.green(color) + amount),
                Math.min(255, Color.blue(color) + amount));
    }

    private static int darken(int color, int amount) {
        return Color.rgb(
                Math.max(0, Color.red(color) - amount),
                Math.max(0, Color.green(color) - amount),
                Math.max(0, Color.blue(color) - amount));
    }
}
