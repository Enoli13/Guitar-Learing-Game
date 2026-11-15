package com.enoli.perde;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Uygulamanın karakteri "Pena" ve diğer çizim nesneleri (sandık, ünite objeleri). */
public final class Art {
    private Art() {}

    private static final android.graphics.Rect TMP = new android.graphics.Rect();
    /** Ekranda görünür mü? Kaydırılıp dışarıda kalan çizimler boşuna yeniden çizilmesin. */
    static boolean onScreen(View v) { return v.isShown() && v.getLocalVisibleRect(TMP); }

    // =====================================================================
    //  PENA — pena şeklinde, elektro gitar seven maskot
    // =====================================================================
    public static class Mascot extends View {
        public enum Mood { IDLE, THINK, LISTEN, HAPPY, SAD, CHEER, TEACH }

        private Mood mood = Mood.IDLE;
        private long moodStart = System.nanoTime();
        private final long born = System.nanoTime();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        public int bodyColor = 0xFFFF9600;
        public boolean animate = true;
        private final List<float[]> notes = new ArrayList<>(); // uçan notalar {x,y,vy,life,rot}
        private final Random rnd = new Random();
        private long lastNote;

        public Mascot(Context c) { super(c); }

        public void setMood(Mood m) {
            mood = m;
            moodStart = System.nanoTime();
            invalidate();
        }

        public Mood mood() { return mood; }

        /** Kısa bir sevinç zıplaması, sonra önceki moda döner. */
        public void pop() {
            Mood prev = mood == Mood.HAPPY ? Mood.IDLE : mood;
            setMood(Mood.HAPPY);
            postDelayed(() -> { if (mood == Mood.HAPPY) setMood(prev); }, 700);
        }

        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); animate = false; }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); animate = true; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight();
            float s = Math.min(W, H) / 100f;      // 100 birimlik çizim alanı
            float t = (System.nanoTime() - born) / 1e9f;
            float mt = (System.nanoTime() - moodStart) / 1e9f;
            c.save();
            c.translate((W - 100 * s) / 2, (H - 100 * s) / 2);
            c.scale(s, s);

            // ---- hareket ----
            float dy = 0, rot = 0, squash = 1;
            switch (mood) {
                case IDLE: case TEACH: dy = (float) Math.sin(t * 2.2) * 2.2f; break;
                case THINK: dy = (float) Math.sin(t * 1.6) * 1.5f; rot = -6; break;
                case LISTEN: dy = (float) Math.sin(t * 6.0) * 1.6f; rot = (float) Math.sin(t * 3) * 5; break;
                case HAPPY: { float j = Math.max(0, (float) Math.sin(mt * 9)); dy = -j * 12; squash = 1 + (1 - j) * 0.05f; break; }
                case CHEER: { float j = Math.abs((float) Math.sin(mt * 7)); dy = -j * 14; rot = (float) Math.sin(mt * 7) * 8; break; }
                case SAD: dy = 5 + (float) Math.sin(t * 1.2) * 0.8f; rot = 4; break;
            }
            // gölge
            p.setColor(0x33000000);
            float sh = 1 - Math.min(0.6f, -dy / 30f);
            c.drawOval(new RectF(50 - 22 * sh, 92, 50 + 22 * sh, 98), p);

            c.save();
            c.translate(0, dy);
            c.rotate(rot, 50, 55);
            c.scale(1 / squash, squash, 50, 90);

            // ---- kollar (gövdenin arkasında) ----
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4.5f);
            p.setColor(Ui.darker(bodyColor, 0.75f));
            float armUp = mood == Mood.HAPPY || mood == Mood.CHEER ? -26 : mood == Mood.SAD ? 12 : mood == Mood.TEACH ? -14 : 4;
            float wave = mood == Mood.CHEER ? (float) Math.sin(mt * 14) * 8 : mood == Mood.TEACH ? (float) Math.sin(t * 5) * 5 : 0;
            c.drawLine(22, 48, 8, 50 + armUp + wave, p);
            c.drawLine(78, 48, 92, 50 + (mood == Mood.TEACH ? 4 : armUp) - wave, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(8, 50 + armUp + wave, 4, p);
            c.drawCircle(92, 50 + (mood == Mood.TEACH ? 4 : armUp) - wave, 4, p);

            // ---- gövde: pena şekli ----
            path.reset();
            path.moveTo(50, 92);
            path.cubicTo(30, 78, 10, 52, 12, 32);
            path.cubicTo(14, 12, 32, 6, 50, 6);
            path.cubicTo(68, 6, 86, 12, 88, 32);
            path.cubicTo(90, 52, 70, 78, 50, 92);
            path.close();
            p.setShader(new LinearGradient(0, 6, 0, 92, Ui.darker(bodyColor, 1.0f) | 0xFF000000, Ui.darker(bodyColor, 0.78f), Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
            // parlama
            p.setColor(0x40FFFFFF);
            c.drawOval(new RectF(24, 12, 44, 22), p);
            // kenar
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); p.setColor(Ui.darker(bodyColor, 0.6f));
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);

            // ---- yüz ----
            boolean blink = (t % 3.7f) < 0.12f && mood != Mood.HAPPY && mood != Mood.CHEER;
            float eyeY = 38, lx = 37, rx = 63;
            float lookX = mood == Mood.THINK ? 3 : mood == Mood.LISTEN ? (float) Math.sin(t * 2) * 2 : 0;
            float lookY = mood == Mood.THINK ? -3 : mood == Mood.SAD ? 2 : 0;
            if (mood == Mood.HAPPY || mood == Mood.CHEER) {
                // ^ ^ gözler
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(0xFF2B1A00);
                Path e = new Path();
                e.moveTo(lx - 7, eyeY + 2); e.quadTo(lx, eyeY - 7, lx + 7, eyeY + 2);
                e.moveTo(rx - 7, eyeY + 2); e.quadTo(rx, eyeY - 7, rx + 7, eyeY + 2);
                c.drawPath(e, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                p.setColor(Color.WHITE);
                if (blink) { c.drawRect(lx - 9, eyeY - 1, lx + 9, eyeY + 2, p); c.drawRect(rx - 9, eyeY - 1, rx + 9, eyeY + 2, p); }
                else {
                    c.drawOval(new RectF(lx - 9, eyeY - 11, lx + 9, eyeY + 9), p);
                    c.drawOval(new RectF(rx - 9, eyeY - 11, rx + 9, eyeY + 9), p);
                    p.setColor(0xFF1B1D2A);
                    c.drawCircle(lx + lookX, eyeY + lookY, 5, p);
                    c.drawCircle(rx + lookX, eyeY + lookY, 5, p);
                    p.setColor(Color.WHITE);
                    c.drawCircle(lx + lookX + 1.8f, eyeY + lookY - 2, 1.6f, p);
                    c.drawCircle(rx + lookX + 1.8f, eyeY + lookY - 2, 1.6f, p);
                }
            }
            // kaşlar
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(0xFF4A2A00);
            if (mood == Mood.SAD) { c.drawLine(lx - 8, eyeY - 12, lx + 6, eyeY - 16, p); c.drawLine(rx + 8, eyeY - 12, rx - 6, eyeY - 16, p); }
            else if (mood == Mood.THINK) { c.drawLine(lx - 7, eyeY - 15, lx + 7, eyeY - 14, p); c.drawLine(rx - 7, eyeY - 18, rx + 7, eyeY - 15, p); }
            else if (mood != Mood.HAPPY && mood != Mood.CHEER) { c.drawLine(lx - 7, eyeY - 15, lx + 7, eyeY - 16, p); c.drawLine(rx - 7, eyeY - 16, rx + 7, eyeY - 15, p); }
            // ağız
            p.setColor(0xFF4A1A00);
            Path m = new Path();
            switch (mood) {
                case HAPPY: case CHEER:
                    p.setStyle(Paint.Style.FILL);
                    m.moveTo(38, 54); m.quadTo(50, 72, 62, 54); m.close();
                    c.drawPath(m, p);
                    p.setColor(0xFFFF6B8A); c.drawOval(new RectF(45, 60, 55, 66), p);
                    break;
                case SAD:
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
                    m.moveTo(41, 63); m.quadTo(50, 55, 59, 63); c.drawPath(m, p);
                    // gözyaşı
                    p.setStyle(Paint.Style.FILL); p.setColor(0xFF7FD3FF);
                    float ty = (mt * 25) % 22;
                    c.drawCircle(lx - 6, eyeY + 10 + ty, 2.6f, p);
                    break;
                case THINK:
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
                    c.drawLine(44, 60, 56, 58, p);
                    break;
                case LISTEN:
                    p.setStyle(Paint.Style.FILL);
                    float o = 3 + Math.abs((float) Math.sin(t * 6)) * 3;
                    c.drawOval(new RectF(46, 56, 54, 56 + o + 3), p);
                    break;
                default:
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
                    m.moveTo(42, 56); m.quadTo(50, 64, 58, 56); c.drawPath(m, p);
            }
            p.setStyle(Paint.Style.FILL);
            // yanaklar
            p.setColor(0x55FF5E5E);
            c.drawCircle(28, 52, 4.5f, p); c.drawCircle(72, 52, 4.5f, p);
            // LISTEN: kulaklık
            if (mood == Mood.LISTEN) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(0xFF2A2D3E);
                c.drawArc(new RectF(14, 2, 86, 60), 195, 150, false, p);
                p.setStyle(Paint.Style.FILL); p.setColor(0xFF1CB0F6);
                c.drawRoundRect(new RectF(10, 26, 20, 42), 4, 4, p);
                c.drawRoundRect(new RectF(80, 26, 90, 42), 4, 4, p);
            }
            c.restore();

            // ---- efektler ----
            if (mood == Mood.CHEER || mood == Mood.HAPPY) {
                p.setColor(Ui.YELLOW);
                for (int i = 0; i < 4; i++) {
                    double a = t * 2 + i * Math.PI / 2;
                    float sx = 50 + (float) Math.cos(a) * 46, sy = 40 + (float) Math.sin(a) * 30 + dy * 0.3f;
                    c.drawPath(Views.star(sx, sy, 4 + (float) Math.sin(t * 8 + i) * 1.5f), p);
                }
            }
            if (mood == Mood.LISTEN && System.nanoTime() - lastNote > 450_000_000L) {
                lastNote = System.nanoTime();
                notes.add(new float[]{rnd.nextBoolean() ? 6 : 86, 30, -18 - rnd.nextFloat() * 8, 1f, rnd.nextFloat() * 30 - 15});
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            for (int i = notes.size() - 1; i >= 0; i--) {
                float[] n = notes.get(i);
                n[1] += n[2] * 0.016f; n[3] -= 0.016f * 0.8f;
                if (n[3] <= 0) { notes.remove(i); continue; }
                p.setColor(Color.argb((int) (255 * n[3]), 28, 176, 246));
                p.setTextSize(14);
                c.save(); c.rotate(n[4], n[0], n[1]); c.drawText("♪", n[0], n[1], p); c.restore();
            }
            c.restore();
            if (animate && onScreen(this)) postInvalidateOnAnimation();
        }
    }

    // =====================================================================
    //  SANDIK — sallanır, açılınca ışık saçar
    // =====================================================================
    public static class Chest extends View {
        private boolean open;
        private long openedAt;
        private final long born = System.nanoTime();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Chest(Context c) { super(c); }
        public void openNow() { open = true; openedAt = System.nanoTime(); invalidate(); }
        public boolean isOpen() { return open; }

        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight();
            float s = Math.min(W, H) / 100f;
            float t = (System.nanoTime() - born) / 1e9f;
            c.save();
            c.translate((W - 100 * s) / 2, (H - 100 * s) / 2);
            c.scale(s, s);
            float ot = open ? (System.nanoTime() - openedAt) / 1e9f : 0;
            if (open) {
                // ışınlar
                c.save();
                c.rotate(ot * 40, 50, 50);
                p.setColor(0x55FFE066);
                for (int i = 0; i < 12; i++) {
                    c.rotate(30, 50, 50);
                    Path r = new Path(); r.moveTo(50, 50); r.lineTo(46, -10); r.lineTo(54, -10); r.close();
                    c.drawPath(r, p);
                }
                c.restore();
            } else {
                // davetkâr sallanma
                float k = (t % 2f) < 0.5f ? (float) Math.sin(t * 40) * 4 : 0;
                c.rotate(k, 50, 85);
            }
            // gövde
            p.setColor(0xFF9A5B22); c.drawRoundRect(new RectF(14, 46, 86, 88), 6, 6, p);
            p.setColor(0xFF7A4518); c.drawRect(14, 60, 86, 64, p);
            p.setColor(Ui.YELLOW); c.drawRect(14, 46, 20, 88, p); c.drawRect(80, 46, 86, 88, p);
            // kapak
            c.save();
            float lid = open ? Math.min(1, ot * 4) * -70 : 0;
            c.rotate(lid, 16, 46);
            p.setColor(0xFFB06A2A); c.drawRoundRect(new RectF(12, 26, 88, 48), 10, 10, p);
            p.setColor(Ui.YELLOW); c.drawRect(12, 26, 18, 48, p); c.drawRect(82, 26, 88, 48, p);
            c.restore();
            // kilit
            if (!open) { p.setColor(Ui.YELLOW); c.drawRoundRect(new RectF(44, 42, 56, 56), 3, 3, p); p.setColor(0xFF5A3510); c.drawCircle(50, 48, 2.2f, p); }
            else {
                // fırlayan penalar
                for (int i = 0; i < 6; i++) {
                    float a = (float) (Math.PI * (0.15 + 0.7 * i / 5.0));
                    float d = Math.min(1.2f, ot * 2) * 40;
                    float x = 50 - (float) Math.cos(a) * d, y = 46 - (float) Math.sin(a) * d + ot * ot * 10;
                    p.setColor(i % 2 == 0 ? Ui.ORANGE : Ui.YELLOW);
                    Path pk = new Path(); pk.moveTo(x, y + 6); pk.quadTo(x - 6, y - 2, x - 4, y - 5); pk.quadTo(x, y - 7, x + 4, y - 5); pk.quadTo(x + 6, y - 2, x, y + 6); c.drawPath(pk, p);
                }
            }
            c.restore();
            if (onScreen(this) && (open ? ot < 6 : true)) postInvalidateOnAnimation();
        }
    }

    // =====================================================================
    //  ÜNİTE OBJELERİ — elektro gitar, amfi, metronom, nota, pedal
    // =====================================================================
    public static class UnitObject extends View {
        public int kind;
        private final long born = System.nanoTime();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public UnitObject(Context c, int kind) { super(c); this.kind = kind % 5; }

        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight();
            float s = Math.min(W, H) / 100f;
            float t = (System.nanoTime() - born) / 1e9f;
            c.save();
            c.translate((W - 100 * s) / 2, (H - 100 * s) / 2);
            c.scale(s, s);
            c.rotate((float) Math.sin(t * 1.5) * 4, 50, 50);
            switch (kind) {
                case 0: guitar(c); break;
                case 1: amp(c, t); break;
                case 2: metronome(c, t); break;
                case 3: note(c); break;
                default: pedal(c); break;
            }
            c.restore();
            if (onScreen(this)) postInvalidateOnAnimation();
        }

        private void guitar(Canvas c) {
            c.save(); c.rotate(-35, 50, 50);
            p.setColor(0xFF6B4423); c.drawRect(46, 2, 54, 56, p);           // sap
            p.setColor(0xFFDDDDDD); for (int i = 0; i < 6; i++) c.drawRect(46, 8 + i * 8, 54, 9 + i * 8, p);
            p.setColor(0xFF2A2D3E); c.drawRoundRect(new RectF(43, -4, 57, 8), 3, 3, p); // kafa
            p.setColor(0xFFFFFFFF);
            Path b = new Path();                                             // strat gövde
            b.moveTo(50, 50); b.cubicTo(30, 44, 22, 56, 30, 66); b.cubicTo(20, 78, 30, 98, 50, 96);
            b.cubicTo(70, 98, 80, 78, 70, 66); b.cubicTo(78, 56, 70, 44, 50, 50); b.close();
            p.setColor(0xFFE23D3D); c.drawPath(b, p);
            p.setColor(0xFFFFFFFF); c.drawRoundRect(new RectF(40, 60, 60, 86), 6, 6, p);
            p.setColor(0xFF333333); for (int i = 0; i < 3; i++) c.drawRect(43, 64 + i * 7, 57, 66 + i * 7, p);
            c.restore();
        }

        private void amp(Canvas c, float t) {
            p.setColor(0xFF2A2A2A); c.drawRoundRect(new RectF(12, 18, 88, 90), 6, 6, p);
            p.setColor(0xFFC9A86A); c.drawRect(12, 18, 88, 32, p);
            p.setColor(0xFF111111); for (int i = 0; i < 5; i++) c.drawCircle(24 + i * 13, 25, 3, p);
            p.setColor(0xFF3B3B3B); c.drawRoundRect(new RectF(18, 38, 82, 84), 4, 4, p);
            float pulse = 1 + Math.abs((float) Math.sin(t * 6)) * 0.08f;
            p.setColor(0xFF1A1A1A); c.drawCircle(50, 61, 18 * pulse, p);
            p.setColor(0xFF444444); c.drawCircle(50, 61, 6, p);
        }

        private void metronome(Canvas c, float t) {
            Path b = new Path(); b.moveTo(30, 92); b.lineTo(42, 10); b.lineTo(58, 10); b.lineTo(70, 92); b.close();
            p.setColor(0xFF8B5A2B); c.drawPath(b, p);
            p.setColor(0xFFF2E3C6); Path f = new Path(); f.moveTo(38, 80); f.lineTo(46, 22); f.lineTo(54, 22); f.lineTo(62, 80); f.close(); c.drawPath(f, p);
            c.save(); c.rotate((float) Math.sin(t * Math.PI * 2) * 25, 50, 78);
            p.setColor(0xFF333333); c.drawRect(48.5f, 26, 51.5f, 78, p); c.drawRect(44, 38, 56, 44, p);
            c.restore();
        }

        private void note(Canvas c) {
            p.setColor(Ui.BLUE);
            c.drawOval(new RectF(18, 66, 42, 84), p); c.drawOval(new RectF(58, 58, 82, 76), p);
            c.drawRect(38, 18, 43, 76, p); c.drawRect(78, 10, 83, 68, p);
            Path beam = new Path(); beam.moveTo(38, 18); beam.lineTo(83, 10); beam.lineTo(83, 22); beam.lineTo(38, 30); beam.close();
            c.drawPath(beam, p);
        }

        private void pedal(Canvas c) {
            p.setColor(0xFFFF9600); c.drawRoundRect(new RectF(22, 12, 78, 92), 8, 8, p);
            p.setColor(0xFF222222); for (int i = 0; i < 3; i++) c.drawCircle(34 + i * 16, 26, 5, p);
            p.setColor(0xFFDDDDDD); c.drawCircle(50, 74, 9, p);
            p.setColor(Ui.RED); c.drawCircle(50, 46, 3, p);
            p.setColor(0xFF222222); p.setTextSize(9); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("DRIVE", 50, 60, p);
        }
    }

    // =====================================================================
    //  Konuşma balonu: maskot + balon satırı oluşturur
    // =====================================================================
    public static android.widget.LinearLayout speech(Context c, Mascot m, String text, int sizeDp, float textSp) {
        android.widget.LinearLayout row = Ui.hbox(c);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(m, new android.widget.LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp)));
        android.widget.TextView bubble = Ui.text(c, text, textSp, Ui.TEXT, true);
        bubble.setBackground(Ui.outlined(Ui.SURFACE, Ui.LINE, Ui.dp(c, 2), Ui.dp(c, 16)));
        int pd = Ui.dp(c, 12);
        bubble.setPadding(pd, pd, pd, pd);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        lp.leftMargin = Ui.dp(c, 8);
        row.addView(bubble, lp);
        row.setTag(bubble);
        return row;
    }
}
