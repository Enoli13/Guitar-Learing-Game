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
    private Art.Mascot mascot;
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
        finished = false; running = false; misses = 0; recentHits.clear(); lastHitMidi = -99;
        if (e.title != null && !e.title.isEmpty() && e.type != Ex.Type.INFO) addView(Ui.text(ctx, e.title, 14, Ui.MUTED, true));
        mascot = new Art.Mascot(ctx);
        if (e.type != Ex.Type.INFO) {
            mascot.setMood(LessonActivity.isMic(e.type) ? Art.Mascot.Mood.IDLE : Art.Mascot.Mood.THINK);
            LinearLayout row = Art.speech(ctx, mascot, e.prompt, 84, 18);
            addView(row, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 4, 12));
            Ui.slideUp(row);
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
            case SING: buildSing(); break;
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
        if (mascot != null && ex.type != Ex.Type.INFO) mascot.setMood(pass ? Art.Mascot.Mood.HAPPY : Art.Mascot.Mood.SAD);
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

    private long audioEndsAt;

    private void playPromptAudio() {
        List<AudioOut.Ev> evs = new ArrayList<>();
        float t = 0;
        if (ex.audioChords != null) {
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
        }
        if (ex.audio != null) {
            if (ex.audioHarmonic) {
                for (int i = 0; i < ex.audio.length; i++) evs.add(new AudioOut.Ev(ex.audio[i], t + i * 0.03f, 1.6f));
                t += 1.8f;
            } else {
                if (ex.audioChords != null) t += 0.4f;
                for (int i = 0; i < ex.audio.length; i++) evs.add(new AudioOut.Ev(ex.audio[i], t + i * 0.55f, 0.5f));
                t += ex.audio.length * 0.55f;
            }
        }
        if (evs.isEmpty()) return;
        AudioOut.play(AudioOut.render(evs));
        audioEndsAt = System.nanoTime() + (long) ((t + 0.6f) * 1e9);
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

    static boolean isLegato(Ex.Note n) {
        return n.label != null && (n.label.startsWith("h") || n.label.startsWith("p") || n.label.startsWith("s") || n.label.startsWith("b"));
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
            if (mascot != null) mascot.setMood(Art.Mascot.Mood.LISTEN);
            if (!in.isRunning()) {
                if (!in.start()) { status.setText("Mikrofon açılamadı"); status.setTextColor(Ui.RED); }
            }
        });
    }

    private long latencyNs() { return (long) (prog.latencyMs * 1e6); }

    // ------------------------------------------------------------------ INFO
    private void buildInfo() {
        mascot.setMood(Art.Mascot.Mood.TEACH);
        LinearLayout head = Art.speech(ctx, mascot, ex.title == null || ex.title.isEmpty() ? "Yeni bilgi!" : ex.title, 96, 20);
        addView(head, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 0, 12));
        LinearLayout card = Ui.card(ctx);
        TextView t = Ui.text(ctx, ex.body, 17, Ui.TEXT, false);
        card.addView(t);
        addView(card);
        Ui.slideUp(card);
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
            Ui.pressable(b);
            b.setOnClickListener(v -> {
                if (finished) return;
                boolean ok = idx == ex.correct;
                v.setBackground(Ui.outlined(ok ? 0xFF1F3A12 : 0xFF3A1414, ok ? Ui.GREEN : Ui.RED, dp(2), dp(14)));
                if (ok) Ui.pop(v); else Ui.shake(v);
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
            if (ok) Ui.pop(fret); else Ui.shake(fret);
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
    private final java.util.ArrayDeque<long[]> recentHits = new java.util.ArrayDeque<>(); // {midi, zaman}

    private void rememberHit(int midi, long t) {
        recentHits.addLast(new long[]{midi, t});
        while (recentHits.size() > 8) recentHits.removeFirst();
    }

    /** Bu perde son 3 saniyede çalınmış ve hâlâ titreşiyor olabilir mi? */
    private boolean stillRinging(int m, long t) {
        for (long[] r : recentHits) {
            int d = Math.abs(m - (int) r[0]);
            if ((d == 0 || d == 12 || d == 19 || d == 24) && t - r[1] < 3_000_000_000L) return true;
        }
        return false;
    }
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
                // önceki telin titreşimi / el değmesi gibi vuruşsuz perde değişimlerini yok say (legato hariç)
                if (!attack && !isLegato(exp)) return;
                if (matchesNote(exp, m, ex.strictOctave)) {
                    markWait(next, 1);
                    mascot.pop();
                    rememberHit(m, t);
                    lastHitMidi = m; lastHitT = t;
                    next++;
                    if (next >= ex.notes.size()) { done(true, passMsg("Harika!")); return; }
                    if (tab != null) { tab.current = next; tab.invalidate(); }
                    if (staff != null) { staff.current = next; staff.invalidate(); }
                    status.setText("Doğru! Sıradaki…");
                    status.setTextColor(Ui.GREEN);
                    return;
                }
                if (lastHitMidi > 0 && (m == lastHitMidi || Math.abs(m - lastHitMidi) == 12) && !attack) return;
                if (!attack && t - lastHitT < 250_000_000L) return;
                if (stillRinging(m, t)) return; // önceki telin titreşimi: yeni notayı beklemeye devam et
                String want = ex.hideTarget ? "başka bir nota" : Theory.midiName(exp.midi);
                String msg = "Yanlış nota: " + Theory.midiName(m) + " çaldın, beklenen " + want;
                if (forgive(msg)) return;
                markWait(next, 2);
                done(false, msg);
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
    private long[] T; // kullanılmıyor (eski)
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
        addStatus("Başla'ya bas: 5 sn sessiz geri sayım, sonra sayım ölçüsü, sonra \"ŞİMDİ!\" yazınca çalmaya başla.");
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
        // 5 sn sessiz geri sayım, sonra (farklı sesli) sayım ölçüsü, sonra 1. vuruş
        met = new AudioOut.Metronome(bpm, ex.beatsPerBar, LEAD_SECONDS);
        met.audible = prog.metronomeAudible;
        // yavaş tempolarda 2 ölçü sayım: ritme oturmak için zaman
        countIn = ex.beatsPerBar * (bpm <= 80 ? 2 : 1);
        met.countInBeats = countIn;
        met.start();
        running = true;
        misses = 0;
    }

    static final float LEAD_SECONDS = 5f;

    /** Geri sayım / sayım / "ŞİMDİ" / normal metin. beat: 1. vuruşa göre (sayımda negatif). */
    private String phaseText(double beat, String normal) {
        long now = System.nanoTime(), first = met.beatNanos(0);
        if (now < first) return "⏳ Hazırlan… " + (int) Math.ceil((first - now) / 1e9);
        if (beat < 0) {
            int b = (int) Math.floor(beat + countIn);
            return "Sayım: " + (b % ex.beatsPerBar + 1) + "   (" + (int) Math.ceil(-beat) + " vuruş sonra başla)";
        }
        if (beat < 1) return "▶ ŞİMDİ!";
        return normal;
    }

    // ---- zorluk ----
    private int misses;

    /** Tek yol: sabit tolerans; ısınma görevleri kendi (daha geniş) toleransını getirir. */
    private float windowMs() { return Math.max(prog.timingWindowMs, ex.tolMs); }

    private int allowedMisses() { return 0; }

    /** Kolay modda küçük hataları affeder: true dönerse görev devam eder. */
    private boolean forgive(String msg) {
        if (misses >= allowedMisses()) return false;
        misses++;
        int left = allowedMisses() - misses;
        status.setText("⚠ " + msg + " — devam et! (kalan hata hakkı: " + left + ")");
        status.setTextColor(Ui.YELLOW);
        if (mascot != null) {
            mascot.setMood(Art.Mascot.Mood.THINK);
            h.postDelayed(() -> { if (!finished && mascot != null) mascot.setMood(Art.Mascot.Mood.LISTEN); }, 700);
        }
        return true;
    }

    private String passMsg(String perfect) {
        return perfect;
    }

    private long beatT(double beat) { return met.beatNanos(countIn + beat) + latencyNs(); }

    private void startTimedSeq() {
        next = 0; lastHitMidi = -99; bendIdx = -1;
        startMetronome(effectiveBpm());
        double beatMs = 60000.0 / effectiveBpm();
        int n = ex.notes.size();
        T = new long[0]; W = new long[n];
        for (int i = 0; i < n; i++) {
            Ex.Note nt = ex.notes.get(i);
            float gapPrev = i > 0 ? nt.beat - ex.notes.get(i - 1).beat : 4;
            float gapNext = i < n - 1 ? ex.notes.get(i + 1).beat - nt.beat : 4;
            double w = Math.min(windowMs(), 0.45 * Math.min(gapPrev, gapNext) * beatMs);
            if (nt.label != null && (nt.label.startsWith("h") || nt.label.startsWith("p") || nt.label.startsWith("s"))) w *= 1.5;
            W[i] = (long) (Math.max(45, w) * 1e6);
        }
        status.setText("Hazırlan: 5 sn sessizlik, sonra sayım, sonra başla.");
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

    /** Notanın hedef zamanı: metronomun gerçek çıkış zamanından her seferinde yeniden hesaplanır. */
    private long tAt(int i) { return beatT(ex.notes.get(i).beat); }

    private void seqNote(long t, float midi, boolean attack) {
        if (!running || W == null) return;
        int m = Math.round(midi);
        // bend sürerken ara perdeleri yok say
        if (bendIdx >= 0 && t < bendEnd) {
            Ex.Note b = ex.notes.get(bendIdx);
            int base = b.midi - Integer.parseInt(b.label.substring(1));
            if (m >= base - 1 && m <= b.midi + 1) return;
        }
        if (next >= W.length) return;
        Ex.Note exp = ex.notes.get(next);
        if (!attack && !isLegato(exp)) return; // önceki telin titreşimi yeni nota sayılmaz
        int expMidi = exp.midi;
        boolean isBend = exp.label != null && exp.label.startsWith("b") && exp.label.length() > 1;
        if (isBend) expMidi = exp.midi - Integer.parseInt(exp.label.substring(1));
        long tn = tAt(next), lo = tn - W[next], hi = tn + W[next];
        if (t < beatT(0) - 300_000_000L) return; // sayım sırasında yok say
        if (t < lo) {
            if (lastHitMidi > 0 && (m == lastHitMidi || Math.abs(m - lastHitMidi) == 12 || t - lastHitT < 120_000_000L)) return;
            if (stillRinging(m, t)) return;
            if (!attack && lastHitMidi < 0) return;
            failSeq(next, "Erken ya da fazladan nota (" + Theory.midiName(m) + ")", false);
            return;
        }
        if (t <= hi) {
            if (isBend ? matches(expMidi, m, true) : matchesNote(exp, m, true)) {
                setTabState(next, 1);
                rememberHit(m, t);
                lastHitMidi = m; lastHitT = t;
                if (isBend) { bendIdx = next; bendMax = m; bendEnd = tn + (long) (exp.dur * 60e9 / effectiveBpm()); }
                next++;
                return;
            }
            if (!attack && lastHitMidi > 0 && m == lastHitMidi) return;
            if (stillRinging(m, t)) return; // önceki tel çalmaya devam ediyor; doğru nota gelirse yine kabul edilir
            failSeq(next, "Yanlış nota: " + Theory.midiName(m) + " (beklenen " + Theory.midiName(expMidi) + ")", true);
            return;
        }
        failSeq(next, "Geç kaldın: " + Math.round((t - tn) / 1e6) + " ms", true);
    }

    private void setTabState(int i, int s) { if (tab != null && tab.state != null) { tab.state[i] = s; tab.invalidate(); } }

    private void failSeq(int i, String msg, boolean skipNote) {
        setTabState(i, 2);
        if (forgive(msg)) { if (skipNote && i == next) next++; return; }
        done(false, msg);
    }

    private void tickSeq() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        if (tab != null) { tab.cursorBeat = (float) Math.max(0, beat); tab.invalidate(); }
        beats.beatPos = (float) (beat + countIn);
        beats.centerText = phaseText(beat, Math.round(effectiveBpm()) + " BPM");
        beats.invalidate();
        if (beat >= 0 && status.getText().toString().startsWith("Hazırlan")) { status.setText("Çal!"); status.setTextColor(Ui.BLUE); }
        if (bendIdx >= 0 && now > bendEnd + 150_000_000L) {
            Ex.Note b = ex.notes.get(bendIdx);
            if (bendMax < b.midi - 0.5f) { int bi = bendIdx; bendIdx = -1; failSeq(bi, "Bend hedefe ulaşmadı (" + Theory.midiName(b.midi) + ")", false); if (finished) return; }
            bendIdx = -1;
        }
        if (next < W.length && now > tAt(next) + W[next] + 380_000_000L) {
            failSeq(next, "Kaçırdın: " + Theory.midiName(ex.notes.get(next).midi) + " zamanında gelmedi", true);
            if (finished) return;
        }
        if (next >= W.length && bendIdx < 0 && now > tAt(W.length - 1) + 300_000_000L) { done(true, passMsg("Zamanlama kusursuz!")); return; }
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
            final long wc = (long) (Math.max(160, windowMs() * 1.5) * 1e6);
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
                        if (bad != null) {
                            String msg = ex.chords.get(idx).split("@")[0] + " yerine " + bad + " duyuldu";
                            if (!forgive(msg)) { done(false, msg); return; }
                        }
                        chordIdx = idx + 1;
                        if (chordIdx >= ex.chords.size()) { h.postDelayed(() -> done(true, passMsg("Geçişler temiz ve zamanında!")), 300); return; }
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
        beats.centerText = phaseText(beat, Math.round(effectiveBpm()) + " BPM");
        beats.invalidate();
        // görüntülenecek akor: bir sonraki akoru yarım vuruş önceden göster
        int show = (int) Math.max(0, Math.min(ex.chords.size() - 1, Math.floor((beat + 0.6) / ex.beatsPerChord)));
        String nm = ex.chords.get(show).split("@")[0];
        if (!nm.equals(cur.name)) { cur.name = nm; cur.shape = Theory.shapeFor(ex.chords.get(show)); cur.invalidate(); seqText.setText(chordSeqText(show)); }
        if (chordIdx < ex.chords.size() && !chordPending && now > beatT(chordIdx * ex.beatsPerChord) + wc + 450_000_000L) {
            String msg = ex.chords.get(chordIdx).split("@")[0] + " akoruna zamanında geçemedin";
            if (!forgive(msg)) { done(false, msg); return; }
            chordIdx++;
            if (chordIdx >= ex.chords.size()) { done(true, passMsg("Bitti!")); return; }
        }
        h.postDelayed(() -> tickChanges(cur, seqText, wc), 16);
    }

    // ------------------------------------------------------------------ RİTİM
    /** Bir ritim izi: beklenen vuruşlar (vuruş cinsinden) ve durumları. */
    private final class Track {
        double[] beat; boolean[] hit; int[] disp; long win, lat, lastMatched; boolean tap; String who;
        long at(int i) { return met.beatNanos(countIn + beat[i]) + lat; }
    }

    private Track main, pulse;     // main: kalıp (mikrofon ya da dokunma); pulse: iki parçalı egzersizde dokunulan nabız
    private int onsetsHeard;
    private Views.RhythmNotation notation;

    private Track makeTrack(List<Double> bs, List<Integer> disp, boolean tap, float tolMs, double minGapBeats) {
        Track t = new Track();
        t.beat = new double[bs.size()]; t.hit = new boolean[bs.size()]; t.disp = new int[bs.size()];
        for (int i = 0; i < t.beat.length; i++) { t.beat[i] = bs.get(i); t.disp[i] = disp.get(i); }
        t.tap = tap;
        t.lat = tap ? 0 : latencyNs();
        double gapMs = minGapBeats * 60000 / effectiveBpm();
        t.win = (long) (Math.min(tolMs, gapMs * 0.45) * 1e6);
        return t;
    }

    private void markDisp(Track t, int i, int st) {
        if (t == pulse) return;
        if (notation != null) { if (t.disp[i] < notation.state.length) notation.state[t.disp[i]] = st; notation.invalidate(); }
        else if (beats != null && beats.gridState != null) { beats.gridState[t.disp[i]] = st; beats.invalidate(); }
    }

    private void buildRhythm() {
        if (ex.freeCount > 0) { buildFreeStrum(); return; }
        if (ex.strumChord != null && !ex.tapMode && !ex.voice) {
            Views.Chord cv = new Views.Chord(ctx);
            cv.shape = Theory.shapeFor(ex.strumChord); cv.name = ex.strumChord;
            addView(cv);
        }
        beats = new Views.Beats(ctx);
        beats.beatsPerBar = ex.beatsPerBar;
        if (ex.notation == null) {
            beats.grid = ex.hits;
            beats.stepsPerBar = ex.grid;
            beats.rhythmText = ex.rhythmText;
            beats.gridState = new int[ex.hits.length];
        }
        beats.centerText = Math.round(effectiveBpm()) + " BPM";
        addView(beats);
        if (ex.notation != null) {
            notation = new Views.RhythmNotation(ctx);
            notation.set(ex.notation, ex.beatsPerBar);
            addView(notation);
        }
        String how;
        if (ex.pulseTap) how = "Sol elinle (ya da başparmağınla) her vuruşta DOKUN düğmesine bas; aynı anda ritmi “ta” diye sesle söyle.";
        else if (ex.voice) how = "Ritmi “ta” hecesiyle, kısa ve net söyle. Sus olan yerde sessiz kal.";
        else if (ex.tapMode) how = ex.notation != null ? "Notaların başladığı anlarda dokun, suslarda dokunma." : "↓ olan yerlerde dokun, • olan yerlerde dokunma.";
        else how = ex.notation != null ? "Notaların başladığı anlarda telleri susturup vur; suslarda vurma." : "↓ aşağı vuruş, ↑ yukarı vuruş, • sus (çalma ama el sallanmaya devam etsin)";
        if (ex.gapFromBar > 0) how += "\n🤫 " + ex.gapFromBar + (ex.gapToBar > ex.gapFromBar ? "–" + ex.gapToBar : "") + ". ölçüde metronom susacak: nabzı içinden sürdür!";
        addView(Ui.text(ctx, how, 14, Ui.MUTED, false));
        addStatus("Başla'ya bas: önce 5 sn sessiz geri sayım, sonra tok sesli sayım ölçüsü, sonra \"ŞİMDİ!\" yazınca 1. vuruş.");
        boolean needPad = ex.tapMode || ex.pulseTap;
        boolean needMic = !ex.tapMode;
        Button pad = null;
        if (needPad) {
            pad = Ui.button(ctx, ex.pulseTap ? "👆  NABIZ" : "👆  DOKUN", Ui.PURPLE, Ui.darker(Ui.PURPLE, 0.7f));
            pad.setTextSize(24);
            pad.setEnabled(false);
            final Button fpad = pad;
            pad.setOnTouchListener((v, e) -> {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN && fpad.isEnabled()) {
                    long ago = (android.os.SystemClock.uptimeMillis() - e.getEventTime()) * 1_000_000L;
                    onTrackEvent(ex.pulseTap ? pulse : main, System.nanoTime() - ago);
                    fpad.animate().scaleX(0.92f).scaleY(0.92f).setDuration(50).withEndAction(() -> fpad.animate().scaleX(1).scaleY(1).setDuration(120).start()).start();
                }
                return true;
            });
            addView(pad, new LinearLayout.LayoutParams(Ui.MATCH, dp(ex.pulseTap ? 110 : 150)));
        }
        final Button fpad2 = pad;
        Runnable go = () -> {
            startMetronome(effectiveBpm());
            if (ex.gapFromBar > 0) {
                int to = Math.max(ex.gapFromBar, ex.gapToBar);
                met.setMute(countIn + (ex.gapFromBar - 1) * ex.beatsPerBar, countIn + to * ex.beatsPerBar);
            }
            onsetsHeard = 0;
            List<Double> bs = new ArrayList<>();
            List<Integer> idx = new ArrayList<>();
            double minGap;
            if (ex.hitBeats != null) {
                for (int i = 0; i < ex.hitBeats.length; i++) { bs.add((double) ex.hitBeats[i]); idx.add(ex.hitDisp[i]); }
                minGap = 4;
                for (int i = 1; i < ex.hitBeats.length; i++) minGap = Math.min(minGap, ex.hitBeats[i] - ex.hitBeats[i - 1]);
            } else {
                double stepBeats = (double) ex.beatsPerBar / ex.grid;
                for (int i = 0; i < ex.hits.length; i++) if (ex.hits[i]) { bs.add(i * stepBeats); idx.add(i); }
                minGap = stepBeats;
            }
            float tol = windowMs();
            if (ex.tapMode) tol = Math.max(tol, 150);
            if (ex.voice) tol = Math.max(tol, 160);
            main = makeTrack(bs, idx, ex.tapMode, tol, minGap);
            pulse = null;
            if (ex.pulseTap) {
                List<Double> pb = new ArrayList<>(); List<Integer> pi = new ArrayList<>();
                int total = (int) Math.ceil(bs.isEmpty() ? ex.beatsPerBar : (bs.get(bs.size() - 1) + 0.01) / ex.beatsPerBar) * ex.beatsPerBar;
                for (int b = 0; b < total; b++) { pb.add((double) b); pi.add(b); }
                pulse = makeTrack(pb, pi, true, Math.max(tol, 170), 1);
            }
            if (fpad2 != null) fpad2.setEnabled(true);
            if (mascot != null) mascot.setMood(Art.Mascot.Mood.LISTEN);
            if (needMic) {
                listen(new AudioIn.Listener() {
                    public void onFrame(long n, float m, float c, float db) {}
                    public void onNote(long n, float m, boolean a) {}
                    public void onOnset(long t, float db) { onsetsHeard++; flashHeard(); onTrackEvent(main, t); }
                });
            }
            tickRhythm();
        };
        startBtn = Ui.primary(ctx, "▶  Başla");
        startBtn.setOnClickListener(v -> {
            startBtn.setEnabled(false); startBtn.setAlpha(0.4f);
            if (needMic) host.withMic(go); else go.run();
        });
        addView(startBtn, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 8, 0));
        if (needMic) addSkipMic();
    }

    private void flashHeard() {
        if (heard == null) return;
        heard.setText("🎤 vuruş duyuldu (" + onsetsHeard + ")");
        heard.setTextColor(Ui.BLUE);
        heard.animate().cancel();
        heard.setAlpha(1f);
        heard.animate().alpha(0.35f).setDuration(500).start();
    }

    /** Metronomsuz serbest vuruş: mikrofonun seni duyduğunu doğrular, hata yok. */
    private void buildFreeStrum() {
        Views.Ring ring = new Views.Ring(ctx);
        ring.color = Ui.BLUE;
        ring.label = "0 / " + ex.freeCount;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), dp(140));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        addView(ring, lp);
        addStatus("🎤 Dinliyorum… telleri susturup vur");
        onsetsHeard = 0;
        listen(new AudioIn.Listener() {
            public void onFrame(long n, float m, float c, float db) {}
            public void onNote(long n, float m, boolean a) {}
            public void onOnset(long t, float db) {
                onsetsHeard++;
                ring.value = (float) onsetsHeard / ex.freeCount;
                ring.label = onsetsHeard + " / " + ex.freeCount;
                ring.invalidate();
                Ui.pop(ring);
                if (mascot != null) mascot.pop();
                if (onsetsHeard >= ex.freeCount) done(true, "Mikrofon vuruşlarını net duyuyor!");
            }
        });
        TextView hint = Ui.text(ctx, "Sayaç artmıyorsa telefonu gitara yaklaştır ya da Profil → Ayarlar → Mikrofon eşiğini düşür.", 13, Ui.MUTED, false);
        addView(hint, Ui.lpm(ctx, Ui.MATCH, Ui.WRAP, 10, 0));
        addSkipMic();
    }

    private void onTrackEvent(Track tr, long t) {
        if (!running || tr == null || finished) return;
        if (t < tr.at(0) - 2 * tr.win) return; // sayım sırasında yok say
        int best = -1; long bd = Long.MAX_VALUE;
        for (int i = 0; i < tr.beat.length; i++) { if (tr.hit[i]) continue; long dd = Math.abs(t - tr.at(i)); if (dd < bd) { bd = dd; best = i; } }
        if (best >= 0 && bd <= tr.win) {
            tr.hit[best] = true; tr.lastMatched = t;
            markDisp(tr, best, 1);
            if (mascot != null && tr.tap) mascot.pop();
            return;
        }
        if (t - tr.lastMatched < 100_000_000L) return; // aynı vuruşun ikinci algısı
        String who = tr == pulse ? "Nabız: " : "";
        String msg;
        if (best >= 0) {
            long d = t - tr.at(best);
            msg = who + (d < 0 ? "Biraz erken (" + Math.round(-d / 1e6) + " ms)" : "Biraz geç (" + Math.round(d / 1e6) + " ms)");
        } else msg = who + "Fazladan vuruş";
        tr.lastMatched = t;
        if (best >= 0) markDisp(tr, best, 2);
        done(false, msg + (tr.tap ? "" : ex.voice ? " ya da sus yerinde ses çıkardın" : " ya da sus yerinde çaldın"));
    }

    private boolean checkMisses(Track tr, long now) {
        if (tr == null) return true;
        boolean all = true;
        for (int i = 0; i < tr.beat.length; i++) {
            if (tr.hit[i]) continue;
            all = false;
            if (now > tr.at(i) + tr.win + (tr.tap ? 150_000_000L : 400_000_000L)) {
                markDisp(tr, i, 2);
                if (!tr.tap && onsetsHeard == 0) {
                    done(false, ex.voice ? "Mikrofon sesini duymadı. “Ta” hecesini daha yüksek ve kısa söyle, telefonu yaklaştır."
                            : "Mikrofon hiç vuruş duymadı. Telefonu gitara yaklaştır, daha güçlü vur ya da Profil → Ayarlar → Mikrofon eşiğini düşür.");
                } else done(false, (tr == pulse ? "Nabzı kaçırdın: " : "Bir vuruşu kaçırdın: ") + (i + 1) + ". vuruş");
                return false;
            }
        }
        return all;
    }

    private void tickRhythm() {
        if (!running || finished) return;
        long now = System.nanoTime();
        double beat = (now - met.beatNanos(countIn)) / (60e9 / effectiveBpm());
        beats.beatPos = (float) (beat + countIn);
        beats.gridPos = (float) beat;
        boolean silent = met.isMuted(beat + countIn);
        beats.hideDots = silent;
        beats.centerText = phaseText(beat, silent ? "🤫 içinden say!" : Math.round(effectiveBpm()) + " BPM");
        beats.invalidate();
        if (notation != null) { notation.cursorBeat = (float) beat; notation.invalidate(); }
        boolean a = checkMisses(main, now);
        if (finished) return;
        boolean b = checkMisses(pulse, now);
        if (finished) return;
        long lastT = main.beat.length > 0 ? main.at(main.beat.length - 1) : now;
        if (pulse != null && pulse.beat.length > 0) lastT = Math.max(lastT, pulse.at(pulse.beat.length - 1));
        if (a && b && now > lastT + main.win + 200_000_000L) { done(true, passMsg(ex.gapFromBar > 0 ? "Nabzı içinde taşıyorsun!" : "Ritim tam oturdu!")); return; }
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
                    if (!a) return;
                    if (t < beatT(0) - 200_000_000L) return;
                    int pc = Theory.pc(Math.round(midi));
                    boolean ok = false;
                    for (int p : ex.allowedPcs) if (p == pc) ok = true;
                    if (!ok) { String msg = "Gam dışı nota: " + Theory.SHARP[pc]; if (!forgive(msg)) done(false, msg + " — " + ex.scaleLabel + " notalarında kal"); return; }
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
        beats.centerText = phaseText(beat, "Kalan vuruş: " + Math.max(0, (int) Math.ceil(ex.beats - beat)));
        beats.invalidate();
        if (beat >= ex.beats) {
            if (improvCount >= ex.beats / 2) done(true, passMsg(improvCount + " nota, hepsi gam içinde!"));
            else done(false, "Daha cesur ol: en az " + (ex.beats / 2) + " nota çalmalısın (" + improvCount + ")");
            return;
        }
        h.postDelayed(this::tickImprov, 16);
    }

    // ------------------------------------------------------------------ SESLE SÖYLEME
    private void buildSing() {
        addAudioButton();
        meter = new Views.Meter(ctx);
        meter.big = ex.hideTarget ? "♪" : (ex.singLabel != null ? ex.singLabel.split(" ")[0] : "♪");
        meter.small = "Sesini bekliyorum";
        addView(meter);
        addStatus("🎤 Dinle, sonra söyle. Hangi oktavda söylediğin önemli değil.");
        addView(Ui.text(ctx, "İpucu: “laa” ya da notanın adıyla, düz ve uzun söyle. İbre ortaya gelince 0,3 saniye tut.", 13, Ui.MUTED, false));
        next = 0;
        final int[] good = {0}, wrong = {0}, wrongPc = {-1};
        final long[] noteStart = {System.nanoTime()};
        listen(new AudioIn.Listener() {
            public void onNote(long n, float m, boolean a) {}
            public void onOnset(long n, float db) {}
            public void onFrame(long n, float midi, float c, float db) {
                long now = System.nanoTime();
                if (now < audioEndsAt) { noteStart[0] = now; return; } // kendi çaldığımız sesi duymayalım
                if (now - noteStart[0] > 15_000_000_000L) { done(false, "Ses duyulmadı ya da hedefe ulaşılamadı. Telefonu ağzına yaklaştır ve daha uzun söyle."); return; }
                if (midi < 0) { good[0] = 0; return; }
                int target = ex.notes.get(next).midi;
                float diff = midi - target;
                diff -= 12 * Math.round(diff / 12f);
                meter.active = true;
                meter.cents = diff * 100;
                meter.small = (Math.abs(diff) < 0.3f ? "✓ tut!" : diff < 0 ? "↑ biraz daha ince söyle" : "↓ biraz daha kalın söyle") + "   (" + (next + 1) + "/" + ex.notes.size() + ")";
                meter.invalidate();
                showHeard(midi);
                if (Math.abs(diff) < 0.45f) {
                    wrong[0] = 0;
                    if (++good[0] >= 12) {
                        good[0] = 0;
                        mascot.pop();
                        if (staff != null) { staff.current = next + 1; staff.invalidate(); }
                        next++;
                        noteStart[0] = now;
                        if (next >= ex.notes.size()) { done(true, "Tam isabet, kulağın ve sesin uyumlu!"); return; }
                        status.setText("Doğru! Sıradaki nota…"); status.setTextColor(Ui.GREEN);
                    }
                } else {
                    good[0] = 0;
                    int pc = Theory.pc(Math.round(midi));
                    if (Math.abs(diff) > 0.8f && pc == wrongPc[0]) wrong[0]++; else { wrong[0] = 0; wrongPc[0] = pc; }
                    if (wrong[0] >= 30) {
                        String want = ex.hideTarget ? "başka bir nota" : (ex.singLabel != null ? ex.singLabel : Theory.SHARP[Theory.pc(target)]);
                        done(false, Theory.SHARP[pc] + " söylüyorsun, beklenen " + want + (ex.explain.isEmpty() ? "" : "\n" + ex.explain));
                    }
                }
            }
        });
        addSkipMic();
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
                    double k = (t - met.beatNanos(countIn) - 150_000_000L) / (double) period;
                    long kk = Math.round(k);
                    if (kk < 0 || kk >= 8) return;
                    long off = t - met.beatNanos(countIn + kk);
                    if (Math.abs(off) < 600_000_000L) { calOffsets.add(off); status.setText("Vuruş: " + calOffsets.size() + "/8"); }
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
        beats.centerText = phaseText(beat, "Vur!");
        beats.invalidate();
        if (beat > 9.3) {
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
