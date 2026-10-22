package id.orangetv.launcher;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Latar sinematik layar penuh yang mengikuti aplikasi yang sedang terfokus,
 * ala layar utama konsol modern: poster abstrak raksasa di belakang strip ikon.
 *
 * Untuk aplikasi populer yang terkurasi, latarnya adalah ARTWORK
 * sinematik ORIGINAL yang dibundel di res/drawable-nodpi (latar_*.jpg —
 * karya orisinal yang dibuat khusus untuk Orange TV, bukan poster film
 * sungguhan). Artwork digambar menutupi layar (center-crop) di atas
 * gradien palet sebagai lapisan bawah/cadangan.
 *
 * Aplikasi lain memakai latar yang DIGAMBAR dari kode: gradien dalam
 * dari palet warna khas aplikasi, cahaya radial lembut, beberapa bidang
 * cahaya diagonal yang sangat halus, lalu vignette gelap di tepi. Untuk
 * aplikasi yang tidak dikenal, palet diturunkan dari hash nama paket
 * (selalu nada permata gelap di mode Malam).
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

    /**
     * Artwork sinematik original yang dibundel (v2.1), satu per aplikasi
     * populer. Semua karya orisinal — tanpa teks, tanpa logo, tanpa tokoh
     * atau adegan berhak cipta. Paket yang tidak ada di peta ini memakai
     * gradien tergambar seperti sebelumnya.
     */
    private static final Map<String, Integer> ARTWORK = new LinkedHashMap<>();

    static {
        ARTWORK.put("com.netflix.mediaclient", R.drawable.latar_netflix);
        ARTWORK.put("com.google.android.youtube.tv", R.drawable.latar_youtube);
        ARTWORK.put("com.google.android.youtube", R.drawable.latar_youtube);
        ARTWORK.put("com.tencent.qqlive", R.drawable.latar_wetv);
        ARTWORK.put("com.vidio.android", R.drawable.latar_vidio);
        ARTWORK.put("com.disney.disneyplus", R.drawable.latar_disney);
        ARTWORK.put("in.startv.hotstar", R.drawable.latar_disney);
        ARTWORK.put("com.amazon.avod.thirdpartyclient", R.drawable.latar_prime);
        ARTWORK.put("com.spotify.music", R.drawable.latar_spotify);
        ARTWORK.put("com.spotify.tv.android", R.drawable.latar_spotify);
    }

    /** Resources aplikasi untuk membaca artwork; diisi sekali dari Activity. */
    private static Resources resources;

    /** Wajib dipanggil sekali (dari MainActivity.onCreate) sebelum render. */
    public static void init(Context context) {
        if (resources == null && context != null) {
            resources = context.getApplicationContext().getResources();
        }
    }

    /** Cache kecil bitmap artwork hasil dekode (maks 4, LRU). */
    private static final LinkedHashMap<Integer, Bitmap> ART_CACHE =
            new LinkedHashMap<Integer, Bitmap>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
                    if (size() > 4) {
                        eldest.getValue().recycle();
                        return true;
                    }
                    return false;
                }
            };

    /**
     * Dekode artwork untuk paket ini (di-downsample agar lebarnya tidak
     * melebihi target), atau null bila paket tidak punya artwork / belum
     * init / dekode gagal — pemanggil lalu memakai gradien.
     */
    private static Bitmap artworkFor(String packageName, int targetWidth) {
        if (resources == null || packageName == null) {
            return null;
        }
        Integer resId = ARTWORK.get(packageName);
        if (resId == null) {
            return null;
        }
        synchronized (ART_CACHE) {
            Bitmap cached = ART_CACHE.get(resId);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(resources, resId, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetWidth && sample < 16) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeResource(resources, resId, opts);
            if (bmp != null) {
                synchronized (ART_CACHE) {
                    ART_CACHE.put(resId, bmp);
                }
            }
            return bmp;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Gambar src memenuhi dst dengan pemotongan tengah (center-crop). */
    private static void drawCenterCrop(Canvas cv, Bitmap src, int dstW, int dstH, Paint paint) {
        float scale = Math.max((float) dstW / src.getWidth(), (float) dstH / src.getHeight());
        int cropW = Math.round(dstW / scale);
        int cropH = Math.round(dstH / scale);
        int left = Math.max(0, (src.getWidth() - cropW) / 2);
        int top = Math.max(0, (src.getHeight() - cropH) / 2);
        Rect s = new Rect(left, top, left + cropW, top + cropH);
        Rect d = new Rect(0, 0, dstW, dstH);
        cv.drawBitmap(src, s, d, paint);
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

        Bitmap art = artworkFor(packageName, width);
        if (art != null) {
            // Artwork sinematik original (v2.1) menutupi seluruh layar;
            // gradien palet di cabang else adalah lapisan bawah/cadangan.
            paint.setFilterBitmap(true);
            drawCenterCrop(cv, art, width, height, paint);
            paint.setFilterBitmap(false);
        } else {
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
        } // akhir cabang gradien (bila tidak ada artwork)

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
