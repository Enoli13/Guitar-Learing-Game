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
        public List<Float> measureLens = new ArrayList<>();
        public String error;
        public String title = "", artist = "", format = "ASCII tab", trackName = "";
        public float tempo;
        public int beatsPerBar = 4;
        public int[] tuning = Theory.OPEN.clone();
        public float start(int m) { return m < measureStarts.size() ? measureStarts.get(m) : m * beatsPerBar; }
        public float len(int m) { return m < measureLens.size() ? measureLens.get(m) : beatsPerBar; }
        public boolean standardTuning() { return java.util.Arrays.equals(tuning, Theory.OPEN); }
        public int chordCount() { int c = 0; for (Ex.Note n : notes) if (n.chord != null) c++; return c; }
    }

    /** Akort adı: "Standart (E A D G B E)" ya da ör. "Eb standart". */
    public static String tuningName(int[] t) {
        StringBuilder sb = new StringBuilder();
        for (int s = 6; s >= 1; s--) { if (s < 6) sb.append(' '); sb.append(ChordDict.ROOT[((t[s] % 12) + 12) % 12]); }
        int d = t[6] - Theory.OPEN[6];
        boolean uniform = true;
        for (int s = 1; s <= 6; s++) if (t[s] - Theory.OPEN[s] != d) uniform = false;
        if (uniform && d == 0) return "Standart (" + sb + ")";
        if (uniform) return (d < 0 ? -d + " yarım ses pes" : d + " yarım ses tiz") + " (" + sb + ")";
        boolean drop = true;
        for (int s = 1; s <= 5; s++) if (t[s] - Theory.OPEN[s] != t[5] - Theory.OPEN[5]) drop = false;
        if (drop && t[6] - t[5] == -7) return "Drop " + ChordDict.ROOT[((t[6] % 12) + 12) % 12] + " (" + sb + ")";
        return sb.toString();
    }

    /** Yapıştırılan metin ya da kayıtlı veri: ASCII tab, MusicXML veya uygulamanın kendi biçimi. */
    public static Result parseAny(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("#perde1")) return fromText(t);
        if (t.startsWith("<?xml") || t.contains("<score-partwise") || t.contains("<score-timewise")) {
            try {
                SongData sd = MusicXml.parse(t.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                List<Integer> g = sd.guitarTracks();
                if (g.isEmpty()) { Result r = new Result(); r.error = "MusicXML'de nota bulunamadı."; return r; }
                return sd.toResult(g.get(0));
            } catch (Exception e) { Result r = new Result(); r.error = "MusicXML okunamadı: " + e.getMessage(); return r; }
        }
        return parse(text, 4);
    }

    /** Dosyadan gelen şarkıyı saklamak için düz metne çevirir (ritim ve akort kaybolmaz). */
    public static String toText(Result r) {
        StringBuilder sb = new StringBuilder("#perde1\n");
        sb.append("title=").append(r.title.replace('\n', ' ')).append('\n');
        sb.append("artist=").append(r.artist.replace('\n', ' ')).append('\n');
        sb.append("format=").append(r.format).append('\n');
        sb.append("track=").append(r.trackName.replace('\n', ' ')).append('\n');
        sb.append("tempo=").append(r.tempo).append('\n');
        sb.append("tuning=");
        for (int s = 1; s <= 6; s++) sb.append(s > 1 ? "," : "").append(r.tuning[s]);
        sb.append("\nmeasures=");
        for (int m = 0; m < r.measures; m++) sb.append(m > 0 ? ";" : "").append(r.start(m)).append(':').append(r.len(m));
        sb.append('\n');
        for (Ex.Note n : r.notes) {
            sb.append("n=").append(n.beat).append(',').append(n.dur).append(',').append(n.label == null ? "-" : n.label);
            if (n.strs != null) for (int i = 0; i < n.strs.length; i++) sb.append(',').append(n.strs[i]).append(':').append(n.frets[i]);
            else sb.append(',').append(n.string).append(':').append(n.fret);
            sb.append('\n');
        }
        return sb.toString();
    }

    public static Result fromText(String t) {
        SongData sd = new SongData();
        SongData.Track tr = new SongData.Track();
        sd.tracks.add(tr);
        for (String line : t.split("\n")) {
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String k = line.substring(0, eq), v = line.substring(eq + 1);
            try {
                switch (k) {
                    case "title": sd.title = v; break;
                    case "artist": sd.artist = v; break;
                    case "format": sd.format = v; break;
                    case "track": tr.name = v; break;
                    case "tempo": sd.tempo = Float.parseFloat(v); break;
                    case "tuning": { String[] a = v.split(","); for (int s = 1; s <= 6 && s <= a.length; s++) tr.tuning[s] = Integer.parseInt(a[s - 1].trim()); break; }
                    case "measures": for (String m : v.split(";")) { if (m.isEmpty()) continue; String[] a = m.split(":"); sd.mStart.add(Float.parseFloat(a[0])); sd.mLen.add(Float.parseFloat(a[1])); } break;
                    case "n": {
                        String[] a = v.split(",");
                        SongData.Ev e = new SongData.Ev();
                        e.beat = Float.parseFloat(a[0]); e.dur = Float.parseFloat(a[1]);
                        e.label = a[2].equals("-") ? null : a[2];
                        for (int i = 3; i < a.length; i++) { String[] q = a[i].split(":"); int s = Integer.parseInt(q[0]), f = Integer.parseInt(q[1]); e.notes.add(new int[]{s, f, 0, tr.tuning[s] + f}); }
                        tr.evs.add(e);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (tr.evs.isEmpty()) { Result r = new Result(); r.error = "Kayıtlı şarkı okunamadı."; return r; }
        return sd.toResult(0);
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
        int[] tun = tuningFromLabels(blocks.get(0));
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
                        if (last.strs == null) { last.strs = new int[]{last.string}; last.frets = new int[]{last.fret}; }
                        last.strs = append(last.strs, q[1]); last.frets = append(last.frets, q[2]);
                        int m2 = tun[q[1]] + q[2];
                        if (m2 < last.midi) { last.alts = append(last.alts, last.midi); last.string = q[1]; last.fret = q[2]; last.midi = m2; }
                        else last.alts = append(last.alts, m2);
                        continue;
                    }
                    Ex.Note n = new Ex.Note(q[1], q[2], beat, 1);
                    n.midi = tun[q[1]] + q[2];
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
        for (int m = 0; m < r.measures; m++) r.measureLens.add((float) beatsPerBar);
        r.tuning = tun;
        ChordDict.annotate(r.notes);
        if (r.notes.isEmpty()) r.error = "Tab'da çalınacak nota bulunamadı.";
        return r;
    }

    /** Tel etiketlerinden (ör. "D|", "Eb|") akordu çıkarır; okunamazsa standart. */
    static int[] tuningFromLabels(String[] b) {
        int[] t = Theory.OPEN.clone();
        int[] got = new int[7];
        for (int k = 0; k < 6; k++) {
            String s = b[k].trim();
            int p = 0;
            while (p < s.length() && s.charAt(p) != '|' && s.charAt(p) != '-') p++;
            String lab = s.substring(0, p).trim();
            if (lab.isEmpty() || lab.length() > 3) return t;
            char L = Character.toUpperCase(lab.charAt(0));
            int idx = "CDEFGAB".indexOf(L);
            if (idx < 0) return t;
            int pc = new int[]{0, 2, 4, 5, 7, 9, 11}[idx];
            if (lab.length() > 1) { char a = lab.charAt(1); if (a == '#') pc++; else if (a == 'b') pc--; }
            pc = (pc + 12) % 12;
            int open = Theory.OPEN[k + 1];
            int d = ((pc - open % 12) % 12 + 12) % 12;
            if (d > 6) d -= 12;
            got[k + 1] = open + d;
        }
        // sadece makul akortlar (her tel en fazla 3 yarım ses oynar)
        for (int s = 1; s <= 6; s++) if (Math.abs(got[s] - Theory.OPEN[s]) > 3) return t;
        return got;
    }

    static int[] append(int[] a, int v) {
        if (a == null) return new int[]{v};
        int[] b = java.util.Arrays.copyOf(a, a.length + 1);
        b[a.length] = v;
        return b;
    }

    /** Ölçü aralığını kesip 0'dan başlayan nota listesi döner. */
    public static List<Ex.Note> slice(Result r, int fromMeasure, int toMeasure, int beatsPerBar) {
        float a = r.start(fromMeasure), z = r.start(toMeasure) + r.len(toMeasure);
        List<Ex.Note> l = new ArrayList<>();
        for (Ex.Note n : r.notes) {
            if (n.beat >= a - 0.001f && n.beat < z - 0.001f) {
                Ex.Note c = new Ex.Note(n.string, n.fret, n.beat - a, n.dur);
                c.midi = n.midi; c.label = n.label; c.alts = n.alts;
                c.strs = n.strs; c.frets = n.frets; c.chord = n.chord; c.chordPcs = n.chordPcs; c.power = n.power;
                c.dur = Math.min(n.dur, z - n.beat);
                l.add(c);
            }
        }
        return l;
    }
}
