package com.enoli.perde;

import java.util.Arrays;
import java.util.List;

/**
 * Tab–akor sözlüğü: aynı anda basılan seslerin (perde sütunu) hangi akor olduğunu bulur.
 * Kök, tür ve bas sesine bakar; çevrimleri "C/E" gibi slash akor olarak adlandırır.
 */
public final class ChordDict {
    private ChordDict() {}

    /** Akor türleri: sembol ve kökten yarım ses aralıkları. Sıra = tercih (basit olan önce). */
    static final Object[][] TYPES = {
            {"5", new int[]{0, 7}},
            {"", new int[]{0, 4, 7}},
            {"m", new int[]{0, 3, 7}},
            {"7", new int[]{0, 4, 7, 10}},
            {"m7", new int[]{0, 3, 7, 10}},
            {"maj7", new int[]{0, 4, 7, 11}},
            {"sus4", new int[]{0, 5, 7}},
            {"sus2", new int[]{0, 2, 7}},
            {"dim", new int[]{0, 3, 6}},
            {"aug", new int[]{0, 4, 8}},
            {"6", new int[]{0, 4, 7, 9}},
            {"m6", new int[]{0, 3, 7, 9}},
            {"add9", new int[]{0, 2, 4, 7}},
            {"madd9", new int[]{0, 2, 3, 7}},
            {"9", new int[]{0, 2, 4, 7, 10}},
            {"m9", new int[]{0, 2, 3, 7, 10}},
            {"maj9", new int[]{0, 2, 4, 7, 11}},
            {"7sus4", new int[]{0, 5, 7, 10}},
            {"m7b5", new int[]{0, 3, 6, 10}},
            {"dim7", new int[]{0, 3, 6, 9}},
            {"7#9", new int[]{0, 3, 4, 7, 10}},
            {"5add9", new int[]{0, 2, 7}},
            // 5'li düşük (gitarda çok yaygın)
            {"7", new int[]{0, 4, 10}},
            {"m7", new int[]{0, 3, 10}},
            {"maj7", new int[]{0, 4, 11}},
            {"7#9", new int[]{0, 3, 4, 10}},
    };

    /** Gitarcıların alışık olduğu kök adları. */
    public static final String[] ROOT = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};

    public static class Match {
        public String name; public int root, bass; public int[] pcs; public boolean power;
    }

    /** Sesler bir akor oluşturuyorsa adını döner, değilse null (ör. iki sesli çift nota). */
    public static Match identify(int[] midis) {
        if (midis == null || midis.length < 2) return null;
        int bass = Integer.MAX_VALUE;
        boolean[] has = new boolean[12];
        for (int m : midis) { bass = Math.min(bass, m); has[((m % 12) + 12) % 12] = true; }
        int bpc = bass % 12;
        int n = 0;
        for (boolean h : has) if (h) n++;
        if (n < 2) return null; // oktav
        Match best = null; int bestScore = Integer.MAX_VALUE;
        for (int ti = 0; ti < TYPES.length; ti++) {
            int[] iv = (int[]) TYPES[ti][1];
            if (iv.length != n) continue;
            for (int r = 0; r < 12; r++) {
                if (!has[r]) continue;
                boolean ok = true;
                for (int x : iv) if (!has[(r + x) % 12]) { ok = false; break; }
                if (!ok) continue;
                // kök basta olmalı (çevrim cezası), basit tür tercih
                int score = ti + (r == bpc ? 0 : 40);
                if (n == 2 && r != bpc) continue;          // ters power chord'u (4'lü) akor sayma
                if (score < bestScore) {
                    bestScore = score;
                    best = new Match();
                    best.root = r; best.bass = bpc;
                    String sym = (String) TYPES[ti][0];
                    best.power = sym.equals("5");
                    best.name = ROOT[r] + sym + (r == bpc ? "" : "/" + ROOT[bpc]);
                    best.pcs = new int[iv.length];
                    for (int i = 0; i < iv.length; i++) best.pcs[i] = (r + iv[i]) % 12;
                }
            }
        }
        return best;
    }

    /** Akor sütunlarını adlandırır: notanın chord alanı dolar. Tek ses/çift ses dokunulmaz. */
    public static void annotate(List<Ex.Note> notes) {
        for (Ex.Note n : notes) {
            n.chord = null; n.chordPcs = null;
            if (n.alts == null || n.alts.length == 0) continue;
            int[] all = Arrays.copyOf(n.alts, n.alts.length + 1);
            all[n.alts.length] = n.midi;
            Match m = identify(all);
            if (m == null) continue;
            n.chord = m.name;
            n.chordPcs = m.pcs;
            n.power = m.power;
        }
    }
}
