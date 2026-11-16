package com.enoli.perde;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Özel çizim görünümleri. */
public final class Views {
    private Views() {}

    static Paint paint(int color) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); return p; }

    // ======================= KLAVYE =======================
    public static class Fretboard extends View {
        public int from = 0, to = 12;
        public final List<int[]> dots = new ArrayList<>();   // {tel, perde, renk}
        public final List<String> labels = new ArrayList<>();
        public interface OnTap { void tap(int string, int fret); }
        public OnTap onTap;
        private final Paint p = paint(0);
        private final float d;

        public Fretboard(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }

        public void addDot(int s, int f, int color, String label) { dots.add(new int[]{s, f, color}); labels.add(label); invalidate(); }
        public void clearDots() { dots.clear(); labels.clear(); invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            int W = MeasureSpec.getSize(w);
            setMeasuredDimension(W, (int) (170 * d));
        }

        float fretX(float f) {
            float left = 34 * d, right = getWidth() - 10 * d;
            int n = to - from + (from == 0 ? 1 : 1);
            float cell = (right - left) / n;
            return left + (f - from + (from == 0 ? 0 : 0)) * cell;
        }

        float stringY(int s) {
            float top = 18 * d, bottom = getHeight() - 26 * d;
            return top + (s - 1) * (bottom - top) / 5f;
        }

        @Override protected void onDraw(Canvas c) {
            int n = to - from + 1;
            float left = 34 * d, right = getWidth() - 10 * d;
            float cell = (right - left) / n;
            float top = stringY(1), bottom = stringY(6);
            p.setColor(0xFF3B2A1E);
            c.drawRoundRect(new RectF(left, top - 8 * d, right, bottom + 8 * d), 6 * d, 6 * d, p);
            // perde telleri
            for (int i = 0; i <= n; i++) {
                float x = left + i * cell;
                int fretNo = from + i - 1;
                p.setColor(fretNo == 0 && from == 0 ? 0xFFEEE8D5 : 0xFFB0B0B8);
                p.setStrokeWidth(fretNo == 0 && from == 0 ? 5 * d : 2 * d);
                if (i > 0) c.drawLine(x, top - 8 * d, x, bottom + 8 * d, p);
            }
            // işaret noktaları
            p.setColor(0x55FFFFFF);
            for (int f = Math.max(1, from); f <= to; f++) {
                float cx = left + (f - from + 0.5f) * cell;
                float cy = (top + bottom) / 2;
                if (f % 12 == 3 || f % 12 == 5 || f % 12 == 7 || f % 12 == 9) c.drawCircle(cx, cy, 5 * d, p);
                if (f % 12 == 0) { c.drawCircle(cx, stringY(2) + (stringY(3) - stringY(2)) / 2, 5 * d, p); c.drawCircle(cx, stringY(4) + (stringY(5) - stringY(4)) / 2, 5 * d, p); }
            }
            // teller
            for (int s = 1; s <= 6; s++) {
                p.setColor(s <= 2 ? 0xFFDADADA : 0xFFC9A86A);
                p.setStrokeWidth((0.8f + s * 0.35f) * d);
                c.drawLine(left - 4 * d, stringY(s), right, stringY(s), p);
            }
            // etiketler
            p.setTextSize(11 * d);
            p.setColor(Ui.MUTED);
            p.setTextAlign(Paint.Align.CENTER);
            String[] sn = {"", "e", "B", "G", "D", "A", "E"};
            for (int s = 1; s <= 6; s++) c.drawText(sn[s], 14 * d, stringY(s) + 4 * d, p);
            for (int f = from; f <= to; f++) {
                if (f == 0) continue;
                float cx = left + (f - from + 0.5f) * cell;
                if (f % 12 == 3 || f % 12 == 5 || f % 12 == 7 || f % 12 == 9 || f % 12 == 0 || f == from)
                    c.drawText(String.valueOf(f), cx, getHeight() - 6 * d, p);
            }
            // noktalar
            for (int i = 0; i < dots.size(); i++) {
                int[] dt = dots.get(i);
                float cx = dt[1] == 0 && from == 0 ? left + 0.5f * cell * 0.6f : left + (dt[1] - from + 0.5f) * cell;
                float cy = stringY(dt[0]);
                p.setColor(dt[2]);
                c.drawCircle(cx, cy, 10 * d, p);
                String lb = labels.get(i);
                if (lb != null && !lb.isEmpty()) {
                    p.setColor(Color.WHITE);
                    p.setTextSize(10 * d);
                    p.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText(lb, cx, cy + 3.5f * d, p);
                    p.setTypeface(Typeface.DEFAULT);
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (onTap == null) return false;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int n = to - from + 1;
                float left = 34 * d, right = getWidth() - 10 * d;
                float cell = (right - left) / n;
                int f = (int) Math.floor((e.getX() - left) / cell) + from;
                if (e.getX() < left) f = 0;
                f = Math.max(from, Math.min(to, f));
                int best = 1; float bd = 1e9f;
                for (int s = 1; s <= 6; s++) { float dd = Math.abs(stringY(s) - e.getY()); if (dd < bd) { bd = dd; best = s; } }
                onTap.tap(best, f);
            }
            return true;
        }
    }

    // ======================= TAB =======================
    public static class Tab extends View {
        public List<Ex.Note> notes = new ArrayList<>();
        public int[] state;           // 0 bekliyor, 1 doğru, 2 yanlış
        public float cursorBeat = -1; // zamanlı modda imleç
        public int current = 0;       // bekleme modunda sıradaki nota
        public boolean hide;          // kulaktan çalma
        public int beatsPerBar = 4;
        private final Paint p = paint(0);
        private final float d;
        public Tab(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }

        public void set(List<Ex.Note> n) { notes = n; state = new int[n.size()]; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (140 * d)); }

        @Override protected void onDraw(Canvas c) {
            float top = 18 * d, gap = 17 * d;
            float W = getWidth();
            float pxPerBeat = 70 * d;
            float playX = W * 0.28f;
            float offset;
            if (cursorBeat >= 0) offset = playX - cursorBeat * pxPerBeat;
            else {
                float cb = current < notes.size() ? notes.get(current).beat : (notes.isEmpty() ? 0 : notes.get(notes.size() - 1).beat);
                float total = notes.isEmpty() ? 0 : notes.get(notes.size() - 1).beat * pxPerBeat;
                offset = total < W - 60 * d ? 30 * d : Math.min(30 * d, playX - cb * pxPerBeat);
            }
            p.setStrokeWidth(1.2f * d);
            p.setColor(0xFF555A70);
            for (int s = 0; s < 6; s++) c.drawLine(0, top + s * gap, W, top + s * gap, p);
            // ölçü çizgileri
            if (!notes.isEmpty()) {
                float end = notes.get(notes.size() - 1).beat + notes.get(notes.size() - 1).dur;
                for (int b = 0; b <= end + 0.01f; b += beatsPerBar) {
                    float x = offset + b * pxPerBeat - 12 * d;
                    p.setColor(0xFF6A7088);
                    c.drawLine(x, top, x, top + 5 * gap, p);
                }
            }
            if (cursorBeat >= 0) {
                p.setColor(0x661CB0F6);
                p.setStrokeWidth(3 * d);
                c.drawLine(playX, top - 10 * d, playX, top + 5 * gap + 10 * d, p);
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = 0; i < notes.size(); i++) {
                Ex.Note n = notes.get(i);
                float x = offset + n.beat * pxPerBeat;
                if (x < -30 * d || x > W + 30 * d) continue;
                int s = n.string > 0 ? n.string : 1;
                float y = top + (s - 1) * gap;
                int col = state != null && state[i] == 1 ? Ui.GREEN : state != null && state[i] == 2 ? Ui.RED : (cursorBeat < 0 && i == current ? Ui.BLUE : Ui.TEXT);
                String label = hide || n.string <= 0 ? "?" : (n.fret + (n.label != null ? n.label : ""));
                p.setTextSize(15 * d);
                float tw = p.measureText(label) + 8 * d;
                p.setColor(Ui.BG);
                c.drawRect(x - tw / 2, y - 9 * d, x + tw / 2, y + 9 * d, p);
                p.setColor(col);
                c.drawText(label, x, y + 5 * d, p);
                // süre çubuğu
                p.setColor(Ui.darker(col, 0.5f));
                c.drawRect(x - 6 * d, top + 5 * gap + 12 * d, x - 6 * d + Math.max(4 * d, n.dur * pxPerBeat - 6 * d), top + 5 * gap + 16 * d, p);
            }
            p.setTypeface(Typeface.DEFAULT);
        }
    }

    // ======================= PORTE =======================
    public static class Staff extends View {
        public int[] midis = new int[0];
        public int[] state;
        public int current = -1;
        private final Paint p = paint(Ui.TEXT);
        private final float d;
        public Staff(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        public void set(int[] m) { midis = m; state = new int[m.length]; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (170 * d)); }

        /** E4 (64) alt çizgi = adım 0. Diyatonik adım sayısı. */
        static int step(int midi) {
            int oct = midi / 12 - 1;
            String n = Theory.SHARP[Theory.pc(midi)];
            int li = "CDEFGAB".indexOf(n.charAt(0));
            return (oct * 7 + li) - (4 * 7 + 2);
        }

        @Override protected void onDraw(Canvas c) {
            float gap = 11 * d;
            float bottomLine = getHeight() * 0.62f;
            p.setStrokeWidth(1.3f * d);
            p.setColor(Ui.MUTED);
            float left = 10 * d, right = getWidth() - 10 * d;
            for (int i = 0; i < 5; i++) c.drawLine(left, bottomLine - i * gap, right, bottomLine - i * gap, p);
            // sol anahtarı
            p.setColor(Ui.TEXT);
            String clef = "𝄞";
            p.setTextSize(gap * 7.2f);
            if (p.hasGlyph(clef)) c.drawText(clef, left + 2 * d, bottomLine + gap * 1.15f, p);
            else { p.setTextSize(gap * 3.4f); p.setTypeface(Typeface.SERIF); c.drawText("G", left + 4 * d, bottomLine - gap * 0.3f, p); p.setTypeface(Typeface.DEFAULT); }
            float x0 = left + 70 * d;
            float stepX = midis.length > 0 ? Math.min(60 * d, (right - x0 - 20 * d) / midis.length) : 0;
            for (int i = 0; i < midis.length; i++) {
                int st = step(midis[i]);
                float x = x0 + i * stepX + stepX / 2;
                float y = bottomLine - st * gap / 2f;
                p.setColor(Ui.MUTED);
                p.setStrokeWidth(1.3f * d);
                for (int l = -2; l >= st; l -= 2) c.drawLine(x - 12 * d, bottomLine - l * gap / 2f, x + 12 * d, bottomLine - l * gap / 2f, p);
                for (int l = 10; l <= st; l += 2) c.drawLine(x - 12 * d, bottomLine - l * gap / 2f, x + 12 * d, bottomLine - l * gap / 2f, p);
                int col = state != null && state[i] == 1 ? Ui.GREEN : state != null && state[i] == 2 ? Ui.RED : (i == current ? Ui.BLUE : Ui.TEXT);
                p.setColor(col);
                c.save();
                c.rotate(-20, x, y);
                c.drawOval(new RectF(x - 7 * d, y - 5 * d, x + 7 * d, y + 5 * d), p);
                c.restore();
                p.setStrokeWidth(1.6f * d);
                if (st < 4) c.drawLine(x + 6.3f * d, y, x + 6.3f * d, y - 3.3f * gap, p);
                else c.drawLine(x - 6.3f * d, y, x - 6.3f * d, y + 3.3f * gap, p);
                if (Theory.SHARP[Theory.pc(midis[i])].length() > 1) {
                    p.setTextSize(16 * d);
                    c.drawText("#", x - 22 * d, y + 5 * d, p);
                }
            }
        }
    }

    // ======================= AKOR DİYAGRAMI =======================
    public static class Chord extends View {
        public String shape = "x32010";
        public String name = "";
        private final Paint p = paint(Ui.TEXT);
        private final float d;
        public Chord(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (190 * d)); }

        @Override protected void onDraw(Canvas c) {
            int[] f = Theory.parseShape(shape);
            int mx = 0, mn = 99;
            for (int v : f) if (v > 0) { mx = Math.max(mx, v); mn = Math.min(mn, v); }
            int base = mx <= 4 ? 1 : mn;
            float w = 150 * d, left = (getWidth() - w) / 2, top = 40 * d, h = 120 * d;
            float sx = w / 5, fy = h / 4;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(18 * d);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setColor(Ui.TEXT);
            c.drawText(name, getWidth() / 2f, 18 * d, p);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(Ui.MUTED);
            p.setStrokeWidth(1.5f * d);
            for (int i = 0; i < 6; i++) c.drawLine(left + i * sx, top, left + i * sx, top + h, p);
            for (int j = 0; j <= 4; j++) {
                p.setStrokeWidth(j == 0 && base == 1 ? 5 * d : 1.5f * d);
                c.drawLine(left, top + j * fy, left + w, top + j * fy, p);
            }
            if (base > 1) { p.setTextSize(13 * d); p.setColor(Ui.TEXT); c.drawText(base + "fr", left - 22 * d, top + fy * 0.65f, p); }
            // barre tespiti
            int minF = 99; int cnt = 0;
            for (int v : f) if (v > 0) minF = Math.min(minF, v);
            for (int v : f) if (v == minF) cnt++;
            for (int i = 0; i < 6; i++) {
                float x = left + i * sx;
                if (f[i] < 0) { p.setColor(Ui.RED); p.setTextSize(14 * d); c.drawText("×", x, top - 8 * d, p); continue; }
                if (f[i] == 0) { p.setColor(Ui.MUTED); p.setStyle(Paint.Style.STROKE); c.drawCircle(x, top - 13 * d, 5 * d, p); p.setStyle(Paint.Style.FILL); continue; }
                float y = top + (f[i] - base + 0.5f) * fy;
                p.setColor(Ui.BLUE);
                c.drawCircle(x, y, 9 * d, p);
            }
            if (cnt >= 3 && minF > 0 && minF < 99) {
                int a = -1, b = -1;
                for (int i = 0; i < 6; i++) if (f[i] == minF) { if (a < 0) a = i; b = i; }
                float y = top + (minF - base + 0.5f) * fy;
                p.setColor(Ui.BLUE);
                c.drawRoundRect(new RectF(left + a * sx - 9 * d, y - 9 * d, left + b * sx + 9 * d, y + 9 * d), 9 * d, 9 * d, p);
            }
        }
    }

    // ======================= VURUŞ / RİTİM ŞERİDİ =======================
    public static class Beats extends View {
        public int beatsPerBar = 4;
        public float beatPos = -1;     // şu anki vuruş (count-in negatif)
        public boolean[] grid;          // ritim kalıbı
        public int stepsPerBar = 8;
        public int[] gridState;
        public String rhythmText;
        public String centerText = "";
        public float gridPos = Float.NaN; // sayım sonrası vuruş (negatif = sayım sürüyor)
        public boolean hideDots;          // metronom sustuğunda nabız noktaları gizlenir
        private final Paint p = paint(0);
        private final float d;
        public Beats(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) ((grid != null ? 130 : 70) * d)); }

        @Override protected void onDraw(Canvas c) {
            float W = getWidth();
            float r = 11 * d;
            int cur = beatPos < 0 || hideDots ? -1 : ((int) Math.floor(beatPos)) % beatsPerBar;
            for (int i = 0; i < beatsPerBar; i++) {
                float x = W / 2 + (i - (beatsPerBar - 1) / 2f) * 36 * d;
                p.setColor(i == cur ? (i == 0 ? Ui.ORANGE : Ui.BLUE) : Ui.SURFACE2);
                c.drawCircle(x, 22 * d, i == cur ? r * 1.25f : r, p);
            }
            if (centerText != null && !centerText.isEmpty()) {
                p.setColor(Ui.TEXT); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(15 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText(centerText, W / 2, 58 * d, p);
                p.setTypeface(Typeface.DEFAULT);
            }
            if (grid == null) return;
            int total = grid.length;
            float top = 75 * d, cellW = Math.min(30 * d, (W - 20 * d) / Math.min(total, stepsPerBar * 2));
            int visible = Math.min(total, stepsPerBar * 2);
            float gp = Float.isNaN(gridPos) ? beatPos : gridPos;
            int curStep = gp < 0 ? -1 : (int) Math.floor(gp / beatsPerBar * stepsPerBar);
            int startStep = Math.max(0, Math.min(total - visible, (curStep / stepsPerBar) * stepsPerBar - (curStep >= stepsPerBar ? 0 : 0)));
            if (curStep >= stepsPerBar * 2) startStep = Math.min(total - visible, (curStep / stepsPerBar - 1) * stepsPerBar);
            float left = (W - visible * cellW) / 2;
            for (int k = 0; k < visible; k++) {
                int i = startStep + k;
                float x = left + k * cellW;
                boolean beat = i % (stepsPerBar / beatsPerBar == 0 ? 1 : stepsPerBar / beatsPerBar) == 0;
                p.setColor(i == curStep ? 0x441CB0F6 : 0);
                c.drawRect(x, top, x + cellW, top + 40 * d, p);
                if (grid[i]) {
                    int st = gridState != null ? gridState[i] : 0;
                    p.setColor(st == 1 ? Ui.GREEN : st == 2 ? Ui.RED : Ui.TEXT);
                    char ch = rhythmText != null && rhythmText.length() > 0 ? rhythmText.charAt(i % rhythmText.length()) : 'D';
                    p.setTextAlign(Paint.Align.CENTER); p.setTextSize(16 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
                    c.drawText(ch == 'U' ? "↑" : ch == 'X' || ch == 'x' ? "×" : "↓", x + cellW / 2, top + 26 * d, p);
                } else {
                    p.setColor(Ui.LINE);
                    c.drawCircle(x + cellW / 2, top + 20 * d, 2.5f * d, p);
                }
                if (beat) { p.setColor(Ui.MUTED); p.setTextSize(10 * d); p.setTypeface(Typeface.DEFAULT); p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf((i / Math.max(1, stepsPerBar / beatsPerBar)) % beatsPerBar + 1), x + cellW / 2, top + 52 * d, p); }
            }
        }
    }

    // ======================= PERDE ÖLÇER (akort / bend) =======================
    public static class Meter extends View {
        public float cents = 0;    // -50..+50
        public boolean active = false;
        public String big = "", small = "";
        public float progress = -1; // bend için 0..1+
        private final Paint p = paint(0);
        private final float d;
        public Meter(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (150 * d)); }

        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), cx = W / 2;
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(active ? Ui.TEXT : Ui.MUTED);
            p.setTextSize(44 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(big, cx, 52 * d, p);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(14 * d); p.setColor(Ui.MUTED);
            c.drawText(small, cx, 74 * d, p);
            float bw = W - 60 * d, left = 30 * d, y = 105 * d;
            p.setColor(Ui.SURFACE2);
            c.drawRoundRect(new RectF(left, y - 6 * d, left + bw, y + 6 * d), 6 * d, 6 * d, p);
            if (progress >= 0) {
                float x = left + Math.max(0, Math.min(1.2f, progress)) / 1.2f * bw;
                float tx = left + bw / 1.2f;
                p.setColor(Ui.GREEN); c.drawRect(tx - 2 * d, y - 14 * d, tx + 2 * d, y + 14 * d, p);
                p.setColor(Math.abs(progress - 1) < 0.15f ? Ui.GREEN : Ui.BLUE);
                c.drawCircle(x, y, 11 * d, p);
                return;
            }
            p.setColor(Ui.GREEN);
            c.drawRect(cx - 2 * d, y - 14 * d, cx + 2 * d, y + 14 * d, p);
            if (active) {
                float x = cx + Math.max(-50, Math.min(50, cents)) / 50f * bw / 2;
                p.setColor(Math.abs(cents) < 5 ? Ui.GREEN : Math.abs(cents) < 15 ? Ui.YELLOW : Ui.RED);
                c.drawCircle(x, y, 11 * d, p);
            }
            p.setColor(Ui.MUTED); p.setTextSize(11 * d);
            c.drawText("pes", left + 14 * d, y + 30 * d, p);
            c.drawText("tiz", left + bw - 14 * d, y + 30 * d, p);
        }
    }

    // ======================= İLERLEME ÇUBUĞU =======================
    public static class Bar extends View {
        public float value = 0; public int color = Ui.GREEN;
        private final Paint p = paint(0); private final float d;
        public Bar(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (16 * d)); }
        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), W = getWidth();
            p.setColor(Ui.SURFACE2); c.drawRoundRect(new RectF(0, 0, W, h), h / 2, h / 2, p);
            p.setColor(color); c.drawRoundRect(new RectF(0, 0, Math.max(h, W * Math.min(1, value)), h), h / 2, h / 2, p);
            p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(h / 2, h * 0.2f, Math.max(h, W * Math.min(1, value)) - h / 2, h * 0.42f), h / 4, h / 4, p);
        }
    }

    // ======================= HALKA (günlük hedef) =======================
    public static class Ring extends View {
        public float value; public String label = ""; public int color = Ui.ORANGE;
        private final Paint p = paint(0); private final float d;
        public Ring(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), st = 7 * d;
            RectF r = new RectF(st, st, s - st, s - st);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(st); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Ui.SURFACE2); c.drawArc(r, 0, 360, false, p);
            p.setColor(color); c.drawArc(r, -90, 360 * Math.min(1, value), false, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Ui.TEXT); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(13 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(label, s / 2, s / 2 + 5 * d, p);
        }
    }

    // ======================= KONFETİ =======================
    public static class Confetti extends View {
        static class P { float x, y, vx, vy, rot, vr; int color; }
        private final List<P> ps = new ArrayList<>();
        private final Paint p = paint(0);
        private long last;
        public Confetti(Context c) { super(c); setClickable(false); }
        public void burst() {
            Random r = new Random();
            int[] cols = {Ui.GREEN, Ui.BLUE, Ui.YELLOW, Ui.ORANGE, Ui.PINK, Ui.PURPLE, Ui.RED};
            float W = Math.max(1, getWidth());
            for (int i = 0; i < 120; i++) {
                P q = new P();
                q.x = W / 2 + (r.nextFloat() - 0.5f) * W * 0.3f; q.y = getHeight() * 0.35f;
                q.vx = (r.nextFloat() - 0.5f) * 1400; q.vy = -r.nextFloat() * 1500 - 300;
                q.rot = r.nextFloat() * 360; q.vr = (r.nextFloat() - 0.5f) * 720; q.color = cols[r.nextInt(cols.length)];
                ps.add(q);
            }
            last = System.nanoTime();
            invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            if (ps.isEmpty()) return;
            long now = System.nanoTime(); float dt = Math.min(0.05f, (now - last) / 1e9f); last = now;
            float s = getResources().getDisplayMetrics().density;
            for (int i = ps.size() - 1; i >= 0; i--) {
                P q = ps.get(i);
                q.vy += 2200 * dt; q.x += q.vx * dt; q.y += q.vy * dt; q.rot += q.vr * dt; q.vx *= 0.99f;
                if (q.y > getHeight() + 40) { ps.remove(i); continue; }
                p.setColor(q.color);
                c.save(); c.rotate(q.rot, q.x, q.y); c.drawRect(q.x - 4 * s, q.y - 7 * s, q.x + 4 * s, q.y + 7 * s, p); c.restore();
            }
            if (!ps.isEmpty()) postInvalidateOnAnimation();
        }
    }

    // ======================= Yol düğümü (ders dairesi) =======================
    public static class Node extends View {
        public int color = Ui.GREEN; public int stars = -1; public boolean locked, current; public String glyph = "★";
        private final Paint p = paint(0); private final float d;
        private float pulse;
        public Node(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension((int) (86 * d), (int) (96 * d)); }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = 40 * d, r = 32 * d;
            int col = locked ? 0xFF3A3E52 : color;
            if (current) {
                pulse = (pulse + 0.04f) % 1f;
                p.setColor((((int) (90 * (1 - pulse))) << 24) | (col & 0xFFFFFF));
                c.drawCircle(cx, cy + 3 * d, r + 10 * d * pulse, p);
                postInvalidateDelayed(30);
            }
            p.setColor(Ui.darker(col, 0.7f));
            c.drawOval(new RectF(cx - r, cy - r + 6 * d, cx + r, cy + r + 6 * d), p);
            p.setColor(col);
            c.drawOval(new RectF(cx - r, cy - r, cx + r, cy + r), p);
            p.setColor(locked ? 0xFF6A6F86 : Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(26 * d); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(locked ? "🔒" : glyph, cx, cy + 9 * d, p);
            if (stars > 0) {
                p.setTextSize(13 * d);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) sb.append(i < stars ? "★" : "☆");
                p.setColor(Ui.YELLOW);
                c.drawText(sb.toString(), cx, cy + r + 22 * d, p);
            }
        }
    }


    // ======================= RİTİM NOTASI (tek çizgi) =======================
    public static class RhythmNotation extends View {
        public static class Item {
            public float beat, dur; public boolean rest, dotted, triplet; public String value; // w h q e s
            public boolean barBefore;
        }
        public List<Item> items = new ArrayList<>();
        public int[] state = new int[0];
        public float cursorBeat = -99;
        public int beatsPerBar = 4;
        private final Paint p = paint(Ui.TEXT);
        private final float d;
        public RhythmNotation(Context c) { super(c); d = c.getResources().getDisplayMetrics().density; }
        public void set(List<Item> it, int bpb) { items = it; beatsPerBar = bpb; state = new int[it.size()]; invalidate(); }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (120 * d)); }

        @Override protected void onDraw(Canvas c) {
            if (items.isEmpty()) return;
            Item last = items.get(items.size() - 1);
            float totalBeats = (float) Math.ceil((last.beat + last.dur) / beatsPerBar - 0.001) * beatsPerBar;
            float W = getWidth(), left = 14 * d, right = W - 10 * d;
            // en fazla 2 ölçü göster, imleçle kaydır
            float perView = Math.min(totalBeats, beatsPerBar * 2);
            float startBeat = 0;
            if (cursorBeat > perView - 0.5f) startBeat = (float) Math.floor((cursorBeat) / beatsPerBar) * beatsPerBar - (perView > beatsPerBar ? beatsPerBar : 0);
            startBeat = Math.max(0, Math.min(startBeat, totalBeats - perView));
            float pxb = (right - left) / perView;
            float y = 70 * d;
            p.setStrokeWidth(1.5f * d); p.setColor(Ui.MUTED);
            c.drawLine(left, y, right, y, p);
            for (float b = 0; b <= totalBeats + 0.01f; b += beatsPerBar) {
                float x = left + (b - startBeat) * pxb - 4 * d;
                if (x < left - 1 || x > right + 1) continue;
                c.drawLine(x, y - 22 * d, x, y + 22 * d, p);
            }
            // vuruş numaraları
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(10 * d);
            for (int b = (int) startBeat; b < startBeat + perView; b++) {
                float x = left + (b - startBeat) * pxb + 8 * d;
                c.drawText(String.valueOf(b % beatsPerBar + 1), x, y + 40 * d, p);
            }
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.beat < startBeat - 0.01f || it.beat >= startBeat + perView - 0.01f) continue;
                float x = left + (it.beat - startBeat) * pxb + 8 * d;
                int col = state.length > i && state[i] == 1 ? Ui.GREEN : state.length > i && state[i] == 2 ? Ui.RED : Ui.TEXT;
                p.setColor(col);
                p.setStrokeWidth(1.8f * d);
                if (it.rest) { drawRest(c, it, x, y); continue; }
                boolean open = it.value.equals("w") || it.value.equals("h");
                p.setStyle(open ? Paint.Style.STROKE : Paint.Style.FILL);
                c.save(); c.rotate(-20, x, y);
                c.drawOval(new RectF(x - 6 * d, y - 4.5f * d, x + 6 * d, y + 4.5f * d), p);
                c.restore();
                p.setStyle(Paint.Style.FILL);
                if (!it.value.equals("w")) c.drawLine(x + 5.5f * d, y, x + 5.5f * d, y - 30 * d, p);
                int flags = it.value.equals("e") ? 1 : it.value.equals("s") ? 2 : 0;
                // komşu kısa notalarla kiriş (aynı vuruş içinde)
                Item nx = i + 1 < items.size() ? items.get(i + 1) : null;
                boolean beamNext = flags > 0 && nx != null && !nx.rest && (nx.value.equals("e") || nx.value.equals("s")) && Math.floor(nx.beat + 0.001) == Math.floor(it.beat + 0.001) && nx.beat < startBeat + perView;
                Item pv = i > 0 ? items.get(i - 1) : null;
                boolean beamPrev = flags > 0 && pv != null && !pv.rest && (pv.value.equals("e") || pv.value.equals("s")) && Math.floor(pv.beat + 0.001) == Math.floor(it.beat + 0.001);
                if (beamNext) {
                    float nxX = left + (nx.beat - startBeat) * pxb + 8 * d;
                    int nf = nx.value.equals("s") ? 2 : 1;
                    for (int k = 0; k < Math.min(flags, nf); k++) c.drawRect(x + 5.5f * d - 1, y - 30 * d + k * 5 * d, nxX + 5.5f * d + 1, y - 27 * d + k * 5 * d, p);
                } else if (!beamPrev) {
                    for (int k = 0; k < flags; k++) c.drawLine(x + 5.5f * d, y - 30 * d + k * 6 * d, x + 12 * d, y - 22 * d + k * 6 * d, p);
                }
                if (it.dotted) c.drawCircle(x + 11 * d, y - 1 * d, 2 * d, p);
                if (it.triplet && (pv == null || !pv.triplet || Math.floor(pv.beat + 0.001) != Math.floor(it.beat + 0.001))) {
                    p.setTextSize(11 * d); c.drawText("3", x + 14 * d, y - 36 * d, p);
                }
            }
            if (cursorBeat >= startBeat && cursorBeat <= startBeat + perView) {
                float x = left + (cursorBeat - startBeat) * pxb + 8 * d;
                p.setColor(0x881CB0F6); p.setStrokeWidth(3 * d);
                c.drawLine(x, y - 38 * d, x, y + 24 * d, p);
            }
        }

        private void drawRest(Canvas c, Item it, float x, float y) {
            switch (it.value) {
                case "w": c.drawRect(x - 6 * d, y - 8 * d, x + 6 * d, y - 3 * d, p); break;
                case "h": c.drawRect(x - 6 * d, y - 4 * d, x + 6 * d, y + 1 * d, p); break;
                case "q": {
                    Path z = new Path();
                    z.moveTo(x - 2 * d, y - 14 * d); z.lineTo(x + 4 * d, y - 6 * d); z.lineTo(x - 2 * d, y + 1 * d);
                    z.lineTo(x + 4 * d, y + 8 * d); z.quadTo(x - 5 * d, y + 4 * d, x + 1 * d, y + 13 * d);
                    p.setStyle(Paint.Style.STROKE); c.drawPath(z, p); p.setStyle(Paint.Style.FILL); break;
                }
                default: {
                    c.drawCircle(x - 2 * d, y - 6 * d, 2.5f * d, p);
                    c.drawLine(x - 2 * d, y - 5 * d, x + 4 * d, y - 7 * d, p);
                    c.drawLine(x + 4 * d, y - 7 * d, x, y + 8 * d, p);
                    if (it.value.equals("s")) c.drawCircle(x - 3 * d, y + 1 * d, 2.5f * d, p);
                }
            }
            if (it.dotted) c.drawCircle(x + 9 * d, y - 3 * d, 2 * d, p);
        }
    }

    public static Path star(float cx, float cy, float r) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) (rr * Math.cos(a)), y = cy + (float) (rr * Math.sin(a));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        return path;
    }
}
