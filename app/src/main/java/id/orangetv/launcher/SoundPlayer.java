package id.orangetv.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Pemutar efek suara navigasi Orange TV.
 *
 * Suara pindah fokus (suara_pindah.mp3) adalah berkas yang disediakan
 * langsung oleh pemilik proyek. Suara pilih/kembali adalah WAV mungil
 * yang DISINTESIS sendiri (lihat scripts/gen-suara.py). Semuanya diputar
 * lewat SoundPool dengan volume pelan (~0,35) agar elegan, bukan ramai.
 *
 * Kelas ini tidak boleh pernah mengganggu navigasi: semua panggilan
 * dibungkus try/catch dan kegagalan audio diabaikan diam-diam.
 */
public final class SoundPlayer {

    private static final float VOLUME = 0.35f;

    private SoundPool pool;
    private int moveId;
    private int selectId;
    private int backId;
    private int lastMoveStream = 0;
    private boolean enabled = true;

    public SoundPlayer(Context context) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build();
            moveId = pool.load(context, R.raw.suara_pindah, 1);
            selectId = pool.load(context, R.raw.suara_pilih, 1);
            backId = pool.load(context, R.raw.suara_kembali, 1);
        } catch (Exception e) {
            pool = null; // TV tanpa jalur audio yang sehat: lanjut tanpa suara
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Bunyi navigasi saat fokus berpindah antar ubin. Berkasnya berdurasi
     * ~0,76 dtk (klik + ekor meluruh), jadi putaran sebelumnya dihentikan
     * dulu agar perpindahan cepat tidak menumpuk bunyi menjadi keruh.
     */
    public void move() {
        if (!enabled || pool == null || moveId == 0) {
            return;
        }
        try {
            if (lastMoveStream != 0) {
                pool.stop(lastMoveStream);
            }
            lastMoveStream = pool.play(moveId, VOLUME, VOLUME, 1, 0, 1.0f);
        } catch (Exception ignored) {
        }
    }

    /** Lonceng dua nada saat aplikasi dipilih / diluncurkan. */
    public void select() {
        play(selectId);
    }

    /** Blip rendah saat pengguna menekan Kembali. */
    public void back() {
        play(backId);
    }

    private void play(int soundId) {
        if (!enabled || pool == null || soundId == 0) {
            return;
        }
        try {
            pool.play(soundId, VOLUME, VOLUME, 1, 0, 1.0f);
        } catch (Exception ignored) {
            // Suara tidak boleh pernah menjatuhkan launcher.
        }
    }

    /** Lepaskan SoundPool; panggil dari onDestroy activity. */
    public void release() {
        try {
            if (pool != null) {
                pool.release();
            }
        } catch (Exception ignored) {
        }
        pool = null;
    }
}
