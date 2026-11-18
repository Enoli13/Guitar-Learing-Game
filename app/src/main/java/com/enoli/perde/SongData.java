package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;

/** Dosyadan (Guitar Pro, MusicXML) okunan şarkının ortak, biçimden bağımsız hali. */
public class SongData {
    public String title = "", artist = "", format = "";
    public float tempo = 0;
    public final List<Float> mStart = new ArrayList<>(), mLen = new ArrayList<>(); // vuruş (dörtlük) cinsinden
    public final List<Track> tracks = new ArrayList<>();

    public static class Track {
        public String name = "";
        public int[] tuning = Theory.OPEN.clone();   // 1..6 = telin boş sesi (MIDI)
        public int strings = 6;
        public boolean drums;
        public final List<Ev> evs = new ArrayList<>();
    }

    /** Aynı anda çalınan sesler (tek nota ya da akor). */
    public static class Ev {
        public float beat, dur;
        public final List<int[]> notes = new ArrayList<>(); // {tel, perde, bağ(1/0), midi}
        public String label;                               // bN (bu notada bend)
        public String legato;                              // h ya da s: bu notadan SONRAKİ notaya bağlı geçiş
    }

    /** Gitar için uygun parçalar (davul ve 6 telden farklıları sona). */
    public List<Integer> guitarTracks() {
        List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            Track t = tracks.get(i);
            if (t.drums) continue;
            int notes = 0;
            for (Ev e : t.evs) notes += e.notes.size();
            if (notes == 0) continue;
            if (t.strings == 6) a.add(i); else b.add(i);
        }
        a.addAll(b);
        return a;
    }

    /** Seçilen parçayı uygulamanın nota listesine çevirir: bağlı sesler birleşir, akorlar adlandırılır. */
    public TabImport.Result toResult(int trackIdx) {
        TabImport.Result r = new TabImport.Result();
        Track t = tracks.get(trackIdx);
        r.title = title; r.artist = artist; r.tempo = tempo; r.format = format; r.trackName = t.name;
        r.tuning = t.tuning.clone();
        r.measureStarts.addAll(mStart);
        r.measureLens.addAll(mLen);
        r.measures = mStart.size();
        if (!mLen.isEmpty()) r.beatsPerBar = Math.round(mLen.get(0));
        Ex.Note last = null;
        String pending = null; int pendingMidi = 0;
        for (Ev e : t.evs) {
            List<int[]> fresh = new ArrayList<>();
            for (int[] n : e.notes) if (n[2] == 0 && n[0] >= 1 && n[0] <= 6) fresh.add(n);
            if (fresh.isEmpty()) {
                // tamamen bağlı: önceki sesi uzat
                if (last != null && !e.notes.isEmpty()) last.dur = Math.max(last.dur, e.beat + e.dur - last.beat);
                continue;
            }
            int lo = 0;
            for (int i = 1; i < fresh.size(); i++) if (fresh.get(i)[3] < fresh.get(lo)[3]) lo = i;
            int[] m = fresh.get(lo);
            Ex.Note n = new Ex.Note(m[0], m[1], e.beat, e.dur);
            n.midi = m[3];
            n.label = e.label;
            if (n.label == null && pending != null && fresh.size() == 1) {
                n.label = pending.equals("h") ? (n.midi < pendingMidi ? "p" : "h") : "s";
            }
            pending = fresh.size() == 1 ? e.legato : null;
            pendingMidi = n.midi;
            if (e.label != null && e.label.startsWith("b")) {
                try { n.midi += Integer.parseInt(e.label.substring(1)); } catch (Exception ignored) { n.label = null; }
            }
            if (fresh.size() > 1) {
                n.strs = new int[fresh.size()]; n.frets = new int[fresh.size()];
                int k = 0;
                for (int i = 0; i < fresh.size(); i++) {
                    int[] q = fresh.get(i);
                    n.strs[i] = q[0]; n.frets[i] = q[1];
                    if (i != lo) n.alts = TabImport.append(n.alts, q[3]);
                    k++;
                }
            }
            r.notes.add(n);
            last = n;
        }
        ChordDict.annotate(r.notes);
        if (r.notes.isEmpty()) r.error = "Bu parçada çalınacak nota yok.";
        return r;
    }

    static int stdMidi(int string, int fret, int[] tuning) { return tuning[string] + fret; }
}
