package id.orangetv.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pabrik ubin &amp; tombol kaca Orange TV v2 (gaya strip konsol modern).
 *
 * Ubin strip = kotak kaca membulat berisi ikon saja, tanpa label — nama
 * aplikasi tampil besar di kiri bawah layar. Saat terfokus, ubin membesar
 * ~1,42x, terangkat, dan pinggirnya menjadi cincin ORANYE TEBAL — sentuhan
 * oranye khas Orange TV, tetap hemat seperti prinsip v1.
 */
public final class TileFactory {

    private TileFactory() {
    }

    static int dp(Context c, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics());
    }

    /** Kaca untuk ubin strip & kartu: gradien vertikal + sudut + pinggir. */
    public static GradientDrawable glass(UiTheme theme, Context c, boolean focused, float cornerDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{theme.tileTopFocused, theme.tileBottomFocused}
                        : new int[]{theme.tileTop, theme.tileBottom});
        d.setCornerRadius(dp(c, cornerDp));
        if (focused) {
            d.setStroke(dp(c, 4f), UiTheme.ACCENT); // cincin oranye tebal (v2.2.3)
        } else {
            d.setStroke(dp(c, 1), theme.tileRim);
        }
        return d;
    }

    /** Kaca bundar untuk tombol klaster kanan atas. */
    public static GradientDrawable circleGlass(UiTheme theme, Context c, boolean focused) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{theme.tileTopFocused, theme.tileBottomFocused}
                        : new int[]{theme.tileTop, theme.tileBottom});
        d.setShape(GradientDrawable.OVAL);
        if (focused) {
            d.setStroke(dp(c, 4f), UiTheme.ACCENT);
        } else {
            d.setStroke(dp(c, 1), theme.tileRim);
        }
        return d;
    }

    /**
     * Ubin ikon strip (92dp): ikon aplikasi di tengah, tanpa teks.
     * Perilaku fokus visual (kaca + skala + angkat) dipasang di sini; reaksi
     * tambahan pemanggil (latar, nama besar, suara) diteruskan lewat
     * {@code extraFocus} karena satu View hanya punya satu listener fokus.
     */
    public static FrameLayout stripTile(Context c, final UiTheme theme, android.graphics.drawable.Drawable icon,
                                        View.OnClickListener onClick,
                                        View.OnLongClickListener onLongClick,
                                        final View.OnFocusChangeListener extraFocus) {
        FrameLayout tile = new FrameLayout(c);
        tile.setFocusable(true);
        tile.setClickable(true);
        if (onLongClick != null) {
            tile.setLongClickable(true);
            tile.setOnLongClickListener(onLongClick);
        }
        tile.setOnClickListener(onClick);
        tile.setClipToOutline(false);

        ImageView iconView = new ImageView(c);
        int iconSize = dp(c, 58);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(iconSize, iconSize);
        ip.gravity = Gravity.CENTER;
        iconView.setLayoutParams(ip);
        iconView.setImageDrawable(icon);
        tile.addView(iconView);

        tile.setBackground(glass(theme, c, false, 20));
        tile.setElevation(dp(c, 2));
        tile.setAlpha(0.70f);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(glass(theme, v.getContext(), hasFocus, 20));
                v.setElevation(dp(v.getContext(), hasFocus ? 24 : 2));
                v.animate()
                        .scaleX(hasFocus ? 1.42f : 1.0f)
                        .scaleY(hasFocus ? 1.42f : 1.0f)
                        .alpha(hasFocus ? 1.0f : 0.70f)
                        .setDuration(160)
                        .start();
                if (hasFocus) {
                    v.bringToFront();
                }
                if (extraFocus != null) {
                    extraFocus.onFocusChange(v, hasFocus);
                }
            }
        });
        return tile;
    }

    /** Ubin alat di ujung strip: glif gambar-kode di tengah, tanpa teks. */
    public static FrameLayout glyphTile(Context c, final UiTheme theme, int glyphKind,
                                          View.OnClickListener onClick,
                                          final View.OnFocusChangeListener extraFocus) {
        FrameLayout tile = new FrameLayout(c);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(onClick);

        ImageView iconView = new ImageView(c);
        int iconSize = dp(c, 46);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(iconSize, iconSize);
        ip.gravity = Gravity.CENTER;
        iconView.setLayoutParams(ip);
        iconView.setImageBitmap(Glyphs.draw(glyphKind, theme.textPrimary));
        tile.addView(iconView);

        tile.setBackground(glass(theme, c, false, 20));
        tile.setElevation(dp(c, 2));
        tile.setAlpha(0.70f);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(glass(theme, v.getContext(), hasFocus, 20));
                v.setElevation(dp(v.getContext(), hasFocus ? 24 : 2));
                v.animate()
                        .scaleX(hasFocus ? 1.42f : 1.0f)
                        .scaleY(hasFocus ? 1.42f : 1.0f)
                        .alpha(hasFocus ? 1.0f : 0.70f)
                        .setDuration(160)
                        .start();
                if (hasFocus) {
                    v.bringToFront();
                }
                if (extraFocus != null) {
                    extraFocus.onFocusChange(v, hasFocus);
                }
            }
        });
        return tile;
    }

    /** Tombol bundar klaster kanan atas (Input / Wi-Fi / Pengaturan). */
    public static FrameLayout roundButton(Context c, final UiTheme theme, int glyphKind,
                                          View.OnClickListener onClick) {
        FrameLayout btn = new FrameLayout(c);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setOnClickListener(onClick);

        ImageView iconView = new ImageView(c);
        int iconSize = dp(c, 26);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(iconSize, iconSize);
        ip.gravity = Gravity.CENTER;
        iconView.setLayoutParams(ip);
        iconView.setImageBitmap(Glyphs.draw(glyphKind, theme.textPrimary));
        btn.addView(iconView);

        btn.setBackground(circleGlass(theme, c, false));
        btn.setElevation(dp(c, 2));
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(circleGlass(theme, v.getContext(), hasFocus));
                v.setElevation(dp(v.getContext(), hasFocus ? 10 : 2));
                v.animate()
                        .scaleX(hasFocus ? 1.28f : 1.0f)
                        .scaleY(hasFocus ? 1.28f : 1.0f)
                        .setDuration(150)
                        .start();
            }
        });
        return btn;
    }

    /** Label seksi kecil (dipakai untuk label di kartu info). */
    public static TextView smallLabel(Context c, UiTheme theme, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(theme.textSecondary);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t.setMaxLines(2);
        return t;
    }

    /** Nilai statistik tebal di kartu info. */
    public static TextView statValue(Context c, UiTheme theme) {
        TextView t = new TextView(c);
        t.setTextColor(theme.textPrimary);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 3);
        t.setLayoutParams(p);
        return t;
    }
}
