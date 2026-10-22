package id.orangetv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Layar utama Orange TV — satu layar sederhana ala Apple TV:
 * bar atas (logo + jam), baris Favorit, kisi Semua Aplikasi, baris Pengaturan.
 *
 * Semua personalisasi (mode Siang/Malam + daftar Favorit) tinggal di
 * SharedPreferences perangkat, jadi launcher yang sama bisa dibagikan ke
 * siapa pun dengan aplikasi dan selera yang berbeda-beda.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "orange_tv";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_FAVORITES = "favorites";
    private static final int MAX_FAVORITES = 8;
    private static final String FAV_SEPARATOR = "\n";

    private SharedPreferences prefs;
    private UiTheme theme;
    private boolean nightMode;

    private List<AppEntry> apps = new ArrayList<>();
    private final List<String> favoritePackages = new ArrayList<>();

    private GridLayout favoritesGrid;
    private GridLayout appsGrid;
    private TextView clockView;
    private TextView dateView;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30_000); // segarkan tiap 30 detik
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        nightMode = prefs.getBoolean(KEY_NIGHT_MODE, true); // bawaan: Malam
        theme = UiTheme.of(nightMode);

        apps = AppCatalog.loadApps(this);
        loadOrSeedFavorites();

        setContentView(buildScreen());
        populateSections();

        // Fokus awal ke ubin pertama agar remote langsung bisa dipakai.
        favoritesGrid.post(new Runnable() {
            @Override
            public void run() {
                if (favoritesGrid.getChildCount() > 0) {
                    favoritesGrid.getChildAt(0).requestFocus();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    // ------------------------------------------------------------------
    // Pembangunan layar (seluruhnya dari kode, tanpa layout XML)
    // ------------------------------------------------------------------

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.bgTop, theme.bgBottom});
        scroll.setBackground(bg);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padX = TileFactory.dp(this, 48);
        content.setPadding(padX, TileFactory.dp(this, 30), padX, TileFactory.dp(this, 40));
        scroll.addView(content);

        content.addView(buildTopBar());

        content.addView(sectionLabel("Favorit"));
        favoritesGrid = new GridLayout(this);
        favoritesGrid.setColumnCount(4);
        content.addView(favoritesGrid);

        TextView hint = new TextView(this);
        hint.setText("Tekan lama OK untuk tambah/hapus Favorit");
        hint.setTextColor(theme.textSecondary);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = TileFactory.dp(this, 10);
        hint.setLayoutParams(hintParams);
        content.addView(hint);

        content.addView(sectionLabel("Semua Aplikasi"));
        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(5);
        content.addView(appsGrid);

        content.addView(sectionLabel("Pengaturan"));
        content.addView(buildSettingsRow());

        return scroll;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // Logo kecil: kotak oranye sudut membulat + segitiga putih.
        // Satu dari sedikit tempat oranye muncul — dan hanya seukuran perangko.
        TextView logo = new TextView(this);
        logo.setText("▶");
        logo.setTextColor(0xFFFFFFFF);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(UiTheme.ACCENT);
        logoBg.setCornerRadius(TileFactory.dp(this, 8));
        logo.setBackground(logoBg);
        int logoSize = TileFactory.dp(this, 30);
        bar.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        // Nama + garis aksen oranye tipis di bawahnya.
        LinearLayout nameColumn = new LinearLayout(this);
        nameColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = TileFactory.dp(this, 12);
        nameColumn.setLayoutParams(nameParams);

        TextView wordmark = new TextView(this);
        wordmark.setText("Orange TV");
        wordmark.setTextColor(theme.textPrimary);
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        wordmark.setTypeface(Typeface.DEFAULT_BOLD);
        nameColumn.addView(wordmark);

        View accentLine = new View(this);
        GradientDrawable lineBg = new GradientDrawable();
        lineBg.setColor(UiTheme.ACCENT);
        lineBg.setCornerRadius(TileFactory.dp(this, 2));
        accentLine.setBackground(lineBg);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                TileFactory.dp(this, 54), TileFactory.dp(this, 3));
        lineParams.topMargin = TileFactory.dp(this, 5);
        accentLine.setLayoutParams(lineParams);
        nameColumn.addView(accentLine);
        bar.addView(nameColumn);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Jam + tanggal (Bahasa Indonesia).
        LinearLayout clockColumn = new LinearLayout(this);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        clockColumn.setGravity(Gravity.RIGHT);
        clockView = new TextView(this);
        clockView.setTextColor(theme.textPrimary);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.RIGHT);
        clockColumn.addView(clockView);
        dateView = new TextView(this);
        dateView.setTextColor(theme.textSecondary);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        dateView.setGravity(Gravity.RIGHT);
        clockColumn.addView(dateView);
        bar.addView(clockColumn);

        // Chip mode: pil kaca dengan pinggir oranye tipis (status aktif).
        TextView chip = new TextView(this);
        chip.setText(nightMode ? "Mode Malam" : "Mode Siang");
        chip.setTextColor(theme.textPrimary);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setPadding(TileFactory.dp(this, 16), TileFactory.dp(this, 8),
                TileFactory.dp(this, 16), TileFactory.dp(this, 8));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setBackground(chipBackground(false));
        chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(chipBackground(hasFocus));
                v.animate().scaleX(hasFocus ? 1.06f : 1f).scaleY(hasFocus ? 1.06f : 1f)
                        .setDuration(150).start();
            }
        });
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNightMode();
            }
        });
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.leftMargin = TileFactory.dp(this, 22);
        chip.setLayoutParams(chipParams);
        bar.addView(chip);

        updateClock();
        return bar;
    }

    private GradientDrawable chipBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(theme.chipFill);
        d.setCornerRadius(TileFactory.dp(this, 999));
        // Pinggir oranye tipis: penanda mode adalah fitur identitas Orange TV.
        d.setStroke(TileFactory.dp(this, focused ? 2.5f : 1f), UiTheme.ACCENT);
        return d;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(theme.textPrimary);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = TileFactory.dp(this, 28);
        p.bottomMargin = TileFactory.dp(this, 12);
        label.setLayoutParams(p);
        return label;
    }

    private View buildSettingsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout modeTile = TileFactory.textTile(this, theme,
                nightMode ? "Mode Malam" : "Mode Siang", "Ganti mode tampilan",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleNightMode();
                    }
                });
        row.addView(modeTile, settingsTileParams(false));

        LinearLayout tvTile = TileFactory.textTile(this, theme,
                "Pengaturan TV", "Buka setelan Android",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openTvSettings();
                    }
                });
        row.addView(tvTile, settingsTileParams(false));

        LinearLayout reloadTile = TileFactory.textTile(this, theme,
                "Muat Ulang Aplikasi", "Segarkan daftar aplikasi",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        reloadApps();
                    }
                });
        row.addView(reloadTile, settingsTileParams(true));
        return row;
    }

    private LinearLayout.LayoutParams settingsTileParams(boolean last) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                TileFactory.dp(this, 220), TileFactory.dp(this, 84));
        if (!last) {
            p.rightMargin = TileFactory.dp(this, 12);
        }
        return p;
    }

    // ------------------------------------------------------------------
    // Pengisian ubin
    // ------------------------------------------------------------------

    private void populateSections() {
        // Favorit: ubin besar, urutan sesuai simpanan pengguna.
        favoritesGrid.removeAllViews();
        for (String pkg : favoritePackages) {
            final AppEntry app = AppCatalog.findByPackage(apps, pkg);
            if (app == null) {
                continue; // aplikasi sudah dicopot; lewati saja
            }
            LinearLayout tile = TileFactory.appTile(this, theme, app, true,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            launch(app);
                        }
                    },
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            removeFavorite(app.packageName);
                            return true;
                        }
                    });
            favoritesGrid.addView(tile, gridParams(
                    TileFactory.dp(this, 200), TileFactory.dp(this, 128)));
        }
        if (favoritesGrid.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada Favorit. Tekan lama OK pada aplikasi di bawah untuk menambahkan.");
            empty.setTextColor(theme.textSecondary);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            favoritesGrid.addView(empty);
        }

        // Semua aplikasi: ubin kecil, urutan abjad dari katalog.
        appsGrid.removeAllViews();
        for (final AppEntry app : apps) {
            LinearLayout tile = TileFactory.appTile(this, theme, app, false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            launch(app);
                        }
                    },
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            addFavorite(app.packageName);
                            return true;
                        }
                    });
            appsGrid.addView(tile, gridParams(
                    TileFactory.dp(this, 160), TileFactory.dp(this, 108)));
        }
    }

    private GridLayout.LayoutParams gridParams(int width, int height) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = width;
        p.height = height;
        p.rightMargin = TileFactory.dp(this, 12);
        p.bottomMargin = TileFactory.dp(this, 12);
        return p;
    }

    // ------------------------------------------------------------------
    // Favorit per perangkat (SharedPreferences)
    // ------------------------------------------------------------------

    private void loadOrSeedFavorites() {
        favoritePackages.clear();
        if (!prefs.contains(KEY_FAVORITES)) {
            // Lari pertama di perangkat ini: semai dari daftar pilihan utama.
            favoritePackages.addAll(AppCatalog.seedFavorites(apps));
            saveFavorites();
            return;
        }
        String stored = prefs.getString(KEY_FAVORITES, "");
        if (stored != null && !stored.isEmpty()) {
            for (String pkg : stored.split(FAV_SEPARATOR)) {
                if (!pkg.isEmpty()) {
                    favoritePackages.add(pkg);
                }
            }
        }
    }

    private void saveFavorites() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < favoritePackages.size(); i++) {
            if (i > 0) {
                sb.append(FAV_SEPARATOR);
            }
            sb.append(favoritePackages.get(i));
        }
        prefs.edit().putString(KEY_FAVORITES, sb.toString()).apply();
    }

    private void addFavorite(String packageName) {
        if (favoritePackages.contains(packageName)) {
            toast("Sudah ada di Favorit");
            return;
        }
        if (favoritePackages.size() >= MAX_FAVORITES) {
            toast("Favorit penuh (maksimal " + MAX_FAVORITES + ")");
            return;
        }
        favoritePackages.add(packageName);
        saveFavorites();
        populateSections();
        toast("Ditambahkan ke Favorit");
    }

    private void removeFavorite(String packageName) {
        if (favoritePackages.remove(packageName)) {
            saveFavorites();
            populateSections();
            toast("Dihapus dari Favorit");
        }
    }

    // ------------------------------------------------------------------
    // Aksi-aksi
    // ------------------------------------------------------------------

    private void launch(AppEntry app) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (intent == null) {
            // Cadangan: panggil langsung komponen peluncurnya.
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(app.component);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Aplikasi tidak bisa dibuka");
        }
    }

    private void toggleNightMode() {
        nightMode = !nightMode;
        prefs.edit().putBoolean(KEY_NIGHT_MODE, nightMode).apply();
        recreate(); // terapkan tema baru seketika
    }

    private void openTvSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            toast("Pengaturan TV tidak bisa dibuka");
        }
    }

    private void reloadApps() {
        apps = AppCatalog.loadApps(this);
        // Buang Favorit yang aplikasinya sudah tidak terpasang.
        boolean pruned = false;
        java.util.Iterator<String> it = favoritePackages.iterator();
        while (it.hasNext()) {
            if (AppCatalog.findByPackage(apps, it.next()) == null) {
                it.remove();
                pruned = true;
            }
        }
        if (pruned) {
            saveFavorites();
        }
        populateSections();
        toast("Daftar aplikasi dimuat ulang");
    }

    private void updateClock() {
        if (clockView == null || dateView == null) {
            return;
        }
        Date now = new Date();
        Locale indonesia = new Locale("id", "ID");
        clockView.setText(new SimpleDateFormat("HH:mm", indonesia).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", indonesia).format(now));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
