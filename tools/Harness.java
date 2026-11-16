import com.enoli.perde.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** JVM üzerinde çalışan doğrulama testleri (Android stub'ları sınıf yolu için gerekir). */
public class Harness {
    static int fails = 0;
    static void check(boolean c, String m) { if (!c) { fails++; System.out.println("FAIL: " + m); } }

    public static void main(String[] a) throws Exception {
        String assets = a[0];
        // 1) Müfredat
        Curriculum cur = new Curriculum();
        cur.parse(new BufferedReader(new InputStreamReader(new FileInputStream(assets + "/curriculum.txt"), StandardCharsets.UTF_8)));
        System.out.println("Ünite: " + cur.units.size() + "  Ders: " + cur.lessons.size());
        Set<String> ids = new HashSet<>();
        int specs = 0, exs = 0;
        Map<Ex.Type, Integer> cnt = new TreeMap<>();
        for (Curriculum.Lesson l : cur.lessons) {
            check(ids.add(l.id), "dup id " + l.id);
            check(!l.specs.isEmpty() || !l.cards.isEmpty(), "boş ders " + l.id);
            for (String s : l.specs) {
                specs++;
                for (int rep = 0; rep < 30; rep++) {
                    List<Ex> list = Gen.make(s, new Random(rep * 31 + s.hashCode()));
                    check(!list.isEmpty(), "üretim yok: " + s);
                    for (Ex e : list) {
                        if (rep == 0) { exs++; cnt.merge(e.type, 1, Integer::sum); }
                        check(!(e.type == Ex.Type.INFO && "Hata".equals(e.title)), "hata: " + s + " -> " + e.body);
                        if (e.type == Ex.Type.CHOICE) {
                            check(e.options.size() >= 2, "az seçenek: " + s + " " + e.prompt);
                            check(e.correct >= 0 && e.correct < e.options.size(), "doğru indeks: " + s);
                            check(new HashSet<>(e.options).size() == e.options.size(), "tekrarlı seçenek: " + s);
                        }
                        if (e.type == Ex.Type.PLAY_SEQ || e.type == Ex.Type.PLAY_NOTES) {
                            check(!e.notes.isEmpty(), "nota yok: " + s);
                            for (Ex.Note n : e.notes) check(n.midi >= 40 && n.midi <= 92, "aralık dışı midi " + n.midi + " : " + s);
                        }
                        if (e.type == Ex.Type.PLAY_CHORD) check(e.chordShape != null, "şekil yok: " + s + " " + e.chord);
                        if (e.type == Ex.Type.CHANGES) for (String c : e.chords) check(Theory.shapeFor(c) != null, "şekil yok: " + c);
                        if (e.type == Ex.Type.RHYTHM && e.freeCount == 0) check(e.hits != null && e.hits.length > 0 && e.grid > 0, "ritim boş: " + s);
                        if (e.prompt != null && e.prompt.contains("\\")) check(false, "kaçış karakteri: " + s);
                        if (e.type == Ex.Type.RHYTHM && e.strumChord != null) check(Theory.shapeFor(e.strumChord) != null, "şekil yok");
                    }
                }
            }
        }
        System.out.println("Spec: " + specs + "  Alıştırma örneği: " + exs + "  Türler: " + cnt);
        // şarkı önkoşulları
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(assets + "/songs.txt"), StandardCharsets.UTF_8));
        String line; int songs = 0;
        while ((line = br.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            songs++;
            for (String p : line.split("\\|")[2].split(",")) check(cur.byId.containsKey(p.trim()), "şarkı önkoşulu yok: " + p);
        }
        System.out.println("Şarkı: " + songs);

        // 2) Teori
        check(String.join(" ", Theory.spellScale("F", "major")).equals("F G A Bb C D E"), "F majör");
        check(String.join(" ", Theory.spellScale("D", "major")).equals("D E F# G A B C#"), "D majör");
        check(String.join(" ", Theory.spellScale("Eb", "major")).equals("Eb F G Ab Bb C D"), "Eb majör");
        check(String.join(" ", Theory.spellScale("A", "harmmin")).equals("A B C D E F G#"), "A arm minör");
        check(Theory.spellChord("D", Theory.CT.get("m")).equals("D F A"), "Dm");
        check(Theory.spellChord("C", Theory.CT.get("7#9")).equals("C E G Bb D#"), "C7#9 " + Theory.spellChord("C", Theory.CT.get("7#9")));
        check(Theory.spellChord("B", Theory.CT.get("m7b5")).equals("B D F A"), "Bm7b5");
        check(Theory.diatonicChord("G", 6).equals("F#dim"), "G vii");
        check(Theory.shapeFor("F").equals("133211"), "F shape");
        check(Theory.shapeFor("G@6").equals("355433"), "G@6 " + Theory.shapeFor("G@6"));
        check(Theory.shapeFor("C@5").equals("x35553"), "C@5 " + Theory.shapeFor("C@5"));
        check(Theory.shapeFor("Bm").equals("x24432"), "Bm");
        check(Theory.shapeFor("A#5@6").equals("68(8)xxx") || Theory.shapeFor("A#5@6").equals("688xxx"), "A#5@6 " + Theory.shapeFor("A#5@6"));
        // tüm şekillerin notaları akorla uyumlu mu
        String[] shapes = {"E", "Em", "A", "Am", "D", "Dm", "G", "C", "E7", "A7", "D7", "G7", "C7", "B7", "Am7", "Em7", "Dm7", "Cmaj7", "Fmaj7", "Dsus2", "Dsus4", "Asus2", "Asus4", "Cadd9", "F", "Bm", "B", "Fm",
                "G@6", "Am@6", "Dm@5", "C@5", "Cm@5", "D@5", "Bm7b5@5", "Cdim7@5", "Em7b5@5", "C9@5", "E7#9@5", "A13@5", "E5", "A5", "G5@6", "C#5@5", "G/B", "D/F#", "Am/G", "C/G"};
        for (String s : shapes) {
            int[] pcs = Theory.chordPcs(s);
            for (float m : Theory.shapeMidis(Theory.shapeFor(s))) {
                boolean in = false; for (int p : pcs) if (p == Theory.pc((int) m)) in = true;
                check(in, "şekil " + s + " " + Theory.shapeFor(s) + " akor dışı nota " + Theory.midiName((int) m));
            }
        }

        // 3) Perde tespiti (YIN) Karplus-Strong sinyaliyle
        float maxErr = 0;
        for (int m = 40; m <= 88; m++) {
            float[] x = AudioOut.pluck(m, 0.5f, 0.5f);
            float[] s = new float[2048];
            System.arraycopy(x, 4000, s, 0, 2048);
            float[] r = Dsp.yin(s, 0, 1024, 44100, 70, 1400, new float[1026]);
            float err = Math.abs(Dsp.hzToMidi(r[0]) - m);
            maxErr = Math.max(maxErr, err);
            check(err < 0.35f, "yin " + m + " -> " + Dsp.hzToMidi(r[0]) + " conf " + r[1]);
        }
        System.out.println("YIN maks hata (yarım ses): " + maxErr);

        // 4) Gerçek zaman hattı: AudioIn.process ile nota olayları
        float bpm = 90; double beat = 60.0 / bpm;
        int[] melody = {52, 53, 55, 57, 55, 53, 52, 59};
        // tek telde çalınan melodi gibi: her nota bir sonrakinde susar (son 5 ms yumuşak)
        float[] audio = new float[(int) ((1 + melody.length * beat / 2) * 44100)];
        for (int i = 0; i < melody.length; i++) {
            float[] pl = AudioOut.pluck(melody[i], (float) (beat / 2), 0.5f);
            int off = (int) ((0.5 + i * beat / 2) * 44100);
            for (int k = 0; k < pl.length && off + k < audio.length; k++) audio[off + k] += pl[k];
        }
        Random rn = new Random(1);
        AudioIn in = new AudioIn();
        List<long[]> notes = new ArrayList<>();
        List<Long> onsets = new ArrayList<>();
        in.setListener(new AudioIn.Listener() {
            public void onFrame(long n, float m, float c, float db) {}
            public void onNote(long t, float m, boolean att) { notes.add(new long[]{t, Math.round(m), att ? 1 : 0}); }
            public void onOnset(long t, float db) { onsets.add(t); }
        });
        in.resetAnalysis(0);
        short[] buf = new short[256];
        for (int i = 0; i + 256 <= audio.length; i += 256) {
            for (int k = 0; k < 256; k++) buf[k] = (short) Math.max(-32767, Math.min(32767, (audio[i + k] * 0.6f + (rn.nextFloat() - 0.5f) * 0.002f) * 32767));
            in.process(buf, 256);
        }
        System.out.println("Onset: " + onsets.size() + "  Nota olayı: " + notes.size());
        int ok = 0; double sumErr = 0, maxT = 0;
        for (int i = 0; i < melody.length; i++) {
            double t0 = (0.5 + i * beat / 2) * 1e9;
            for (long[] n : notes) if (Math.abs(n[0] - t0) < 60e6 && n[1] == melody[i]) { ok++; sumErr += Math.abs(n[0] - t0); maxT = Math.max(maxT, Math.abs(n[0] - t0)); break; }
        }
        for (long[] n : notes) System.out.println("  t=" + (n[0] / 1e6) + "ms midi=" + n[1] + " atak=" + n[2]);
        System.out.println("Doğru zamanlı nota: " + ok + "/" + melody.length + "  ort. hata " + (ok > 0 ? sumErr / ok / 1e6 : -1) + " ms, maks " + maxT / 1e6 + " ms");
        check(ok == melody.length, "gerçek zaman nota tespiti");
        check(notes.size() <= melody.length + 1, "fazla olay: " + notes.size());

        // 4b) Metronom tıkı tek başına vuruş üretmemeli; akor vuruşları ve susturulmuş vuruşlar algılanmalı
        {
            float[] sig = new float[44100 * 4];
            // tıklar (2600 Hz) her 0.5 s
            for (int b = 0; b < 8; b++) { int off = (int) (b * 0.5 * 44100); for (int i = 0; i < 882 && off + i < sig.length; i++) sig[off + i] += (float) (0.3 * Math.exp(-i / 176.0) * Math.sin(2 * Math.PI * 2600 * i / 44100)); }
            System.out.println("Tık-only onset: " + countOnsets(sig, null));
            check(countOnsets(sig, null) == 0, "tık vuruş sayıldı");
            // akor vuruşları 0.6 s arayla (6 tel, 12 ms yayılım) + tıklar
            // gerçekçi model: bir tel yeniden çalınınca önceki titreşimi kesilir
            String[] seqc = {"G", "G", "C", "D", "Em", "C", "G", "D"};
            float[] au = new float[sig.length];
            for (int k = 0; k < seqc.length; k++) {
                int[] fr = Theory.parseShape(Theory.shapeFor(seqc[k]));
                for (int st = 0; st < 6; st++) {
                    if (fr[st] < 0) continue;
                    float[] pl = AudioOut.pluck(Theory.OPEN[6 - st] + fr[st], 0.42f, 0.18f);
                    int off = (int) ((0.4 + k * 0.42 + st * 0.012) * 44100);
                    for (int q = 0; q < pl.length && off + q < au.length; q++) au[off + q] += pl[q];
                }
            }
            for (int i = 0; i < Math.min(au.length, sig.length); i++) sig[i] = sig[i] * 0.3f + au[i] * 0.7f;
            List<Long> on = new ArrayList<>();
            countOnsets(sig, on);
            int good = 0;
            for (int k = 0; k < seqc.length; k++) { double t0 = (0.4 + k * 0.42) * 1e9; for (long t : on) if (Math.abs(t - t0) < 35e6) { good++; break; } }
            System.out.println("Akor vuruşu: " + good + "/" + seqc.length + " (toplam onset " + on.size() + ")");
            for (long t : on) System.out.print(Math.round(t / 1e6) + " "); System.out.println();
            check(good == seqc.length && on.size() <= seqc.length + 1, "akor vuruşları");
            // susturulmuş vuruşlar (filtrelenmiş gürültü patlamaları) 8'lik 100 BPM
            float[] mu = new float[44100 * 4];
            Random r2 = new Random(5);
            float prev = 0;
            for (int k = 0; k < 10; k++) { int off = (int) ((0.3 + k * 0.3) * 44100); for (int i = 0; i < 1500; i++) { float nz = (r2.nextFloat() - 0.5f); prev = prev * 0.8f + nz * 0.2f; mu[off + i] += prev * 1.5f * (float) Math.exp(-i / 300.0); } }
            List<Long> on2 = new ArrayList<>();
            countOnsets(mu, on2);
            int g2 = 0;
            for (int k = 0; k < 10; k++) { double t0 = (0.3 + k * 0.3) * 1e9; for (long t : on2) if (Math.abs(t - t0) < 30e6) { g2++; break; } }
            System.out.println("Susturulmuş vuruş: " + g2 + "/10 (toplam " + on2.size() + ")");
            check(g2 >= 9 && on2.size() <= 11, "susturulmuş vuruşlar");
        }

        // 5) Akor tanıma
        String[] chords = {"Em", "E", "Am", "A", "D", "Dm", "G", "C", "F", "Bm", "E5", "A5", "G5@6", "E7", "Cmaj7", "Dsus4", "Am7"};
        int chordOk = 0;
        for (String c : chords) {
            float[] mids = Theory.shapeMidis(Theory.shapeFor(c));
            List<AudioOut.Ev> ce = new ArrayList<>();
            for (int i = 0; i < mids.length; i++) ce.add(new AudioOut.Ev(mids[i], i * 0.012f, 1.2f));
            float[] au = AudioOut.render(ce);
            float[] blk = new float[4096];
            System.arraycopy(au, (int) (0.06 * 44100), blk, 0, 4096);
            float[] chroma = Dsp.chroma(blk, 44100);
            String r = judge(chroma, c);
            if (r == null) chordOk++; else System.out.println("  akor " + c + " -> " + r);
            // yanlış akor reddedilmeli
            String wrong = c.startsWith("E") ? "C" : "E";
            check(judge(chroma, wrong) != null, "yanlış akor kabul edildi: " + c + " vs " + wrong);
        }
        System.out.println("Akor tanıma: " + chordOk + "/" + chords.length);
        check(chordOk >= chords.length - 1, "akor tanıma");

        // 6) Tab içe aktarma
        String tab = "e|-------------------|-------0-----------|\n" +
                     "B|-----1-------1-----|-----1---1---------|\n" +
                     "G|---0-------0---0---|---2-------2-------|\n" +
                     "D|-------------------|-------------------|\n" +
                     "A|-3-------3---------|-------------------|\n" +
                     "E|-------------------|-0-------------0---|\n";
        TabImport.Result tr = TabImport.parse(tab, 4);
        System.out.println("Tab: " + tr.notes.size() + " nota, " + tr.measures + " ölçü, hata=" + tr.error);
        for (Ex.Note n : tr.notes) System.out.print(n.string + ":" + n.fret + "@" + n.beat + (n.alts != null ? "+" + n.alts.length : "") + " ");
        System.out.println();
        check(tr.measures == 2, "ölçü sayısı");
        check(tr.notes.size() >= 10, "tab nota sayısı");

        // 7) Pozisyon algoritması: A minör pentatonik kutu 1
        StringBuilder sb = new StringBuilder();
        for (int[] q : Gen.positionNotes(Theory.scalePcs("A:minpent"), 5, 4)) sb.append(q[0]).append(":").append(q[1]).append(" ");
        System.out.println("Am pent kutu1: " + sb);
        check(sb.toString().trim().equals("6:5 6:8 5:5 5:7 4:5 4:7 3:5 3:7 2:5 2:8 1:5 1:8"), "pentatonik kutu");
        sb.setLength(0);
        for (int[] q : Gen.positionNotes(Theory.scalePcs("C:major"), 0, 4)) sb.append(q[0]).append(":").append(q[1]).append(" ");
        System.out.println("C majör açık: " + sb);

        System.out.println(fails == 0 ? "TÜM TESTLER GEÇTİ" : fails + " HATA");
        System.exit(fails == 0 ? 0 : 1);
    }

    static int countOnsets(float[] sig, List<Long> out) {
        AudioIn in = new AudioIn();
        final int[] n = {0};
        in.setListener(new AudioIn.Listener() {
            public void onFrame(long a, float m, float c, float d) {}
            public void onNote(long a, float m, boolean b) {}
            public void onOnset(long t, float db) { n[0]++; if (out != null) out.add(t); }
        });
        in.resetAnalysis(0);
        short[] buf = new short[256];
        Random rn = new Random(3);
        for (int i = 0; i + 256 <= sig.length; i += 256) {
            for (int k = 0; k < 256; k++) buf[k] = (short) Math.max(-32767, Math.min(32767, (sig[i + k] * 0.6f + (rn.nextFloat() - 0.5f) * 0.002f) * 32767));
            in.process(buf, 256);
        }
        return n[0];
    }

    // ExerciseView.judgeChord ile aynı mantık (View sınıfını yüklememek için kopya)
    static final String[] JT = {"", "m", "5", "7", "m7", "maj7", "sus2", "sus4", "dim"};
    static String judge(float[] chroma, String target) {
        return ExerciseView.judgeChord(chroma, target);
    }
}
