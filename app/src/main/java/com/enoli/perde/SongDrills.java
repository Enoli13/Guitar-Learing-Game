package com.enoli.perde;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Şarkının içinden teori soruları: ünitenin konusu ne ise (nota adları, akorlar, aralıklar, gamlar, ritim, akort)
 * sorular yüklediğin tab'daki gerçek notalardan üretilir. Böylece şarkıyı çalarken konuyu da öğrenirsin.
 */
public final class SongDrills {
    private SongDrills() {}

    enum Kind { NOTE, STAFF, CHORD, INTERVAL, CENTER, SCALE, RHYTHM, TUNING }

    static Kind[] kindsFor(String unit) {
        switch (unit == null ? "" : unit) {
            case "u01": case "u02": case "u03": case "k01": return new Kind[]{Kind.NOTE};
            case "n01": case "u04": case "u11": case "c01": return new Kind[]{Kind.RHYTHM, Kind.NOTE};
            case "u05": case "u07": case "u13": case "u16": return new Kind[]{Kind.CHORD, Kind.NOTE};
            case "u10": case "u17": case "u23": return new Kind[]{Kind.CHORD, Kind.CENTER, Kind.SCALE};
            case "u06": case "u14": return new Kind[]{Kind.STAFF, Kind.NOTE};
            case "u08": case "u21": return new Kind[]{Kind.INTERVAL, Kind.NOTE};
            case "u09": case "u12": case "u18": case "u19": case "u20": case "u24": return new Kind[]{Kind.SCALE, Kind.CENTER, Kind.INTERVAL};
            case "u15": return new Kind[]{Kind.CHORD, Kind.NOTE, Kind.INTERVAL};
            case "u22": return new Kind[]{Kind.RHYTHM, Kind.INTERVAL, Kind.NOTE};
            case "u25": case "u26": return new Kind[]{Kind.TUNING, Kind.CHORD, Kind.SCALE, Kind.NOTE};
            default: return new Kind[]{Kind.NOTE, Kind.CHORD};
        }
    }

    /** notes: çalışılan bölüm. count kadar soru (üretilebilenler). */
    public static List<Ex> make(TabImport.Result r, List<Ex.Note> notes, String unit, int count, Random rnd) {
        List<Ex> out = new ArrayList<>();
        Kind[] ks = kindsFor(unit);
        boolean preciseRhythm = r.format != null && !r.format.startsWith("ASCII");
        int guard = 0;
        Set<String> seen = new LinkedHashSet<>();
        boolean tuningAsked = false;
        while (out.size() < count && guard++ < count * 12) {
            Kind k = ks[guard % ks.length == 0 ? 0 : rnd.nextInt(ks.length)];
            if (guard <= ks.length) k = ks[guard - 1];
            Ex e = null;
            switch (k) {
                case NOTE: e = note(notes, rnd, false); break;
                case STAFF: e = note(notes, rnd, true); break;
                case CHORD: e = chord(notes, rnd); break;
                case INTERVAL: e = interval(notes, rnd); break;
                case CENTER: e = center(notes, rnd); break;
                case SCALE: e = scale(notes, rnd); break;
                case RHYTHM: e = preciseRhythm ? rhythm(r, notes, rnd) : null; break;
                case TUNING: if (!tuningAsked) { e = tuning(r, rnd); tuningAsked = true; } break;
            }
            if (e == null) continue;
            String sig = e.prompt + e.options.get(e.correct);
            if (!seen.add(sig) && guard < count * 8) continue;
            e.title = "Şarkıdan teori";
            out.add(e);
        }
        if (out.isEmpty()) { Ex e = note(notes, rnd, false); if (e != null) out.add(e); }
        return out;
    }

    static Ex choice(String prompt, String correct, List<String> wrong, Random r) {
        Ex e = new Ex();
        e.type = Ex.Type.CHOICE;
        e.prompt = prompt;
        Set<String> o = new LinkedHashSet<>();
        o.add(correct);
        List<String> w = new ArrayList<>(wrong);
        Collections.shuffle(w, r);
        for (String s : w) { if (o.size() >= 4) break; if (!s.equals(correct)) o.add(s); }
        if (o.size() < 2) return null;
        e.options = new ArrayList<>(o);
        Collections.shuffle(e.options, r);
        e.correct = e.options.indexOf(correct);
        return e;
    }

    static List<Ex.Note> singles(List<Ex.Note> notes) {
        List<Ex.Note> l = new ArrayList<>();
        for (Ex.Note n : notes) if (n.chord == null && n.alts == null && n.string > 0) l.add(n);
        return l;
    }

    static String label(int pc) { return Gen.pcLabel(pc) + " (" + ScoreView.noteName(pc + 60).replace("♯", "#") + ")"; }

    static Ex note(List<Ex.Note> notes, Random r, boolean staff) {
        List<Ex.Note> s = singles(notes);
        if (s.isEmpty()) return null;
        Ex.Note n = s.get(r.nextInt(s.size()));
        int pc = Theory.pc(n.midi);
        List<String> w = new ArrayList<>();
        for (int d = -3; d <= 3; d++) if (d != 0) w.add(label(Theory.pc(pc + d)));
        Ex e = choice(staff ? "Şarkının porteye yazılmış bu notası hangisi?" : "Şarkıdaki bu ses (" + n.string + ". tel, " + n.fret + ". perde) hangi nota?", label(pc), w, r);
        if (e == null) return null;
        if (staff) e.staffMidis = new int[]{n.midi + 12};
        else { Ex.Note c = new Ex.Note(n.string, n.fret, 0, 1); c.midi = n.midi; e.notes.add(c); e.showTab = true; }
        e.explain = Theory.STRING_NAME[n.string] + " teli, " + n.fret + ". perde = " + Theory.nameWithSolfej(pc) + (staff ? ". Gitar porteden bir oktav pes çalar." : "");
        return e;
    }

    private static int measureOf(float beat) { return (int) (beat / 4) + 1; }

    static String voicing(Ex.Note n) {
        char[] v = "xxxxxx".toCharArray();
        String[] fr = new String[7];
        Arrays.fill(fr, "x");
        if (n.strs != null) for (int i = 0; i < n.strs.length; i++) fr[n.strs[i]] = String.valueOf(n.frets[i]);
        StringBuilder sb = new StringBuilder();
        for (int st = 6; st >= 1; st--) { if (st < 6) sb.append('-'); sb.append(fr[st]); }
        return sb.toString();
    }

    static Ex chord(List<Ex.Note> notes, Random r) {
        List<Ex.Note> ch = new ArrayList<>();
        for (Ex.Note n : notes) if (n.chord != null && n.strs != null) ch.add(n);
        if (ch.isEmpty()) return null;
        Ex.Note n = ch.get(r.nextInt(ch.size()));
        ChordDict.Match m = null;
        int[] all = ScoreView.midis(n);
        m = ChordDict.identify(all);
        if (m == null) return null;
        String root = ChordDict.ROOT[m.root];
        String sym = n.chord.substring(root.length());
        String base = sym.contains("/") ? sym.substring(0, sym.indexOf('/')) : sym;
        List<String> w = new ArrayList<>();
        // aynı kök başka tür, ya da aynı tür başka kök
        for (String t : new String[]{"", "m", "5", "7", "m7", "maj7", "sus4", "sus2"}) if (!t.equals(base)) w.add(root + t);
        for (int d : new int[]{-2, 2, 5, 7}) w.add(ChordDict.ROOT[(m.root + d + 12) % 12] + base);
        Collections.shuffle(w, r);
        List<String> pick = new ArrayList<>(w.subList(0, Math.min(5, w.size())));
        Ex e = choice("Şarkıdaki bu akor sütunu hangi akor?\n(kalın telden inceye: " + voicing(n) + ")", n.chord, pick, r);
        if (e == null) return null;
        StringBuilder names = new StringBuilder();
        int[] sorted = all.clone(); Arrays.sort(sorted);
        for (int x : sorted) { if (names.length() > 0) names.append(", "); names.append(Theory.SHARP[Theory.pc(x)]); }
        e.explain = "Basılan sesler: " + names + " → kök " + root + (base.isEmpty() ? ", majör üçlü" : base.equals("5") ? ", sadece kök + beşli = power chord" : base.equals("m") ? ", minör üçlü" : ", tür: " + base) + (sym.contains("/") ? "; en kalın ses kök değil, o yüzden slash (/) akor" : "") + ".";
        e.audio = new float[all.length];
        for (int i = 0; i < all.length; i++) e.audio[i] = sorted[i];
        e.audioHarmonic = true;
        return e;
    }

    static Ex interval(List<Ex.Note> notes, Random r) {
        List<Ex.Note> s = singles(notes);
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < s.size(); i++) {
            int d = Math.abs(s.get(i + 1).midi - s.get(i).midi);
            if (d >= 1 && d <= 12) pairs.add(new int[]{i, d});
        }
        if (pairs.isEmpty()) return null;
        int[] p = pairs.get(r.nextInt(pairs.size()));
        Ex.Note a = s.get(p[0]), b = s.get(p[0] + 1);
        int d = p[1];
        List<String> w = new ArrayList<>();
        for (int x = Math.max(1, d - 3); x <= Math.min(12, d + 3); x++) if (x != d) w.add(Theory.IVAL_NAME[x]);
        Ex e = choice("Şarkıda art arda gelen bu iki ses arasındaki aralık ne?\n" + a.string + ". tel " + a.fret + " → " + b.string + ". tel " + b.fret, Theory.IVAL_NAME[d], w, r);
        if (e == null) return null;
        Ex.Note c1 = new Ex.Note(a.string, a.fret, 0, 1), c2 = new Ex.Note(b.string, b.fret, 1, 1);
        c1.midi = a.midi; c2.midi = b.midi;
        e.notes.add(c1); e.notes.add(c2); e.showTab = true;
        e.audio = new float[]{a.midi, b.midi};
        e.explain = Theory.SHARP[Theory.pc(a.midi)] + " → " + Theory.SHARP[Theory.pc(b.midi)] + ": " + d + " yarım ses = " + Theory.IVAL_NAME[d] + ".";
        return e;
    }

    /** Süre ağırlıklı ses sınıfı dağılımı. */
    static float[] weights(List<Ex.Note> notes) {
        float[] w = new float[12];
        for (Ex.Note n : notes) for (int m : ScoreView.midis(n)) w[Theory.pc(m)] += Math.max(0.1f, Math.min(n.dur, 2));
        return w;
    }

    static Ex center(List<Ex.Note> notes, Random r) {
        float[] w = weights(notes);
        int best = 0;
        for (int i = 1; i < 12; i++) if (w[i] > w[best]) best = i;
        float second = 0;
        for (int i = 0; i < 12; i++) if (i != best) second = Math.max(second, w[i]);
        if (w[best] < second * 1.15f) return null; // belirgin değilse sorma
        List<String> wr = new ArrayList<>();
        for (int i = 0; i < 12; i++) if (i != best && w[i] > 0) wr.add(label(i));
        for (int d : new int[]{5, 7, 2}) wr.add(label((best + d) % 12));
        Ex e = choice("Bu bölümde en çok (en uzun süre) duyulan nota hangisi? Genelde tonun merkezi odur.", label(best), wr, r);
        if (e == null) return null;
        e.explain = "Sürelerine göre toplayınca en ağır nota " + Theory.nameWithSolfej(best) + ". Melodiler çoğunlukla bu notaya \"döner\".";
        return e;
    }

    static final String[][] SCALES = {
            {"minör pentatonik", "0,3,5,7,10"}, {"majör pentatonik", "0,2,4,7,9"}, {"blues gamı", "0,3,5,6,7,10"},
            {"majör gam", "0,2,4,5,7,9,11"}, {"doğal minör", "0,2,3,5,7,8,10"}, {"armonik minör", "0,2,3,5,7,8,11"},
            {"Dorian", "0,2,3,5,7,9,10"}, {"Frig", "0,1,3,5,7,8,10"}, {"Mixolidyen", "0,2,4,5,7,9,10"}, {"Frig dominant (Hicaz)", "0,1,4,5,7,8,10"},
    };

    static int[] pcs(String s) { String[] a = s.split(","); int[] r = new int[a.length]; for (int i = 0; i < a.length; i++) r[i] = Integer.parseInt(a[i]); return r; }

    static boolean contains(int root, int[] iv, boolean[] used) {
        boolean[] in = new boolean[12];
        for (int x : iv) in[(root + x) % 12] = true;
        for (int i = 0; i < 12; i++) if (used[i] && !in[i]) return false;
        return true;
    }

    static Ex scale(List<Ex.Note> notes, Random r) {
        float[] w = weights(notes);
        boolean[] used = new boolean[12];
        int n = 0, best = 0;
        for (int i = 0; i < 12; i++) { if (w[i] > 0) { used[i] = true; n++; } if (w[i] > w[best]) best = i; }
        if (n < 4) return null;
        // kökü en ağır nota olan ve tüm sesleri içeren en küçük gam
        String found = null;
        for (String[] sc : SCALES) if (contains(best, pcs(sc[1]), used)) { found = sc[0]; break; }
        if (found == null) return null;
        String correct = ChordDict.ROOT[best] + " " + found;
        List<String> wr = new ArrayList<>();
        for (String[] sc : SCALES) for (int root = 0; root < 12; root++) {
            String nm = ChordDict.ROOT[root] + " " + sc[0];
            if (!contains(root, pcs(sc[1]), used) && (root == best || sc[0].equals(found))) wr.add(nm);
        }
        Ex e = choice("Bu bölümün bütün notaları hangi gamın içinde (merkez " + ChordDict.ROOT[best] + " alınırsa)?", correct, wr, r);
        if (e == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) if (used[i]) { if (sb.length() > 0) sb.append(' '); sb.append(Theory.SHARP[i]); }
        e.explain = "Bölümdeki sesler: " + sb + ". Bunların hepsi " + correct + " içinde; diğer şıklarda en az bir nota gamın dışında kalıyor.";
        return e;
    }

    static final String[][] VALS = {{"4", "birlik (4 vuruş)"}, {"2", "ikilik (2 vuruş)"}, {"1.5", "noktalı dörtlük (1½ vuruş)"}, {"1", "dörtlük (1 vuruş)"},
            {"0.75", "noktalı sekizlik (¾ vuruş)"}, {"0.5", "sekizlik (½ vuruş)"}, {"0.25", "onaltılık (¼ vuruş)"}, {"0.333", "triole sekizlik (⅓ vuruş)"}};

    static Ex rhythm(TabImport.Result r, List<Ex.Note> notes, Random rnd) {
        List<Ex.Note> cand = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Ex.Note n : notes) for (String[] v : VALS) if (Math.abs(n.dur - Float.parseFloat(v[0])) < 0.03f) { cand.add(n); names.add(v[1]); break; }
        if (cand.isEmpty()) return null;
        int i = rnd.nextInt(cand.size());
        Ex.Note n = cand.get(i);
        List<String> w = new ArrayList<>();
        for (String[] v : VALS) w.add(v[1]);
        String what = n.chord != null ? n.chord + " akoru" : n.string + ". tel " + n.fret + ". perde";
        float inBar = n.beat;
        int bar = 0;
        // bölümün kendi ölçülerine göre
        while (inBar >= 4 - 1e-3f) { inBar -= 4; bar++; }
        Ex e = choice("Bölümün " + (bar + 1) + ". ölçüsünde " + beatName(inBar) + " gelen ses (" + what + ") ne kadar sürüyor?", names.get(i), w, rnd);
        if (e == null) return null;
        e.explain = "Bir sonraki sese kadar geçen süre " + trim(n.dur) + " vuruş.";
        return e;
    }

    static String beatName(float b) {
        int whole = (int) Math.floor(b + 1e-3f);
        float frac = b - whole;
        String base = (whole + 1) + ". vuruşta";
        if (frac < 0.05f) return base;
        if (Math.abs(frac - 0.5f) < 0.05f) return (whole + 1) + ". vuruşun \"ve\"sinde";
        if (Math.abs(frac - 0.25f) < 0.05f) return (whole + 1) + ". vuruşun \"e\"sinde";
        if (Math.abs(frac - 0.75f) < 0.05f) return (whole + 1) + ". vuruşun \"a\"sında";
        return (whole + 1) + ". vuruştan sonra";
    }

    static String trim(float f) { return Math.abs(f - Math.round(f)) < 0.01f ? String.valueOf(Math.round(f)) : String.format(java.util.Locale.ROOT, "%.2f", f); }

    static Ex tuning(TabImport.Result r, Random rnd) {
        String c = TabImport.tuningName(r.tuning);
        List<String> w = new ArrayList<>(Arrays.asList("Standart (E A D G B E)", "1 yarım ses pes (Eb Ab C# F# Bb Eb)", "Drop D (D A D G B E)", "2 yarım ses pes (D G C F A D)", "Drop C# (C# G# C# F# Bb Eb)"));
        String cs = c.startsWith("1 yarım ses pes") ? "1 yarım ses pes (Eb Ab C# F# Bb Eb)" : c.startsWith("2 yarım") ? "2 yarım ses pes (D G C F A D)" : c;
        Ex e = choice("Bu şarkı hangi akortta çalınıyor?", cs, w, rnd);
        if (e == null) return null;
        e.explain = "Tab dosyasındaki akort: " + c + ". Gitarını buna göre ayarlamazsan doğru perdeyi bassan bile ses farklı çıkar.";
        return e;
    }

    // ------------------------------------------------------------------ ÇALARAK ÖĞREN (Şarkı Yolu)

    static Ex.Note copyAt(Ex.Note n, float beat) {
        Ex.Note c = new Ex.Note(n.string, n.fret, beat, 1);
        c.midi = n.midi; c.alts = n.alts; c.chord = n.chord; c.chordPcs = n.chordPcs; c.power = n.power;
        c.strs = n.strs; c.frets = n.frets;
        return c;
    }

    static Ex playEx(String title, String prompt, String explain) {
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.title = title; e.prompt = prompt; e.explain = explain;
        e.showTab = true;
        return e;
    }

    /**
     * Şarkı Yolu için konuyu ÇALARAK öğreten görevler: soru yok, her görevde gitar çalınır.
     * Konu (nota adı, akor, aralık, gam, ritim, akort) şarkının bu bölümündeki gerçek seslerle çalıştırılır.
     */
    public static List<Ex> play(TabImport.Result r, List<Ex.Note> notes, String unit, int count, Random rnd) {
        List<Ex> out = new ArrayList<>();
        Kind[] ks = kindsFor(unit);
        Set<String> seen = new LinkedHashSet<>();
        for (int guard = 0; out.size() < count && guard < count * 10; guard++) {
            Kind k = guard < ks.length ? ks[guard] : ks[rnd.nextInt(ks.length)];
            Ex e = null;
            switch (k) {
                case NOTE: e = playNote(notes, rnd, false); break;
                case STAFF: e = playNote(notes, rnd, true); break;
                case CHORD: e = playChord(notes, rnd); break;
                case INTERVAL: e = playInterval(notes, rnd); break;
                case CENTER: case SCALE: e = playScale(notes); break;
                case RHYTHM: e = null; break; // ritim: bölümün metronomla çalınması zaten ritim görevi
                case TUNING: e = playTuning(r); break;
            }
            if (e == null) continue;
            if (!seen.add(e.prompt)) continue;
            out.add(e);
        }
        if (out.isEmpty()) { Ex e = playNote(notes, rnd, false); if (e != null) out.add(e); }
        return out;
    }

    static Ex playNote(List<Ex.Note> notes, Random r, boolean staff) {
        List<Ex.Note> s = singles(notes);
        if (s.isEmpty()) return null;
        Ex.Note n = s.get(r.nextInt(s.size()));
        int pc = Theory.pc(n.midi);
        String name = Theory.nameWithSolfej(pc);
        Ex e = playEx("Şarkının notası", staff
                ? "Porteye yazılmış bu nota şarkıdan: " + name + ". Gitarda bul ve çal."
                : "Şarkının bu bölümünde " + name + " notası var: " + Theory.STRING_NAME[n.string] + ", " + n.fret + ". perde. Çal.",
                Theory.STRING_NAME[n.string] + " teli, " + n.fret + ". perde = " + name + ".");
        e.notes.add(copyAt(n, 0));
        if (staff) { e.staffMidis = new int[]{n.midi + 12}; e.showTab = false; }
        return e;
    }

    static Ex playChord(List<Ex.Note> notes, Random r) {
        List<Ex.Note> ch = new ArrayList<>();
        for (Ex.Note n : notes) if (n.chord != null && n.strs != null && n.chordPcs != null) ch.add(n);
        if (ch.isEmpty()) return null;
        Ex.Note n = ch.get(r.nextInt(ch.size()));
        int[] all = ScoreView.midis(n).clone();
        Arrays.sort(all);
        StringBuilder names = new StringBuilder();
        for (int x : all) { if (names.length() > 0) names.append(", "); names.append(Theory.SHARP[Theory.pc(x)]); }
        Ex e = playEx("Şarkının akoru", "Bölümdeki " + n.chord + " akorunu bas (kalından inceye " + voicing(n) + ") ve bir kez vur. İçindeki sesler: " + names + ".",
                n.chord + " = " + names + ". Basmadığın teller çalmasın.");
        e.notes.add(copyAt(n, 0));
        return e;
    }

    static Ex playInterval(List<Ex.Note> notes, Random r) {
        List<Ex.Note> s = singles(notes);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i + 1 < s.size(); i++) {
            int d = Math.abs(s.get(i + 1).midi - s.get(i).midi);
            if (d >= 1 && d <= 12) idx.add(i);
        }
        if (idx.isEmpty()) return null;
        int i = idx.get(r.nextInt(idx.size()));
        Ex.Note a = s.get(i), b = s.get(i + 1);
        int d = Math.abs(b.midi - a.midi);
        Ex e = playEx("Şarkıdaki aralık", "Şarkıda art arda gelen iki ses: " + Theory.SHARP[Theory.pc(a.midi)] + " → " + Theory.SHARP[Theory.pc(b.midi)]
                + ". Aralarında " + d + " yarım ses var = " + Theory.IVAL_NAME[d] + ". İkisini sırayla çal ve aralığı kulağınla duy.",
                d + " yarım ses = " + Theory.IVAL_NAME[d] + ".");
        e.notes.add(copyAt(a, 0)); e.notes.add(copyAt(b, 1));
        e.audio = new float[]{a.midi, b.midi};
        return e;
    }

    static Ex playScale(List<Ex.Note> notes) {
        // bölümün kullandığı sesler, kalından inceye, her ses bir kez
        List<Ex.Note> s = singles(notes);
        java.util.TreeMap<Integer, Ex.Note> by = new java.util.TreeMap<>();
        for (Ex.Note n : s) if (!by.containsKey(n.midi)) by.put(n.midi, n);
        if (by.size() < 3) return null;
        float[] w = weights(notes);
        int best = 0;
        for (int i = 1; i < 12; i++) if (w[i] > w[best]) best = i;
        boolean[] used = new boolean[12];
        for (int m : by.keySet()) used[Theory.pc(m)] = true;
        String found = null;
        for (String[] sc : SCALES) if (contains(best, pcs(sc[1]), used)) { found = ChordDict.ROOT[best] + " " + sc[0]; break; }
        StringBuilder sb = new StringBuilder();
        for (int m : by.keySet()) { if (sb.length() > 0) sb.append(' '); sb.append(Theory.SHARP[Theory.pc(m)]); }
        Ex e = playEx("Bölümün gamı", "Bu bölüm şu sesleri kullanıyor: " + sb + (found != null ? " — hepsi " + found + " içinde" : "")
                + ". Merkez nota " + Theory.SHARP[best] + ". Kalından inceye sırayla çal.",
                found != null ? "Bölümün sesleri " + found + " gamından." : "Bölümün sesleri: " + sb + ".");
        float b = 0;
        int k = 0;
        for (Ex.Note n : by.values()) { if (k++ >= 8) break; e.notes.add(copyAt(n, b)); b += 1; }
        return e;
    }

    static Ex playTuning(TabImport.Result r) {
        Ex e = new Ex();
        e.type = Ex.Type.TUNE;
        e.title = "Şarkının akordu";
        e.prompt = "Bu şarkı " + TabImport.tuningName(r.tuning) + " akortta. Telleri akort et.";
        return e;
    }
}
