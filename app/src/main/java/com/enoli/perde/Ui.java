package com.enoli.perde;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kod ile arayüz kurmak için küçük yardımcılar ve renk paleti. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF111219, SURFACE = 0xFF1C1E2B, SURFACE2 = 0xFF262938, LINE = 0xFF33374A;
    public static final int TEXT = 0xFFF2F3F7, MUTED = 0xFF9AA0B5;
    public static final int GREEN = 0xFF58CC02, GREEN_D = 0xFF46A302, RED = 0xFFFF4B4B, RED_D = 0xFFD33131;
    public static final int ORANGE = 0xFFFF9600, YELLOW = 0xFFFFC800, BLUE = 0xFF1CB0F6, BLUE_D = 0xFF1899D6, PURPLE = 0xFFCE82FF, PINK = 0xFFFF86D0;
    public static final int[] UNIT_COLORS = {0xFF58CC02, 0xFF1CB0F6, 0xFFCE82FF, 0xFFFF9600, 0xFFFF4B4B, 0xFF00CD9C, 0xFFFFC800, 0xFFFF86D0};

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable outlined(int fill, int stroke, int strokePx, float radiusPx) {
        GradientDrawable g = rounded(fill, radiusPx);
        g.setStroke(strokePx, stroke);
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    /** Duolingo tarzı "3B" buton: alt kenarı koyu. */
    public static Button button(Context c, String label, int color, int shadow) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        int r = dp(c, 14);
        GradientDrawable top = rounded(color, r);
        GradientDrawable bottom = rounded(shadow, r);
        android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bottom, top});
        ld.setLayerInset(1, 0, 0, 0, dp(c, 4));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), ld, null));
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 16));
        b.setStateListAnimator(null);
        return b;
    }

    public static Button primary(Context c, String label) { return button(c, label, GREEN, GREEN_D); }
    public static Button secondary(Context c, String label) { return button(c, label, SURFACE2, 0xFF15161F); }

    public static Button option(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setGravity(Gravity.CENTER);
        b.setBackground(outlined(SURFACE, LINE, dp(c, 2), dp(c, 14)));
        b.setPadding(dp(c, 12), dp(c, 14), dp(c, 12), dp(c, 14));
        b.setStateListAnimator(null);
        return b;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(rounded(SURFACE, dp(c, 18)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lpm(Context c, int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, top); p.bottomMargin = dp(c, bottom);
        return p;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static int darker(int color, float f) {
        int a = Color.alpha(color), r = (int) (Color.red(color) * f), g = (int) (Color.green(color) * f), b = (int) (Color.blue(color) * f);
        return Color.argb(a, r, g, b);
    }
}
