package com.enoli.perde;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;

/**
 * Mikrofon girişi. Arka planda okur; her 1024 örnekte bir perde karesi,
 * her 256 örnekte bir enerji bloğu analiz eder ve dinleyiciye nota olayları gönderir.
 * Tüm zamanlar System.nanoTime() tabanındadır.
 */
public class AudioIn {
    public static final int SR = 44100;
    static final int HOP = 1024, W = 1024, SUB = 256;

    public interface Listener {
        /** Her perde karesinde. midi<0 ise net perde yok. */
        void onFrame(long nanos, float midi, float clarity, float db);
        /** Yeni bir nota başladığında (vuruş ya da perde değişimi). */
        void onNote(long onsetNanos, float midi, boolean attack);
        /** Perdesiz vuruş (sadece ritim için; akor/susturulmuş tel). */
        void onOnset(long nanos, float db);
    }

    private volatile boolean running;
    private Thread thread;
    private AudioRecord rec;
    private volatile Listener listener;
    public volatile float sensitivityDb = 0; // ayarlardan: + daha az hassas

    // halka tampon (chroma için)
    private final float[] ring = new float[1 << 15];
    private long totalSamples;
    private long baseNanos; // örnek 0'ın nanoTime karşılığı (zaman damgasıyla güncellenir)

    public void setListener(Listener l) { listener = l; }

    @SuppressLint("MissingPermission")
    public boolean start() {
        if (running) return true;
        int min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min, 4096 * 2);
        int[] sources = {MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int src : sources) {
            try {
                rec = new AudioRecord(src, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                if (rec.getState() == AudioRecord.STATE_INITIALIZED) break;
                rec.release(); rec = null;
            } catch (Exception e) { rec = null; }
        }
        if (rec == null) return false;
        try { rec.startRecording(); } catch (Exception e) { rec.release(); rec = null; return false; }
        running = true;
        totalSamples = 0;
        baseNanos = System.nanoTime();
        thread = new Thread(this::loop, "AudioIn");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
        return true;
    }

    public void stop() {
        running = false;
        if (thread != null) try { thread.join(500); } catch (InterruptedException ignored) {}
        thread = null;
        if (rec != null) { try { rec.stop(); } catch (Exception ignored) {} rec.release(); rec = null; }
    }

    public boolean isRunning() { return running; }

    public long sampleToNanos(long s) { return baseNanos + s * 1_000_000_000L / SR; }

    /** Belirli bir zamandan sonraki ms süresince sesten chroma hesaplar (yoksa null). */
    public synchronized float[] chromaAt(long fromNanos, int ms) {
        long s0 = (fromNanos - baseNanos) * SR / 1_000_000_000L;
        int n = 4096;
        long end = Math.min(totalSamples, s0 + (long) ms * SR / 1000);
        long start = Math.max(end - n, totalSamples - ring.length + 1);
        if (end - start < 2048) return null;
        float[] x = new float[n];
        int mask = ring.length - 1;
        for (int i = 0; i < n; i++) {
            long idx = start + i;
            x[i] = idx < end ? ring[(int) (idx & mask)] : 0;
        }
        return Dsp.chroma(x, SR);
    }

    /**
     * Notaya göre dinleme (polifonik ortamda): vuruştan sonra her aday notanın harmoniklerinde
     * vuruştan önceye göre ne kadar YENİ enerji belirdiğini ölçer. Önceki teller titreşmeye devam etse de
     * yeni çalınan notayı bulur. Dönüş: her aday için 0..1 arası pay (adaylar arasında normalize).
     */
    public synchronized float[] newEnergy(long onsetNanos, int[] midis) {
        long s0 = (onsetNanos - baseNanos) * SR / 1_000_000_000L;
        int n = 4096;
        long postA = s0 + SR * 30 / 1000, preA = s0 - n - SR * 15 / 1000;
        if (postA + n > totalSamples || preA < totalSamples - ring.length + 1 || preA < 0) return null;
        float[] post = ringSlice(postA, n), pre = ringSlice(preA, n);
        float[] out = new float[midis.length];
        for (int i = 0; i < midis.length; i++) out[i] = harmonicGain(post, pre, midis[i]);
        return out;
    }

    /** Aday notanın harmoniklerinde vuruşla gelen enerji; temel ya da 2. harmonik de yükselmiş olmalı. */
    static float harmonicGain(float[] post, float[] pre, int midi) {
        double f0 = 440 * Math.pow(2, (midi - 69) / 12.0);
        float sum = 0, low = 0, max = 0;
        for (int h = 1; h <= 6; h++) {
            double f = f0 * h;
            if (f > SR / 2.5) break;
            float a = goertzel(post, f), b = goertzel(pre, f);
            float g = Math.max(0, a - 1.1f * b);
            float w = (float) (1 / Math.sqrt(h));
            sum += w * g;
            if (h <= 2) low = Math.max(low, g);
            max = Math.max(max, g);
        }
        if (max <= 0 || low < 0.2f * max) return 0;
        return sum;
    }

    static float goertzel(float[] x, double f) {
        int n = x.length;
        double w = 2 * Math.PI * f / SR, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < n; i++) {
            double win = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            double s = x[i] * win + c * s1 - s2;
            s2 = s1; s1 = s;
        }
        double p = s1 * s1 + s2 * s2 - c * s1 * s2;
        return (float) Math.sqrt(Math.max(0, p)) / n;
    }

    /**
     * Beklenen nota bu vuruşla mı çalındı? Beklenen notanın yeni enerjisi, yarım/tam ses komşularından
     * (yanlış perde) belirgin şekilde yüksek olmalı. Oktav hataları kabul edilir.
     */
    public boolean heardExpected(long onsetNanos, int expected) {
        int[] c = {expected, expected - 1, expected + 1, expected - 2, expected + 2};
        float[] e = newEnergy(onsetNanos, c);
        if (e == null || e[0] <= 0) return false;
        float rival = 0;
        for (int i = 1; i < c.length; i++) rival = Math.max(rival, e[i]);
        return e[0] >= 1.4f * rival && e[0] > 1e-4f;
    }

    // ---- analiz durumu ----
    private final float[] frame = new float[W * 2];
    private final float[] scratch = new float[W + 2];
    private int framePos = 0;
    private float noiseDb = -70;
    private long lastOnsetSample = -100000;
    private float candMidi = -1; private int stableCount = 0; private long stableStart = 0;
    private boolean noteActive = false; private float activeMidi = -1;
    public volatile float onsetSensitivity = 1f;
    public static java.util.function.Consumer<float[]> debug;
    /** Döngü (loopback) kalibrasyonu: hoparlörden çıkan tıkı örnek hassasiyetinde yakalar. */
    public volatile boolean loopbackMode;
    private float lbNoise = 1e-4f; private long lbLast = -1_000_000; // >1 daha az hassas
    // spektral akı
    private static final int FN = 1024;
    private final float[] fre = new float[FN], fim = new float[FN], prevMag = new float[FN / 2], win = new float[FN];
    private final float[] odfHist = new float[24]; private int odfIdx = 0;
    private float odf1 = 0, odf2 = 0; private long odf1Sample = 0; private float db1 = -90;
    // vuruş sonrası perde analizi
    private long pendOnset = -1; private int pendAttempt = 0;
    private float rawPrev = 0;

    public void resetAnalysis(long base) {
        baseNanos = base; totalSamples = 0; framePos = 0; noiseDb = -70; lastOnsetSample = -100000;
        candMidi = -1; stableCount = 0; noteActive = false; activeMidi = -1;
        java.util.Arrays.fill(prevMag, 0); java.util.Arrays.fill(odfHist, 0); odf1 = odf2 = 0; pendOnset = -1;
        for (int i = 0; i < FN; i++) win[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FN - 1)));
    }

    private void loop() {
        short[] pcm = new short[SUB];
        resetAnalysis(System.nanoTime());
        AudioTimestamp ts = new AudioTimestamp();
        int tsCounter = 0;
        while (running) {
            int r = rec.read(pcm, 0, SUB);
            if (r <= 0) continue;
            if (++tsCounter % 40 == 0) {
                try {
                    if (rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                        baseNanos = ts.nanoTime - ts.framePosition * 1_000_000_000L / SR;
                    }
                } catch (Throwable ignored) {}
            }
            process(pcm, r);
        }
    }

    private float[] ringSlice(long start, int n) {
        float[] x = new float[n];
        int mask = ring.length - 1;
        for (int i = 0; i < n; i++) x[i] = ring[(int) ((start + i) & mask)];
        return x;
    }

    private float median(float[] a) {
        float[] b = a.clone(); java.util.Arrays.sort(b); return b[b.length / 2];
    }

    public void process(short[] pcm, int r) {
        double e = 0;
        synchronized (this) {
            int mask = ring.length - 1;
            for (int i = 0; i < r; i++) {
                float v = pcm[i] / 32768f;
                ring[(int) ((totalSamples + i) & mask)] = v;
                if (framePos < frame.length) frame[framePos++] = v;
                e += v * v;
            }
            totalSamples += r;
        }
        float db = (float) (10 * Math.log10(e / r + 1e-12));
        if (loopbackMode) {
            float rms = (float) Math.sqrt(e / r);
            long blockStart = totalSamples - r;
            float thr = Math.max(lbNoise * 8, 0.02f);
            if (blockStart - lbLast > SR / 5) {
                int mask = ring.length - 1;
                for (int i = 0; i < r; i++) {
                    if (Math.abs(ring[(int) ((blockStart + i) & mask)]) > thr) {
                        lbLast = blockStart + i;
                        Listener ll = listener;
                        if (ll != null) ll.onOnset(sampleToNanos(blockStart + i), db);
                        break;
                    }
                }
            }
            if (blockStart - lbLast > SR / 10) lbNoise = rms < lbNoise ? rms : lbNoise * 0.995f + rms * 0.005f;
            return;
        }
        if (db < noiseDb) noiseDb = noiseDb * 0.9f + db * 0.1f; else noiseDb += 0.01f;
        float gate = Math.max(noiseDb + 10, -60) + sensitivityDb;
        Listener l = listener;

        // --- spektral akı (80–1500 Hz) ---
        if (totalSamples >= FN) {
            float[] x = ringSlice(totalSamples - FN, FN);
            for (int i = 0; i < FN; i++) { fre[i] = x[i] * win[i]; fim[i] = 0; }
            Dsp.fft(fre, fim);
            float flux = 0, eClick = 0, eAll = 0, prevSum = 0;
            for (int k = 2; k <= 232; k++) {
                float mag2 = fre[k] * fre[k] + fim[k] * fim[k];
                eAll += mag2;
                boolean clickBin = (k >= 38 && k <= 46) || (k >= 56 && k <= 65);
                if (clickBin) { eClick += mag2; continue; }
                float m = (float) Math.sqrt(mag2);
                float w = k <= 35 ? 1f : 0.5f; // tiz bölge vuruş gürültüsü için yarım ağırlık
                float d = m - prevMag[k];
                if (d > 0) flux += w * d;
                prevSum += w * prevMag[k];
                prevMag[k] = m;
            }
            float floorMag = (float) (Math.pow(10, gate / 20) * FN * 0.25);
            flux = flux / (prevSum + floorMag * 60);
            // metronom tıkı: enerjinin çoğu tık frekanslarındaysa yok say
            if (eClick > eAll * 0.4f) flux = 0;
            float raw = flux;
            flux = raw + rawPrev; rawPrev = raw; // iki bloğun toplamı (akorun telleri ~60 ms'ye yayılır)
            // tepe seçimi (bir blok gecikmeli)
            float thr = median(odfHist) * 1.5f + 0.15f * onsetSensitivity;
            if (odf1 > thr && odf1 > odf2 && odf1 >= flux && db1 > gate && odf1Sample - lastOnsetSample > SR * 0.09) {
                lastOnsetSample = odf1Sample;
                long on = sampleToNanos(odf1Sample);
                if (l != null) l.onOnset(on, db1);
                pendOnset = odf1Sample; pendAttempt = 0;
            }
            if (debug != null) debug.accept(new float[]{totalSamples, flux, thr, db});
            odfHist[odfIdx] = flux; odfIdx = (odfIdx + 1) % odfHist.length;
            odf2 = odf1; odf1 = flux; odf1Sample = totalSamples - r - r; db1 = Math.max(db, db1 * 0 + db);
        }

        // --- vuruş sonrası perde (yeni nota baskın) ---
        if (pendOnset >= 0) {
            long start = pendOnset + (pendAttempt == 0 ? SR / 50 : SR * 50 / 1000);
            if (totalSamples >= start + 2 * W) {
                float[] x = ringSlice(start, 2 * W);
                float[] p = Dsp.yin(x, 0, W, SR, 70, 1400, scratch);
                boolean ringing = p[0] > 0 && noteActive && Math.abs(Dsp.hzToMidi(p[0]) - activeMidi) < 0.5f;
                if (p[0] > 0 && p[1] > 0.78f && !(ringing && pendAttempt == 0)) {
                    float m = Dsp.hzToMidi(p[0]);
                    if (l != null) l.onNote(sampleToNanos(pendOnset), m, true);
                    noteActive = true; activeMidi = m; pendOnset = -1;
                    candMidi = m; stableCount = 3;
                } else if (++pendAttempt > 1) pendOnset = -1;
            }
        }

        // --- kare tabanlı perde (gösterim + legato) ---
        if (framePos >= frame.length) {
            float[] p = Dsp.yin(frame, 0, W, SR, 70, 1400, scratch);
            double fe = 0;
            for (int i = W; i < 2 * W; i++) fe += frame[i] * frame[i];
            float fdb = (float) (10 * Math.log10(fe / W + 1e-12));
            long nanos = sampleToNanos(totalSamples - W / 2);
            float midi = (p[0] > 0 && p[1] > 0.8f && fdb > gate - 6) ? Dsp.hzToMidi(p[0]) : -1;
            if (l != null) l.onFrame(nanos, midi, p[1], fdb);
            if (midi > 0 && candMidi > 0 && Math.abs(midi - candMidi) < 0.6f) stableCount++;
            else { stableCount = midi > 0 ? 1 : 0; stableStart = totalSamples - W; }
            candMidi = midi;
            if (stableCount >= 3 && pendOnset < 0 && totalSamples - lastOnsetSample > SR * 0.15) {
                int d = Math.abs(Math.round(midi) - Math.round(activeMidi));
                boolean related = d == 12 || d == 24 || d == 19 || d == 7;
                if ((!noteActive || d >= 1) && !(noteActive && related)) {
                    if (l != null) l.onNote(sampleToNanos(stableStart), midi, false);
                    noteActive = true; activeMidi = midi;
                } else if (d == 0) activeMidi = activeMidi * 0.7f + midi * 0.3f;
            }
            if (midi < 0 && fdb < gate - 3) noteActive = false;
            System.arraycopy(frame, HOP, frame, 0, frame.length - HOP);
            framePos -= HOP;
        }
    }
}
