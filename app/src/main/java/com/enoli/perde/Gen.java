package com.enoli.perde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Müfredat satırlarını (spec) alıştırmalara çevirir. Her çağrıda yeni rastgele örnek üretir,
 * böylece aralıklı tekrarda aynı kart farklı sorularla gelir.
 */
public final class Gen {
    private Gen() {}

    public static List<Ex> make(String spec, Random r) {
        List<Ex> out = new ArrayList<>();
        try {
            if (spec.startsWith("q:")) { out.add(staticQ(spec)); }
            else if (spec.startsWith("g:")) {
                Map<String, String> p = params(spec.substring(2).trim());
                String name = p.get("_");
                int n = Integer.parseInt(p.getOrDefault("n", "1"));
                for (int i = 0; i < n; i++) {
                    Ex e = one(name, p, r);
                    if (e != null) out.add(e);
                }
            }
        } catch (Exception ex) {
            Ex e = new Ex();
            e.type = Ex.Type.INFO;
            e.title = "Hata";
            e.body = "Alıştırma üretilemedi: " + spec + "\n" + ex;
            out.add(e);
        }
        for (Ex e : out) if (e.key == null) e.key = spec;
        return out;
    }

    // ---------- Ayrıştırma ----------
    static Map<String, String> params(String s) {
        Map<String, String> m = new HashMap<>();
        int i = 0; boolean first = true;
        while (i < s.length()) {
            while (i < s.length() && s.charAt(i) == ' ') i++;
            if (i >= s.length()) break;
            int eq = -1, j = i;
            while (j < s.length() && s.charAt(j) != ' ') { if (s.charAt(j) == '=' && eq < 0) eq = j; if (s.charAt(j) == '"' ) break; j++; }
            if (eq < 0) {
                int e = s.indexOf(' ', i); if (e < 0) e = s.length();
                if (first) m.put("_", s.substring(i, e));
                i = e; first = false; continue;
            }
            String k = s.substring(i, eq);
            int v0 = eq + 1;
            String v;
            if (v0 < s.length() && s.charAt(v0) == '"') {
                int e = s.indexOf('"', v0 + 1);
                v = s.substring(v0 + 1, e); i = e + 1;
            } else {
                int e = s.indexOf(' ', v0); if (e < 0) e = s.length();
                v = s.substring(v0, e); i = e;
            }
            m.put(k, v); first = false;
        }
        return m;
    }

    static int[] range(String s) {
        if (s.contains("-")) { String[] a = s.split("-"); return new int[]{Integer.parseInt(a[0]), Integer.parseInt(a[1])}; }
        int v = Integer.parseInt(s); return new int[]{v, v};
    }

    static int[] ints(String s) {
        if (s.contains("-") && !s.contains(",")) {
            int[] rg = range(s);
            int lo = Math.min(rg[0], rg[1]), hi = Math.max(rg[0], rg[1]);
            int[] r = new int[hi - lo + 1];
            for (int i = 0; i < r.length; i++) r[i] = rg[0] <= rg[1] ? rg[0] + i : rg[0] - i;
            return r;
        }
        String[] a = s.split(",");
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) r[i] = Integer.parseInt(a[i].trim());
        return r;
    }

    static <T> T pick(List<T> l, Random r) { return l.get(r.nextInt(l.size())); }
    static String pick(String[] a, Random r) { return a[r.nextInt(a.length)]; }
    static int pick(int[] a, Random r) { return a[r.nextInt(a.length)]; }

    static Ex choice(String prompt, String correct, List<String> pool, Random r) {
        Ex e = new Ex();
        e.type = Ex.Type.CHOICE;
        e.prompt = prompt;
        Set<String> opts = new LinkedHashSet<>();
        opts.add(correct);
        List<String> pp = new ArrayList<>(pool);
        Collections.shuffle(pp, r);
        for (String s : pp) { if (opts.size() >= 4) break; if (!s.equals(correct)) opts.add(s); }
        e.options = new ArrayList<>(opts);
        Collections.shuffle(e.options, r);
        e.correct = e.options.indexOf(correct);
        return e;
    }

    static Ex staticQ(String spec) {
        String body = spec.substring(2).trim();
        String explain = "";
        int ix = body.indexOf(" // ");
        if (ix >= 0) { explain = body.substring(ix + 4).trim(); body = body.substring(0, ix); }
        String[] parts = body.split("\\|");
        List<String> wrong = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) wrong.add(parts[i].trim());
        Ex e = choice(parts[0].trim(), parts[1].trim(), wrong, new Random());
        e.explain = explain;
        return e;
    }

    static String pcLabel(int pc) {
        String s = Theory.SHARP[pc], f = Theory.FLAT[pc];
        return s.equals(f) ? s : s + "/" + f;
    }

    static List<String> allPcLabels() { List<String> l = new ArrayList<>(); for (int i = 0; i < 12; i++) l.add(pcLabel(i)); return l; }

    // ---------- Üreticiler ----------
    static Ex one(String name, Map<String, String> p, Random r) {
        switch (name) {
            case "alpha": return alpha(r);
            case "semi": return semi(p, r);
            case "fretname": return fretName(p, r);
            case "fretfind": return fretFind(p, r);
            case "staffname": return staffName(p, r);
            case "staffplay": return staffPlay(p, r);
            case "ival": return ival(p, r);
            case "ivalear": return ivalEar(p, r);
            case "ivalplay": return ivalPlay(p, r);
            case "scalespell": return scaleSpell(p, r);
            case "degree": return degree(p, r);
            case "chordspell": return chordSpell(p, r);
            case "chordear": return chordEar(p, r);
            case "chordid": return chordId(p, r);
            case "diatonic": return diatonic(p, r);
            case "keysig": return keySig(r);
            case "relative": return relative(r);
            case "mode": return mode(p, r);
            case "rhythmval": return rhythmVal(r);
            case "progear": return progEar(p, r);
            case "open": return open(p);
            case "playnote": return playNote(p, r);
            case "seq": return seq(p);
            case "spider": return spider(p);
            case "scale": return scale(p);
            case "arp": return arp(p);
            case "chord": return chord(p, r);
            case "changes": return changes(p);
            case "strum": return strum(p);
            case "improv": return improv(p);
            case "earback": return earBack(p, r);
            case "bend": return bend(p);
            case "octave": return octave(r);
            case "calibrate": { Ex e = new Ex(); e.type = Ex.Type.CALIBRATE; e.title = "Gecikme ayarı"; return e; }
            case "tune": { Ex e = new Ex(); e.type = Ex.Type.TUNE; e.title = "Akort"; e.prompt = "Altı teli de akort et"; return e; }
            default: return null;
        }
    }

    static Ex alpha(Random r) {
        int t = r.nextInt(3), i = r.nextInt(7);
        List<String> letters = new ArrayList<>(); Collections.addAll(letters, Theory.LETTERS);
        List<String> solf = new ArrayList<>(); Collections.addAll(solf, Theory.SOLFEJ);
        if (t == 0) return choice(Theory.LETTERS[i] + " notasından sonra gelen doğal nota (diyezsiz/bemolsüz) hangisi?", Theory.LETTERS[(i + 1) % 7], letters, r);
        if (t == 1) return choice(Theory.LETTERS[i] + " notasından önce gelen doğal nota (diyezsiz/bemolsüz) hangisi?", Theory.LETTERS[(i + 6) % 7], letters, r);
        if (r.nextBoolean()) return choice("\"" + Theory.SOLFEJ[i] + "\" hangi harfle yazılır?", Theory.LETTERS[i], letters, r);
        return choice(Theory.LETTERS[i] + " harfinin solfej adı nedir?", Theory.SOLFEJ[i], solf, r);
    }

    static Ex semi(Map<String, String> p, Random r) {
        int max = Integer.parseInt(p.getOrDefault("max", "2"));
        int root = Theory.LETTER_PC[r.nextInt(7)];
        int d = 1 + r.nextInt(max);
        boolean down = p.containsKey("down") && r.nextBoolean();
        int ans = Theory.pc(root + (down ? -d : d));
        return choice(Theory.SHARP[root] + " notasının " + d + " yarım ses (" + d + " perde) " + (down ? "aşağısı" : "yukarısı") + " hangi nota?",
                pcLabel(ans), allPcLabels(), r);
    }

    static int[] randPos(Map<String, String> p, Random r) {
        int[] ss = ints(p.getOrDefault("s", "1-6"));
        int[] fr = range(p.getOrDefault("f", "0-5"));
        int s = pick(ss, r);
        int f = fr[0] + r.nextInt(fr[1] - fr[0] + 1);
        return new int[]{s, f};
    }

    static Ex fretName(Map<String, String> p, Random r) {
        int[] pos = randPos(p, r);
        int pc = Theory.pc(Theory.OPEN[pos[0]] + pos[1]);
        List<String> pool = new ArrayList<>();
        for (int d = -3; d <= 3; d++) if (d != 0) pool.add(pcLabel(Theory.pc(pc + d)));
        boolean tab = "tab".equals(p.get("view"));
        Ex e = choice(tab ? "Bu tab hangi notayı gösteriyor?" : "İşaretli perdedeki nota hangisi?", pcLabel(pc), pool, r);
        if (tab) {
            e.notes.add(new Ex.Note(pos[0], pos[1], 0, 1));
            e.showTab = true;
        } else {
            e.fretDots.add(new int[]{pos[0], pos[1]});
            int[] fr = range(p.getOrDefault("f", "0-5"));
            e.fretFrom = 0; e.fretTo = Math.max(5, fr[1]);
        }
        e.explain = Theory.STRING_NAME[pos[0]] + ", " + pos[1] + ". perde = " + Theory.nameWithSolfej(pc);
        return e;
    }

    static Ex fretFind(Map<String, String> p, Random r) {
        int[] pos = randPos(p, r);
        int pc = Theory.pc(Theory.OPEN[pos[0]] + pos[1]);
        int[] fr = range(p.getOrDefault("f", "0-12"));
        Ex e = new Ex();
        e.type = Ex.Type.FRET_TAP;
        e.prompt = Theory.STRING_NAME[pos[0]] + " üzerinde " + pcLabel(pc) + " notasına dokun";
        e.tapString = pos[0]; e.tapPc = pc;
        e.fretFrom = 0; e.fretTo = Math.max(fr[1], 5);
        return e;
    }

    static final int[] NATURAL_MIDI_OK = {0, 2, 4, 5, 7, 9, 11};

    static int randNatural(int lo, int hi, boolean acc, Random r) {
        for (int k = 0; k < 100; k++) {
            int m = lo + r.nextInt(hi - lo + 1);
            if (acc) return m;
            int pc = Theory.pc(m);
            for (int v : NATURAL_MIDI_OK) if (v == pc) return m;
        }
        return 60;
    }

    static Ex staffName(Map<String, String> p, Random r) {
        int lo = Integer.parseInt(p.getOrDefault("lo", "64")), hi = Integer.parseInt(p.getOrDefault("hi", "77"));
        int m = randNatural(lo, hi, "1".equals(p.get("acc")), r);
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < 7; i++) pool.add(Theory.LETTERS[i] + " (" + Theory.SOLFEJ[i] + ")");
        String correct;
        if (p.containsKey("acc") && Theory.SHARP[Theory.pc(m)].length() > 1) { pool = allPcLabels(); correct = pcLabel(Theory.pc(m)); }
        else { int li = "CDEFGAB".indexOf(Theory.SHARP[Theory.pc(m)].charAt(0)); correct = Theory.LETTERS[li] + " (" + Theory.SOLFEJ[li] + ")"; }
        Ex e = choice("Portedeki nota hangisi?", correct, pool, r);
        e.staffMidis = new int[]{m};
        return e;
    }

    static Ex staffPlay(Map<String, String> p, Random r) {
        int lo = Integer.parseInt(p.getOrDefault("lo", "64")), hi = Integer.parseInt(p.getOrDefault("hi", "77"));
        int n = Integer.parseInt(p.getOrDefault("len", "1"));
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.prompt = "Portedeki " + (n > 1 ? "notaları sırayla" : "notayı") + " gitarda çal (gitar yazılandan bir oktav pes duyulur)";
        e.staffMidis = new int[n];
        for (int i = 0; i < n; i++) {
            int m = randNatural(lo, hi, "1".equals(p.get("acc")), r);
            e.staffMidis[i] = m;
            e.notes.add(Ex.Note.ofMidi(m - 12, i, 1));
        }
        e.strictOctave = true;
        e.showTab = false;
        return e;
    }

    static final int[] IVAL_DEG = {0, 1, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6, 0};

    static Ex ival(Map<String, String> p, Random r) {
        String[] set = p.getOrDefault("set", "m2,M2,m3,M3,P4,P5").split(",");
        int iv = Theory.ivalCode(pick(set, r));
        int li = r.nextInt(7);
        int rootPc = Theory.LETTER_PC[li];
        String rootName = Theory.LETTERS[li];
        String target = Theory.spellLetter((li + IVAL_DEG[iv]) % 7, Theory.pc(rootPc + iv));
        if (r.nextBoolean()) {
            List<String> pool = new ArrayList<>();
            for (int d = -2; d <= 2; d++) if (d != 0) pool.add(Theory.spellLetter((li + IVAL_DEG[iv]) % 7, Theory.pc(rootPc + iv + d)));
            for (int d = -1; d <= 1; d += 2) pool.add(Theory.spellLetter((li + IVAL_DEG[iv] + d + 7) % 7, Theory.pc(rootPc + iv)));
            Ex e = choice(rootName + " notasının " + Theory.IVAL_NAME[iv].toLowerCase() + " yukarısı hangi nota?", target, pool, r);
            e.explain = rootName + " → " + target + " = " + iv + " yarım ses";
            return e;
        }
        List<String> pool = new ArrayList<>();
        for (String c : set) pool.add(Theory.IVAL_NAME[Theory.ivalCode(c)]);
        for (int d = -1; d <= 1; d += 2) { int k = iv + d; if (k > 0 && k < 13) pool.add(Theory.IVAL_NAME[k]); }
        Ex e = choice(rootName + " ile yukarıdaki " + target + " arasındaki aralık nedir?", Theory.IVAL_NAME[iv], pool, r);
        e.explain = iv + " yarım ses = " + Theory.IVAL_NAME[iv];
        return e;
    }

    static Ex ivalEar(Map<String, String> p, Random r) {
        String[] set = p.getOrDefault("set", "M3,P5,P8").split(",");
        int iv = Theory.ivalCode(pick(set, r));
        int root = 45 + r.nextInt(14);
        boolean harm = "harm".equals(p.get("mode")) || ("mix".equals(p.get("mode")) && r.nextBoolean());
        boolean down = "down".equals(p.get("dir")) || ("both".equals(p.get("dir")) && r.nextBoolean());
        List<String> pool = new ArrayList<>();
        for (String c : set) pool.add(Theory.IVAL_NAME[Theory.ivalCode(c)]);
        Ex e = choice("Dinle: iki nota arasındaki aralık ne?", Theory.IVAL_NAME[iv], pool, r);
        e.audio = down ? new float[]{root + iv, root} : new float[]{root, root + iv};
        e.audioHarmonic = harm;
        e.explain = "İpucu: aralığı bildiğin bir melodinin ilk iki notasıyla eşleştir.";
        return e;
    }

    static Ex ivalPlay(Map<String, String> p, Random r) {
        String[] set = p.getOrDefault("set", "M3,P5,P8").split(",");
        int iv = Theory.ivalCode(pick(set, r));
        int[] pos = randPos(p.containsKey("s") ? p : params("x s=6,5 f=1-7"), r);
        int root = Theory.OPEN[pos[0]] + pos[1];
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.prompt = "Önce işaretli kökü (" + Theory.SHARP[Theory.pc(root)] + "), sonra onun " + Theory.IVAL_NAME[iv].toLowerCase() + " yukarısını çal";
        e.notes.add(new Ex.Note(pos[0], pos[1], 0, 1));
        Ex.Note second = Ex.Note.ofMidi(root + iv, 1, 1);
        second.label = "?";
        e.notes.add(second);
        e.fretDots.add(pos);
        e.fretTo = 12;
        e.strictOctave = true;
        e.showTab = false;
        return e;
    }

    static final String[] DEF_KEYS = {"C", "G", "D", "A", "E", "F", "Bb", "Eb"};

    static Ex scaleSpell(Map<String, String> p, Random r) {
        String[] keys = p.getOrDefault("keys", "C,G,D,F").split(",");
        String[] types = p.getOrDefault("types", "major").split(",");
        String k = pick(keys, r), t = pick(types, r);
        String[] notes = Theory.spellScale(k, t);
        String label = k + " " + Theory.SCALE_TR.get(t);
        if (r.nextBoolean() && notes.length >= 5) {
            int d = r.nextInt(notes.length);
            List<String> pool = allPcLabels();
            pool.clear();
            for (int dd = -2; dd <= 2; dd++) if (dd != 0) pool.add(Theory.spellLetter(("CDEFGAB".indexOf(notes[d].charAt(0)) + 7) % 7, Theory.parsePc(notes[d]) + dd));
            Ex e = choice(label + " gamının " + (d + 1) + ". notası hangisi?", notes[d], pool, r);
            e.explain = label + ": " + String.join(" ", notes);
            return e;
        }
        List<String> pool = new ArrayList<>();
        for (String kk : DEF_KEYS) pool.add(kk + " " + Theory.SCALE_TR.get(t));
        for (String kk : keys) pool.add(kk + " " + Theory.SCALE_TR.get(t));
        Ex e = choice("Bu notalar hangi gamı oluşturur?\n" + String.join("  ", notes), label, pool, r);
        return e;
    }

    static Ex degree(Map<String, String> p, Random r) {
        String k = pick(p.getOrDefault("keys", "C,G,D,F").split(","), r);
        String[] sc = Theory.spellScale(k, "major");
        int d = r.nextInt(7);
        List<String> pool = new ArrayList<>();
        if (r.nextBoolean()) {
            Collections.addAll(pool, sc);
            return choice(k + " majörde " + Theory.DEGREE_TR[d].toLowerCase() + " (" + (d + 1) + ". derece) hangi nota?", sc[d], pool, r);
        }
        Collections.addAll(pool, Theory.DEGREE_TR);
        return choice(k + " majörde " + sc[d] + " notası hangi derecedir?", Theory.DEGREE_TR[d], pool, r);
    }

    static Ex chordSpell(Map<String, String> p, Random r) {
        String[] types = p.getOrDefault("types", ",m").split(",", -1);
        String[] roots = p.getOrDefault("roots", "C,D,E,F,G,A,B,Bb,Eb").split(",");
        String root = pick(roots, r), t = pick(types, r);
        Theory.ChordType ct = Theory.CT.get(t);
        String sp = Theory.spellChord(root, ct);
        String nm = root + t;
        if (r.nextBoolean()) {
            List<String> pool = new ArrayList<>();
            for (Theory.ChordType o : Theory.CHORD_TYPES) if (o != ct && o.iv.length == ct.iv.length) pool.add(Theory.spellChord(root, o));
            for (String rr : roots) if (!rr.equals(root)) pool.add(Theory.spellChord(rr, ct));
            Ex e = choice(nm + " akorunun notaları hangileri?", sp, pool, r);
            e.explain = nm + " = " + ct.tr + ": " + ivText(ct.iv);
            return e;
        }
        List<String> pool = new ArrayList<>();
        for (String tt : types) pool.add(root + tt);
        for (Theory.ChordType o : Theory.CHORD_TYPES) if (o.iv.length == ct.iv.length) pool.add(root + o.sym);
        Ex e = choice("Bu notalar hangi akoru oluşturur?\n" + sp, nm, pool, r);
        e.explain = nm + " = " + ct.tr + ": " + ivText(ct.iv);
        return e;
    }

    static String ivText(int[] iv) {
        StringBuilder sb = new StringBuilder();
        String[] deg = {"1", "b2", "2", "b3", "3", "4", "b5", "5", "#5", "6", "b7", "7", "8", "b9", "9", "#9", "10", "11", "#11", "12", "b13", "13"};
        for (int v : iv) { if (sb.length() > 0) sb.append("-"); sb.append(v < deg.length ? deg[v] : String.valueOf(v)); }
        return sb.toString();
    }

    static Ex chordEar(Map<String, String> p, Random r) {
        String[] types = p.getOrDefault("types", ",m").split(",", -1);
        String t = pick(types, r);
        Theory.ChordType ct = Theory.CT.get(t);
        int root = 48 + r.nextInt(8);
        List<String> pool = new ArrayList<>();
        for (String tt : types) pool.add(Theory.CT.get(tt).tr);
        Ex e = choice("Dinle: bu akorun türü ne?", ct.tr, pool, r);
        e.audio = new float[ct.iv.length];
        for (int i = 0; i < ct.iv.length; i++) e.audio[i] = root + ct.iv[i];
        e.audioHarmonic = true;
        e.explain = "Majör parlak/mutlu, minör hüzünlü; dim gergin, aug havada asılı; 7'li akorlar çözülmek ister.";
        return e;
    }

    static Ex chordId(Map<String, String> p, Random r) {
        String[] set = p.getOrDefault("set", "Em,Am,E,A,D,G,C").split(",");
        String c = pick(set, r);
        List<String> pool = new ArrayList<>(); Collections.addAll(pool, set);
        Ex e = choice("Bu akor diyagramı hangi akoru gösteriyor?", c, pool, r);
        e.chordShape = Theory.shapeFor(c);
        return e;
    }

    static Ex diatonic(Map<String, String> p, Random r) {
        String k = pick(p.getOrDefault("keys", "C,G,D,A,E,F").split(","), r);
        int d = r.nextInt(7);
        String ch = Theory.diatonicChord(k, d);
        List<String> pool = new ArrayList<>();
        if (r.nextBoolean()) {
            for (int i = 0; i < 7; i++) pool.add(Theory.diatonicChord(k, i));
            String[] sc = Theory.spellScale(k, "major");
            pool.add(sc[d] + (ch.endsWith("m") ? "" : "m"));
            Ex e = choice(k + " majörde " + Theory.ROMAN[d] + " akoru hangisi?", ch, pool, r);
            e.explain = k + " majör: " + diatonicRow(k);
            return e;
        }
        Collections.addAll(pool, Theory.ROMAN);
        Ex e = choice(k + " majör tonunda " + ch + " akoru hangi derecedir?", Theory.ROMAN[d], pool, r);
        e.explain = k + " majör: " + diatonicRow(k);
        return e;
    }

    static String diatonicRow(String k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) sb.append(Theory.ROMAN[i]).append("=").append(Theory.diatonicChord(k, i)).append("  ");
        return sb.toString().trim();
    }

    static Ex keySig(Random r) {
        int i = r.nextInt(Theory.MAJOR_KEYS.length);
        boolean minor = r.nextInt(3) == 0;
        String key = minor ? Theory.MINOR_KEYS[i] + " minör" : Theory.MAJOR_KEYS[i] + " majör";
        if (r.nextBoolean()) {
            List<String> pool = new ArrayList<>();
            for (int s = -7; s <= 7; s++) pool.add(Theory.sigText(s));
            List<String> near = new ArrayList<>();
            for (int d = -2; d <= 2; d++) { int s = Theory.MAJOR_SIG[i] + d; if (d != 0 && s >= -7 && s <= 7) near.add(Theory.sigText(s)); }
            near.add(Theory.sigText(-Theory.MAJOR_SIG[i]));
            return choice(key + " tonalitesinin donanımında kaç işaret var?", Theory.sigText(Theory.MAJOR_SIG[i]), near, r);
        }
        List<String> pool = new ArrayList<>();
        for (int j = 0; j < Theory.MAJOR_KEYS.length; j++) pool.add(minor ? Theory.MINOR_KEYS[j] + " minör" : Theory.MAJOR_KEYS[j] + " majör");
        return choice("Donanımı " + Theory.sigText(Theory.MAJOR_SIG[i]) + " olan " + (minor ? "minör" : "majör") + " tonalite hangisi?", key, pool, r);
    }

    static Ex relative(Random r) {
        int i = r.nextInt(12);
        List<String> pool = new ArrayList<>();
        if (r.nextBoolean()) {
            for (String s : Theory.MINOR_KEYS) pool.add(s + " minör");
            Ex e = choice(Theory.MAJOR_KEYS[i] + " majörün ilgili (relative) minörü hangisi?", Theory.MINOR_KEYS[i] + " minör", pool, r);
            e.explain = "İlgili minör, majörün 6. derecesinden başlar (3 yarım ses aşağı).";
            return e;
        }
        for (String s : Theory.MAJOR_KEYS) pool.add(s + " majör");
        Ex e = choice(Theory.MINOR_KEYS[i] + " minörün ilgili majörü hangisi?", Theory.MAJOR_KEYS[i] + " majör", pool, r);
        e.explain = "İlgili majör, minörün 3 yarım ses yukarısıdır.";
        return e;
    }

    static final String[] MODES = {"ionian", "dorian", "phrygian", "lydian", "mixolydian", "aeolian", "locrian"};
    static final String[] MODE_CHAR = {"doğal 4 ve 7 (majörün kendisi)", "majör 6 (minör ama parlak)", "b2 (karanlık, İspanyol havası)", "#4 (rüya gibi, parlak)",
            "b7 (majör ama blues/rock)", "b6 (doğal minör)", "b5 ve b2 (en karanlık, kararsız)"};
    static final String[] MODE_Q = {"majör", "minör", "minör", "majör", "majör", "minör", "eksik"};

    static Ex mode(Map<String, String> p, Random r) {
        int t = r.nextInt(3), m = r.nextInt(7);
        String mn = Theory.SCALE_TR.get(MODES[m]);
        List<String> pool = new ArrayList<>();
        if (t == 0) {
            for (String s : MODE_CHAR) pool.add(s);
            return choice(mn + " modunun karakteristik (onu ayırt eden) özelliği nedir?", MODE_CHAR[m], pool, r);
        }
        if (t == 1) {
            String[] roots = {"C", "D", "E", "F", "G", "A", "B"};
            int[] maj = Theory.SCALES.get("major");
            String root = pick(roots, r);
            int parentPc = Theory.pc(Theory.parsePc(root) - maj[m]);
            String parent = Theory.name(parentPc, parentPc == 10 || parentPc == 3 || parentPc == 8 || parentPc == 1 || parentPc == 6);
            for (String s : Theory.MAJOR_KEYS) pool.add(s + " majör");
            Ex e = choice(root + " " + mn + " hangi majör gamın notalarını kullanır?", parent + " majör", pool, r);
            e.explain = mn + " majör gamın " + (m + 1) + ". derecesinden başlar.";
            return e;
        }
        Collections.addAll(pool, "majör", "minör", "eksik");
        return choice(mn + " modunun tonik akoru hangi türdedir?", MODE_Q[m], pool, r);
    }

    static final String[][] RHYTHM_Q = {
            {"4/4'te bir dörtlük nota kaç vuruş sürer?", "1"}, {"4/4'te bir ikilik nota kaç vuruş sürer?", "2"},
            {"4/4'te bir birlik nota kaç vuruş sürer?", "4"}, {"4/4'te bir sekizlik nota kaç vuruş sürer?", "1/2"},
            {"4/4'te bir on altılık nota kaç vuruş sürer?", "1/4"}, {"Noktalı ikilik kaç vuruş sürer?", "3"},
            {"Noktalı dörtlük kaç vuruş sürer?", "1 1/2"}, {"Noktalı sekizlik kaç vuruş sürer?", "3/4"},
            {"Sekizlik triole (üçleme) notalarının her biri kaç vuruş?", "1/3"}, {"3/4 ölçüsünde bir ölçü kaç dörtlük eder?", "3"},
            {"6/8 ölçüsünde bir ölçüde kaç sekizlik vardır?", "6"}, {"12/8 ölçüsü kaç ana vuruşa (noktalı dörtlük) bölünür?", "4"},
            {"Bir dörtlük nota kaç sekizliğe eşittir?", "2"}, {"Bir ikilik nota kaç on altılığa eşittir?", "8"},
    };

    static Ex rhythmVal(Random r) {
        String[] q = RHYTHM_Q[r.nextInt(RHYTHM_Q.length)];
        List<String> pool = new ArrayList<>();
        Collections.addAll(pool, "1", "2", "3", "4", "6", "8", "1/2", "1/4", "1/3", "1 1/2", "3/4");
        return choice(q[0], q[1], pool, r);
    }

    static final String[][] PROGS = {
            {"I-IV-V-I", "0,3,4,0"}, {"I-V-vi-IV", "0,4,5,3"}, {"ii-V-I", "1,4,0"}, {"I-vi-IV-V", "0,5,3,4"},
            {"vi-IV-I-V", "5,3,0,4"}, {"I-IV-I-V", "0,3,0,4"}, {"I-iii-IV-V", "0,2,3,4"}, {"IV-V-iii-vi", "3,4,2,5"},
    };

    static Ex progEar(Map<String, String> p, Random r) {
        int cnt = Integer.parseInt(p.getOrDefault("set", "4"));
        String[] pr = PROGS[r.nextInt(Math.min(cnt, PROGS.length))];
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < Math.min(cnt, PROGS.length); i++) pool.add(PROGS[i][0]);
        Ex e = choice("Dinle: bu akor dizisi hangisi? (ilk akor tonik değilse dikkat)", pr[0], pool, r);
        String key = pick(new String[]{"C", "G", "D", "A"}, r);
        e.audioChords = new ArrayList<>();
        for (String d : pr[1].split(",")) e.audioChords.add(Theory.diatonicChord(key, Integer.parseInt(d)));
        e.explain = key + " majörde: " + String.join(" - ", e.audioChords);
        return e;
    }

    static Ex open(Map<String, String> p) {
        int[] ss = ints(p.getOrDefault("s", "6-1"));
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.prompt = "Açık telleri sırayla çal";
        for (int i = 0; i < ss.length; i++) e.notes.add(new Ex.Note(ss[i], 0, i, 1));
        e.strictOctave = true;
        return e;
    }

    static Ex playNote(Map<String, String> p, Random r) {
        int n = Integer.parseInt(p.getOrDefault("len", "3"));
        boolean byName = "name".equals(p.get("show"));
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int[] pos = randPos(p, r);
            Ex.Note nt = new Ex.Note(pos[0], pos[1], i, 1);
            e.notes.add(nt);
            if (byName) {
                if (sb.length() > 0) sb.append("  →  ");
                sb.append(Theory.STRING_NAME[pos[0]].split(" ")[0]).append(" tel: ").append(pcLabel(Theory.pc(nt.midi)));
            }
        }
        if (byName) {
            e.prompt = "Notaları bul ve sırayla çal:\n" + sb;
            e.showTab = false;
            e.strictOctave = false;
        } else {
            e.prompt = "Tab'daki notaları sırayla çal (bekleme modu)";
            e.strictOctave = true;
        }
        return e;
    }

    /** "6:0/q 6:1h/e r/q 3:7b2/q" biçimindeki dizgeyi notalara çevirir. */
    public static List<Ex.Note> parseTab(String tab) {
        List<Ex.Note> l = new ArrayList<>();
        float beat = 0;
        for (String tok : tab.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] a = tok.split("/");
            float dur = durOf(a.length > 1 ? a[1] : "q");
            if (a[0].equals("r")) { beat += dur; continue; }
            String[] sf = a[0].split(":");
            int s = Integer.parseInt(sf[0]);
            String fs = sf[1];
            int k = 0; while (k < fs.length() && Character.isDigit(fs.charAt(k))) k++;
            int f = Integer.parseInt(fs.substring(0, k));
            Ex.Note n = new Ex.Note(s, f, beat, dur);
            if (k < fs.length()) {
                n.label = fs.substring(k);
                if (n.label.startsWith("b") && n.label.length() > 1) n.midi += Integer.parseInt(n.label.substring(1));
            }
            l.add(n);
            beat += dur;
        }
        return l;
    }

    static float durOf(String d) {
        if (d.equals("t")) return 1f / 3;
        float v;
        boolean dot = d.endsWith("."), trip = d.endsWith("t");
        String b = d.replace(".", "").replace("t", "");
        switch (b) {
            case "w": v = 4; break; case "h": v = 2; break; case "q": v = 1; break;
            case "e": v = 0.5f; break; case "s": v = 0.25f; break; default: v = 1;
        }
        if (dot) v *= 1.5f;
        if (trip) v = v * 2 / 3;
        return v;
    }

    static Ex timed(Map<String, String> p, String prompt) {
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_SEQ;
        e.bpm = Float.parseFloat(p.getOrDefault("bpm", "60"));
        e.beatsPerBar = Integer.parseInt(p.getOrDefault("bar", "4"));
        e.prompt = p.getOrDefault("title", prompt);
        e.strictOctave = true;
        return e;
    }

    static Ex seq(Map<String, String> p) {
        Ex e = timed(p, "Metronomla, zamanında çal");
        e.notes = parseTab(p.get("tab"));
        repeat(e, Integer.parseInt(p.getOrDefault("rep", "1")));
        return e;
    }

    static void repeat(Ex e, int rep) {
        if (rep <= 1 || e.notes.isEmpty()) return;
        Ex.Note last = e.notes.get(e.notes.size() - 1);
        float len = last.beat + last.dur;
        len = (float) Math.ceil(len / e.beatsPerBar) * e.beatsPerBar;
        List<Ex.Note> base = new ArrayList<>(e.notes);
        for (int k = 1; k < rep; k++) for (Ex.Note n : base) {
            Ex.Note c = new Ex.Note(n.string, n.fret, n.beat + k * len, n.dur);
            c.midi = n.midi; c.label = n.label; e.notes.add(c);
        }
    }

    static Ex spider(Map<String, String> p) {
        Ex e = timed(p, "Örümcek egzersizi: 1-2-3-4 parmaklar, her perdeye bir parmak");
        int[] ss = ints(p.getOrDefault("s", "6-1"));
        int f0 = Integer.parseInt(p.getOrDefault("f", "1"));
        float d = durOf(p.getOrDefault("dur", "q"));
        float b = 0;
        boolean back = p.containsKey("back");
        List<int[]> pos = new ArrayList<>();
        for (int s : ss) for (int k = 0; k < 4; k++) pos.add(new int[]{s, f0 + k});
        if (back) for (int i = ss.length - 1; i >= 0; i--) for (int k = 3; k >= 0; k--) pos.add(new int[]{ss[i], f0 + k});
        for (int[] q : pos) { e.notes.add(new Ex.Note(q[0], q[1], b, d)); b += d; }
        return e;
    }

    /** Pozisyon içinde gam/arpej notalarını bulur (6. telden 1. tele, artan). */
    public static List<int[]> positionNotes(int[] pcs, int pos, int span) {
        boolean[] in = new boolean[12];
        for (int pc : pcs) in[Theory.pc(pc)] = true;
        List<int[]> l = new ArrayList<>();
        int last = -1;
        for (int s = 6; s >= 1; s--) {
            for (int f = Math.max(0, pos); f <= pos + span - 1; f++) {
                int m = Theory.OPEN[s] + f;
                if (in[Theory.pc(m)] && m > last) { l.add(new int[]{s, f, m}); last = m; }
            }
        }
        // atlanan gam notalarını genişletilmiş perdelerle tamamla
        if (l.isEmpty()) return l;
        int lo = l.get(0)[2], hi = l.get(l.size() - 1)[2];
        for (int m = lo + 1; m < hi; m++) {
            if (!in[Theory.pc(m)]) continue;
            boolean has = false;
            for (int[] q : l) if (q[2] == m) { has = true; break; }
            if (has) continue;
            int[] best = null;
            for (int s = 6; s >= 1; s--) {
                int f = m - Theory.OPEN[s];
                if (f == pos + span || f == pos - 1) { if (f >= 0) { best = new int[]{s, f, m}; break; } }
            }
            if (best != null) {
                int idx = 0; while (idx < l.size() && l.get(idx)[2] < m) idx++;
                l.add(idx, best);
            }
        }
        return l;
    }

    static Ex scale(Map<String, String> p) {
        String key = p.getOrDefault("key", "C"), type = p.getOrDefault("type", "major");
        int pos = Integer.parseInt(p.getOrDefault("pos", "0"));
        int span = Integer.parseInt(p.getOrDefault("span", "4"));
        Ex e = timed(p, key + " " + Theory.SCALE_TR.get(type) + " — pozisyon " + pos);
        List<int[]> l = positionNotes(Theory.scalePcs(key + ":" + type), pos, span);
        int rootPc = Theory.parsePc(key);
        if (!p.containsKey("full")) {
            // kökten köke: ilk kökten son köke kadar kırp
            int a = 0, b = l.size() - 1;
            while (a < l.size() && Theory.pc(l.get(a)[2]) != rootPc) a++;
            while (b > a && Theory.pc(l.get(b)[2]) != rootPc) b--;
            if (b > a) l = new ArrayList<>(l.subList(a, b + 1));
        }
        String dir = p.getOrDefault("dir", "updown");
        List<int[]> seq = new ArrayList<>(l);
        if (dir.equals("down")) Collections.reverse(seq);
        else if (dir.equals("updown")) for (int i = l.size() - 2; i >= 0; i--) seq.add(l.get(i));
        float d = durOf(p.getOrDefault("dur", "q"));
        float b = 0;
        for (int[] q : seq) { e.notes.add(new Ex.Note(q[0], q[1], b, d)); b += d; }
        return e;
    }

    static Ex arp(Map<String, String> p) {
        String ch = p.getOrDefault("chord", "Am");
        int pos = Integer.parseInt(p.getOrDefault("pos", "5"));
        Ex e = timed(p, ch + " arpeji — pozisyon " + pos);
        List<int[]> l = positionNotes(Theory.chordPcs(ch), pos, Integer.parseInt(p.getOrDefault("span", "4")));
        List<int[]> seq = new ArrayList<>(l);
        if (!"up".equals(p.get("dir"))) for (int i = l.size() - 2; i >= 0; i--) seq.add(l.get(i));
        float d = durOf(p.getOrDefault("dur", "e"));
        float b = 0;
        for (int[] q : seq) { e.notes.add(new Ex.Note(q[0], q[1], b, d)); b += d; }
        return e;
    }

    static Ex chord(Map<String, String> p, Random r) {
        String[] names = p.getOrDefault("name", "Em").split(",");
        String nm = pick(names, r);
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_CHORD;
        e.chord = nm;
        e.chordShape = Theory.shapeFor(nm);
        e.prompt = Theory.chordTr(nm) + " akorunu bas ve bir kez vur";
        return e;
    }

    static Ex changes(Map<String, String> p) {
        Ex e = new Ex();
        e.type = Ex.Type.CHANGES;
        String[] ch = p.getOrDefault("ch", "Em,G").split(",");
        int rep = Integer.parseInt(p.getOrDefault("rep", "2"));
        for (int k = 0; k < rep; k++) Collections.addAll(e.chords, ch);
        e.bpm = Float.parseFloat(p.getOrDefault("bpm", "60"));
        e.beatsPerChord = Integer.parseInt(p.getOrDefault("beats", "4"));
        e.beatsPerBar = Integer.parseInt(p.getOrDefault("bar", "4"));
        e.prompt = "Akor geçişleri: her akora ilk vuruşunda tam zamanında vur";
        return e;
    }

    static Ex strum(Map<String, String> p) {
        Ex e = new Ex();
        e.type = Ex.Type.RHYTHM;
        String pat = p.getOrDefault("p", "D.D.D.D.");
        e.grid = Integer.parseInt(p.getOrDefault("grid", String.valueOf(pat.length())));
        e.beatsPerBar = Integer.parseInt(p.getOrDefault("bar", "4"));
        e.bpm = Float.parseFloat(p.getOrDefault("bpm", "60"));
        e.bars = Integer.parseInt(p.getOrDefault("bars", "2"));
        e.strumChord = p.get("ch");
        e.rhythmText = pat;
        e.hits = new boolean[pat.length() * e.bars];
        for (int b = 0; b < e.bars; b++) for (int i = 0; i < pat.length(); i++) {
            char c = pat.charAt(i);
            e.hits[b * pat.length() + i] = c == 'D' || c == 'U' || c == 'X' || c == 'x';
        }
        e.prompt = e.strumChord != null ? e.strumChord + " akoruyla ritmi çal" : "Telleri sol elinle susturup ritmi çal";
        return e;
    }

    static Ex improv(Map<String, String> p) {
        Ex e = new Ex();
        e.type = Ex.Type.IMPROV;
        String sc = p.getOrDefault("scale", "A:minpent");
        e.allowedPcs = Theory.scalePcs(sc);
        e.scaleLabel = sc.split(":")[0] + " " + Theory.SCALE_TR.get(sc.split(":")[1]);
        e.bpm = Float.parseFloat(p.getOrDefault("bpm", "80"));
        e.beats = Integer.parseInt(p.getOrDefault("beats", "16"));
        int pos = Integer.parseInt(p.getOrDefault("pos", "5"));
        for (int[] q : positionNotes(e.allowedPcs, pos, 4)) e.fretDots.add(new int[]{q[0], q[1]});
        e.fretFrom = Math.max(0, pos - 1); e.fretTo = pos + 5;
        e.prompt = "Doğaçlama: " + e.scaleLabel + " dışında nota çalma! En az " + (e.beats / 2) + " nota çal.";
        return e;
    }

    static Ex earBack(Map<String, String> p, Random r) {
        String sc = p.getOrDefault("scale", "C:major");
        int[] pcs = Theory.scalePcs(sc);
        int len = Integer.parseInt(p.getOrDefault("len", "3"));
        int lo = Integer.parseInt(p.getOrDefault("lo", "48")), hi = Integer.parseInt(p.getOrDefault("hi", "60"));
        List<Integer> avail = new ArrayList<>();
        for (int m = lo; m <= hi; m++) for (int pc : pcs) if (Theory.pc(m) == pc) avail.add(m);
        int idx = r.nextInt(avail.size());
        // ilk nota tonik olsun ki referans olsun
        for (int i = 0; i < avail.size(); i++) if (Theory.pc(avail.get(i)) == pcs[0]) { idx = i; break; }
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.hideTarget = true;
        e.showTab = false;
        e.audio = new float[len];
        for (int i = 0; i < len; i++) {
            int m = avail.get(idx);
            e.audio[i] = m;
            e.notes.add(Ex.Note.ofMidi(m, i, 1));
            int step = r.nextInt(5) - 2; if (step == 0) step = 1;
            idx = Math.max(0, Math.min(avail.size() - 1, idx + step));
        }
        e.prompt = "Kulaktan çal: dinlediğin melodiyi gitarda tekrar et. İlk nota " + Theory.SHARP[Theory.pc((int) e.audio[0])] + ".";
        return e;
    }

    static Ex bend(Map<String, String> p) {
        Ex e = new Ex();
        e.type = Ex.Type.BEND;
        e.bendString = Integer.parseInt(p.getOrDefault("s", "3"));
        e.bendFret = Integer.parseInt(p.getOrDefault("f", "7"));
        e.bendSemis = Integer.parseInt(p.getOrDefault("st", "2"));
        e.bendTarget = Theory.OPEN[e.bendString] + e.bendFret + e.bendSemis;
        e.fretDots.add(new int[]{e.bendString, e.bendFret});
        e.fretDots.add(new int[]{e.bendString, e.bendFret + e.bendSemis});
        e.fretFrom = Math.max(0, e.bendFret - 2); e.fretTo = e.bendFret + 4;
        e.prompt = Theory.STRING_NAME[e.bendString] + " " + e.bendFret + ". perdeyi çal ve teli " + (e.bendSemis == 1 ? "yarım" : e.bendSemis == 2 ? "tam" : "1,5") +
                " ses bükerek " + Theory.SHARP[Theory.pc((int) e.bendTarget)] + " perdesine ulaştır, orada tut";
        return e;
    }

    static Ex octave(Random r) {
        int s = r.nextBoolean() ? 6 : 5, f = 1 + r.nextInt(7);
        int m = Theory.OPEN[s] + f;
        Ex e = new Ex();
        e.type = Ex.Type.PLAY_NOTES;
        e.prompt = "İşaretli notayı (" + Theory.SHARP[Theory.pc(m)] + ") ve oktav şekliyle bir oktav yukarısını çal";
        e.notes.add(new Ex.Note(s, f, 0, 1));
        Ex.Note o = new Ex.Note(s - 2, f + 2, 1, 1);
        o.label = "?";
        e.notes.add(o);
        e.fretDots.add(new int[]{s, f});
        e.strictOctave = true;
        e.showTab = false;
        return e;
    }
}
