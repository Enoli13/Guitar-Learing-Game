package com.enoli.perde;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tek bir alıştırmayı çizer ve yürütür. Mikrofon görevleri hatanın olduğu anda başarısız olur.
 */
public class ExerciseView extends LinearLayout {

    public interface Host {
        AudioIn audio();
        /** Mikrofon iznini garanti eder; izin varsa then çalışır. */
        void withMic(Runnable then);
        void onResult(Ex ex, boolean pass, String message);
        void skipMicTasks();
    }

    private final Context ctx;
    private Ex ex;
    private Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean finished, running;
    private AudioOut.Metronome met;
    private TextView status, heard;
    private Views.Tab tab;
    private Views.Staff staff;
    private Views.Fretboard fret;
    private Views.Beats beats;
    private Views.Meter meter;
    private Button startBtn;
    private float bpmBonus;
    private final Progress prog;

    public ExerciseView(Context c) {
        super(c);
        ctx = c;
        prog = Progress.get(c);
        setOrientation(VERTICAL);
    }

    int dp(float v) { return Ui.dp(ctx, v); }

    public void bind(Ex e, Host host, float bpmBonus) {
        this.ex = e; this.host = host; this.bpmBonus = bpmBonus;
        removeAllViews();
        finished = false; running = false;
        if (e.title != null && !e.title.isEmpty() && e.type != Ex.Type.INFO) addView(Ui.text(ctx, e.title, 14, Ui.MUTED, true));
        if (e.type != Ex.Type.INFO) {
            TextView pr = Ui.text(ctx, e.prompt, 20, Ui.TEXT, true);
            addView(pr, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 4, 12));
        }
        switch (e.type) {
            case INFO: buildInfo(); break;
            case CHOICE: buildChoice(); break;
            case FRET_TAP: buildFretTap(); break;
            case PLAY_NOTES: buildWaitMode(); break;
            case PLAY_SEQ: buildTimedSeq(); break;
            case PLAY_CHORD: buildChord(); break;
            case CHANGES: buildChanges(); break;
            case RHYTHM: buildRhythm(); break;
            case IMPROV: buildImprov(); break;
            case TUNE: buildTune(); break;
            case BEND: buildBend(); break;
            case CALIBRATE: buildCalibrate(); break;
        }
    }

    public void stop() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (met != null) { met.stop(); met = null; }
        if (host != null && host.audio() != null) host.audio().setListener(null);
        AudioOut.stopPlayback();
    }

    private void done(boolean pass, String msg) {
        if (finished) return;
        finished = true;
        stop();
        if (status != null) {
            status.setText(pass ? "✓ " + (msg == null ? "Harika!" : msg) : "✗ " + msg);
            status.setTextColor(pass ? Ui.GREEN : Ui.RED);
        }
        host.onResult(ex, pass, msg);
    }

    // ------------------------------------------------------------------ ortak parçalar
    private void addAudioButton() {
        if (ex.audio == null && ex.audioChords == null) return;
        Button b = Ui.button(ctx, "🔊  Dinle", Ui.BLUE, Ui.BLUE_D);
        b.setOnClickListener(v -> playPromptAudio());
        addView(b, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 4, 10));
        h.postDelayed(this::playPromptAudio, 350);
    }

    private void playPromptAudio() {
        if (ex.audioChords != null) {
            List<AudioOut.Ev> evs = new ArrayList<>();
            float t = 0;
            for (String ch : ex.audioChords) {
                int[] pcs = Theory.chordPcs(ch);
                int root = 48 + pcs[0];
                if (root > 54) root -= 12;
                evs.add(new AudioOut.Ev(root - 12, t, 1.1f));
                for (int i = 0; i < pcs.length; i++) {
                    int m = 48 + pcs[i];
                    while (m < root) m += 12;
                    evs.add(new AudioOut.Ev(m, t + i * 0.012f, 1.1f));
                }
                t += 1.2f;
            }
            AudioOut.play(AudioOut.render(evs));
        } else if (ex.audioHarmonic) {
            AudioOut.playChord(ex.audio, true);
        } else {
            AudioOut.playNotes(ex.audio);
        }
    }

    private void addVisuals() {
        if (ex.staffMidis != null) {
            staff = new Views.Staff(ctx);
            staff.set(ex.staffMidis);
            addView(staff);
        }
        if (ex.chordShape != null) {
            Views.Chord cv = new Views.Chord(ctx);
            cv.shape = ex.chordShape;
            cv.name = ex.type == Ex.Type.CHOICE ? "?" : (ex.chord != null ? ex.chord.split("@")[0] : "");
            addView(cv);
        }
        if (!ex.fretDots.isEmpty()) {
            fret = new Views.Fretboard(ctx);
            fret.from = ex.fretFrom; fret.to = ex.fretTo;
            for (int[] d : ex.fretDots) fret.addDot(d[0], d[1], Ui.BLUE, "");
            addView(fret);
        }
        if (!ex.notes.isEmpty() && ex.showTab && (ex.type == Ex.Type.CHOICE || ex.type == Ex.Type.PLAY_NOTES || ex.type == Ex.Type.PLAY_SEQ)) {
            tab = new Views.Tab(ctx);
            tab.beatsPerBar = ex.beatsPerBar;
            tab.hide = ex.hideTarget;
            tab.set(ex.notes);
            addView(tab);
        }
    }

    private void addStatus(String s) {
        status = Ui.text(ctx, s, 15, Ui.MUTED, true);
        status.setGravity(Gravity.CENTER);
        addView(status, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 10, 2));
        heard = Ui.text(ctx, "", 13, Ui.MUTED, false);
        heard.setGravity(Gravity.CENTER);
        addView(heard, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 0, 6));
    }

    private void addSkipMic() {
        Button skip = Ui.secondary(ctx, "Şu an gitar çalamam (1 saat atla)");
        skip.setTextSize(13);
        skip.setOnClickListener(v -> { stop(); host.skipMicTasks(); });
        addView(skip, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 14, 0));
    }

    private void showHeard(float midi) {
        if (heard == null) return;
        if (midi < 0) return;
        int m = Math.round(midi);
        int cents = Math.round((midi - m) * 100);
        heard.setText("🎤 Duyulan: " + Theory.midiName(m) + (cents >= 0 ? "  +" : "  ") + cents + "¢");
    }

    private boolean matches(int expected, int got, boolean strict) {
        int diff = got - expected;
        if (diff == 0) return true;
        if (diff % 12 == 0) {
            if (!strict) return true;
            return !prog.strictOctave && Math.abs(diff) == 12;
        }
        return false;
    }

    private boolean matchesNote(Ex.Note exp, int got, boolean strict) {
        if (matches(exp.midi, got, strict)) return true;
        if (exp.alts != null) for (int a : exp.alts) if (matches(a, got, strict)) return true;
        return false;
    }

    private void listen(AudioIn.Listener l) {
        host.withMic(() -> {
            AudioIn in = host.audio();
            in.sensitivityDb = prog.micSensitivity;
            in.onsetSensitivity = Math.max(0.5f, 1 + prog.micSensitivity / 10f);
            in.setListener(new AudioIn.Listener() {
                public void onFrame(long n, float m, float c, float db) { h.post(() -> { if (!finished) l.onFrame(n, m, c, db); }); }
                public void onNote(long n, float m, boolean a) { h.post(() -> { if (!finished) l.onNote(n, m, a); }); }
                public void onOnset(long n, float db) { h.post(() -> { if (!finished) l.onOnset(n, db); }); }
            });
            if (!in.isRunning()) {
                if (!in.start()) { status.setText("Mikrofon açılamadı"); status.setTextColor(Ui.RED); }
            }
        });
    }

    private long latencyNs() { return (long) (prog.latencyMs * 1e6); }

    // ------------------------------------------------------------------ INFO
    private void buildInfo() {
        LinearLayout card = Ui.card(ctx);
        if (ex.title != null && !ex.title.isEmpty()) card.addView(Ui.text(ctx, ex.title, 22, Ui.TEXT, true), Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 0, 10));
        TextView t = Ui.text(ctx, ex.body, 17, Ui.TEXT, false);
        card.addView(t);
        addView(card);
        Button b = Ui.primary(ctx, "Anladım");
        b.setOnClickListener(v -> done(true, null));
        addView(b, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 18, 0));
    }

    // ------------------------------------------------------------------ CHOICE
    private void buildChoice() {
        addVisuals();
        addAudioButton();
        for (int i = 0; i < ex.options.size(); i++) {
            final int idx = i;
            Button b = Ui.option(ctx, ex.options.get(i));
            b.setOnClickListener(v -> {
                if (finished) return;
                boolean ok = idx == ex.correct;
                v.setBackground(Ui.outlined(ok ? 0xFF1F3A12 : 0xFF3A1414, ok ? Ui.GREEN : Ui.RED, dp(2), dp(14)));
                if (!ok) {
                    View cv = getChildAt(indexOfChild(v) - idx + ex.correct);
                    if (cv != null) cv.setBackground(Ui.outlined(0xFF1F3A12, Ui.GREEN, dp(2), dp(14)));
                }
                done(ok, ok ? null : "Doğru cevap: " + ex.options.get(ex.correct) + (ex.explain.isEmpty() ? "" : "\n" + ex.explain));
            });
            addView(b, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 5, 5));
        }
    }

    // ------------------------------------------------------------------ FRET TAP
    private void buildFretTap() {
        fret = new Views.Fretboard(ctx);
        fret.from = 0; fret.to = ex.fretTo;
        fret.onTap = (s, f) -> {
            if (finished) return;
            int pc = Theory.pc(Theory.OPEN[s] + f);
            boolean ok = (ex.tapString == 0 || s == ex.tapString) && pc == ex.tapPc;
            fret.addDot(s, f, ok ? Ui.GREEN : Ui.RED, Theory.SHARP[pc]);
            if (!ok) {
                for (int ff = 0; ff <= ex.fretTo; ff++) if (Theory.pc(Theory.OPEN[ex.tapString] + ff) == ex.tapPc) fret.addDot(ex.tapString, ff, Ui.GREEN, Theory.SHARP[ex.tapPc]);
            }
            done(ok, ok ? null : "Dokunduğun: " + Theory.SHARP[pc] + " (" + s + ". tel, " + f + ". perde)");
        };
        addView(fret);
        addView(Ui.text(ctx, "Klavyede doğru yere dokun. Üstteki çizgi 1. tel (ince E).", 13, Ui.MUTED, false));
    }

    // ------------------------------------------------------------------ BEKLEME MODU
    private int next;
    private int lastHitMidi = -99;
    private long lastHitT;

    private void buildWaitMode() {
        addVisuals();
        addAudioButton();
        if (fret != null) {
            // ikinci nota (oktav/aralık) gizli: sadece kök işaretli
        }
        addStatus("🎤 Dinliyorum… çal");
        next = 0;
        if (staff != null) staff.current = 0;
        listen(new AudioIn.Listener() {
            public void onFrame(long n, float m, float c, float db) { showHeard(m); }
            public void onOnset(long n, float db) {}
            public void onNote(long t, float midi, boolean attack) {
                int m = Math.round(midi);
                Ex.Note exp = ex.notes.get(next);
                if (matchesNote(exp, m, ex.strictOctave)) {
                    markWait(next, 1);
                    lastHitMidi = m; lastHitT = t;
                    next++;
                    if (next >= ex.notes.size()) { done(true, null); return; }
                    if (tab != null) { tab.current = next; tab.invalidate(); }
                    if (staff != null) { staff.current = next; staff.invalidate(); }
                    status.setText("Doğru! Sıradaki…");
                    status.setTextColor(Ui.GREEN);
                    return;
                }
                if (lastHitMidi > 0 && (m == lastHitMidi || Math.abs(m - lastHitMidi) == 12) && !attack) return;
                if (!attack && t - lastHitT < 250_000_000L) return;
                markWait(next, 2);
                String want = ex.hideTarget ? "başka bir nota" : Theory.midiName(exp.midi);
                done(false, "Yanlış nota: " + Theory.midiName(m) + " çaldın, beklenen " + want);
            }
        });
        addSkipMic();
    }

    private void markWait(int i, int st) {
        if (tab != null && tab.state != null && i < tab.state.length) { tab.state[i] = st; tab.invalidate(); }
        if (staff != null && staff.state != null && i < staff.state.length) { staff.state[i] = st; staff.invalidate(); }
        if (fret != null && st == 1 && i > 0) {
            Ex.Note n = ex.notes.get(i);
            if (n.string > 0) fret.addDot(n.string, n.fret, Ui.GREEN, Theory.SHARP[Theory.pc(n.midi)]);
        }
    }

    // ------------------------------------------------------------------ ZAMANLI DİZİ
    private long[] T;
    private long[] W;
    private int countIn;
    private int bendIdx = -1;
    private float bendMax = 0;
    private long bendEnd;

    private float effectiveBpm() { return ex.bpm + bpmBonus; }

    private void buildTimedSeq() {
        addVisuals();
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = ex.beatsPerBar;
        beats.centerText = Math.round(effectiveBpm()) + " BPM";
        addView(beats);
        addStatus("Hazır olunca Başla'ya bas. Metronom 1 ölçü sayacak.");
        addStartButton(this::startTimedSeq);
        addSkipMic();
    }

    private void addStartButton(Runnable r) {
        startBtn = Ui.primary(ctx, "▶  Başla");
        startBtn.setOnClickListener(v -> {
            startBtn.setEnabled(false);
            startBtn.setAlpha(0.4f);
            host.withMic(r);
        });
        addView(startBtn, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 8, 0));
    }

    private void startMetronome(float bpm) {
        met = new AudioOut.Metronome(bpm, ex.beatsPerBar);
        met.audible = prog.metronomeAudible;
        met.start();
        countIn = ex.beatsPerBar;
        running = true;
    }

    private long beatT(double beat) { return met.beatNanos(countIn + beat) + latencyNs(); }

    private void startTimedSeq() {
        next = 0; lastHitMidi = -99; bendIdx = -1;
        startMetronome(effectiveBpm());
        double beatMs = 60000.0 / effectiveBpm();
        int n = ex.notes.size();
        T = new long[n]; W = new long[n];
        for (int i = 0; i < n; i++) {
            Ex.Note nt = ex.notes.get(i);
            float gapPrev = i > 0 ? nt.beat - ex.notes.get(i - 1).beat : 4;
            float gapNext = i < n - 1 ? ex.notes.get(i + 1).beat - nt.beat : 4;
            double w = Math.min(prog.timingWindowMs, 0.45 * Math.min(gapPrev, gapNext) * beatMs);
            if (nt.label != null && (nt.label.startsWith("h") || nt.label.startsWith("p") || nt.label.startsWith("s"))) w *= 1.5;
            W[i] = (long) (Math.max(45, w) * 1e6);
        }
        status.setText("Sayıyor…");
        listen(new AudioIn.Listener() {
            public void onFrame(long t, float m, float c, float db) {
                showHeard(m);
                if (bendIdx >= 0 && m > 0) bendMax = Math.max(bendMax, m);
            }
            public void onOnset(long t, float db) {}
            public void onNote(long t, float midi, boolean attack) { seqNote(t, midi, attack); }
        });
        tickSeq();
    }

    private void seqNote(long t, float midi, boolean attack) {
        if (!running || T == null) return;
        int m = Math.round(midi);
        // bend sürerken ara perdeleri yok say
        if (bendIdx >= 0 && t < bendEnd) {
            Ex.Note b = ex.notes.get(bendIdx);
            int base = b.midi - Integer.parseInt(b.label.substring(1));
            if (m >= base - 1 && m <= b.midi + 1) return;
        }
        if (next >= T.length) return;
        Ex.Note exp = ex.notes.get(next);
        int expMidi = exp.midi;
        boolean isBend = exp.label != null && exp.label.startsWith("b") && exp.label.length() > 1;
        if (isBend) expMidi = exp.midi - Integer.parseInt(exp.label.substring(1));
        long lo = T[next] - W[next], hi = T[next] + W[next];
        if (t < beatT(0) - 300_000_000L) return; // sayım sırasında yok say
        if (t < lo) {
            if (lastHitMidi > 0 && (m == lastHitMidi || Math.abs(m - lastHitMidi) == 12 || t - lastHitT < 120_000_000L)) return;
            if (!attack && lastHitMidi < 0) return;
            failSeq(next, "Erken ya da fazladan nota (" + Theory.midiName(m) + ")");
            return;
        }
        if (t <= hi) {
            if (isBend ? matches(expMidi, m, true) : matchesNote(exp, m, true)) {
                setTabState(next, 1);
                lastHitMidi = m; lastHitT = t;
                if (isBend) { bendIdx = next; bendMax = m; bendEnd = T[next] + (long) (exp.dur * 60e9 / effectiveBpm()); }
                next++;
                return;
            }
            if (!attack && lastHitMidi > 0 && m == lastHitMidi) return;
            failSeq(next, "Yanlış nota: " + Theory.midiName(m) + " (beklenen " + Theory.midiName(expMidi) + ")");
            return;
        }
        failSeq(next, "Geç kaldın: " + Math.round((t - T[next]) / 1e6) + " ms");
    }

    private void setTabState(int i, int s) { if (tab != null && tab.state != null) { tab.state[i] = s; tab.invalidate(); } }

    private void failSeq(int i, String msg) {
        setTabState(i, 2);
        done(false, msg);
    }

    private void tickSeq() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        if (tab != null) { tab.cursorBeat = (float) Math.max(0, beat); tab.invalidate(); }
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = beat < 0 ? "Sayım: " + (int) Math.ceil(-beat) : Math.round(effectiveBpm()) + " BPM";
        beats.invalidate();
        if (beat >= 0 && status.getText().toString().startsWith("Sayı")) { status.setText("Çal!"); status.setTextColor(Ui.BLUE); }
        if (bendIdx >= 0 && now > bendEnd + 150_000_000L) {
            Ex.Note b = ex.notes.get(bendIdx);
            if (bendMax < b.midi - 0.5f) { failSeq(bendIdx, "Bend hedefe ulaşmadı (" + Theory.midiName(b.midi) + ")"); return; }
            bendIdx = -1;
        }
        if (next < T.length && now > T[next] + W[next] + 150_000_000L) {
            failSeq(next, "Kaçırdın: " + Theory.midiName(ex.notes.get(next).midi) + " zamanında gelmedi");
            return;
        }
        if (next >= T.length && bendIdx < 0 && now > T[T.length - 1] + 300_000_000L) { done(true, "Zamanlama kusursuz!"); return; }
        h.postDelayed(this::tickSeq, 16);
    }

    // ------------------------------------------------------------------ AKOR
    private long ignoreUntil;

    private void buildChord() {
        addVisuals();
        addStatus("🎤 Akoru bas ve bir kez vur");
        ignoreUntil = System.nanoTime() + 700_000_000L;
        listen(new AudioIn.Listener() {
            boolean pending;
            public void onFrame(long n, float m, float c, float db) { showHeard(m); }
            public void onNote(long n, float m, boolean a) {}
            public void onOnset(long t, float db) {
                if (pending || t < ignoreUntil) return;
                pending = true;
                status.setText("Analiz ediliyor…");
                h.postDelayed(() -> {
                    float[] ch = host.audio().chromaAt(t + 40_000_000L, 320);
                    pending = false;
                    if (ch == null) return;
                    String bad = judgeChord(ch, ex.chord);
                    if (bad == null) done(true, ex.chord.split("@")[0] + " temiz!");
                    else done(false, "Duyulan akor " + bad + " gibi. Tüm telleri temiz çalıyor mu? Susturulması gereken teller çalmasın.");
                }, 420);
            }
        });
        addSkipMic();
    }

    static final String[] JUDGE_TYPES = {"", "m", "5", "7", "m7", "maj7", "sus2", "sus4", "dim"};

    /** Geçerse null, değilse duyulan en olası akorun adı. */
    public static String judgeChord(float[] chroma, String target) {
        int[] tp = Theory.chordPcs(target);
        boolean power = ((Theory.ChordType) Theory.parseChord(target)[1]).sym.equals("5");
        float ts = Dsp.templateScore(chroma, tp, power);
        float best = -1; String bestName = "?";
        int[] tsorted = tp.clone(); Arrays.sort(tsorted);
        for (int r = 0; r < 12; r++) for (String ty : JUDGE_TYPES) {
            String nm = Theory.SHARP[r] + ty;
            int[] pc = Theory.chordPcs(nm);
            int[] s = pc.clone(); Arrays.sort(s);
            if (Arrays.equals(s, tsorted)) continue;
            float sc = Dsp.templateScore(chroma, pc, ty.equals("5"));
            if (sc > best) { best = sc; bestName = nm; }
        }
        float margin = power ? 0.08f : 0.02f;
        if (ts > 0.5f && ts >= best - margin) return null;
        return ts >= best ? target : bestName;
    }

    // ------------------------------------------------------------------ AKOR GEÇİŞLERİ
    private int chordIdx;
    private boolean chordPending;

    private void buildChanges() {
        Views.Chord cur = new Views.Chord(ctx);
        cur.shape = Theory.shapeFor(ex.chords.get(0));
        cur.name = ex.chords.get(0).split("@")[0];
        addView(cur);
        TextView seqText = Ui.text(ctx, chordSeqText(0), 16, Ui.MUTED, true);
        seqText.setGravity(Gravity.CENTER);
        addView(seqText);
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = ex.beatsPerBar;
        beats.centerText = Math.round(effectiveBpm()) + " BPM";
        addView(beats);
        addStatus("Her akorun 1. vuruşunda tam zamanında vur.");
        addStartButton(() -> {
            chordIdx = 0; chordPending = false;
            startMetronome(effectiveBpm());
            final long wc = (long) (Math.max(160, prog.timingWindowMs * 1.5) * 1e6);
            listen(new AudioIn.Listener() {
                public void onFrame(long n, float m, float c, float db) {}
                public void onNote(long n, float m, boolean a) {}
                public void onOnset(long t, float db) {
                    if (chordIdx >= ex.chords.size() || chordPending) return;
                    long T0 = beatT(chordIdx * ex.beatsPerChord);
                    if (Math.abs(t - T0) > wc) return;
                    chordPending = true;
                    final int idx = chordIdx;
                    h.postDelayed(() -> {
                        if (finished) return;
                        float[] ch = host.audio().chromaAt(t + 40_000_000L, 300);
                        chordPending = false;
                        if (ch == null) return;
                        String bad = judgeChord(ch, ex.chords.get(idx));
                        if (bad != null) { done(false, ex.chords.get(idx).split("@")[0] + " yerine " + bad + " duyuldu"); return; }
                        chordIdx = idx + 1;
                        if (chordIdx >= ex.chords.size()) { h.postDelayed(() -> done(true, "Geçişler temiz ve zamanında!"), 300); return; }
                    }, 380);
                }
            });
            tickChanges(cur, seqText, wc);
        });
        addSkipMic();
    }

    private String chordSeqText(int i) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < ex.chords.size(); k++) {
            if (k > 0) sb.append("  ");
            String n = ex.chords.get(k).split("@")[0];
            sb.append(k == i ? "[" + n + "]" : n);
        }
        return sb.toString();
    }

    private void tickChanges(Views.Chord cur, TextView seqText, long wc) {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = beat < 0 ? "Sayım: " + (int) Math.ceil(-beat) : Math.round(effectiveBpm()) + " BPM";
        beats.invalidate();
        // görüntülenecek akor: bir sonraki akoru yarım vuruş önceden göster
        int show = (int) Math.max(0, Math.min(ex.chords.size() - 1, Math.floor((beat + 0.6) / ex.beatsPerChord)));
        String nm = ex.chords.get(show).split("@")[0];
        if (!nm.equals(cur.name)) { cur.name = nm; cur.shape = Theory.shapeFor(ex.chords.get(show)); cur.invalidate(); seqText.setText(chordSeqText(show)); }
        if (chordIdx < ex.chords.size() && !chordPending && now > beatT(chordIdx * ex.beatsPerChord) + wc + 200_000_000L) {
            done(false, ex.chords.get(chordIdx).split("@")[0] + " akoruna zamanında geçemedin");
            return;
        }
        h.postDelayed(() -> tickChanges(cur, seqText, wc), 16);
    }

    // ------------------------------------------------------------------ RİTİM
    private long[] RT;
    private boolean[] rHit;
    private int[] rIdx;
    private long rWin, lastOnsetMatched;

    private void buildRhythm() {
        if (ex.strumChord != null) {
            Views.Chord cv = new Views.Chord(ctx);
            cv.shape = Theory.shapeFor(ex.strumChord); cv.name = ex.strumChord;
            addView(cv);
        }
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = ex.beatsPerBar;
        beats.grid = ex.hits;
        beats.stepsPerBar = ex.grid;
        beats.rhythmText = ex.rhythmText;
        beats.gridState = new int[ex.hits.length];
        beats.centerText = Math.round(effectiveBpm()) + " BPM";
        addView(beats);
        addView(Ui.text(ctx, "↓ aşağı vuruş, ↑ yukarı vuruş, • sus (çalma ama el sallanmaya devam etsin)", 13, Ui.MUTED, false));
        addStatus("Hazır olunca Başla.");
        addStartButton(() -> {
            startMetronome(effectiveBpm());
            List<Long> ts = new ArrayList<>();
            List<Integer> idx = new ArrayList<>();
            double stepBeats = (double) ex.beatsPerBar / ex.grid;
            for (int i = 0; i < ex.hits.length; i++) if (ex.hits[i]) { ts.add(beatT(i * stepBeats)); idx.add(i); }
            RT = new long[ts.size()]; rIdx = new int[ts.size()]; rHit = new boolean[ts.size()];
            for (int i = 0; i < RT.length; i++) { RT[i] = ts.get(i); rIdx[i] = idx.get(i); }
            double stepMs = stepBeats * 60000 / effectiveBpm();
            rWin = (long) (Math.min(prog.timingWindowMs, stepMs * 0.45) * 1e6);
            lastOnsetMatched = 0;
            listen(new AudioIn.Listener() {
                public void onFrame(long n, float m, float c, float db) {}
                public void onNote(long n, float m, boolean a) {}
                public void onOnset(long t, float db) { rhythmOnset(t); }
            });
            tickRhythm();
        });
        addSkipMic();
    }

    private void rhythmOnset(long t) {
        if (!running || RT == null) return;
        if (t < beatT(0) - 2 * rWin) return;
        int best = -1; long bd = Long.MAX_VALUE;
        for (int i = 0; i < RT.length; i++) { long dd = Math.abs(t - RT[i]); if (dd < bd) { bd = dd; best = i; } }
        if (best >= 0 && bd <= rWin && !rHit[best]) {
            rHit[best] = true; lastOnsetMatched = t;
            beats.gridState[rIdx[best]] = 1; beats.invalidate();
            return;
        }
        if (t - lastOnsetMatched < 100_000_000L) return;
        if (best >= 0 && bd > rWin) {
            beats.gridState[rIdx[best]] = 2;
            done(false, t < RT[best] ? "Zamansız vuruş: " + Math.round((RT[best] - t) / 1e6) + " ms erken ya da sus yerinde çaldın" : "Zamansız vuruş: " + Math.round((t - RT[best]) / 1e6) + " ms geç ya da sus yerinde çaldın");
        } else done(false, "Fazladan vuruş");
    }

    private void tickRhythm() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = beat < 0 ? "Sayım: " + (int) Math.ceil(-beat) : Math.round(effectiveBpm()) + " BPM";
        beats.invalidate();
        boolean all = true;
        for (int i = 0; i < RT.length; i++) {
            if (!rHit[i]) {
                all = false;
                if (now > RT[i] + rWin + 140_000_000L) { beats.gridState[rIdx[i]] = 2; done(false, "Vuruşu kaçırdın"); return; }
            }
        }
        if (all && now > RT[RT.length - 1] + rWin + 150_000_000L) { done(true, "Ritim tam oturdu!"); return; }
        h.postDelayed(this::tickRhythm, 16);
    }

    // ------------------------------------------------------------------ DOĞAÇLAMA
    private int improvCount;

    private void buildImprov() {
        addVisuals();
        if (fret != null) { fret.clearDots(); for (int[] d : ex.fretDots) { int pc = Theory.pc(Theory.OPEN[d[0]] + d[1]); fret.addDot(d[0], d[1], pc == ex.allowedPcs[0] ? Ui.ORANGE : Ui.BLUE, Theory.SHARP[pc]); } }
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = ex.beatsPerBar;
        addView(beats);
        addStatus("Turuncu = kök nota. Başla'ya bas, " + ex.beats + " vuruş boyunca doğaçla.");
        addStartButton(() -> {
            improvCount = 0;
            startMetronome(effectiveBpm());
            listen(new AudioIn.Listener() {
                public void onFrame(long n, float m, float c, float db) { showHeard(m); }
                public void onOnset(long n, float db) {}
                public void onNote(long t, float midi, boolean a) {
                    if (t < beatT(0) - 200_000_000L) return;
                    int pc = Theory.pc(Math.round(midi));
                    boolean ok = false;
                    for (int p : ex.allowedPcs) if (p == pc) ok = true;
                    if (!ok) { done(false, "Gam dışı nota: " + Theory.SHARP[pc] + " — " + ex.scaleLabel + " notalarında kal"); return; }
                    improvCount++;
                    status.setText("Notalar: " + improvCount + " / en az " + (ex.beats / 2));
                }
            });
            tickImprov();
        });
        addSkipMic();
    }

    private void tickImprov() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = beat < 0 ? "Sayım: " + (int) Math.ceil(-beat) : "Kalan vuruş: " + Math.max(0, (int) Math.ceil(ex.beats - beat));
        beats.invalidate();
        if (beat >= ex.beats) {
            if (improvCount >= ex.beats / 2) done(true, improvCount + " nota, hepsi gam içinde!");
            else done(false, "Daha cesur ol: en az " + (ex.beats / 2) + " nota çalmalısın (" + improvCount + ")");
            return;
        }
        h.postDelayed(this::tickImprov, 16);
    }

    // ------------------------------------------------------------------ AKORT
    private void buildTune() {
        meter = new Views.Meter(ctx);
        meter.big = "—"; meter.small = "Bir tel çal";
        addView(meter);
        LinearLayout row = Ui.hbox(ctx);
        row.setGravity(Gravity.CENTER);
        final TextView[] chips = new TextView[7];
        String[] nm = {"", "e", "B", "G", "D", "A", "E"};
        for (int s = 6; s >= 1; s--) {
            TextView t = Ui.text(ctx, nm[s], 18, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.rounded(Ui.SURFACE2, dp(22)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(t, lp);
            chips[s] = t;
        }
        addView(row, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 8, 8));
        addStatus("Her teli tek tek çal; ibre ortada ve yeşil olunca o tel tamam.");
        final boolean[] tuned = new boolean[7];
        final int[] goodFrames = new int[7];
        listen(new AudioIn.Listener() {
            public void onNote(long n, float m, boolean a) {}
            public void onOnset(long n, float db) {}
            public void onFrame(long n, float midi, float c, float db) {
                if (midi < 0) { meter.active = false; meter.invalidate(); return; }
                int best = 1; float bd = 99;
                for (int s = 1; s <= 6; s++) { float d = Math.abs(midi - Theory.OPEN[s]); if (d < bd) { bd = d; best = s; } }
                if (bd > 3) { meter.big = Theory.midiName(Math.round(midi)); meter.small = "Bu bir açık tel değil"; meter.active = false; meter.invalidate(); return; }
                float cents = (midi - Theory.OPEN[best]) * 100;
                meter.active = true; meter.cents = cents;
                meter.big = Theory.SHARP[Theory.pc(Theory.OPEN[best])];
                meter.small = best + ". tel  " + (cents >= 0 ? "+" : "") + Math.round(cents) + "¢  " + (Math.abs(cents) < 5 ? "✓" : cents < 0 ? "→ sıkıştır" : "← gevşet");
                meter.invalidate();
                if (Math.abs(cents) < 6) { if (++goodFrames[best] > 10 && !tuned[best]) { tuned[best] = true; chips[best].setBackground(Ui.rounded(Ui.GREEN, dp(22))); } }
                else goodFrames[best] = 0;
                boolean all = true;
                for (int s = 1; s <= 6; s++) all &= tuned[s];
                if (all) { prog.unlock("akort"); done(true, "Gitar akortlu!"); }
            }
        });
        Button ok = Ui.secondary(ctx, "Gitarım zaten akortlu, devam");
        ok.setOnClickListener(v -> { prog.unlock("akort"); done(true, null); });
        addView(ok, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 12, 0));
    }

    // ------------------------------------------------------------------ BEND
    private void buildBend() {
        addVisuals();
        meter = new Views.Meter(ctx);
        meter.big = Theory.SHARP[Theory.pc((int) ex.bendTarget)];
        meter.small = "Hedef perde";
        meter.progress = 0;
        addView(meter);
        addStatus("🎤 Notayı çal ve bük");
        final float base = ex.bendTarget - ex.bendSemis;
        final boolean[] started = {false};
        final int[] hold = {0};
        final long deadline = System.nanoTime() + 25_000_000_000L;
        listen(new AudioIn.Listener() {
            public void onNote(long n, float m, boolean a) {}
            public void onOnset(long n, float db) {}
            public void onFrame(long n, float midi, float c, float db) {
                if (System.nanoTime() > deadline) { done(false, "Süre doldu"); return; }
                if (midi < 0) { hold[0] = 0; return; }
                showHeard(midi);
                if (!started[0]) {
                    if (Math.abs(midi - base) < 0.5f) { started[0] = true; status.setText("Şimdi bük!"); status.setTextColor(Ui.BLUE); }
                    else if (Math.abs(midi - base) > 0.5f && Math.abs(midi - ex.bendTarget) > 0.5f) {
                        int m = Math.round(midi);
                        if (Theory.pc(m) != Theory.pc(Math.round(base))) { done(false, "Yanlış perde: " + Theory.midiName(m) + ". " + ex.bendFret + ". perdeden başla"); }
                    }
                    return;
                }
                float prog01 = (midi - base) / ex.bendSemis;
                meter.progress = prog01; meter.invalidate();
                if (midi > ex.bendTarget + 0.6f) { done(false, "Fazla büktün — hedefi aştın"); return; }
                if (Math.abs(midi - ex.bendTarget) < 0.3f) { if (++hold[0] >= 9) done(true, "Bend tam hedefte!"); }
                else hold[0] = 0;
            }
        });
        addSkipMic();
    }

    // ------------------------------------------------------------------ KALİBRASYON
    private final List<Long> calOffsets = new ArrayList<>();

    private void buildCalibrate() {
        ex.beatsPerBar = 4;
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = 4;
        addView(beats);
        addView(Ui.text(ctx, "Telleri susturup her metronom tıkında bir kez vur (8 vuruş). Uygulama ses gecikmesini ölçecek.", 15, Ui.TEXT, false));
        addStatus("Hazır olunca Başla.");
        addStartButton(() -> {
            calOffsets.clear();
            startMetronome(80);
            final long period = (long) (60e9 / 80);
            listen(new AudioIn.Listener() {
                public void onFrame(long n, float m, float c, float db) {}
                public void onNote(long n, float m, boolean a) {}
                public void onOnset(long t, float db) {
                    double k = (t - met.beatNanos(countIn)) / (double) period;
                    long kk = Math.round(k);
                    if (kk < 0 || kk >= 8) return;
                    long off = t - met.beatNanos(countIn + kk);
                    if (Math.abs(off) < 300_000_000L) { calOffsets.add(off); status.setText("Vuruş: " + calOffsets.size() + "/8"); }
                }
            });
            tickCal();
        });
        Button skip = Ui.secondary(ctx, "Şimdilik atla");
        skip.setOnClickListener(v -> done(true, null));
        addView(skip, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 10, 0));
    }

    private void tickCal() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / 80);
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = beat < 0 ? "Sayım: " + (int) Math.ceil(-beat) : "Vur!";
        beats.invalidate();
        if (beat > 8.6) {
            if (calOffsets.size() >= 5) {
                List<Long> s = new ArrayList<>(calOffsets);
                java.util.Collections.sort(s);
                long med = s.get(s.size() / 2);
                prog.latencyMs = med / 1e6f;
                prog.calibrated = true;
                prog.save();
                done(true, "Gecikme: " + Math.round(prog.latencyMs) + " ms olarak ayarlandı");
            } else done(false, "Yeterli vuruş duyulmadı (" + calOffsets.size() + "/8). Daha güçlü vur ya da telefonu yaklaştır.");
            return;
        }
        h.postDelayed(this::tickCal, 16);
    }
}
