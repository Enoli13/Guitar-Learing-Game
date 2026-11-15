package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;

/** Gitar sentezinden üretilmiş küçük geri bildirim sesleri (kutlama = Tiny Habits "celebration"). */
public final class Sfx {
    private Sfx() {}

    private static void seq(float[][] n) {
        new Thread(() -> {
            List<AudioOut.Ev> l = new ArrayList<>();
            for (float[] e : n) l.add(new AudioOut.Ev(e[0], e[1], e[2]));
            try { AudioOut.play(AudioOut.render(l)); } catch (Exception ignored) {}
        }).start();
    }

    public static void good() { seq(new float[][]{{76, 0, 0.25f}, {83, 0.09f, 0.4f}}); }
    public static void bad() { seq(new float[][]{{47, 0, 0.35f}, {46, 0.02f, 0.35f}}); }
    public static void chest() { seq(new float[][]{{72, 0, 0.2f}, {76, 0.07f, 0.2f}, {79, 0.14f, 0.2f}, {84, 0.21f, 0.6f}}); }
    public static void fanfare() { seq(new float[][]{{64, 0, 0.3f}, {68, 0.12f, 0.3f}, {71, 0.24f, 0.3f}, {76, 0.36f, 0.8f}, {52, 0.36f, 0.8f}, {59, 0.38f, 0.8f}}); }
}
