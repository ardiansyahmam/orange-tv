package id.orangetv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Ikon-ikon kecil yang digambar langsung dari kode (garis &amp; bidang
 * sederhana di atas bitmap transparan) — untuk tombol bundar di kanan atas
 * (Input, Wi-Fi, Pengaturan) dan ubin alat di ujung strip.
 *
 * Orange TV tidak membawa berkas gambar ikon apa pun selain logo launcher;
 * semua glif ini digambar saat runtime dengan warna tinta yang diminta.
 */
public final class Glyphs {

    public static final int INPUT = 0;
    public static final int WIFI = 1;
    public static final int GEAR = 2;
    public static final int MOON = 3;
    public static final int SUN = 4;
    public static final int SOUND_ON = 5;
    public static final int SOUND_OFF = 6;
    public static final int CLOCK = 7;
    public static final int REFRESH = 8;

    private static final int SIZE = 96; // px bitmap sumber; diskalakan ImageView

    private Glyphs() {
    }

    /** Gambar satu glif dengan warna tinta tertentu, kembalikan sebagai Bitmap. */
    public static Bitmap draw(int kind, int color) {
        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setStrokeCap(Paint.Cap.ROUND);
        float c = SIZE / 2f;

        switch (kind) {
            case INPUT: {
                // Simbol input/sumber klasik (v2.2.3): garis luar layar
                // persegi membulat + panah TEBAL masuk dari sisi kiri —
                // tangkai menembus tepi kiri layar, kepala panah di dalam.
                // (Glif lama layar+segitiga-putar terbaca sebagai "putar/
                // pengaturan" dari jarak sofa.)
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(7f);
                RectF box = new RectF(30, 24, 84, 72);
                cv.drawRoundRect(box, 9, 9, p);
                cv.drawLine(8, 48, 54, 48, p); // tangkai panah masuk
                Path head = new Path();
                head.moveTo(40, 35);
                head.lineTo(60, 48);
                head.lineTo(40, 61);
                head.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(head, p);
                break;
            }
            case WIFI: {
                // Tiga busur memancar + satu titik di bawah.
                for (int i = 0; i < 3; i++) {
                    float r = 14f + i * 13f;
                    cv.drawArc(new RectF(c - r, 62 - r, c + r, 62 + r), 218, 104, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(c, 66, 5.5f, p);
                break;
            }
            case GEAR: {
                // Roda gigi: 8 jari-jari + lingkaran luar + lubang tengah.
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float x1 = c + (float) (20 * Math.cos(a));
                    float y1 = c + (float) (20 * Math.sin(a));
                    float x2 = c + (float) (31 * Math.cos(a));
                    float y2 = c + (float) (31 * Math.sin(a));
                    p.setStrokeWidth(8f);
                    cv.drawLine(x1, y1, x2, y2, p);
                }
                p.setStrokeWidth(6f);
                cv.drawCircle(c, c, 19, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(c, c, 5.5f, p);
                break;
            }
            case MOON: {
                // Bulan sabit: lingkaran besar dipotong lingkaran bergeser.
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(c - 4, c, 26, p);
                Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
                clear.setXfermode(new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.CLEAR));
                cv.drawCircle(c + 12, c - 10, 22, clear);
                break;
            }
            case SUN: {
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(c, c, 13, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float x1 = c + (float) (21 * Math.cos(a));
                    float y1 = c + (float) (21 * Math.sin(a));
                    float x2 = c + (float) (30 * Math.cos(a));
                    float y2 = c + (float) (30 * Math.sin(a));
                    cv.drawLine(x1, y1, x2, y2, p);
                }
                break;
            }
            case SOUND_ON:
            case SOUND_OFF: {
                // Corong speaker + gelombang suara (atau palang bila mati).
                Path body = new Path();
                body.moveTo(26, 40);
                body.lineTo(38, 40);
                body.lineTo(54, 28);
                body.lineTo(54, 68);
                body.lineTo(38, 56);
                body.lineTo(26, 56);
                body.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(body, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5.5f);
                if (kind == SOUND_ON) {
                    cv.drawArc(new RectF(56, 34, 78, 62), -55, 110, false, p);
                    cv.drawArc(new RectF(62, 26, 92, 70), -55, 110, false, p);
                } else {
                    cv.drawLine(62, 38, 82, 58, p);
                    cv.drawLine(82, 38, 62, 58, p);
                }
                break;
            }
            case CLOCK: {
                p.setStrokeWidth(6f);
                cv.drawCircle(c, c, 27, p);
                cv.drawLine(c, c, c, c - 15, p);
                cv.drawLine(c, c, c + 11, c + 5, p);
                break;
            }
            case REFRESH: {
                // Panah melingkar: busur hampir penuh + kepala panah.
                cv.drawArc(new RectF(24, 24, 72, 72), -40, 290, false, p);
                double a = Math.toRadians(-40);
                float tipX = c + (float) (24 * Math.cos(a));
                float tipY = c + (float) (24 * Math.sin(a));
                Path head = new Path();
                head.moveTo(tipX + 12, tipY - 2);
                head.lineTo(tipX - 4, tipY - 8);
                head.lineTo(tipX - 1, tipY + 9);
                head.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(head, p);
                break;
            }
            default:
                break;
        }
        return bmp;
    }
}
