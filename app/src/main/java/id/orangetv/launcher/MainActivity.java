package id.orangetv.launcher;

import android.app.Activity;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Layar utama Orange TV v2 — gaya konsol modern (ala PS5):
 *
 *   - Latar sinematik layar penuh yang mengikuti aplikasi terfokus
 *     (oleh {@link Backdrop}: artwork sinematik ORIGINAL yang dibundel
 *     untuk aplikasi populer, gradien tergambar dari kode untuk sisanya).
 *   - Satu strip ikon horizontal di dekat atas: Favorit, lalu Semua
 *     Aplikasi, lalu ubin alat. Satu baris = navigasi D-pad yang paling
 *     mudah ditebak (kiri/kanan saja; atas menuju klaster kanan atas).
 *   - Klaster kanan atas: tombol bundar Input, Wi-Fi, Pengaturan, chip
 *     mode Siang/Malam, jam + tanggal Bahasa Indonesia.
 *   - Kiri bawah: nama aplikasi terfokus dalam huruf besar + titik-titik
 *     penanda seksi. Kanan bawah: kartu kaca berisi Waktu Layar Minggu
 *     Ini dan Penyimpanan Terpakai.
 *   - Efek suara navigasi lembut (lihat {@link SoundPlayer}), bisa
 *     dimatikan dari ubin alat "Efek Suara".
 *
 * Personalisasi (mode, Favorit, suara) tetap tinggal di SharedPreferences
 * perangkat — kunci &amp; format Favorit sama dengan v1, jadi peningkatan
 * dari v1 berjalan mulus di tempat.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "orange_tv";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_SOUND_ON = "sound_on";
    private static final int MAX_FAVORITES = 8;
    private static final String FAV_SEPARATOR = "\n";

    private static final int SECTION_FAVORITES = 0;
    private static final int SECTION_ALL = 1;
    private static final int SECTION_TOOLS = 2;

    private static final int TOOL_NONE = -1;
    private static final int TOOL_MODE = 0;
    private static final int TOOL_SOUND = 1;
    private static final int TOOL_USAGE = 2;
    private static final int TOOL_RELOAD = 3;

    private static final Locale ID_LOCALE = new Locale("id", "ID");

    /** Metadata satu ubin di strip. */
    private static final class TileInfo {
        View view;
        AppEntry app;          // null untuk ubin alat
        int section;
        int tool = TOOL_NONE;
        String title;          // teks untuk nama besar di kiri bawah
    }

    private SharedPreferences prefs;
    private UiTheme theme;
    private boolean nightMode;
    private boolean soundOn;
    private SoundPlayer sounds;

    private List<AppEntry> apps = new ArrayList<>();
    private final List<String> favoritePackages = new ArrayList<>();
    private final List<TileInfo> tileInfos = new ArrayList<>();
    private final List<Integer> sectionsPresent = new ArrayList<>();
    private final List<View> dotViews = new ArrayList<>();

    private ImageView backdropFront;
    private ImageView backdropBack;
    private boolean frontIsA = true;
    private String currentBackdropKey = "";
    private int backdropW;
    private int backdropH;

    private HorizontalScrollView stripScroll;
    private LinearLayout stripContainer;
    private LinearLayout dotsRow;
    private TextView bigName;
    private TextView clockView;
    private TextView dateView;
    private TextView screenTimeValue;
    private TextView storageValue;

    private long lastMoveSoundAt = 0;
    private boolean suppressFirstMoveSound = true;

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            clockHandler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        nightMode = prefs.getBoolean(KEY_NIGHT_MODE, true); // bawaan: Malam
        soundOn = prefs.getBoolean(KEY_SOUND_ON, true);     // bawaan: Nyala
        theme = UiTheme.of(nightMode);
        Backdrop.init(this); // agar Backdrop bisa membaca artwork yang dibundel
        sounds = new SoundPlayer(this);
        sounds.setEnabled(soundOn);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        backdropW = dm.widthPixels;
        backdropH = dm.heightPixels;

        apps = AppCatalog.loadApps(this);
        loadOrSeedFavorites();

        setContentView(buildScreen());
        populateStrip();

        // Latar + fokus awal: ubin pertama strip.
        if (!tileInfos.isEmpty()) {
            final TileInfo first = tileInfos.get(0);
            showBackdrop(first.app != null ? first.app.packageName : null);
            bigName.setText(first.title);
            setActiveDot(first.section);
            stripContainer.post(new Runnable() {
                @Override
                public void run() {
                    first.view.requestFocus();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockTick);
        clockHandler.post(clockTick);
        updateStats();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockTick);
    }

    @Override
    protected void onDestroy() {
        if (sounds != null) {
            sounds.release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Launcher tidak "keluar"; Kembali memainkan bunyi kembali dan
        // mengembalikan fokus ke awal strip.
        if (sounds != null) {
            sounds.back();
        }
        if (!tileInfos.isEmpty()) {
            tileInfos.get(0).view.requestFocus();
        }
    }

    // ------------------------------------------------------------------
    // Pembangunan layar
    // ------------------------------------------------------------------

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);

        // Latar sinematik dua lapis untuk crossfade.
        backdropFront = new ImageView(this);
        backdropFront.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropBack = new ImageView(this);
        backdropBack.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropBack.setAlpha(0f);
        root.addView(backdropFront, matchParent());
        root.addView(backdropBack, matchParent());

        // Scrim halus: gelap di atas & bawah (Malam) agar teks terbaca.
        View scrim = new View(this);
        GradientDrawable scrimBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                nightMode
                        ? new int[]{0x73000000, 0x00000000, 0xC2000000}
                        : new int[]{0x2EFFFFFF, 0x00000000, 0x73F4ECDD});
        scrim.setBackground(scrimBg);
        root.addView(scrim, matchParent());

        // Kolom atas: bar merek+klaster, lalu strip.
        LinearLayout topColumn = new LinearLayout(this);
        topColumn.setOrientation(LinearLayout.VERTICAL);
        int padX = TileFactory.dp(this, 48);
        topColumn.setPadding(padX, TileFactory.dp(this, 26), padX, 0);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        topParams.gravity = Gravity.TOP;
        root.addView(topColumn, topParams);

        FrameLayout firstClusterButton = buildTopBar(topColumn);

        stripScroll = new HorizontalScrollView(this);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        stripScroll.setClipToPadding(false);
        stripContainer = new LinearLayout(this);
        stripContainer.setOrientation(LinearLayout.HORIZONTAL);
        stripContainer.setGravity(Gravity.CENTER_VERTICAL);
        stripContainer.setClipToPadding(false);
        stripContainer.setPadding(TileFactory.dp(this, 8), TileFactory.dp(this, 20),
                TileFactory.dp(this, 28), TileFactory.dp(this, 20));
        stripScroll.addView(stripContainer);
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stripParams.topMargin = TileFactory.dp(this, 30);
        stripScroll.setLayoutParams(stripParams);
        topColumn.addView(stripScroll);

        // Petunjuk kecil, rendah hati, di bawah strip.
        TextView hint = new TextView(this);
        hint.setText("Tekan lama OK untuk tambah/hapus Favorit");
        hint.setTextColor(theme.textSecondary);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        hint.setAlpha(0.85f);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintParams.gravity = Gravity.TOP | Gravity.LEFT;
        hintParams.leftMargin = TileFactory.dp(this, 56);
        hintParams.topMargin = TileFactory.dp(this, 246);
        root.addView(hint, hintParams);

        // Kiri bawah: nama besar + titik seksi.
        LinearLayout bottomLeft = new LinearLayout(this);
        bottomLeft.setOrientation(LinearLayout.VERTICAL);
        bigName = new TextView(this);
        bigName.setTextColor(theme.textPrimary);
        bigName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        bigName.setTypeface(Typeface.DEFAULT_BOLD);
        bigName.setShadowLayer(8, 0, 3, nightMode ? 0xAA000000 : 0x26000000);
        bigName.setMaxLines(1);
        bigName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottomLeft.addView(bigName);
        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dotsParams.topMargin = TileFactory.dp(this, 16);
        dotsRow.setLayoutParams(dotsParams);
        bottomLeft.addView(dotsRow);
        FrameLayout.LayoutParams blParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        blParams.leftMargin = TileFactory.dp(this, 56);
        blParams.bottomMargin = TileFactory.dp(this, 42);
        root.addView(bottomLeft, blParams);

        // Kanan bawah: kartu info kaca.
        root.addView(buildStatsCard());

        // Fokus D-pad yang tegas: dari strip, Atas -> klaster; dari klaster,
        // Bawah -> ubin pertama strip. (Diisi setelah strip terisi.)
        pendingClusterFocusTarget = firstClusterButton;
        return root;
    }

    private View pendingClusterFocusTarget;

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /** Bar atas: merek mungil di kiri; klaster Input/Wi-Fi/Pengaturan + chip + jam di kanan. */
    private FrameLayout buildTopBar(LinearLayout parent) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(bar);

        // Merek: logo oranye kecil + nama + garis aksen tipis (identitas hemat).
        TextView logo = new TextView(this);
        logo.setText("▶");
        logo.setTextColor(0xFFFFFFFF);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(UiTheme.ACCENT);
        logoBg.setCornerRadius(TileFactory.dp(this, 8));
        logo.setBackground(logoBg);
        int logoSize = TileFactory.dp(this, 28);
        bar.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        LinearLayout nameColumn = new LinearLayout(this);
        nameColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.leftMargin = TileFactory.dp(this, 11);
        nameColumn.setLayoutParams(nameParams);
        TextView wordmark = new TextView(this);
        wordmark.setText("Orange TV");
        wordmark.setTextColor(theme.textPrimary);
        wordmark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        wordmark.setTypeface(Typeface.DEFAULT_BOLD);
        nameColumn.addView(wordmark);
        View accentLine = new View(this);
        GradientDrawable lineBg = new GradientDrawable();
        lineBg.setColor(UiTheme.ACCENT);
        lineBg.setCornerRadius(TileFactory.dp(this, 2));
        accentLine.setBackground(lineBg);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                TileFactory.dp(this, 46), TileFactory.dp(this, 3));
        lineParams.topMargin = TileFactory.dp(this, 4);
        accentLine.setLayoutParams(lineParams);
        nameColumn.addView(accentLine);
        bar.addView(nameColumn);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Klaster kanan atas.
        FrameLayout inputBtn = TileFactory.roundButton(this, theme, Glyphs.INPUT,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openInputPicker();
                    }
                });
        bar.addView(inputBtn, clusterButtonParams(true));
        FrameLayout wifiBtn = TileFactory.roundButton(this, theme, Glyphs.WIFI,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openWifiSettings();
                    }
                });
        bar.addView(wifiBtn, clusterButtonParams(false));
        FrameLayout settingsBtn = TileFactory.roundButton(this, theme, Glyphs.GEAR,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openTvSettings();
                    }
                });
        bar.addView(settingsBtn, clusterButtonParams(false));

        // Chip mode (pil kaca, pinggir oranye tipis) — saklar Siang/Malam.
        TextView chip = new TextView(this);
        chip.setText(nightMode ? "Mode Malam" : "Mode Siang");
        chip.setTextColor(theme.textPrimary);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        chip.setPadding(TileFactory.dp(this, 14), TileFactory.dp(this, 7),
                TileFactory.dp(this, 14), TileFactory.dp(this, 7));
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
        chipParams.leftMargin = TileFactory.dp(this, 14);
        chip.setLayoutParams(chipParams);
        bar.addView(chip);

        // Jam + tanggal Bahasa Indonesia.
        LinearLayout clockColumn = new LinearLayout(this);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        clockColumn.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clockParams.leftMargin = TileFactory.dp(this, 20);
        clockColumn.setLayoutParams(clockParams);
        clockView = new TextView(this);
        clockView.setTextColor(theme.textPrimary);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.RIGHT);
        clockColumn.addView(clockView);
        dateView = new TextView(this);
        dateView.setTextColor(theme.textSecondary);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dateView.setGravity(Gravity.RIGHT);
        clockColumn.addView(dateView);
        bar.addView(clockColumn);

        // Semua elemen klaster: Bawah -> ubin pertama strip (diisi belakangan).
        inputBtn.setId(View.generateViewId());
        wifiBtn.setId(View.generateViewId());
        settingsBtn.setId(View.generateViewId());
        chip.setId(View.generateViewId());
        clusterViews.add(inputBtn);
        clusterViews.add(wifiBtn);
        clusterViews.add(settingsBtn);
        clusterViews.add(chip);

        updateClock();
        return inputBtn;
    }

    private final List<View> clusterViews = new ArrayList<>();

    private LinearLayout.LayoutParams clusterButtonParams(boolean first) {
        int size = TileFactory.dp(this, 46);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        if (!first) {
            p.leftMargin = TileFactory.dp(this, 10);
        }
        return p;
    }

    private GradientDrawable chipBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(theme.chipFill);
        d.setCornerRadius(TileFactory.dp(this, 999));
        d.setStroke(TileFactory.dp(this, focused ? 2.5f : 1f), UiTheme.ACCENT);
        return d;
    }

    /** Kartu kaca kanan bawah: Waktu Layar Minggu Ini | Penyimpanan Terpakai. */
    private View buildStatsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(TileFactory.dp(this, 20), TileFactory.dp(this, 14),
                TileFactory.dp(this, 20), TileFactory.dp(this, 14));
        card.setBackground(TileFactory.glass(theme, this, false, 18));
        card.setElevation(TileFactory.dp(this, 4));

        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.addView(TileFactory.smallLabel(this, theme, "Waktu Layar Minggu Ini"));
        screenTimeValue = TileFactory.statValue(this, theme);
        screenTimeValue.setText("—");
        col1.addView(screenTimeValue);
        card.addView(col1);

        View divider = new View(this);
        GradientDrawable divBg = new GradientDrawable();
        divBg.setColor(theme.tileRim);
        divider.setBackground(divBg);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                TileFactory.dp(this, 1), TileFactory.dp(this, 46));
        divParams.leftMargin = TileFactory.dp(this, 18);
        divParams.rightMargin = TileFactory.dp(this, 18);
        divider.setLayoutParams(divParams);
        card.addView(divider);

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.addView(TileFactory.smallLabel(this, theme, "Penyimpanan Terpakai"));
        storageValue = TileFactory.statValue(this, theme);
        storageValue.setText("—");
        col2.addView(storageValue);
        card.addView(col2);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        p.rightMargin = TileFactory.dp(this, 48);
        p.bottomMargin = TileFactory.dp(this, 42);
        card.setLayoutParams(p);
        return card;
    }

    // ------------------------------------------------------------------
    // Strip: Favorit -> Semua Aplikasi -> Alat (satu baris)
    // ------------------------------------------------------------------

    private void populateStrip() {
        stripContainer.removeAllViews();
        tileInfos.clear();
        sectionsPresent.clear();

        List<AppEntry> favApps = new ArrayList<>();
        for (String pkg : favoritePackages) {
            AppEntry app = AppCatalog.findByPackage(apps, pkg);
            if (app != null) {
                favApps.add(app);
            }
        }
        List<AppEntry> others = new ArrayList<>();
        for (AppEntry app : apps) {
            if (!favoritePackages.contains(app.packageName)) {
                others.add(app);
            }
        }

        boolean first = true;
        if (!favApps.isEmpty()) {
            sectionsPresent.add(SECTION_FAVORITES);
            for (AppEntry app : favApps) {
                addAppTile(app, SECTION_FAVORITES, first);
                first = false;
            }
        }
        if (!others.isEmpty()) {
            if (!first) {
                addSeparator();
            }
            sectionsPresent.add(SECTION_ALL);
            for (AppEntry app : others) {
                addAppTile(app, SECTION_ALL, first);
                first = false;
            }
        }

        // Ubin alat selalu ada di ujung strip.
        if (!first) {
            addSeparator();
        }
        sectionsPresent.add(SECTION_TOOLS);
        addToolTile(TOOL_MODE, nightMode ? Glyphs.MOON : Glyphs.SUN,
                nightMode ? "Mode Malam" : "Mode Siang", first);
        first = false;
        addToolTile(TOOL_SOUND, soundOn ? Glyphs.SOUND_ON : Glyphs.SOUND_OFF,
                "Efek Suara", false);
        addToolTile(TOOL_USAGE, Glyphs.CLOCK, "Izin Waktu Layar", false);
        addToolTile(TOOL_RELOAD, Glyphs.REFRESH, "Muat Ulang Aplikasi", false);

        buildDots();

        // Rajut fokus Atas/Bawah antara strip dan klaster kanan atas.
        if (!tileInfos.isEmpty() && pendingClusterFocusTarget != null) {
            int firstTileId = tileInfos.get(0).view.getId();
            for (TileInfo info : tileInfos) {
                info.view.setNextFocusUpId(pendingClusterFocusTarget.getId());
            }
            for (View v : clusterViews) {
                v.setNextFocusDownId(firstTileId);
            }
        }
    }

    private void addAppTile(final AppEntry app, int section, boolean isFirstTile) {
        final TileInfo info = new TileInfo();
        info.app = app;
        info.section = section;
        info.title = app.label;
        FrameLayout tile = TileFactory.stripTile(this, theme, app.icon,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        launch(app);
                    }
                },
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        toggleFavorite(app);
                        return true;
                    }
                },
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        onTileFocus(info, hasFocus);
                    }
                });
        tile.setId(View.generateViewId());
        info.view = tile;
        tileInfos.add(info);
        stripContainer.addView(tile, stripTileParams(isFirstTile));
    }

    private void addToolTile(final int tool, int glyph, String title, boolean isFirstTile) {
        final TileInfo info = new TileInfo();
        info.app = null;
        info.section = SECTION_TOOLS;
        info.tool = tool;
        info.title = title;
        FrameLayout tile = TileFactory.glyphTile(this, theme, glyph,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onToolClicked(tool);
                    }
                },
                new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        onTileFocus(info, hasFocus);
                    }
                });
        tile.setId(View.generateViewId());
        info.view = tile;
        tileInfos.add(info);
        stripContainer.addView(tile, stripTileParams(isFirstTile));
    }

    private LinearLayout.LayoutParams stripTileParams(boolean isFirstTile) {
        int size = TileFactory.dp(this, 92);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        if (!isFirstTile) {
            p.leftMargin = TileFactory.dp(this, 14);
        }
        return p;
    }

    private void addSeparator() {
        View sep = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.tileRim);
        bg.setCornerRadius(TileFactory.dp(this, 1));
        sep.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                TileFactory.dp(this, 2), TileFactory.dp(this, 54));
        p.leftMargin = TileFactory.dp(this, 12);
        p.rightMargin = TileFactory.dp(this, 12);
        p.gravity = Gravity.CENTER_VERTICAL;
        sep.setLayoutParams(p);
        stripContainer.addView(sep);
    }

    /** Reaksi saat satu ubin strip mendapat fokus: latar, nama besar, titik, suara. */
    private void onTileFocus(TileInfo info, boolean hasFocus) {
        if (!hasFocus) {
            return;
        }
        showBackdrop(info.app != null ? info.app.packageName : null);
        bigName.setText(info.title);
        setActiveDot(info.section);

        if (suppressFirstMoveSound) {
            suppressFirstMoveSound = false;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastMoveSoundAt >= 45) { // throttle ~45 ms
            lastMoveSoundAt = now;
            sounds.move();
        }
    }

    private void buildDots() {
        dotsRow.removeAllViews();
        dotViews.clear();
        for (int i = 0; i < sectionsPresent.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    TileFactory.dp(this, 8), TileFactory.dp(this, 8));
            if (i > 0) {
                p.leftMargin = TileFactory.dp(this, 7);
            }
            dot.setLayoutParams(p);
            dotsRow.addView(dot);
            dotViews.add(dot);
        }
    }

    private void setActiveDot(int section) {
        int activeIndex = sectionsPresent.indexOf(section);
        for (int i = 0; i < dotViews.size(); i++) {
            View dot = dotViews.get(i);
            boolean active = i == activeIndex;
            GradientDrawable d = new GradientDrawable();
            d.setShape(active ? GradientDrawable.RECTANGLE : GradientDrawable.OVAL);
            d.setCornerRadius(TileFactory.dp(this, 4));
            d.setColor(active
                    ? (nightMode ? 0xFFFFFFFF : 0xFF2B2721)
                    : (nightMode ? 0x66FFFFFF : 0x55000000));
            dot.setBackground(d);
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) dot.getLayoutParams();
            p.width = TileFactory.dp(this, active ? 24 : 8);
            dot.setLayoutParams(p);
        }
    }

    // ------------------------------------------------------------------
    // Latar sinematik (crossfade dua ImageView)
    // ------------------------------------------------------------------

    private void showBackdrop(String packageName) {
        String key = (packageName == null ? "netral" : packageName) + (nightMode ? "#m" : "#s");
        if (key.equals(currentBackdropKey)) {
            return;
        }
        currentBackdropKey = key;
        Bitmap bmp = Backdrop.render(packageName, backdropW, backdropH, nightMode);
        ImageView show = frontIsA ? backdropBack : backdropFront;
        ImageView hide = frontIsA ? backdropFront : backdropBack;
        show.setImageBitmap(bmp);
        show.animate().alpha(1f).setDuration(320).start();
        hide.animate().alpha(0f).setDuration(320).start();
        frontIsA = !frontIsA;
    }

    // ------------------------------------------------------------------
    // Kartu info: waktu layar mingguan + penyimpanan
    // ------------------------------------------------------------------

    private void updateStats() {
        // Penyimpanan: selalu bisa dari StatFs.
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            long total = sf.getTotalBytes();
            long used = total - sf.getAvailableBytes();
            storageValue.setText(formatStorage(used, total));
        } catch (Exception e) {
            storageValue.setText("—");
        }

        // Waktu layar: butuh izin akses penggunaan; tanpa izin tampilkan "—".
        String screenTime = weeklyScreenTime();
        screenTimeValue.setText(screenTime != null ? screenTime : "—");
    }

    /**
     * Total waktu foreground semua aplikasi dalam 7 hari terakhir.
     * Mengembalikan null bila data tidak tersedia (umumnya izin akses
     * penggunaan belum diberikan) — pemanggil menampilkan "—" dan ubin
     * "Izin Waktu Layar" membuka halaman izinnya. Tidak pernah crash.
     */
    private String weeklyScreenTime() {
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                return null;
            }
            long end = System.currentTimeMillis();
            long begin = end - 7L * 24 * 3600 * 1000;
            java.util.Map<String, UsageStats> stats =
                    usm.queryAndAggregateUsageStats(begin, end);
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            long totalMs = 0;
            for (UsageStats s : stats.values()) {
                totalMs += s.getTotalTimeInForeground();
            }
            if (totalMs <= 0) {
                return null;
            }
            double hours = totalMs / 3_600_000.0;
            if (hours >= 1.0) {
                return String.format(ID_LOCALE, "%.1f jam", hours);
            }
            long minutes = Math.max(1, Math.round(totalMs / 60_000.0));
            return minutes + " menit";
        } catch (Exception e) {
            return null;
        }
    }

    private String formatStorage(long used, long total) {
        double gb = 1_000_000_000.0;
        if (total >= gb) {
            return String.format(ID_LOCALE, "%.1f dari %.1f GB", used / gb, total / gb);
        }
        long mb = 1_000_000L;
        return (used / mb) + " dari " + (total / mb) + " MB";
    }

    // ------------------------------------------------------------------
    // Favorit per perangkat (sama seperti v1 — format prefs tidak berubah)
    // ------------------------------------------------------------------

    private void loadOrSeedFavorites() {
        favoritePackages.clear();
        if (!prefs.contains(KEY_FAVORITES)) {
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

    /** Tekan lama OK di ubin mana pun: tambah ke / hapus dari Favorit. */
    private void toggleFavorite(AppEntry app) {
        String pkg = app.packageName;
        if (favoritePackages.contains(pkg)) {
            favoritePackages.remove(pkg);
            saveFavorites();
            toast("Dihapus dari Favorit");
        } else {
            if (favoritePackages.size() >= MAX_FAVORITES) {
                toast("Favorit penuh (maksimal " + MAX_FAVORITES + ")");
                return;
            }
            favoritePackages.add(pkg);
            saveFavorites();
            toast("Ditambahkan ke Favorit");
        }
        populateStrip();
        focusPackage(pkg);
    }

    private void focusPackage(String packageName) {
        for (TileInfo info : tileInfos) {
            if (info.app != null && info.app.packageName.equals(packageName)) {
                info.view.requestFocus();
                return;
            }
        }
        if (!tileInfos.isEmpty()) {
            tileInfos.get(0).view.requestFocus();
        }
    }

    private void focusTool(int tool) {
        for (TileInfo info : tileInfos) {
            if (info.tool == tool) {
                info.view.requestFocus();
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Aksi-aksi
    // ------------------------------------------------------------------

    private void launch(AppEntry app) {
        sounds.select();
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (intent == null) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(app.component);
            intent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Aplikasi tidak bisa dibuka");
        }
    }

    private void onToolClicked(int tool) {
        switch (tool) {
            case TOOL_MODE:
                toggleNightMode();
                break;
            case TOOL_SOUND: {
                soundOn = !soundOn;
                prefs.edit().putBoolean(KEY_SOUND_ON, soundOn).apply();
                sounds.setEnabled(soundOn);
                populateStrip();
                focusTool(TOOL_SOUND);
                if (soundOn) {
                    sounds.select(); // bunyi konfirmasi bahwa suara kembali nyala
                }
                toast(soundOn ? "Efek suara nyala" : "Efek suara mati");
                break;
            }
            case TOOL_USAGE:
                openUsageAccess();
                break;
            case TOOL_RELOAD:
                reloadApps();
                break;
            default:
                break;
        }
    }

    private void toggleNightMode() {
        nightMode = !nightMode;
        prefs.edit().putBoolean(KEY_NIGHT_MODE, nightMode).apply();
        recreate();
    }

    private void reloadApps() {
        apps = AppCatalog.loadApps(this);
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
        populateStrip();
        if (!tileInfos.isEmpty()) {
            tileInfos.get(0).view.requestFocus();
        }
        toast("Daftar aplikasi dimuat ulang");
    }

    /** Buka pemilih input TV bila platform menyediakannya; jika tidak, Pengaturan. */
    private void openInputPicker() {
        if (tryStart(new Intent("com.android.tv.action.VIEW_INPUT"))) {
            return;
        }
        if (tryStart(new Intent(Settings.ACTION_SETTINGS))) {
            return;
        }
        toast("Pemilih input tidak tersedia di TV ini");
    }

    private void openWifiSettings() {
        if (tryStart(new Intent(Settings.ACTION_WIFI_SETTINGS))) {
            return;
        }
        if (tryStart(new Intent(Settings.ACTION_WIRELESS_SETTINGS))) {
            return;
        }
        if (tryStart(new Intent(Settings.ACTION_SETTINGS))) {
            return;
        }
        toast("Pengaturan Wi-Fi tidak bisa dibuka");
    }

    private void openTvSettings() {
        if (tryStart(new Intent(Settings.ACTION_SETTINGS))) {
            return;
        }
        toast("Pengaturan TV tidak bisa dibuka");
    }

    private void openUsageAccess() {
        if (tryStart(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) {
            return;
        }
        if (tryStart(new Intent(Settings.ACTION_SETTINGS))) {
            return;
        }
        toast("Halaman izin tidak bisa dibuka");
    }

    private boolean tryStart(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            sounds.select();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateClock() {
        if (clockView == null || dateView == null) {
            return;
        }
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("HH:mm", ID_LOCALE).format(now));
        dateView.setText(new SimpleDateFormat("EEEE, d MMMM yyyy", ID_LOCALE).format(now));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
