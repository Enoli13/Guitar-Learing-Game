package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;

/** Gitar sentezinden üretilmiş küçük geri bildirim sesleri (kutlama = Tiny Habits "celebration"). */
public final class Sfx {
    private Sfx() {}

    /** Ayarlardan kapatılabilir. */
    public static volatile boolean enabled = true;

    private static void seq(float gain, float[][] n) {
        if (!enabled) return;
        new Thread(() -> {
            List<AudioOut.Ev> l = new ArrayList<>();
            for (float[] e : n) l.add(new AudioOut.Ev(e[0], e[1], e[2]));
            try {
                float[] buf = AudioOut.render(l);
                for (int i = 0; i < buf.length; i++) buf[i] *= gain;
                AudioOut.play(buf);
            } catch (Exception ignored) {}
        }).start();
    }

    public static void good() { seq(0.7f, new float[][]{{76, 0, 0.25f}, {83, 0.09f, 0.4f}}); }
    /** Yumuşak, kısa iniş: azarlamaz, sadece "olmadı" der. */
    public static void bad() { seq(0.35f, new float[][]{{67, 0, 0.2f}, {64, 0.12f, 0.35f}}); }
    public static void chest() { seq(0.7f, new float[][]{{72, 0, 0.2f}, {76, 0.07f, 0.2f}, {79, 0.14f, 0.2f}, {84, 0.21f, 0.6f}}); }
    public static void fanfare() { seq(0.7f, new float[][]{{64, 0, 0.3f}, {68, 0.12f, 0.3f}, {71, 0.24f, 0.3f}, {76, 0.36f, 0.8f}, {52, 0.36f, 0.8f}, {59, 0.38f, 0.8f}}); }
}
