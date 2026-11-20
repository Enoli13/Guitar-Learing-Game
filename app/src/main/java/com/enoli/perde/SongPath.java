package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Şarkı Yolu: Yol'dan ayrı ilerleme. Her ünite, ünitenin şarkısının ardışık küçük bölümlerinden oluşan adımlardır.
 * Her adımda ünitenin bir dersinin teorisi + o bölümden teori soruları + bölümü çalmak vardır.
 * Son adım (final), öğrenilen bütün bölümleri birleştirip çalmaktır.
 */
public final class SongPath {
    private SongPath() {}

    public static class Step {
        public int index;               // 0..n-1 adımlar, n = final
        public boolean fin;
        public Curriculum.Lesson lesson; // teori kaynağı (finalde null)
        public int fromM = -1, toM = -1; // bölüm (0 tabanlı ölçü)
        public String title;
    }

    public static Curriculum.Song mainSong(Curriculum cur, Curriculum.Unit u) {
        for (Curriculum.Song s : cur.songsOf(u)) if (s.main) return s;
        return null;
    }

    public static List<Curriculum.Lesson> lessons(Curriculum.Unit u) {
        List<Curriculum.Lesson> l = new ArrayList<>();
        for (Curriculum.Lesson x : u.lessons) if (!x.id.endsWith("_pratik")) l.add(x);
        return l;
    }

    public static TabImport.Result tab(Progress p, Curriculum.Song s) {
        if (s == null) return null;
        String t = p.songTabs.get(s.id());
        if (t == null || t.trim().isEmpty()) return null;
        TabImport.Result r = TabImport.parseAny(t);
        return r.error != null || r.notes.isEmpty() ? null : r;
    }

    static int notesIn(TabImport.Result r, int a, int z) {
        float s = r.start(a), e = r.start(z) + r.len(z);
        int c = 0;
        for (Ex.Note n : r.notes) if (n.beat >= s - 1e-3f && n.beat < e - 1e-3f) c++;
        return c;
    }

    /** Ünitenin adımları; tab yoksa bölümler -1 kalır. */
    public static List<Step> steps(Curriculum.Unit u, Curriculum.Song song, TabImport.Result r) {
        List<Curriculum.Lesson> ls = lessons(u);
        List<Step> out = new ArrayList<>();
        int m = 0;
        if (r != null) {
            m = song != null && song.startMeasure > 0 ? Math.min(song.startMeasure - 1, r.measures - 1) : 0;
            while (m < r.measures - 1 && notesIn(r, m, m) == 0) m++;
        }
        for (int i = 0; i < ls.size(); i++) {
            Step st = new Step();
            st.index = i; st.lesson = ls.get(i);
            st.title = ls.get(i).title;
            if (r != null) {
                int L = 1;
                while (notesIn(r, m, Math.min(r.measures - 1, m + L - 1)) < 6 && L < 4 && m + L < r.measures) L++;
                if (m >= r.measures) m = Math.max(0, r.measures - L);
                st.fromM = m; st.toM = Math.min(r.measures - 1, m + L - 1);
                m = st.toM + 1;
                if (m >= r.measures) m = 0; // şarkı biterse başa dön
            }
            out.add(st);
        }
        Step fin = new Step();
        fin.index = ls.size(); fin.fin = true; fin.title = "Final: öğrendiklerini birleştir";
        if (r != null && !out.isEmpty()) {
            fin.fromM = out.get(0).fromM;
            int last = out.get(0).toM;
            for (Step s : out) if (s.toM > last && s.fromM >= fin.fromM) last = s.toM;
            fin.toM = Math.min(last, fin.fromM + 15);
        }
        out.add(fin);
        return out;
    }

    public static String key(Curriculum.Unit u, int i) { return "sp:" + u.id + ":" + i; }

    public static int stars(Progress p, Curriculum.Unit u, int i) {
        try { return Integer.parseInt(p.songTabs.get(key(u, i))); } catch (Exception e) { return -1; }
    }

    public static boolean unitDone(Progress p, Curriculum.Unit u) {
        int n = lessons(u).size();
        for (int i = 0; i <= n; i++) if (stars(p, u, i) < 0) return false;
        return true;
    }

    /** Bölüm alıştırması (şarkı şeridi + tab/nota). */
    public static Ex section(TabImport.Result r, int a, int z, float bpm, Ex.Type type) {
        Ex e = new Ex();
        e.type = type;
        e.notes = TabImport.slice(r, a, z, 4);
        int n = z - a + 1;
        e.measureStarts = new float[n]; e.measureLens = new float[n];
        float s = r.start(a);
        for (int i = 0; i < n; i++) { e.measureStarts[i] = r.start(a + i) - s; e.measureLens[i] = r.len(a + i); }
        e.firstMeasure = a + 1;
        e.beatsPerBar = Math.max(1, Math.round(r.len(a)));
        e.songLane = true;
        e.strictOctave = true;
        e.bpm = bpm;
        return e;
    }

    static final String THEORY_GENS = "g: (alpha|semi|fretname|fretfind|staffname|ival|ivalear|scalespell|degree|chordspell|chordear|chordid|diatonic|keysig|relative|mode|rhythmval|progear|degreeear|chordfunc)\\b.*";
    static final String TECH_GENS = "g: (bend|path|pinch|swell)\\b.*";

    /** Bir adımın görev kuyruğu. */
    public static List<Ex> build(Curriculum.Unit u, Curriculum.Song song, TabImport.Result r, Step st, Random rnd) {
        List<Ex> q = new ArrayList<>();
        float tempo = r.tempo > 0 ? r.tempo : 90;
        String where = "ölçü " + (st.fromM + 1) + (st.toM > st.fromM ? "–" + (st.toM + 1) : "");
        Ex intro = new Ex();
        intro.type = Ex.Type.INFO;
        if (!st.fin) {
            intro.title = "🎸 " + song.title + " • " + where;
            intro.body = "Bu adımda " + song.title + " şarkısının " + song.part.toLowerCase() + " kısmından " + where + " bölümünü çalacaksın. Önce konuyu bu bölüm üzerinden öğren: " + st.lesson.title + ".";
            q.add(intro);
            for (int i = 0; i < st.lesson.cards.size(); i++) {
                Ex c = new Ex();
                c.type = Ex.Type.INFO;
                c.title = st.lesson.title + (i > 0 ? " (" + (i + 1) + ")" : "");
                c.body = st.lesson.cards.get(i);
                q.add(c);
            }
            // konunun teori soruları (en fazla 4) ve teknik alıştırmalar (en fazla 2)
            int th = 0, te = 0;
            for (String s : st.lesson.specs) {
                boolean isQ = s.startsWith("q:") || s.matches(THEORY_GENS);
                boolean isT = s.matches(TECH_GENS);
                if (isQ && th < 4) for (Ex e : Gen.make(s, rnd)) { if (th++ < 4) q.add(e); }
                else if (isT && te < 2) for (Ex e : Gen.make(s, rnd)) { if (te++ < 2) q.add(e); }
            }
            // aynı konu, şarkının bu bölümünden
            List<Ex.Note> notes = TabImport.slice(r, st.fromM, st.toM, 4);
            q.addAll(SongDrills.make(r, notes, u.id, 3, rnd));
            Ex wait = section(r, st.fromM, st.toM, tempo, Ex.Type.PLAY_NOTES);
            wait.title = song.title + " • " + where;
            wait.prompt = "Bölümün notalarını sırayla bul — acele yok";
            q.add(wait);
            Ex play = section(r, st.fromM, st.toM, Math.max(45, Math.min(90, Math.round(tempo * 0.6f / 5) * 5)), Ex.Type.PLAY_SEQ);
            play.title = song.title + " • " + where;
            play.prompt = "Şimdi metronomla çal";
            q.add(play);
        } else {
            intro.title = "🏁 Ünite finali: " + song.title;
            intro.body = "Bu ünitede " + song.title + " şarkısının " + where + " bölümünü parça parça öğrendin. Şimdi hepsini birleştirip baştan sona çal. Temiz geçince ünite biter ve sandık açılır.";
            q.add(intro);
            Ex wait = section(r, st.fromM, st.toM, tempo, Ex.Type.PLAY_NOTES);
            wait.title = song.title + " • " + where;
            wait.prompt = "Önce bütün bölümü sırayla bul";
            q.add(wait);
            float slow = Math.max(45, Math.min(90, Math.round(tempo * 0.6f / 5) * 5));
            Ex play = section(r, st.fromM, st.toM, slow, Ex.Type.PLAY_SEQ);
            play.title = song.title + " • " + where;
            play.prompt = "Final: hepsi birden, metronomla";
            q.add(play);
            float target = Math.min(tempo, Math.max(slow + 10, Math.round(tempo * 0.8f / 5) * 5));
            if (target > slow) {
                Ex fast = section(r, st.fromM, st.toM, target, Ex.Type.PLAY_SEQ);
                fast.title = song.title + " • " + where;
                fast.prompt = "Son tur: şarkının temposuna yakın (" + Math.round(target) + " BPM)";
                q.add(fast);
            }
        }
        return q;
    }
}
