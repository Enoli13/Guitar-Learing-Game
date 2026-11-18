package com.enoli.perde;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Şarkı görünümü: ölçüleri satırlara dizer. İki çizim biçimi:
 *  - TAB: 6 telli tab; akor sütunlarının yerine akorun adı yazılır, altta ritim sapları.
 *  - NOTA: sol anahtarlı porte (gitar bir oktav pes çalar, "8"), akorlar porte üstünde ad + ritim çizgisi.
 */
public class ScoreView extends View {
    public enum Mode { TAB, STAFF }
    public Mode mode = Mode.TAB;
    public boolean names = true;          // nota adları (porte altında)
    public int[] state = new int[0];      // nota başına: 0 bekliyor, 1 doğru, 2 yanlış
    public int current = -1;              // bekleme modunda sıradaki nota
    public float cursorBeat = -1;         // zamanlı modda imleç
    public int selFrom = -1, selTo = -1;  // seçili ölçüler (önizleme)
    public interface OnMeasure { void tapped(int measure); }
    public OnMeasure onMeasure;

    private List<Ex.Note> notes = new ArrayList<>();
    private float[] mStart = new float[0], mLen = new float[0];
    private int firstMeasureNumber = 1;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    // ---- yerleşim
    static class Item { int note = -1; float beat, val; boolean rest, dot, trip; int tup = 3; float x, w; }
    static class Measure { int idx; List<Item> items = new ArrayList<>(); float natural, x, w; int line; }
    static class Line { float y, h; int lo, hi; List<Measure> ms = new ArrayList<>(); }
    private final List<Measure> measures = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private int laidW = -1;

    public ScoreView(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }

    /** notes: vuruşlar mStart[0]'dan bağımsız mutlak; ölçüler verilen sıra. */
    public void set(List<Ex.Note> n, float[] starts, float[] lens, int firstNumber) {
        notes = n; mStart = starts; mLen = lens; firstMeasureNumber = firstNumber;
        state = new int[n.size()];
        laidW = -1;
        requestLayout(); invalidate();
    }

    public void setMode(Mode m) { mode = m; laidW = -1; requestLayout(); invalidate(); }

    // ---------------------------------------------------------------- ritim değerleri
    static final float[] VALS = {4, 3, 2, 1.5f, 1, 0.75f, 0.5f, 0.375f, 0.25f, 0.125f};
    static final float[] TRIP = {4 / 3f, 2 / 3f, 1 / 3f, 1 / 6f};

    static Item quant(float dur) {
        Item it = new Item();
        // önce üçleme mi?
        for (float t : TRIP) if (Math.abs(dur - t) < 0.02f) { it.val = t * 1.5f; it.trip = true; return it; }
        boolean std = false;
        for (float v : VALS) if (Math.abs(dur - v) < 0.015f) std = true;
        if (!std) {
            // beşleme, altılama, yedileme: süre * n/m standart bir değere oturuyor mu?
            int[][] tu = {{5, 4}, {6, 4}, {7, 4}, {9, 8}, {5, 2}};
            for (int[] q : tu) for (float v : new float[]{2, 1, 0.5f, 0.25f, 0.125f})
                if (Math.abs(dur * q[0] / q[1] - v) < 0.012f) { it.val = v; it.trip = true; it.tup = q[0]; return it; }
        }
        it.val = 0.125f;
        for (float v : VALS) if (v <= dur + 0.02f) { it.val = v; break; }
        if (it.val == 3 || it.val == 1.5f || it.val == 0.75f || it.val == 0.375f) { it.dot = true; it.val /= 1.5f; }
        return it;
    }

    static float span(Item it) {
        float v = it.val * (it.dot ? 1.5f : 1);
        if (!it.trip) return v;
        return it.tup == 3 ? v / 1.5f : v * (it.tup == 5 || it.tup == 7 || it.tup == 6 ? 4f : it.tup == 9 ? 8f : 2f) / it.tup;
    }

    private void build() {
        measures.clear();
        int ni = 0;
        for (int m = 0; m < mStart.length; m++) {
            Measure ms = new Measure();
            ms.idx = m;
            float a = mStart[m], z = a + mLen[m];
            float t = a;
            while (ni < notes.size() && notes.get(ni).beat < a - 0.01f) ni++;
            int k = ni;
            while (k < notes.size() && notes.get(k).beat < z - 0.01f) {
                Ex.Note n = notes.get(k);
                if (n.beat > t + 0.06f) rests(ms, t, n.beat);
                float next = k + 1 < notes.size() ? Math.min(z, notes.get(k + 1).beat) : z;
                float dd = Math.max(0.1f, Math.min(n.dur, next - n.beat));
                if (dd < next - n.beat - 0.06f && next - n.beat <= 1.01f) dd = next - n.beat; // kısa boşlukları notaya kat
                Item it = quant(dd);
                it.note = k; it.beat = n.beat;
                ms.items.add(it);
                t = n.beat + span(it);
                k++;
            }
            if (z > t + 0.06f) rests(ms, t, z);
            ni = k;
            float w = 14 * d;
            for (Item it : ms.items) {
                it.w = (mode == Mode.TAB ? 20 : 22) * d + 18 * d * (float) Math.sqrt(Math.min(span(it), 4));
                if (it.note >= 0 && notes.get(it.note).chord != null) {
                    p.setTextSize(13 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
                    it.w = Math.max(it.w, p.measureText(notes.get(it.note).chord) + 10 * d);
                }
                if (names && mode == Mode.STAFF && it.note >= 0) it.w = Math.max(it.w, 30 * d);
                w += it.w;
            }
            ms.natural = Math.max(60 * d, w);
            measures.add(ms);
        }
    }

    private void rests(Measure ms, float from, float to) {
        float t = from;
        int guard = 0;
        while (to - t > 0.06f && guard++ < 32) {
            float rem = to - t, v = 0.125f;
            for (float c : new float[]{4, 2, 1, 0.5f, 0.25f, 0.125f}) if (c <= rem + 0.02f) { v = c; break; }
            // vuruş başına hizala (yarım ölçü dörtlük vb. vuruşu kırmasın)
            float inBeat = (t - (float) Math.floor(t + 1e-4f));
            if (inBeat > 0.01f && v >= 1) v = Math.max(0.125f, Math.min(v, 1 - inBeat >= 0.49f ? 0.5f : 0.25f));
            Item it = new Item();
            it.rest = true; it.beat = t; it.val = v;
            ms.items.add(it);
            t += v;
        }
    }

    private float clefW() { return (mode == Mode.STAFF ? 46 : 30) * d; }

    private void layout(int W) {
        build();
        lines.clear();
        float left = 8 * d, right = W - 8 * d;
        Line cur = null; float x = 0;
        for (Measure ms : measures) {
            float avail = right - left - (lines.isEmpty() || cur == null ? clefW() : clefW());
            if (cur == null || x + ms.natural > avail) {
                cur = new Line(); lines.add(cur); x = 0;
            }
            cur.ms.add(ms);
            x += ms.natural;
        }
        // satırları iki yana yasla
        float y = 0;
        for (Line ln : lines) {
            float avail = right - left - clefW();
            float sum = 0;
            for (Measure ms : ln.ms) sum += ms.natural;
            boolean last = ln == lines.get(lines.size() - 1);
            float scale = sum > avail || !last || ln.ms.size() > 1 ? avail / sum : 1;
            if (last && sum * 1.6f < avail) scale = 1.15f;
            float xx = left + clefW();
            int lo = 99, hi = -99;
            for (Measure ms : ln.ms) {
                ms.x = xx; ms.w = ms.natural * scale;
                float ix = xx + 12 * d * scale;
                for (Item it : ms.items) {
                    it.x = ix + it.w * scale * 0.35f;
                    ix += it.w * scale;
                    if (it.note >= 0 && mode == Mode.STAFF) {
                        for (int s : steps(notes.get(it.note))) { lo = Math.min(lo, s); hi = Math.max(hi, s); }
                    }
                }
                xx += ms.w;
            }
            ln.lo = lo; ln.hi = hi;
            ln.y = y;
            ln.h = lineHeight(ln);
            y += ln.h;
        }
        laidW = W;
    }

    // ---------------------------------------------------------------- porte hesapları
    private float ls() { return 8 * d; }
    static final int[] LETTER = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
    static final boolean[] SHARP = {false, true, false, true, false, false, true, false, true, false, true, false};
    static final int BOTTOM = 30; // E4 = alt çizgi (yazılı)

    /** Yazılı (gitar: +12) diyatonik basamak. */
    static int step(int midi) { int w = midi + 12; return (w / 12 - 1) * 7 + LETTER[((w % 12) + 12) % 12]; }

    private int[] steps(Ex.Note n) {
        if (n.chord != null) return new int[]{BOTTOM + 4};
        int[] all = midis(n);
        int[] s = new int[all.length];
        for (int i = 0; i < all.length; i++) s[i] = step(all[i]);
        return s;
    }

    static int[] midis(Ex.Note n) {
        if (n.alts == null) return new int[]{n.midi};
        int[] a = new int[n.alts.length + 1];
        a[0] = n.midi;
        System.arraycopy(n.alts, 0, a, 1, n.alts.length);
        return a;
    }

    private float chordRow() { return 20 * d; }

    private float lineHeight(Line ln) {
        if (mode == Mode.TAB) return chordRow() + 6 * d + 5 * tabGap() + 34 * d + 12 * d;
        int above = Math.max(3, ln.hi - (BOTTOM + 8) + 3);   // basamak (yarım aralık)
        int below = Math.max(2, (BOTTOM) - ln.lo + 2);
        return chordRow() + above * ls() / 2 + 4 * ls() + below * ls() / 2 + (names ? 18 * d : 6 * d) + 10 * d;
    }

    private float staffTop(Line ln) {
        int above = Math.max(3, ln.hi - (BOTTOM + 8) + 3);
        return ln.y + chordRow() + above * ls() / 2;
    }

    private float tabGap() { return 13 * d; }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int W = MeasureSpec.getSize(wSpec);
        if (W <= 0) W = (int) (360 * d);
        if (laidW != W) layout(W);
        float h = 0;
        for (Line ln : lines) h += ln.h;
        setMeasuredDimension(W, (int) Math.max(60 * d, h + 6 * d));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { if (w != laidW) { layout(w); requestLayout(); } }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (onMeasure == null) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            for (Line ln : lines) {
                if (e.getY() < ln.y || e.getY() > ln.y + ln.h) continue;
                for (Measure ms : ln.ms) if (e.getX() >= ms.x && e.getX() < ms.x + ms.w) { onMeasure.tapped(ms.idx); return true; }
            }
        }
        return true;
    }

    private int colorFor(int i) {
        if (i < 0) return Ui.MUTED;
        int st = i < state.length ? state[i] : 0;
        if (st == 1) return Ui.GREEN;
        if (st == 2) return Ui.RED;
        if (i == current) return Ui.BLUE;
        if (cursorBeat >= 0 && i < notes.size() && notes.get(i).beat <= cursorBeat + 0.05f && notes.get(i).beat + notes.get(i).dur > cursorBeat) return Ui.BLUE;
        return Ui.TEXT;
    }

    @Override protected void onDraw(Canvas c) {
        if (laidW != getWidth()) layout(getWidth());
        float W = getWidth();
        for (Line ln : lines) {
            // seçim arka planı
            for (Measure ms : ln.ms) {
                if (selFrom >= 0 && ms.idx >= selFrom && ms.idx <= selTo) {
                    p.setStyle(Paint.Style.FILL); p.setColor(0x221CB0F6);
                    c.drawRoundRect(new RectF(ms.x, ln.y + 2 * d, ms.x + ms.w, ln.y + ln.h - 4 * d), 8 * d, 8 * d, p);
                }
            }
            if (mode == Mode.TAB) drawTabLine(c, ln, W); else drawStaffLine(c, ln, W);
        }
        drawCursor(c);
    }

    private float xOfBeat(Line ln, float beat) {
        Item prev = null; Measure pm = null;
        for (Measure ms : ln.ms) for (Item it : ms.items) {
            if (it.beat > beat) {
                if (prev == null) return it.x;
                float f = (beat - prev.beat) / Math.max(0.01f, it.beat - prev.beat);
                return prev.x + f * (it.x - prev.x);
            }
            prev = it; pm = ms;
        }
        if (prev == null) return -1;
        float end = mStart[pm.idx] + mLen[pm.idx];
        float f = (beat - prev.beat) / Math.max(0.01f, end - prev.beat);
        return prev.x + f * (pm.x + pm.w - prev.x);
    }

    private void drawCursor(Canvas c) {
        if (cursorBeat < 0 || mStart.length == 0) return;
        for (Line ln : lines) {
            Measure a = ln.ms.get(0), z = ln.ms.get(ln.ms.size() - 1);
            if (cursorBeat < mStart[a.idx] || cursorBeat >= mStart[z.idx] + mLen[z.idx]) continue;
            float x = xOfBeat(ln, cursorBeat);
            if (x < 0) return;
            p.setStyle(Paint.Style.FILL); p.setColor(0x881CB0F6); p.setStrokeWidth(3 * d);
            c.drawLine(x, ln.y + 4 * d, x, ln.y + ln.h - 6 * d, p);
            return;
        }
    }

    // ---------------------------------------------------------------- TAB
    private void drawTabLine(Canvas c, Line ln, float W) {
        float top = ln.y + chordRow() + 6 * d, g = tabGap();
        float x0 = 8 * d, x1 = ln.ms.get(ln.ms.size() - 1).x + ln.ms.get(ln.ms.size() - 1).w;
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1 * d); p.setColor(0xFF4A5068);
        for (int s = 0; s < 6; s++) c.drawLine(x0, top + s * g, x1, top + s * g, p);
        // TAB yazısı
        p.setColor(Ui.MUTED); p.setTextSize(11 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
        c.drawText("T", x0 + 11 * d, top + 1.4f * g, p);
        c.drawText("A", x0 + 11 * d, top + 2.9f * g, p);
        c.drawText("B", x0 + 11 * d, top + 4.4f * g, p);
        float stemTop = top + 5 * g + 8 * d;
        for (Measure ms : ln.ms) {
            p.setColor(0xFF6A7088); p.setStrokeWidth(1.4f * d);
            c.drawLine(ms.x + ms.w, top, ms.x + ms.w, top + 5 * g, p);
            if (ms == ln.ms.get(0)) c.drawLine(ms.x, top, ms.x, top + 5 * g, p);
            p.setColor(0xFF6A6F86); p.setTextSize(9 * d); p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.LEFT);
            c.drawText(String.valueOf(firstMeasureNumber + ms.idx), ms.x + 2 * d, top - 3 * d, p);
            for (Item it : ms.items) {
                if (it.rest) { drawRestSmall(c, it, stemTop + 12 * d); continue; }
                Ex.Note n = notes.get(it.note);
                int col = colorFor(it.note);
                if (n.chord != null) {
                    // akor sütunu yerine akorun adı
                    p.setTextSize(13 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
                    float tw = p.measureText(n.chord) + 10 * d;
                    RectF r = new RectF(it.x - tw / 2, top - 4 * d, it.x + tw / 2, top + 5 * g + 4 * d);
                    p.setColor(Ui.darker(col == Ui.TEXT ? Ui.PURPLE : col, 0.35f));
                    c.drawRoundRect(r, 7 * d, 7 * d, p);
                    p.setColor(col == Ui.TEXT ? 0xFFE7C8FF : col);
                    c.save(); c.rotate(-90, it.x, top + 2.5f * g);
                    c.drawText(n.chord, it.x, top + 2.5f * g + 5 * d, p);
                    c.restore();
                } else {
                    p.setTextSize(13 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
                    int[] ss = n.strs != null ? n.strs : new int[]{n.string};
                    int[] ff = n.strs != null ? n.frets : new int[]{n.fret};
                    for (int k = 0; k < ss.length; k++) {
                        if (ss[k] < 1) continue;
                        String lab = String.valueOf(ff[k]);
                        if (k == 0 && n.label != null && ss.length == 1) lab = (n.label.startsWith("b") ? ff[k] + "b" : n.label + ff[k]);
                        float y = top + (ss[k] - 1) * g;
                        float tw = p.measureText(lab) + 6 * d;
                        p.setColor(Ui.BG);
                        c.drawRect(it.x - tw / 2, y - 7 * d, it.x + tw / 2, y + 7 * d, p);
                        p.setColor(col);
                        c.drawText(lab, it.x, y + 4.5f * d, p);
                    }
                }
                drawStem(c, it, it.x, stemTop, stemTop + 22 * d, false, col);
            }
        }
    }

    /** Ritim sapı (tab altında ya da porte notasında). down: sap aşağı. */
    private void drawStem(Canvas c, Item it, float x, float y0, float y1, boolean flagsUp, int col) {
        if (it.val >= 4) return;
        p.setColor(col == Ui.TEXT ? 0xFFB8BDD0 : col); p.setStrokeWidth(1.4f * d);
        float ys = it.val >= 2 ? y0 + (y1 - y0) * 0.5f : y0;
        c.drawLine(x, ys, x, y1, p);
        int flags = it.val <= 0.125f ? 3 : it.val <= 0.25f ? 2 : it.val <= 0.5f ? 1 : 0;
        p.setStrokeWidth(2.2f * d);
        for (int f = 0; f < flags; f++) {
            float fy = y1 - f * 4.5f * d;
            c.drawLine(x, fy, x + 8 * d, fy - 5 * d, p);
        }
        if (it.dot) { p.setStyle(Paint.Style.FILL); c.drawCircle(x + 5 * d, y1 - 2 * d, 1.8f * d, p); }
        if (it.trip) { p.setTextSize(9 * d); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(String.valueOf(it.tup), x, y1 + 11 * d, p); }
    }

    private void drawRestSmall(Canvas c, Item it, float y) {
        p.setColor(0xFF6A6F86); p.setTextAlign(Paint.Align.CENTER);
        drawRest(c, it, it.x, y, 6 * d);
    }

    // ---------------------------------------------------------------- PORTE
    private void drawStaffLine(Canvas c, Line ln, float W) {
        float top = staffTop(ln), L = ls();
        float bottom = top + 4 * L;
        float x0 = 8 * d, x1 = ln.ms.get(ln.ms.size() - 1).x + ln.ms.get(ln.ms.size() - 1).w;
        p.setStyle(Paint.Style.FILL); p.setStrokeWidth(1.1f * d); p.setColor(0xFF6A7088);
        for (int i = 0; i < 5; i++) c.drawLine(x0, top + i * L, x1, top + i * L, p);
        drawClef(c, x0 + 12 * d, top, L);
        if (ln == lines.get(0) && mLen.length > 0) {
            p.setColor(Ui.TEXT); p.setTextSize(15 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
            int num = Math.round(mLen[0]);
            c.drawText(String.valueOf(num), x0 + 36 * d, top + 1.75f * L, p);
            c.drawText("4", x0 + 36 * d, top + 3.75f * L, p);
        }
        for (Measure ms : ln.ms) {
            p.setColor(0xFF8A90A8); p.setStrokeWidth(1.4f * d);
            c.drawLine(ms.x + ms.w, top, ms.x + ms.w, bottom, p);
            p.setColor(0xFF6A6F86); p.setTextSize(9 * d); p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.LEFT);
            c.drawText(String.valueOf(firstMeasureNumber + ms.idx), ms.x + 2 * d, top - 4 * d - (ln.hi > BOTTOM + 9 ? (ln.hi - BOTTOM - 8) * L / 2 : 0), p);
            boolean[] sharpOn = new boolean[80];
            boolean[] seen = new boolean[80];
            for (Item it : ms.items) {
                if (it.rest) { p.setColor(0xFFB8BDD0); drawRest(c, it, it.x, top + 2 * L, L); continue; }
                Ex.Note n = notes.get(it.note);
                int col = colorFor(it.note);
                if (n.chord != null) {
                    // akor: üstte adı, portede ritim çizgisi (slash)
                    p.setColor(col == Ui.TEXT ? 0xFFE7C8FF : col); p.setTextSize(13 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(n.chord, it.x, ln.y + chordRow() - 4 * d, p);
                    float y = top + 2 * L;
                    p.setColor(col); p.setStrokeWidth(3.2f * d);
                    if (it.val >= 2) { p.setStrokeWidth(1.4f * d); p.setStyle(Paint.Style.STROKE); }
                    c.drawLine(it.x - 4.5f * d, y + 4.5f * d, it.x + 4.5f * d, y - 4.5f * d, p);
                    p.setStyle(Paint.Style.FILL);
                    drawStem(c, it, it.x + 4.5f * d, y - 4.5f * d, y - 3.4f * L, true, col);
                    continue;
                }
                int[] ms2 = midis(n);
                int hiStep = -99, loStep = 99;
                for (int m : ms2) {
                    int st = step(m);
                    hiStep = Math.max(hiStep, st); loStep = Math.min(loStep, st);
                    float y = bottom - (st - BOTTOM) * L / 2;
                    // ek çizgiler
                    p.setColor(0xFF8A90A8); p.setStrokeWidth(1.1f * d);
                    for (int s = BOTTOM - 2; s >= st; s -= 2) { float ly = bottom - (s - BOTTOM) * L / 2; c.drawLine(it.x - 8 * d, ly, it.x + 8 * d, ly, p); }
                    for (int s = BOTTOM + 10; s <= st; s += 2) { float ly = bottom - (s - BOTTOM) * L / 2; c.drawLine(it.x - 8 * d, ly, it.x + 8 * d, ly, p); }
                    // değiştirici
                    int w = m + 12, pc = ((w % 12) + 12) % 12;
                    boolean sh = SHARP[pc];
                    int si = Math.max(0, Math.min(79, st));
                    p.setColor(col); p.setTextSize(15 * d); p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.CENTER);
                    if (sh && !sharpOn[si]) { c.drawText("♯", it.x - 12 * d, y + 5 * d, p); sharpOn[si] = true; }
                    else if (!sh && sharpOn[si]) { c.drawText("♮", it.x - 12 * d, y + 5 * d, p); sharpOn[si] = false; }
                    seen[si] = true;
                    // nota başı
                    p.setColor(col);
                    c.save(); c.rotate(-20, it.x, y);
                    RectF head = new RectF(it.x - 5.2f * d, y - 3.8f * d, it.x + 5.2f * d, y + 3.8f * d);
                    if (it.val >= 2) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f * d); }
                    c.drawOval(head, p);
                    p.setStyle(Paint.Style.FILL);
                    c.restore();
                    if (it.dot) c.drawCircle(it.x + 9 * d, y - ((st % 2 == 0) ? 0 : 0) - 2 * d, 1.8f * d, p);
                }
                if (it.val < 4) {
                    boolean up = (hiStep + loStep) / 2f < BOTTOM + 4;
                    float yHi = bottom - (hiStep - BOTTOM) * L / 2, yLo = bottom - (loStep - BOTTOM) * L / 2;
                    if (up) drawStemUp(c, it, it.x + 4.9f * d, yLo, yHi - 3.4f * L, col);
                    else drawStemDown(c, it, it.x - 4.9f * d, yHi, yLo + 3.4f * L, col);
                }
                // teknik etiketi
                if (n.label != null) {
                    p.setColor(Ui.YELLOW); p.setTextSize(10 * d); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);
                    float y = Math.min(top - 4 * d, bottom - (hiStep - BOTTOM) * L / 2 - 3.6f * L);
                    c.drawText(n.label.startsWith("b") ? "bend " + n.label.substring(1) : n.label, it.x, y, p);
                }
                if (names) {
                    p.setColor(col == Ui.TEXT ? Ui.MUTED : col); p.setTextSize(10.5f * d); p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.CENTER);
                    float y = ln.y + ln.h - 12 * d;
                    c.drawText(noteName(n.midi), it.x, y, p);
                }
            }
        }
    }

    private void drawStemUp(Canvas c, Item it, float x, float yHead, float yEnd, int col) {
        p.setColor(col); p.setStrokeWidth(1.4f * d);
        float y0 = yHead;
        c.drawLine(x, y0, x, yEnd, p);
        int flags = it.val <= 0.125f ? 3 : it.val <= 0.25f ? 2 : it.val <= 0.5f ? 1 : 0;
        p.setStrokeWidth(2.2f * d);
        for (int f = 0; f < flags; f++) { float fy = yEnd + f * 5 * d; c.drawLine(x, fy, x + 8 * d, fy + 9 * d, p); }
        if (it.trip) { p.setTextSize(9 * d); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(String.valueOf(it.tup), x, yEnd - 4 * d, p); }
    }

    private void drawStemDown(Canvas c, Item it, float x, float yHead, float yEnd, int col) {
        p.setColor(col); p.setStrokeWidth(1.4f * d);
        c.drawLine(x, yHead, x, yEnd, p);
        int flags = it.val <= 0.125f ? 3 : it.val <= 0.25f ? 2 : it.val <= 0.5f ? 1 : 0;
        p.setStrokeWidth(2.2f * d);
        for (int f = 0; f < flags; f++) { float fy = yEnd - f * 5 * d; c.drawLine(x, fy, x + 8 * d, fy - 9 * d, p); }
        if (it.trip) { p.setTextSize(9 * d); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(String.valueOf(it.tup), x, yEnd + 11 * d, p); }
    }

    /** Sus işaretleri (basit çizimler). */
    private void drawRest(Canvas c, Item it, float x, float yMid, float L) {
        p.setStyle(Paint.Style.FILL);
        if (it.val >= 4) { c.drawRect(x - 5 * d, yMid - L, x + 5 * d, yMid - L + 0.5f * L, p); return; }
        if (it.val >= 2) { c.drawRect(x - 5 * d, yMid - 0.5f * L, x + 5 * d, yMid, p); return; }
        if (it.val >= 1) {
            Path z = new Path();
            z.moveTo(x - 2 * d, yMid - 1.6f * L); z.lineTo(x + 3 * d, yMid - 0.7f * L); z.lineTo(x - 2 * d, yMid + 0.1f * L);
            z.lineTo(x + 3 * d, yMid + 0.9f * L); z.quadTo(x - 4 * d, yMid + 0.6f * L, x, yMid + 1.6f * L);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f * d);
            c.drawPath(z, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        int flags = it.val <= 0.125f ? 3 : it.val <= 0.25f ? 2 : 1;
        p.setStrokeWidth(1.6f * d);
        float top = yMid - 0.8f * L;
        c.drawLine(x + 3 * d, top, x - 2 * d, top + (1.2f + flags * 0.6f) * L, p);
        for (int f = 0; f < flags; f++) {
            float fy = top + f * 0.6f * L;
            c.drawCircle(x - 2 * d, fy + 1.5f * d, 2.1f * d, p);
            c.drawLine(x - 2 * d, fy + 1.5f * d, x + 3 * d - f * 0.6f * d, fy, p);
        }
        if (it.dot) c.drawCircle(x + 7 * d, yMid - 2 * d, 1.6f * d, p);
    }

    /** Basitleştirilmiş sol anahtarı (G anahtarı) + altında 8. */
    private void drawClef(Canvas c, float x, float top, float L) {
        p.setColor(0xFFDADCE6); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f * d);
        Path g = new Path();
        float gy = top + 3 * L; // G çizgisi (2. çizgi alttan)
        g.moveTo(x + 2 * d, top + 5.2f * L);
        g.quadTo(x - 3 * d, top + 5.4f * L, x - 2 * d, top + 4.6f * L);
        g.lineTo(x + 1.5f * d, top - 1.2f * L);
        g.quadTo(x + 6 * d, top - 0.6f * L, x + 3 * d, top + 0.8f * L);
        g.quadTo(x - 7 * d, top + 2.2f * L, x - 5 * d, gy + 0.6f * L);
        g.quadTo(x, gy + 1.9f * L, x + 6 * d, gy + 0.4f * L);
        g.quadTo(x + 5 * d, gy - 1.6f * L, x, gy - 0.9f * L);
        g.quadTo(x - 3 * d, gy - 0.1f * L, x + 0.5f * d, gy + 0.2f * L);
        c.drawPath(g, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(8 * d); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("8", x, top + 5.9f * L, p);
    }

    public static String noteName(int midi) {
        int pc = ((midi % 12) + 12) % 12;
        String b = Theory.SOLFEJ[LETTER[pc]];
        return SHARP[pc] ? b + "♯" : b;
    }
}
