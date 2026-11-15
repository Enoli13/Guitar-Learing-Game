package com.enoli.perde;

import java.util.ArrayList;
import java.util.List;

/** Tek bir alıştırma örneği (veri). Ekranı ExerciseView çizer. */
public class Ex {
    public enum Type { INFO, CHOICE, FRET_TAP, PLAY_NOTES, PLAY_SEQ, PLAY_CHORD, CHANGES, RHYTHM, IMPROV, TUNE, BEND, CALIBRATE }

    public Type type;
    public String title = "";
    public String prompt = "";
    public String body = "";       // INFO metni / açıklama
    public String explain = "";    // yanlışta gösterilen açıklama
    public String key;             // SRS kartı anahtarı (spec satırı)

    // CHOICE
    public List<String> options = new ArrayList<>();
    public int correct;

    // Görseller
    public List<int[]> fretDots = new ArrayList<>();   // {tel, perde}
    public int fretFrom = 0, fretTo = 12;
    public int[] staffMidis;                            // yazılı perde
    public String chordShape;                          // akor diyagramı
    public String rhythmText;                          // ritim kalıbı
    public float[] audio;                              // sentezlenecek notalar
    public boolean audioHarmonic;                      // birlikte mi çalınsın
    public List<String> audioChords;                   // akor dizisi dinletme

    // FRET_TAP
    public int tapString;   // 0 = herhangi tel
    public int tapPc = -1;

    // PLAY_NOTES (bekleme modu) / PLAY_SEQ (zamanlı)
    public List<Note> notes = new ArrayList<>();
    public boolean showTab = true;
    public boolean strictOctave = false;
    public float bpm = 60;
    public int beatsPerBar = 4;
    public boolean hideTarget; // kulaktan çalma: hedef gösterilmez

    // PLAY_CHORD / CHANGES
    public String chord;
    public List<String> chords = new ArrayList<>();
    public int beatsPerChord = 4;
    public int bars = 2;

    // RHYTHM: sekizlik/onaltılık ızgarada vuruşlar
    public int grid = 8;          // ölçü başına adım
    public boolean[] hits;       // tüm ölçüler için
    public String strumChord;

    // IMPROV
    public int[] allowedPcs;
    public int beats = 16;
    public String scaleLabel;

    // BEND
    public int bendString, bendFret;
    public float bendTarget;
    public int bendSemis;

    public static class Note {
        public int string, fret, midi;  // string 1..6, fret -1 = bilinmiyor
        public float beat, dur;         // vuruş cinsinden
        public boolean rest;
        public String label;            // ör. "h" hammer, "p" pull, "b" bend
        public int[] alts;              // akor sütunu: kabul edilen diğer perdeler
        public Note(int s, int f, float beat, float dur) {
            string = s; fret = f; this.beat = beat; this.dur = dur;
            midi = s > 0 && f >= 0 ? Theory.OPEN[s] + f : -1;
        }
        public static Note ofMidi(int midi, float beat, float dur) {
            Note n = new Note(0, -1, beat, dur); n.midi = midi; return n;
        }
    }
}
