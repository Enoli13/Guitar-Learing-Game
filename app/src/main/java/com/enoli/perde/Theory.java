package com.enoli.perde;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Müzik teorisi çekirdeği: notalar, aralıklar, gamlar, akorlar, tonaliteler, akor şekilleri. */
public final class Theory {
    private Theory() {}

    public static final String[] SHARP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    public static final String[] FLAT = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};
    public static final String[] LETTERS = {"C", "D", "E", "F", "G", "A", "B"};
    public static final int[] LETTER_PC = {0, 2, 4, 5, 7, 9, 11};
    public static final String[] SOLFEJ = {"Do", "Re", "Mi", "Fa", "Sol", "La", "Si"};
    /** tel numarası (1=ince mi .. 6=kalın mi) -> açık tel MIDI */
    public static final int[] OPEN = {0, 64, 59, 55, 50, 45, 40};
    public static final String[] STRING_NAME = {"", "1. tel (ince E)", "2. tel (B)", "3. tel (G)", "4. tel (D)", "5. tel (A)", "6. tel (kalın E)"};

    public static int pc(int midi) { return ((midi % 12) + 12) % 12; }

    public static String name(int pc, boolean flat) { return (flat ? FLAT : SHARP)[pc(pc)]; }

    public static String nameWithSolfej(int pc) {
        String n = SHARP[pc(pc)];
        int li = "CDEFGAB".indexOf(n.charAt(0));
        String s = SOLFEJ[li] + (n.length() > 1 ? " diyez" : "");
        return n + " (" + s + ")";
    }

    public static String midiName(int midi) { return SHARP[pc(midi)] + (midi / 12 - 1); }

    /** "C#", "Eb", "F" vb. -> perde sınıfı; geçersizse -1 */
    public static int parsePc(String s) {
        if (s == null || s.isEmpty()) return -1;
        int li = "CDEFGAB".indexOf(Character.toUpperCase(s.charAt(0)));
        if (li < 0) return -1;
        int p = LETTER_PC[li];
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#') p++; else if (c == 'b') p--; else break;
        }
        return pc(p);
    }

    // ---------- Aralıklar ----------
    public static final String[] IVAL_NAME = {"Tam birli (unison)", "Küçük ikili", "Büyük ikili", "Küçük üçlü", "Büyük üçlü", "Tam dörtlü",
            "Triton (artık 4'lü / eksik 5'li)", "Tam beşli", "Küçük altılı", "Büyük altılı", "Küçük yedili", "Büyük yedili", "Oktav"};
    public static final String[] IVAL_CODE = {"P1", "m2", "M2", "m3", "M3", "P4", "TT", "P5", "m6", "M6", "m7", "M7", "P8"};

    public static int ivalCode(String c) { for (int i = 0; i < IVAL_CODE.length; i++) if (IVAL_CODE[i].equals(c)) return i; return -1; }

    // ---------- Gamlar ----------
    public static final Map<String, int[]> SCALES = new HashMap<>();
    public static final Map<String, String> SCALE_TR = new HashMap<>();
    static {
        sc("major", "Majör (İyonyen)", 0, 2, 4, 5, 7, 9, 11);
        sc("minor", "Doğal minör (Aiolyan)", 0, 2, 3, 5, 7, 8, 10);
        sc("harmmin", "Armonik minör", 0, 2, 3, 5, 7, 8, 11);
        sc("melmin", "Melodik minör", 0, 2, 3, 5, 7, 9, 11);
        sc("minpent", "Minör pentatonik", 0, 3, 5, 7, 10);
        sc("majpent", "Majör pentatonik", 0, 2, 4, 7, 9);
        sc("blues", "Blues gamı", 0, 3, 5, 6, 7, 10);
        sc("ionian", "İyonyen", 0, 2, 4, 5, 7, 9, 11);
        sc("dorian", "Doryen", 0, 2, 3, 5, 7, 9, 10);
        sc("phrygian", "Frigyen", 0, 1, 3, 5, 7, 8, 10);
        sc("lydian", "Lidyen", 0, 2, 4, 6, 7, 9, 11);
        sc("mixolydian", "Miksolidyen", 0, 2, 4, 5, 7, 9, 10);
        sc("aeolian", "Aiolyan", 0, 2, 3, 5, 7, 8, 10);
        sc("locrian", "Lokriyen", 0, 1, 3, 5, 6, 8, 10);
        sc("phrygdom", "Frigyen dominant", 0, 1, 4, 5, 7, 8, 10);
        sc("wholetone", "Tam ses gamı", 0, 2, 4, 6, 8, 10);
        sc("dimhw", "Azalmış (yarım-tam)", 0, 1, 3, 4, 6, 7, 9, 10);
        sc("dimwh", "Azalmış (tam-yarım)", 0, 2, 3, 5, 6, 8, 9, 11);
        sc("chromatic", "Kromatik", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        sc("mixblues", "Majör+minör pentatonik karışımı", 0, 2, 3, 4, 5, 7, 9, 10);
    }
    private static void sc(String k, String tr, int... iv) { SCALES.put(k, iv); SCALE_TR.put(k, tr); }

    /** "A:minpent" -> perde sınıfları */
    public static int[] scalePcs(String spec) {
        String[] p = spec.split(":");
        int root = parsePc(p[0]);
        int[] iv = SCALES.get(p[1]);
        int[] r = new int[iv.length];
        for (int i = 0; i < iv.length; i++) r[i] = pc(root + iv[i]);
        return r;
    }

    /** Yedi notalı gamı doğru harflerle yazar (ör. F majör -> F G A Bb C D E). */
    public static String[] spellScale(String rootName, String type) {
        int[] iv = SCALES.get(type);
        String[] out = new String[iv.length];
        int rootPc = parsePc(rootName);
        int li = "CDEFGAB".indexOf(rootName.charAt(0));
        boolean heptatonic = iv.length == 7;
        for (int i = 0; i < iv.length; i++) {
            int target = pc(rootPc + iv[i]);
            if (heptatonic) out[i] = spellLetter((li + i) % 7, target);
            else out[i] = name(target, preferFlat(rootName, type));
        }
        return out;
    }

    /** Verilen harf ile hedef perde sınıfını yazar (C##, Bb gibi). */
    public static String spellLetter(int letterIdx, int targetPc) {
        int base = LETTER_PC[letterIdx];
        int d = pc(targetPc - base);
        if (d > 6) d -= 12;
        StringBuilder sb = new StringBuilder(LETTERS[letterIdx]);
        if (d > 0) for (int i = 0; i < d; i++) sb.append('#');
        else for (int i = 0; i < -d; i++) sb.append('b');
        return sb.toString();
    }

    public static boolean preferFlat(String root, String type) {
        if (root.contains("b")) return true;
        if (root.contains("#")) return false;
        boolean minorish = type.contains("min") || type.equals("dorian") || type.equals("phrygian") || type.equals("aeolian") || type.equals("blues") || type.equals("locrian");
        if (minorish) return "DGCF".contains(root);
        return root.equals("F");
    }

    // ---------- Tonaliteler ----------
    /** Beşliler çemberi sırası, majör. İşaret sayısı: +diyez, -bemol. */
    public static final String[] MAJOR_KEYS = {"C", "G", "D", "A", "E", "B", "F#", "C#", "F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb"};
    public static final int[] MAJOR_SIG = {0, 1, 2, 3, 4, 5, 6, 7, -1, -2, -3, -4, -5, -6, -7};
    public static final String[] MINOR_KEYS = {"A", "E", "B", "F#", "C#", "G#", "D#", "A#", "D", "G", "C", "F", "Bb", "Eb", "Ab"};

    public static String sigText(int s) {
        if (s == 0) return "işaretsiz";
        return Math.abs(s) + (s > 0 ? " diyez" : " bemol");
    }

    // ---------- Akorlar ----------
    public static class ChordType {
        public final String sym, tr; public final int[] iv;
        ChordType(String sym, String tr, int... iv) { this.sym = sym; this.tr = tr; this.iv = iv; }
    }
    public static final List<ChordType> CHORD_TYPES = new ArrayList<>();
    public static final Map<String, ChordType> CT = new HashMap<>();
    static {
        ct("", "majör", 0, 4, 7); ct("m", "minör", 0, 3, 7); ct("dim", "eksik (dim)", 0, 3, 6); ct("aug", "artık (aug)", 0, 4, 8);
        ct("5", "power (5)", 0, 7); ct("7", "dominant 7", 0, 4, 7, 10); ct("maj7", "majör 7", 0, 4, 7, 11); ct("m7", "minör 7", 0, 3, 7, 10);
        ct("m7b5", "yarım eksik (m7b5)", 0, 3, 6, 10); ct("dim7", "eksik 7 (dim7)", 0, 3, 6, 9); ct("sus2", "sus2", 0, 2, 7); ct("sus4", "sus4", 0, 5, 7);
        ct("add9", "add9", 0, 4, 7, 14); ct("6", "majör 6", 0, 4, 7, 9); ct("m6", "minör 6", 0, 3, 7, 9); ct("9", "dominant 9", 0, 4, 7, 10, 14);
        ct("maj9", "majör 9", 0, 4, 7, 11, 14); ct("m9", "minör 9", 0, 3, 7, 10, 14); ct("11", "dominant 11", 0, 4, 7, 10, 14, 17);
        ct("13", "dominant 13", 0, 4, 7, 10, 14, 21); ct("7#9", "7#9 (Hendrix akoru)", 0, 4, 7, 10, 15); ct("mmaj7", "minör-majör 7", 0, 3, 7, 11);
        ct("7sus4", "7sus4", 0, 5, 7, 10);
    }
    private static void ct(String s, String tr, int... iv) { ChordType c = new ChordType(s, tr, iv); CHORD_TYPES.add(c); CT.put(s, c); }

    /** "C#m7" -> {kök pc, tip}; slash akorda bas notası göz ardı edilir (bass ayrıca). */
    public static Object[] parseChord(String name) {
        String n = name.split("/")[0].split("@")[0];
        int i = 1;
        while (i < n.length() && (n.charAt(i) == '#' || n.charAt(i) == 'b')) i++;
        int root = parsePc(n.substring(0, i));
        String rest = n.substring(i);
        ChordType t = CT.get(rest);
        if (t == null) t = CT.get("");
        return new Object[]{root, t};
    }

    public static int[] chordPcs(String name) {
        Object[] p = parseChord(name);
        int root = (Integer) p[0];
        ChordType t = (ChordType) p[1];
        int[] r = new int[t.iv.length + (name.contains("/") ? 1 : 0)];
        for (int i = 0; i < t.iv.length; i++) r[i] = pc(root + t.iv[i]);
        if (name.contains("/")) r[r.length - 1] = parsePc(name.split("/")[1]);
        return r;
    }

    /** Akor notalarını harf doğruluğuyla yazar. */
    public static String spellChord(String rootName, ChordType t) {
        int rootPc = parsePc(rootName);
        int li = "CDEFGAB".indexOf(rootName.charAt(0));
        StringBuilder sb = new StringBuilder();
        for (int iv : t.iv) {
            int deg = ivDegree(iv);
            if (sb.length() > 0) sb.append(' ');
            sb.append(spellLetter((li + deg) % 7, pc(rootPc + iv)));
        }
        return sb.toString();
    }

    private static int ivDegree(int iv) {
        switch (iv) {
            case 0: return 0;
            case 1: case 2: case 14: case 15: return 1;
            case 3: case 4: return 2;
            case 5: case 17: return 3;
            case 6: case 7: case 8: return iv == 8 ? 4 : 4;
            case 9: case 21: return 5;
            case 10: case 11: return 6;
            default: return 0;
        }
    }

    // ---------- Akor şekilleri (6. telden 1. tele, x=susturulmuş) ----------
    public static final Map<String, String> OPEN_SHAPES = new HashMap<>();
    static {
        String[][] s = {
                {"E", "022100"}, {"Em", "022000"}, {"A", "x02220"}, {"Am", "x02210"}, {"D", "xx0232"}, {"Dm", "xx0231"},
                {"G", "320003"}, {"C", "x32010"}, {"E7", "020100"}, {"A7", "x02020"}, {"D7", "xx0212"}, {"G7", "320001"},
                {"C7", "x32310"}, {"B7", "x21202"}, {"Am7", "x02010"}, {"Em7", "022030"}, {"Dm7", "xx0211"}, {"Cmaj7", "x32000"},
                {"Fmaj7", "xx3210"}, {"Amaj7", "x02120"}, {"Dmaj7", "xx0222"}, {"Dsus2", "xx0230"}, {"Dsus4", "xx0233"},
                {"Asus2", "x02200"}, {"Asus4", "x02230"}, {"Esus4", "022200"}, {"Cadd9", "x32033"}, {"E5", "022xxx"},
                {"A5", "x022xx"}, {"D5", "xx023x"}, {"F", "133211"}, {"Bm", "x24432"}, {"B", "x24442"}, {"Fm", "133111"},
                {"G/B", "x20003"}, {"D/F#", "200232"}, {"C/G", "332010"}, {"Am/G", "302210"}, {"Em/B", "x22000"}, {"A/C#", "x42220"},
        };
        for (String[] r : s) OPEN_SHAPES.put(r[0], r[1]);
    }

    /** Akor için şekil verir. "@6" ya da "@5" son eki kök telini zorlar. */
    public static String shapeFor(String name) {
        String clean = name.split("@")[0];
        int force = name.contains("@") ? Integer.parseInt(name.substring(name.indexOf('@') + 1)) : 0;
        if (force == 0 && OPEN_SHAPES.containsKey(clean)) return OPEN_SHAPES.get(clean);
        Object[] p = parseChord(clean);
        int root = (Integer) p[0];
        ChordType t = (ChordType) p[1];
        int f6 = pc(root - 4), f5 = pc(root - 9);
        if (f6 == 0) f6 = 12; if (f5 == 0) f5 = 12;
        String e = eShape(t.sym, f6), a = aShape(t.sym, f5);
        if (force == 6 && e != null) return e;
        if (force == 5 && a != null) return a;
        if (e == null) return a;
        if (a == null) return e;
        return f6 <= f5 ? e : a;
    }

    private static String eShape(String t, int f) {
        int[] o;
        switch (t) {
            case "": o = new int[]{0, 2, 2, 1, 0, 0}; break;
            case "m": o = new int[]{0, 2, 2, 0, 0, 0}; break;
            case "7": o = new int[]{0, 2, 0, 1, 0, 0}; break;
            case "m7": o = new int[]{0, 2, 0, 0, 0, 0}; break;
            case "maj7": o = new int[]{0, -99, 1, 1, 0, -99}; break;
            case "5": o = new int[]{0, 2, 2, -99, -99, -99}; break;
            case "sus4": o = new int[]{0, 2, 2, 2, 0, 0}; break;
            case "7sus4": o = new int[]{0, 2, 0, 2, 0, 0}; break;
            case "dim7": o = new int[]{0, -99, -1, 0, -1, -99}; break;
            case "7#9": return null;
            default: return null;
        }
        return render(o, f);
    }

    private static String aShape(String t, int f) {
        int[] o;
        switch (t) {
            case "": o = new int[]{-99, 0, 2, 2, 2, 0}; break;
            case "m": o = new int[]{-99, 0, 2, 2, 1, 0}; break;
            case "7": o = new int[]{-99, 0, 2, 0, 2, 0}; break;
            case "m7": o = new int[]{-99, 0, 2, 0, 1, 0}; break;
            case "maj7": o = new int[]{-99, 0, 2, 1, 2, 0}; break;
            case "m7b5": o = new int[]{-99, 0, 1, 0, 1, -99}; break;
            case "dim7": o = new int[]{-99, 0, 1, -1, 1, -99}; break;
            case "5": o = new int[]{-99, 0, 2, 2, -99, -99}; break;
            case "sus2": o = new int[]{-99, 0, 2, 2, 0, 0}; break;
            case "sus4": o = new int[]{-99, 0, 2, 2, 3, 0}; break;
            case "9": o = new int[]{-99, 0, -1, 0, 0, 0}; break;
            case "m9": o = new int[]{-99, 0, -2, 0, 0, -99}; break;
            case "maj9": o = new int[]{-99, 0, -1, 1, 0, -99}; break;
            case "6": o = new int[]{-99, 0, 2, 2, 2, 2}; break;
            case "m6": o = new int[]{-99, 0, 2, -1, 1, 0}; break;
            case "dim": o = new int[]{-99, 0, 1, 2, 1, -99}; break;
            case "aug": o = new int[]{-99, 0, 3, 2, 2, -99}; break;
            case "7#9": o = new int[]{-99, 0, -1, 0, 1, -99}; break;
            case "13": o = new int[]{-99, 0, -1, 0, 2, 2}; break;
            case "add9": o = new int[]{-99, 0, -1, -3, 0, 0}; break;
            case "mmaj7": o = new int[]{-99, 0, 2, 1, 1, 0}; break;
            default: return null;
        }
        if (f < 3 && min(o) < 0) f += 12;
        return render(o, f);
    }

    private static int min(int[] o) { int m = 99; for (int v : o) if (v > -50) m = Math.min(m, v); return m; }

    private static String render(int[] o, int f) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (o[i] < -50) { sb.append("x"); continue; }
            int fr = o[i] + f;
            if (fr < 0) return null;
            if (fr > 9) sb.append('(').append(fr).append(')'); else sb.append(fr);
        }
        return sb.toString();
    }

    /** Şekil dizgesini 6 elemanlı perde dizisine çevirir (-1 = susturulmuş). index0 = 6. tel. */
    public static int[] parseShape(String s) {
        int[] r = new int[6];
        int i = 0, k = 0;
        while (k < 6 && i < s.length()) {
            char c = s.charAt(i);
            if (c == 'x' || c == 'X') { r[k++] = -1; i++; }
            else if (c == '(') { int j = s.indexOf(')', i); r[k++] = Integer.parseInt(s.substring(i + 1, j)); i = j + 1; }
            else { r[k++] = c - '0'; i++; }
        }
        return r;
    }

    public static float[] shapeMidis(String shape) {
        int[] f = parseShape(shape);
        List<Float> l = new ArrayList<>();
        for (int i = 0; i < 6; i++) if (f[i] >= 0) l.add((float) (OPEN[6 - i] + f[i]));
        float[] r = new float[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    public static String chordTr(String name) {
        Object[] p = parseChord(name);
        ChordType t = (ChordType) p[1];
        return name.split("@")[0] + " (" + t.tr + ")";
    }

    // ---------- Diyatonik ----------
    public static final String[] ROMAN = {"I", "ii", "iii", "IV", "V", "vi", "vii°"};
    public static final String[] DEGREE_TR = {"Tonik", "Süpertonik", "Medyant", "Subdominant", "Dominant", "Submedyant", "Yeden (leading tone)"};

    public static String diatonicChord(String key, int degree) {
        String[] sc = spellScale(key, "major");
        String[] q = {"", "m", "m", "", "", "m", "dim"};
        return sc[degree] + q[degree];
    }

    public static String fmt(String f, Object... a) { return String.format(Locale.ROOT, f, a); }
}
