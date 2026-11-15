package com.enoli.perde;

/** Sinyal işleme yardımcıları: FFT, YIN perde tespiti, biquad filtre, chroma. */
public final class Dsp {
    private Dsp() {}

    /** Yerinde radix-2 FFT. n 2'nin kuvveti olmalı. */
    public static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cr = 1, ci = 0;
                int half = len >> 1;
                for (int k = 0; k < half; k++) {
                    int a = i + k, b = a + half;
                    float xr = re[b] * cr - im[b] * ci;
                    float xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    /**
     * YIN perde tespiti. buf[off .. off+2*W) kullanılır.
     * Dönen: {frekans Hz, netlik 0..1}. Bulunamazsa frekans 0.
     */
    public static float[] yin(float[] buf, int off, int W, int sr, float minHz, float maxHz, float[] scratch) {
        int tauMin = Math.max(2, (int) (sr / maxHz));
        int tauMax = Math.min(W - 1, (int) (sr / minHz) + 1);
        float[] d = scratch;
        d[0] = 1;
        float running = 0;
        int best = -1;
        for (int tau = 1; tau <= tauMax; tau++) {
            float s = 0;
            if (tau >= tauMin - 1) {
                for (int j = 0; j < W; j++) {
                    float x = buf[off + j] - buf[off + j + tau];
                    s += x * x;
                }
            } else {
                // küçük tau'lar için de normalizasyon toplamına katkı gerekli
                for (int j = 0; j < W; j += 4) {
                    float x = buf[off + j] - buf[off + j + tau];
                    s += 4 * x * x;
                }
            }
            running += s;
            d[tau] = running > 0 ? s * tau / running : 1;
        }
        float thr = 0.15f;
        for (int tau = tauMin; tau < tauMax; tau++) {
            if (d[tau] < thr) {
                while (tau + 1 < tauMax && d[tau + 1] < d[tau]) tau++;
                best = tau;
                break;
            }
        }
        if (best < 0) {
            // eşik altına düşmediyse: global minimuma yakın İLK yerel minimum (alt-oktav hatasını önler)
            float mn = 10;
            for (int tau = tauMin; tau < tauMax; tau++) if (d[tau] < mn) mn = d[tau];
            if (mn > 0.35f) return new float[]{0, 0};
            float lim = mn + 0.08f;
            for (int tau = tauMin + 1; tau < tauMax - 1; tau++) {
                if (d[tau] <= lim && d[tau] <= d[tau - 1] && d[tau] <= d[tau + 1]) { best = tau; break; }
            }
            if (best < 0) return new float[]{0, 0};
        }
        float t = best;
        if (best > 1 && best < tauMax - 1) {
            float s0 = d[best - 1], s1 = d[best], s2 = d[best + 1];
            float den = 2 * (2 * s1 - s2 - s0);
            if (den != 0) t = best + (s2 - s0) / den;
        }
        float conf = 1 - Math.min(1, d[best]);
        return new float[]{sr / t, conf};
    }

    /** İkinci dereceden alçak geçiren filtre (RBJ). */
    public static final class Biquad {
        float b0, b1, b2, a1, a2, z1, z2;
        public Biquad(float sr, float fc, float q) {
            double w = 2 * Math.PI * fc / sr, c = Math.cos(w), s = Math.sin(w), alpha = s / (2 * q);
            double a0 = 1 + alpha;
            b0 = (float) ((1 - c) / 2 / a0); b1 = (float) ((1 - c) / a0); b2 = b0;
            a1 = (float) (-2 * c / a0); a2 = (float) ((1 - alpha) / a0);
        }
        public float process(float x) {
            float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }

    public static float hzToMidi(float hz) { return (float) (69 + 12 * Math.log(hz / 440.0) / Math.log(2)); }
    public static float midiToHz(float m) { return (float) (440 * Math.pow(2, (m - 69) / 12.0)); }

    /** n örneklik (2'nin kuvveti) bloktan 12 boyutlu normalize chroma. */
    public static float[] chroma(float[] x, int sr) {
        int n = x.length;
        float[] re = new float[n], im = new float[n];
        for (int i = 0; i < n; i++) re[i] = x[i] * (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
        fft(re, im);
        float[] c = new float[12];
        int kMin = Math.max(1, (int) (75f * n / sr)), kMax = Math.min(n / 2 - 1, (int) (1700f * n / sr));
        float[] mag = new float[n / 2];
        for (int k = 1; k < n / 2; k++) mag[k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
        for (int k = kMin; k <= kMax; k++) {
            // yalnızca yerel tepeler — sızıntıyı azaltır
            if (mag[k] < mag[k - 1] || mag[k] < mag[k + 1]) continue;
            float hz = (float) k * sr / n;
            int pc = ((Math.round(hzToMidi(hz)) % 12) + 12) % 12;
            float w = (float) Math.sqrt(mag[k]);
            // yüksek harmonikleri biraz bastır
            if (hz > 700) w *= 0.6f;
            c[pc] += w;
        }
        float norm = 0;
        for (float v : c) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < 12; i++) c[i] /= norm;
        return c;
    }

    /** Harmonikleri hesaba katan akor şablonu ile kosinüs benzerliği. */
    public static float templateScore(float[] chroma, int[] pcs, boolean powerChord) {
        float[] t = new float[12];
        int[] harmSemis = {0, 12, 19, 24, 28, 31};
        float[] harmAmp = {1f, 0.7f, 0.45f, 0.35f, 0.2f, 0.18f};
        for (int i = 0; i < pcs.length; i++) {
            float w = i == 0 ? 1.15f : 1f;
            for (int h = 0; h < harmSemis.length; h++) t[((pcs[i] + harmSemis[h]) % 12 + 12) % 12] += w * harmAmp[h];
        }
        float dot = 0, n = 0, cn = 0;
        for (int i = 0; i < 12; i++) { dot += chroma[i] * t[i]; n += t[i] * t[i]; cn += chroma[i] * chroma[i]; }
        return dot / (float) Math.sqrt(n * Math.max(cn, 1e-9f));
    }
}
