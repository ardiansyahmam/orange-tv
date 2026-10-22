package id.orangetv.launcher;

/**
 * Semua warna Orange TV mengalir dari kelas ini (mode Siang &amp; Malam).
 *
 * Prinsip desain: kaca selalu NETRAL — terang berembun di Siang, arang gelap
 * di Malam. Oranye hanya "sentuhan tipis": cincin fokus, logo kecil, garis
 * aksen mungil, dan status aktif chip mode. Tidak ada permukaan oranye besar.
 */
public final class UiTheme {

    /** Oranye identitas — dipakai hemat, hanya untuk aksen tipis. Sama di kedua mode. */
    public static final int ACCENT = 0xFFFF8A2A;

    public final boolean night;

    // Latar belakang (gradien vertikal, atas -> bawah)
    public final int bgTop;
    public final int bgBottom;

    // Kaca ubin (gradien halus atas -> bawah) + pinggiran 1dp
    public final int tileTop;
    public final int tileBottom;
    public final int tileRim;
    // Kaca saat terfokus: isian sedikit lebih terang; pinggirannya diganti cincin oranye tipis
    public final int tileTopFocused;
    public final int tileBottomFocused;

    public final int textPrimary;
    public final int textSecondary;

    // Chip mode di bar atas
    public final int chipFill;

    private UiTheme(boolean night,
                    int bgTop, int bgBottom,
                    int tileTop, int tileBottom, int tileRim,
                    int tileTopFocused, int tileBottomFocused,
                    int textPrimary, int textSecondary,
                    int chipFill) {
        this.night = night;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.tileTop = tileTop;
        this.tileBottom = tileBottom;
        this.tileRim = tileRim;
        this.tileTopFocused = tileTopFocused;
        this.tileBottomFocused = tileBottomFocused;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.chipFill = chipFill;
    }

    public static UiTheme of(boolean night) {
        return night ? night() : day();
    }

    /** Malam: arang hangat nyaris hitam, kaca putih tipis di atasnya. */
    public static UiTheme night() {
        return new UiTheme(
                true,
                0xFF1C1916, 0xFF0B0A08,   // latar: arang hangat -> nyaris hitam
                0x2BFFFFFF, 0x17FFFFFF,   // kaca: putih 17% -> 9%
                0x40FFFFFF,               // pinggir kaca: putih 25%, 1dp
                0x40FFFFFF, 0x26FFFFFF,   // kaca terfokus: sedikit lebih terang
                0xFFF7F2E9,               // teks utama: putih hangat
                0xFFB9B1A3,               // teks sekunder: abu hangat
                0x26FFFFFF);              // chip: kaca tipis
    }

    /** Siang: terang hangat lembut, kaca putih berembun. */
    public static UiTheme day() {
        return new UiTheme(
                false,
                0xFFFBF7EE, 0xFFEEE6D5,   // latar: krem terang -> krem lebih dalam
                0x99FFFFFF, 0x73FFFFFF,   // kaca: putih 60% -> 45%
                0x30000000,               // pinggir kaca: hitam 19%, 1dp (di Siang rim terang tak terlihat)
                0xC2FFFFFF, 0x99FFFFFF,   // kaca terfokus: lebih pekat
                0xFF2B2721,               // teks utama: cokelat nyaris hitam
                0xFF7C7466,               // teks sekunder: abu kecokelatan
                0x1A000000);              // chip: bayang hitam sangat tipis
    }
}
