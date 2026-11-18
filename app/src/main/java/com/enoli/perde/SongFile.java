package com.enoli.perde;

import java.nio.charset.StandardCharsets;

/** Dosya türünü tanıyıp doğru okuyucuya yollar. */
public final class SongFile {
    private SongFile() {}

    /** Metin (ASCII tab) ise null döner; desteklenmeyen biçimde açıklayıcı hata fırlatır. */
    public static SongData load(byte[] d) throws Exception {
        if (d.length < 4) throw new IllegalArgumentException("Dosya boş");
        if (GuitarPro.looks(d)) return GuitarPro.parse(d);
        if (d[0] == 'P' && d[1] == 'K') {
            byte[] g = Gpif.extract(d);
            if (g != null) return Gpif.parse(g);
            return MusicXml.parse(MusicXml.unzipMxl(d));
        }
        String head = new String(d, 0, Math.min(4, d.length), StandardCharsets.ISO_8859_1);
        if (head.startsWith("BCF")) throw new IllegalArgumentException("Guitar Pro 6 (.gpx) dosyası okunamıyor. Guitar Pro / TuxGuitar / MuseScore ile .gp5 ya da MusicXML olarak dışa aktar.");
        if (MusicXml.looksXml(d)) return MusicXml.parse(d);
        if (head.startsWith("MThd")) throw new IllegalArgumentException("MIDI dosyasında tel/perde bilgisi yok. Guitar Pro (.gp/.gp5) ya da MusicXML dosyası kullan.");
        return null;
    }
}
