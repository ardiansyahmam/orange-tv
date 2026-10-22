package id.orangetv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Katalog aplikasi terpasang di TV.
 *
 * Daftar "pilihan utama" di bawah hanya dipakai untuk MENYEMAI Favorit pada
 * lari pertama (first run). Sesudah itu Favorit sepenuhnya milik perangkat:
 * pengguna menambah/menghapus sendiri lewat tekan-lama, tersimpan di
 * SharedPreferences. Tidak ada yang dikunci ke satu rumah tangga tertentu.
 */
public final class AppCatalog {

    /**
     * Paket pilihan utama, berurutan prioritas. Tiga pertama adalah aplikasi
     * yang dipakai pemilik proyek; sisanya layanan streaming besar lain.
     * Hanya yang benar-benar terpasang yang akan dipakai.
     */
    public static final String[] PREFERRED_PACKAGES = {
            "com.google.android.youtube.tv",      // YouTube (Android TV)
            "com.google.android.youtube",         // YouTube
            "com.netflix.mediaclient",            // Netflix
            "com.tencent.qqlive",                 // WeTV
            "com.google.android.apps.youtube.music", // YouTube Music
            "com.vidio.android",                  // Vidio
            "in.startv.hotstar",                  // Disney+ Hotstar
            "com.disney.disneyplus",              // Disney+
            "com.amazon.avod.thirdpartyclient",   // Prime Video
            "com.spotify.music",                  // Spotify
            "com.spotify.tv.android",             // Spotify TV
    };

    /** Jumlah minimum Favorit hasil semaian pertama kali. */
    public static final int SEED_MIN_FAVORITES = 3;

    private AppCatalog() {
    }

    /**
     * Muat semua aplikasi yang punya layar peluncur (kategori LAUNCHER dan
     * LEANBACK_LAUNCHER digabung), selain Orange TV sendiri, terurut label A-Z.
     */
    public static List<AppEntry> loadApps(Context context) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Map<String, AppEntry> byComponent = new LinkedHashMap<>();

        collect(pm, self, Intent.CATEGORY_LAUNCHER, byComponent);
        collect(pm, self, Intent.CATEGORY_LEANBACK_LAUNCHER, byComponent);

        List<AppEntry> apps = new ArrayList<>(byComponent.values());
        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return String.CASE_INSENSITIVE_ORDER.compare(a.label, b.label);
            }
        });
        return apps;
    }

    private static void collect(PackageManager pm, String self, String category,
                                Map<String, AppEntry> out) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(category);
        List<ResolveInfo> results = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo ri : results) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self)) {
                continue; // Orange TV tidak menampilkan dirinya sendiri
            }
            android.content.ComponentName component = new android.content.ComponentName(
                    pkg, ri.activityInfo.name);
            String key = component.flattenToString();
            if (out.containsKey(key)) {
                continue; // sudah tercatat dari kategori lain
            }
            CharSequence label = ri.loadLabel(pm);
            out.put(key, new AppEntry(
                    label != null ? label.toString() : pkg,
                    pkg,
                    component,
                    ri.loadIcon(pm)));
        }
    }

    /** Cari entri aplikasi berdasarkan nama paket; null bila tidak terpasang. */
    public static AppEntry findByPackage(List<AppEntry> apps, String packageName) {
        for (AppEntry app : apps) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    /**
     * Semaian Favorit untuk lari pertama: ambil paket pilihan utama yang
     * terpasang (sesuai urutan prioritas). Bila yang cocok kurang dari
     * {@link #SEED_MIN_FAVORITES}, lengkapi dari aplikasi terpasang lain
     * menurut abjad sampai minimum tercapai.
     */
    public static List<String> seedFavorites(List<AppEntry> apps) {
        List<String> seed = new ArrayList<>();
        for (String pkg : PREFERRED_PACKAGES) {
            if (findByPackage(apps, pkg) != null && !seed.contains(pkg)) {
                seed.add(pkg);
            }
        }
        if (seed.size() < SEED_MIN_FAVORITES) {
            for (AppEntry app : apps) { // apps sudah terurut abjad
                if (seed.size() >= SEED_MIN_FAVORITES) {
                    break;
                }
                if (!seed.contains(app.packageName)) {
                    seed.add(app.packageName);
                }
            }
        }
        return seed;
    }
}
