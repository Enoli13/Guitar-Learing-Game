package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;

/**
 * Ultimate Guitar / Songsterr tarzı ASCII tab'ları ayrıştırır.
 * Ritim, ölçü içindeki karakter konumundan çıkarılır (ölçü = 4 vuruş varsayılır).
 */
public final class TabImport {
    private TabImport() {}

    public static class Result {
        public List<Ex.Note> notes = new ArrayList<>();
        public int measures;
        public List<Float> measureStarts = new ArrayList<>();
        public String error;
    }

    static boolean isTabLine(String s) {
        String t = s.trim();
        if (t.length() < 6) return false;
        int dashes = 0, bad = 0;
        for (char c : t.toCharArray()) {
            if (c == '-') dashes++;
            else if (!(Character.isDigit(c) || "|:hpbr/\\~xX()^.*sSvVtT=<> ".indexOf(c) >= 0 || Character.isLetter(c))) bad++;
        }
        return dashes >= 4 && dashes > t.length() / 3 && bad <= 2;
    }

    public static Result parse(String text, int beatsPerBar) {
        Result r = new Result();
        String[] lines = text.replace("\r", "").split("\n");
        List<String[]> blocks = new ArrayList<>();
        for (int i = 0; i + 5 < lines.length; i++) {
            boolean ok = true;
            for (int k = 0; k < 6; k++) if (!isTabLine(lines[i + k])) { ok = false; break; }
            if (ok) {
                String[] b = new String[6];
                for (int k = 0; k < 6; k++) b[k] = lines[i + k];
                blocks.add(b);
                i += 5;
            }
        }
        if (blocks.isEmpty()) { r.error = "Tab bulunamadı. 6 satırlık (e B G D A E) tab bloklarını yapıştır."; return r; }
        float beatOffset = 0;
        for (String[] b : blocks) {
            // etiketi kaldır: ilk '|' veya '-' öncesi
            String[] L = new String[6];
            int start = Integer.MAX_VALUE;
            for (int k = 0; k < 6; k++) {
                String s = b[k];
                int p = 0;
                while (p < s.length() && s.charAt(p) != '|' && s.charAt(p) != '-') p++;
                start = Math.min(start, p);
                L[k] = s;
            }
            // satırları aynı başlangıçtan hizala
            int len = 0;
            for (int k = 0; k < 6; k++) { int p = 0; while (p < L[k].length() && L[k].charAt(p) != '|' && L[k].charAt(p) != '-') p++; L[k] = L[k].substring(p); len = Math.max(len, L[k].length()); }
            for (int k = 0; k < 6; k++) { StringBuilder sb = new StringBuilder(L[k]); while (sb.length() < len) sb.append('-'); L[k] = sb.toString(); }
            // ölçü çizgileri (ilk satıra göre)
            List<Integer> bars = new ArrayList<>();
            for (int c = 0; c < len; c++) {
                int cnt = 0;
                for (int k = 0; k < 6; k++) if (L[k].charAt(c) == '|') cnt++;
                if (cnt >= 4) bars.add(c);
            }
            if (bars.isEmpty() || bars.get(0) > 1) bars.add(0, -1);
            if (bars.get(bars.size() - 1) < len - 2) bars.add(len);
            for (int m = 0; m + 1 < bars.size(); m++) {
                int a = bars.get(m) + 1, z = bars.get(m + 1);
                if (z - a < 2) continue;
                r.measureStarts.add(beatOffset);
                // sütunları tara
                List<int[]> cols = new ArrayList<>(); // {col, string, fret, techCode}
                boolean[][] used = new boolean[6][len + 1];
                for (int c = a; c < z; c++) {
                    for (int k = 0; k < 6; k++) {
                        if (used[k][c]) continue;
                        char ch = L[k].charAt(c);
                        if (!Character.isDigit(ch)) continue;
                        int fret = ch - '0';
                        if (c + 1 < z && Character.isDigit(L[k].charAt(c + 1))) { fret = fret * 10 + (L[k].charAt(c + 1) - '0'); used[k][c + 1] = true; }
                        if (fret > 24) fret = fret / 10;
                        // teknik: öncesindeki karakter
                        char prev = c > 0 ? L[k].charAt(c - 1) : '-';
                        int tech = prev == 'h' ? 1 : prev == 'p' ? 2 : (prev == '/' || prev == '\\') ? 3 : 0;
                        // bend: sonrası 'b' ve hedef perde
                        int bendTo = -1;
                        int after = c + (fret >= 10 ? 2 : 1);
                        if (after < z && L[k].charAt(after) == 'b') {
                            int q = after + 1, v = 0, n = 0;
                            while (q < z && Character.isDigit(L[k].charAt(q)) && n < 2) { v = v * 10 + (L[k].charAt(q) - '0'); used[k][q] = true; q++; n++; }
                            bendTo = n > 0 ? v : fret + 2;
                        }
                        // ardından gelen hedef perdeleri (bend sonrası "r" vs) yok saymak için işaretle
                        cols.add(new int[]{c, k + 1, fret, tech, bendTo});
                    }
                }
                int width = z - a;
                for (int i = 0; i < cols.size(); i++) {
                    int[] q = cols.get(i);
                    float beat = beatOffset + (float) (q[0] - a) / width * beatsPerBar;
                    beat = Math.round(beat * 4) / 4f; // 16'lığa yuvarla
                    // aynı vuruşta akor: tek notada birleştir (en pes nota beklenir, diğerleri alternatif)
                    Ex.Note last = r.notes.isEmpty() ? null : r.notes.get(r.notes.size() - 1);
                    if (last != null && Math.abs(last.beat - beat) < 0.01f) {
                        int m2 = Theory.OPEN[q[1]] + q[2];
                        if (m2 < last.midi) { last.alts = append(last.alts, last.midi); last.string = q[1]; last.fret = q[2]; last.midi = m2; }
                        else last.alts = append(last.alts, m2);
                        continue;
                    }
                    Ex.Note n = new Ex.Note(q[1], q[2], beat, 1);
                    if (q[3] == 1) n.label = "h"; else if (q[3] == 2) n.label = "p"; else if (q[3] == 3) n.label = "s";
                    if (q[4] > q[2]) { n.label = "b" + (q[4] - q[2]); n.midi += q[4] - q[2]; }
                    r.notes.add(n);
                }
                beatOffset += beatsPerBar;
                r.measures++;
            }
        }
        // süreleri sonraki notaya göre ayarla
        for (int i = 0; i < r.notes.size(); i++) {
            Ex.Note n = r.notes.get(i);
            float nb = i + 1 < r.notes.size() ? r.notes.get(i + 1).beat : n.beat + 1;
            n.dur = Math.max(0.25f, nb - n.beat);
        }
        if (r.notes.isEmpty()) r.error = "Tab'da çalınacak nota bulunamadı.";
        return r;
    }

    static int[] append(int[] a, int v) {
        if (a == null) return new int[]{v};
        int[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = v;
        return b;
    }

    /** Ölçü aralığını kesip 0'dan başlayan nota listesi döner. */
    public static List<Ex.Note> slice(Result r, int fromMeasure, int toMeasure, int beatsPerBar) {
        float a = fromMeasure * beatsPerBar, z = (toMeasure + 1) * beatsPerBar;
        List<Ex.Note> l = new ArrayList<>();
        for (Ex.Note n : r.notes) {
            if (n.beat >= a - 0.001f && n.beat < z - 0.001f) {
                Ex.Note c = new Ex.Note(n.string, n.fret, n.beat - a, n.dur);
                c.midi = n.midi; c.label = n.label; c.alts = n.alts;
                l.add(c);
            }
        }
        return l;
    }
}
