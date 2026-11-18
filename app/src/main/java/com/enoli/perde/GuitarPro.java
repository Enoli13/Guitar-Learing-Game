package com.enoli.perde;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Guitar Pro 3 / 4 / 5 (.gp3 .gp4 .gp5) okuyucu. Sadece çalmak için gereken kısımlar
 * (ölçüler, ritim, tel/perde, bağ, bend, hammer/pull/slide) tutulur, geri kalanı atlanır.
 */
public final class GuitarPro {
    private final byte[] b;
    private int p;
    private int major, minor;
    private static final Charset LATIN = Charset.forName("ISO-8859-1");

    private GuitarPro(byte[] data) { b = data; }

    public static boolean looks(byte[] d) {
        if (d.length < 31) return false;
        String v = new String(d, 1, Math.min(d[0] & 0xFF, 30), LATIN);
        return v.contains("GUITAR PRO") || v.startsWith("CLIPBOARD GP");
    }

    public static SongData parse(byte[] data) throws Exception {
        return new GuitarPro(data).song();
    }

    // ---- ilkel okuyucular (little endian)
    private int u8() { if (p >= b.length) throw new IllegalStateException("Dosya beklenenden kısa"); return b[p++] & 0xFF; }
    private int i8() { return (byte) u8(); }
    private boolean bool() { return u8() != 0; }
    private int i16() { int v = u8() | (u8() << 8); return (short) v; }
    private int i32() { return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24); }
    private void skip(int n) { p += n; if (p > b.length) throw new IllegalStateException("Dosya beklenenden kısa"); }
    private String byteSizeString(int count) {
        int size = u8();
        int len = Math.max(0, Math.min(size, count));
        String s = new String(b, p, Math.min(len, b.length - p), LATIN);
        skip(count);
        return s;
    }
    private String intByteSizeString() { int n = i32(); int size = u8(); String s = new String(b, p, Math.max(0, Math.min(size, Math.min(n - 1, b.length - p))), LATIN); skip(Math.max(0, n - 1)); return s; }
    private String intSizeString() { int n = i32(); String s = new String(b, p, Math.max(0, Math.min(n, b.length - p)), LATIN); skip(Math.max(0, n)); return s; }

    public static boolean DEBUG;
    private void dbg(String w) { if (DEBUG) System.out.println(w + " -> " + p); }
    private boolean v5() { return major >= 5; }
    private boolean v4() { return major >= 4; }
    private boolean v500() { return major == 5 && minor == 0; }

    private SongData song() {
        String ver = byteSizeString(30);
        boolean clip = ver.startsWith("CLIPBOARD");
        if (ver.contains("v3")) { major = 3; minor = 0; }
        else if (ver.contains("v4") || ver.contains("L4") || ver.contains("4.0")) { major = 4; minor = 0; }
        else if (ver.contains("v5.10") || ver.contains("v5.1")) { major = 5; minor = 1; }
        else if (ver.contains("5.0")) { major = 5; minor = 0; }
        else throw new IllegalArgumentException("Desteklenmeyen Guitar Pro sürümü: " + ver + " (GP3/4/5 desteklenir; GP6/7/8 için dosyayı GP5 ya da MusicXML olarak dışa aktar)");
        SongData s = new SongData();
        s.format = "Guitar Pro " + major;
        if (clip && major >= 4) { skip(16); if (v5()) skip(12); }
        // bilgi
        s.title = intByteSizeString();
        intByteSizeString(); // alt başlık
        s.artist = intByteSizeString();
        intByteSizeString(); // albüm
        intByteSizeString(); // söz
        if (v5()) intByteSizeString(); // müzik
        intByteSizeString(); intByteSizeString(); intByteSizeString(); // telif, tab, talimat
        int notes = i32();
        for (int i = 0; i < notes; i++) intByteSizeString();
        if (!v5()) bool(); // triplet feel
        dbg("info");
        if (v4()) { i32(); for (int i = 0; i < 5; i++) { i32(); intSizeString(); } } // şarkı sözleri
        dbg("lyrics");
        if (v5()) {
            if (!v500()) { i32(); i32(); skip(11); } // RSE master
            // sayfa düzeni
            skip(4 * 2 + 4 * 4 + 4 + 2);
            for (int i = 0; i < 10; i++) intByteSizeString();
            intByteSizeString(); // tempo adı
        }
        dbg("pagesetup+tempoName");
        s.tempo = i32();
        if (v5()) { if (!v500()) bool(); i8(); i32(); }
        else { i32(); if (v4()) i8(); }
        skip(64 * 12); // MIDI kanalları
        dbg("channels");
        if (v5()) { skip(19 * 2); i32(); } // yönler + master reverb
        dbg("directions");
        int measureCount = i32(), trackCount = i32();
        if (measureCount < 0 || measureCount > 5000 || trackCount < 1 || trackCount > 64) throw new IllegalStateException("Bozuk dosya");
        // ölçü başlıkları
        int num = 4, den = 4;
        float start = 0;
        for (int m = 0; m < measureCount; m++) {
            if (v5() && m > 0) skip(1);
            int f = u8();
            if ((f & 1) != 0) num = i8();
            if ((f & 2) != 0) den = i8();
            if ((f & 8) != 0) i8();
            if (v5()) {
                if ((f & 32) != 0) { intByteSizeString(); skip(4); }
                if ((f & 64) != 0) { i8(); i8(); }
                if ((f & 16) != 0) u8();
                if ((f & 3) != 0) skip(4);
                if ((f & 16) == 0) skip(1);
                u8(); // triplet feel
            } else {
                if ((f & 16) != 0) u8();
                if ((f & 32) != 0) { intByteSizeString(); skip(4); }
                if ((f & 64) != 0) { i8(); i8(); }
            }
            float len = num * 4f / Math.max(1, den);
            s.mStart.add(start); s.mLen.add(len);
            start += len;
        }
        dbg("headers");
        // parçalar
        for (int t = 0; t < trackCount; t++) {
            SongData.Track tr = new SongData.Track();
            if (v5() && (t == 0 || v500())) skip(1);
            int f = u8();
            tr.drums = (f & 1) != 0;
            tr.name = byteSizeString(40);
            int sc = i32();
            tr.strings = sc;
            int[] tun = new int[8];
            for (int i = 0; i < 7; i++) tun[i] = i32();
            tr.tuning = new int[7];
            for (int i = 1; i <= 6; i++) tr.tuning[i] = i <= sc ? tun[i - 1] : Theory.OPEN[i];
            i32(); // port
            int ch = i32(); i32();
            if (ch == 10) tr.drums = true;
            i32(); // perde sayısı
            int capo = i32();
            skip(4); // renk
            if (capo > 0 && capo < 12) for (int i = 1; i <= 6; i++) tr.tuning[i] += capo;
            if (v5()) {
                i16(); u8(); u8(); u8();
                i32(); i32(); i32(); skip(12);
                i32(); i32(); i32();
                if (v500()) { i16(); skip(1); } else i32();
                if (!v500()) { skip(4); intByteSizeString(); intByteSizeString(); }
            }
            s.tracks.add(tr);
        }
        if (v5()) skip(v500() ? 2 : 1);
        dbg("tracks");
        // ölçüler
        for (int m = 0; m < measureCount; m++) {
            for (int t = 0; t < trackCount; t++) {
                SongData.Track tr = s.tracks.get(t);
                int voices = v5() ? 2 : 1;
                for (int v = 0; v < voices; v++) {
                    float pos = s.mStart.get(m);
                    int beats = i32();
                    if (beats < 0 || beats > 512) throw new IllegalStateException("Bozuk ölçü (" + (m + 1) + ")");
                    for (int k = 0; k < beats; k++) pos += beat(tr, pos, v == 0);
                }
                if (v5() && p < b.length) u8(); // satır sonu (son ölçüde olmayabilir)
            }
        }
        return s;
    }

    /** Bir vuruşu okur; ilerleme süresini (dörtlük cinsinden) döner. */
    private float beat(SongData.Track tr, float pos, boolean keep) {
        int f = u8();
        int status = 1;
        if ((f & 64) != 0) status = u8();
        int dv = i8();
        float dur = 4f / (1 << Math.max(0, Math.min(8, dv + 2)));
        if ((f & 1) != 0) dur *= 1.5f;
        if ((f & 32) != 0) {
            int tup = i32();
            int times = tup == 3 ? 2 : tup == 5 || tup == 6 || tup == 7 ? 4 : tup >= 9 && tup <= 13 ? 8 : tup;
            if (tup > 0) dur = dur * times / tup;
        }
        if ((f & 2) != 0) chord(tr.strings);
        if ((f & 4) != 0) intByteSizeString();
        if ((f & 8) != 0) beatEffects();
        if ((f & 16) != 0) mixTable();
        SongData.Ev ev = new SongData.Ev();
        ev.beat = pos; ev.dur = dur;
        int sf = u8();
        for (int sn = 1; sn <= 7; sn++) {
            if ((sf & (1 << (7 - sn))) == 0) continue;
            int[] n = note(tr, sn, ev);
            if (n != null && sn <= 6) ev.notes.add(n);
        }
        if (v5()) {
            int f2 = i16();
            if ((f2 & 2048) != 0) u8();
        }
        if (status == 0) return 0;
        if (keep && status == 1 && !ev.notes.isEmpty()) tr.evs.add(ev);
        return dur;
    }

    private void chord(int strings) {
        boolean newFormat = bool();
        if (!newFormat) {
            intByteSizeString();
            int first = i32();
            if (first != 0) skip(6 * 4);
            return;
        }
        if (!v4()) {
            skip(1 + 3 + 4 * 5 + 1);
            byteSizeString(22);
            skip(4 * 3 + 4 + 6 * 4 + 4 + 2 * 4 * 3 + 7 + 1);
        } else {
            skip(1 + 3 + 3 + 4 + 4 + 1);
            byteSizeString(22);
            skip(3 + 4 + 7 * 4 + 1 + 5 * 3 + 7 + 1 + 7 + 1);
        }
    }

    private void beatEffects() {
        if (!v4()) {
            int f1 = u8();
            if ((f1 & 32) != 0) { int slap = u8(); i32(); /* tremolo bar ya da slap */ if (slap < 0) i32(); }
            if ((f1 & 64) != 0) { i8(); i8(); }
            return;
        }
        int f1 = i8(), f2 = i8();
        if ((f1 & 32) != 0) i8();
        if ((f2 & 4) != 0) bend();
        if ((f1 & 64) != 0) { i8(); i8(); }
        if ((f2 & 2) != 0) i8();
    }

    /** Bend'in en yüksek değerini yarım ses cinsinden döner. */
    private int bend() {
        i8(); i32();
        int n = i32();
        int max = 0;
        for (int i = 0; i < n; i++) { i32(); max = Math.max(max, i32()); bool(); }
        return Math.round(max / 50f);
    }

    private void mixTable() {
        i8(); // enstrüman
        if (v5()) { i32(); i32(); i32(); if (v500()) { i16(); skip(1); } else i32(); if (v500()) skip(1); }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) v[i] = i8();
        if (v5()) intByteSizeString();
        int tempo = i32();
        for (int i = 0; i < 6; i++) if (v[i] >= 0) i8();
        if (tempo >= 0) { i8(); if (v5() && !v500()) bool(); }
        if (v4()) i8(); // bayraklar
        if (v5()) { i8(); if (!v500()) { intByteSizeString(); intByteSizeString(); } }
    }

    private int[] note(SongData.Track tr, int string, SongData.Ev ev) {
        int f = u8();
        int type = 1;
        if ((f & 32) != 0) type = u8();
        if (!v5() && (f & 1) != 0) { i8(); i8(); }
        if ((f & 16) != 0) i8();
        int fret = 0;
        if ((f & 32) != 0) fret = i8();
        if ((f & 128) != 0) { i8(); i8(); }
        if (v5()) {
            if ((f & 1) != 0) skip(8);
            u8();
        }
        if ((f & 8) != 0) noteEffects(ev);
        if (type == 3) return null; // ölü (susturulmuş) nota: perdesiz vuruş
        if (fret < 0 || fret > 30) fret = 0;
        boolean tie = type == 2;
        if (string > 6) return null;
        int midi = tr.tuning[string] + fret;
        if (tie) {
            // bağlı notanın perdesi önceki aynı tel notasından gelir
            for (int i = tr.evs.size() - 1; i >= 0 && i >= tr.evs.size() - 64; i--) {
                boolean found = false;
                for (int[] q : tr.evs.get(i).notes) if (q[0] == string) { fret = q[1]; midi = q[3]; found = true; break; }
                if (found) break;
            }
        }
        return new int[]{string, fret, tie ? 1 : 0, midi};
    }

    private void noteEffects(SongData.Ev ev) {
        if (!v4()) {
            int f = u8();
            if ((f & 2) != 0) ev.legato = "h";
            if ((f & 1) != 0) { int s = bend(); if (s > 0) ev.label = "b" + s; }
            if ((f & 16) != 0) { i8(); u8(); u8(); i8(); }
            if ((f & 4) != 0) ev.legato = "s";
            return;
        }
        int f1 = i8(), f2 = i8();
        if ((f1 & 2) != 0) ev.legato = "h";
        if ((f1 & 1) != 0) { int s = bend(); if (s > 0) ev.label = "b" + s; }
        if ((f1 & 16) != 0) { if (v5()) { u8(); u8(); u8(); u8(); u8(); } else { i8(); u8(); u8(); i8(); } }
        if ((f2 & 4) != 0) i8();
        if ((f2 & 8) != 0) { int st = v5() ? u8() : i8(); if (v5() ? (st & 3) != 0 : st == 1 || st == 2) ev.legato = "s"; }
        if ((f2 & 16) != 0) {
            int ht = i8();
            if (v5()) { if (ht == 2) { u8(); i8(); u8(); } else if (ht == 3) u8(); }
        }
        if ((f2 & 32) != 0) { i8(); i8(); }
    }
}
