package id.orangetv.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pabrik ubin kaca Orange TV.
 *
 * Ubin = panel tembus cahaya dengan gradien halus, pinggir 1dp, sudut 18dp.
 * Saat terfokus: ubin membesar ~1,08x, terangkat, dan pinggirnya berubah
 * menjadi cincin ORANYE TIPIS (2,5dp) — satu-satunya tempat oranye tampil
 * di ubin, sesuai prinsip "sentuhan oranye tipis".
 */
public final class TileFactory {

    private TileFactory() {
    }

    static int dp(Context c, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics());
    }

    /** Drawable kaca: gradien vertikal + sudut membulat + pinggir tipis. */
    public static GradientDrawable glass(UiTheme theme, Context c, boolean focused) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{theme.tileTopFocused, theme.tileBottomFocused}
                        : new int[]{theme.tileTop, theme.tileBottom});
        d.setCornerRadius(dp(c, 18));
        if (focused) {
            // Cincin fokus oranye tipis — aksen, bukan permukaan.
            d.setStroke(dp(c, 2.5f), UiTheme.ACCENT);
        } else {
            d.setStroke(dp(c, 1), theme.tileRim);
        }
        return d;
    }

    /** Pasang perilaku fokus standar: ganti kaca + animasi membesar + terangkat. */
    private static void attachGlassFocus(final View tile, final UiTheme theme) {
        final Context c = tile.getContext();
        tile.setBackground(glass(theme, c, false));
        tile.setElevation(dp(c, 2));
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(glass(theme, c, hasFocus));
                v.setElevation(dp(c, hasFocus ? 12 : 2));
                v.animate()
                        .scaleX(hasFocus ? 1.08f : 1.0f)
                        .scaleY(hasFocus ? 1.08f : 1.0f)
                        .setDuration(150)
                        .start();
            }
        });
    }

    /** Ubin aplikasi: ikon di atas, label di bawah. */
    public static LinearLayout appTile(Context c, UiTheme theme, AppEntry app, boolean large,
                                       View.OnClickListener onClick,
                                       View.OnLongClickListener onLongClick) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        if (onLongClick != null) {
            tile.setLongClickable(true);
            tile.setOnLongClickListener(onLongClick);
        }
        tile.setOnClickListener(onClick);
        tile.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 8));

        ImageView icon = new ImageView(c);
        int iconSize = dp(c, large ? 56 : 44);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setImageDrawable(app.icon);
        tile.addView(icon);

        TextView label = new TextView(c);
        label.setText(app.label);
        label.setTextColor(theme.textPrimary);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 16 : 14);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(c, 8);
        label.setLayoutParams(labelParams);
        tile.addView(label);

        attachGlassFocus(tile, theme);
        return tile;
    }

    /** Ubin teks kecil untuk baris Pengaturan: judul + keterangan mungil. */
    public static LinearLayout textTile(Context c, UiTheme theme, String title, String subtitle,
                                        View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(onClick);
        tile.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));

        TextView titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextColor(theme.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(titleView);

        if (subtitle != null) {
            TextView subView = new TextView(c);
            subView.setText(subtitle);
            subView.setTextColor(theme.textSecondary);
            subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            subView.setGravity(Gravity.CENTER);
            subView.setMaxLines(1);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(c, 4);
            subView.setLayoutParams(p);
            tile.addView(subView);
        }

        attachGlassFocus(tile, theme);
        return tile;
    }
}
