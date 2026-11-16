package com.enoli.perde;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.List;

/** Ses çıkışı: Karplus-Strong gitar sentezi ve örnek-doğru metronom. */
public class AudioOut {
    public static final int SR = 44100;

    // ---------- Gitar sentezi ----------
    /** Tek bir telli nota üretir (Karplus-Strong). */
    public static float[] pluck(float midi, float seconds, float amp) {
        int n = (int) (seconds * SR);
        float[] y = new float[n];
        float hz = Dsp.midiToHz(midi);
        double P = SR / hz;                 // toplam döngü gecikmesi
        int N = (int) Math.floor(P - 0.5 - 0.1);
        double frac = P - 0.5 - N;          // allpass ile ince akort
        float C = (float) ((1 - frac) / (1 + frac));
        java.util.Random rnd = new java.util.Random((long) (midi * 1000));
        for (int i = 0; i <= N && i < n; i++) y[i] = rnd.nextFloat() * 2 - 1;
        for (int k = 0; k < 2; k++) for (int i = 1; i <= N && i < n; i++) y[i] = 0.5f * (y[i] + y[i - 1]);
        float decay = 0.996f - Math.max(0, (midi - 52)) * 0.00015f;
        float apX1 = 0, apY1 = 0;
        for (int i = N + 1; i < n; i++) {
            float lp = decay * 0.5f * (y[i - N] + y[i - N - 1]);
            float ap = C * lp + apX1 - C * apY1;
            apX1 = lp; apY1 = ap;
            y[i] = ap;
        }
        for (int i = 0; i < n; i++) y[i] *= amp;
        int fade = Math.min(n, SR / 50);
        for (int i = 0; i < fade; i++) y[n - 1 - i] *= (float) i / fade;
        return y;
    }

    public static class Ev { public float midi; public float start, dur; public Ev(float m, float s, float d) { midi = m; start = s; dur = d; } }

    /** Olay listesini tek bir tampona miksler. */
    public static float[] render(List<Ev> evs) {
        float end = 0;
        for (Ev e : evs) end = Math.max(end, e.start + e.dur);
        float[] mix = new float[(int) ((end + 0.3f) * SR)];
        for (Ev e : evs) {
            float[] s = pluck(e.midi, e.dur + 0.25f, 0.35f);
            int off = (int) (e.start * SR);
            for (int i = 0; i < s.length && off + i < mix.length; i++) mix[off + i] += s[i];
        }
        float mx = 0;
        for (float v : mix) mx = Math.max(mx, Math.abs(v));
        if (mx > 0.95f) for (int i = 0; i < mix.length; i++) mix[i] *= 0.95f / mx;
        return mix;
    }

    private static AudioTrack current;

    public static synchronized void play(float[] data) {
        stopPlayback();
        short[] pcm = new short[data.length];
        for (int i = 0; i < data.length; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, data[i] * 32767));
        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2).build();
        t.write(pcm, 0, pcm.length);
        t.play();
        current = t;
    }

    public static synchronized void stopPlayback() {
        if (current != null) {
            try { current.stop(); } catch (Exception ignored) {}
            current.release();
            current = null;
        }
    }

    public static void playNotes(float... midis) {
        List<Ev> l = new ArrayList<>();
        for (int i = 0; i < midis.length; i++) l.add(new Ev(midis[i], i * 0.55f, 0.5f));
        play(render(l));
    }

    public static void playChord(float[] midis, boolean arpeggiate) {
        List<Ev> l = new ArrayList<>();
        for (int i = 0; i < midis.length; i++) l.add(new Ev(midis[i], arpeggiate ? i * 0.03f : 0, 1.6f));
        play(render(l));
    }

    // ---------- Metronom ----------
    /**
     * Akış modunda çalışan metronom. Vuruş k'nın tam zamanını (nanoTime) beatNanos(k) ile verir.
     */
    public static class Metronome {
        private volatile boolean running;
        private Thread thread;
        private AudioTrack track;
        private final float bpm;
        private final int beatsPerBar;
        public volatile boolean audible = true;
        private volatile long frame0Nanos = 0; // çıkış karesi 0'ın nanoTime karşılığı
        private final long framesPerBeat;
        private final long startFrame;
        private volatile boolean anchored;
        public volatile float extraLatencyMs = 0;

        public Metronome(float bpm, int beatsPerBar) {
            this.bpm = bpm;
            this.beatsPerBar = beatsPerBar;
            this.framesPerBeat = Math.round(SR * 60.0 / bpm);
            this.startFrame = SR / 2; // yarım saniye sessizlikle başla
        }

        public float bpm() { return bpm; }
        public long beatIntervalNanos() { return Math.round(60e9 / bpm); }

        public void start() {
            int min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(Math.max(min, 2048 * 2)).build();
            running = true;
            frame0Nanos = System.nanoTime() + 60_000_000L; // tahmini, zaman damgasıyla düzeltilecek
            track.play();
            thread = new Thread(this::loop, "Metronome");
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }

        public void stop() {
            running = false;
            if (thread != null) try { thread.join(300); } catch (InterruptedException ignored) {}
            if (track != null) { try { track.stop(); } catch (Exception ignored) {} track.release(); track = null; }
        }

        /** Vuruş k (0'dan başlar) kulağa ulaştığı an. */
        public long beatNanos(double k) {
            return frame0Nanos + (long) ((startFrame + k * framesPerBeat) * 1e9 / SR) + (long) (extraLatencyMs * 1e6);
        }

        public boolean isAnchored() { return anchored; }

        private volatile double muteFrom = -1, muteTo = -1;
        /** [from, to) vuruş aralığında tık sesi çıkmaz (nabzı içselleştirme). Vuruşlar sayım dahil 0'dan sayılır. */
        public void setMute(double fromBeat, double toBeat) { muteFrom = fromBeat; muteTo = toBeat; }
        public boolean isMuted(double beat) { return beat >= muteFrom && beat < muteTo; }

        private void loop() {
            int block = 256;
            short[] buf = new short[block];
            long written = 0;
            float[] hi = clickWave(2600, 0.020f, 0.9f), lo = clickWave(1800, 0.020f, 0.6f);
            AudioTimestamp ts = new AudioTimestamp();
            int c = 0;
            while (running) {
                for (int i = 0; i < block; i++) {
                    long f = written + i - startFrame;
                    float v = 0;
                    if (f >= 0 && audible) {
                        long beat = f / framesPerBeat, inBeat = f % framesPerBeat;
                        float[] w = (beat % beatsPerBar == 0) ? hi : lo;
                        if (inBeat < w.length && !isMuted(beat)) v = w[(int) inBeat];
                    }
                    buf[i] = (short) (v * 20000);
                }
                track.write(buf, 0, block);
                written += block;
                if (++c % 20 == 0) {
                    try {
                        if (track.getTimestamp(ts) && ts.framePosition > 0) {
                            frame0Nanos = ts.nanoTime - ts.framePosition * 1_000_000_000L / SR;
                            anchored = true;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        private static float[] clickWave(float hz, float sec, float amp) {
            int n = (int) (sec * SR);
            float[] w = new float[n];
            for (int i = 0; i < n; i++) {
                float env = (float) Math.exp(-i / (SR * 0.004));
                w[i] = (float) (amp * env * Math.sin(2 * Math.PI * hz * i / SR));
            }
            return w;
        }
    }
}
