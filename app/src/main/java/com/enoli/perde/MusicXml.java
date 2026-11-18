package com.enoli.perde;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** MusicXML (.musicxml, .xml) ve sıkıştırılmış MusicXML (.mxl) okuyucu. */
public final class MusicXml {
    private MusicXml() {}

    public static boolean looksXml(byte[] d) {
        String head = new String(d, 0, Math.min(d.length, 2000), StandardCharsets.UTF_8);
        return head.contains("<score-partwise") || head.contains("<score-timewise") || (head.startsWith("<?xml") && head.contains("score"));
    }

    /** .mxl: zip içinden ana MusicXML dosyasını çıkarır. */
    public static byte[] unzipMxl(byte[] zip) throws Exception {
        Map<String, byte[]> files = Zip.read(zip);
        byte[] cont = files.get("META-INF/container.xml");
        if (cont != null) {
            String c = new String(cont, StandardCharsets.UTF_8);
            int i = c.indexOf("full-path=\"");
            if (i >= 0) {
                int j = c.indexOf('"', i + 11);
                byte[] f = files.get(c.substring(i + 11, j));
                if (f != null) return f;
            }
        }
        for (Map.Entry<String, byte[]> en : files.entrySet())
            if (!en.getKey().startsWith("META-INF") && (en.getKey().endsWith(".xml") || en.getKey().endsWith(".musicxml"))) return en.getValue();
        throw new IllegalArgumentException("Zip içinde MusicXML yok");
    }

    static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    static Element child(Element e, String name) {
        if (e == null) return null;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && ((Element) n).getTagName().equals(name)) return (Element) n;
        return null;
    }

    static String text(Element e, String name) {
        Element c = child(e, name);
        return c == null ? null : c.getTextContent().trim();
    }

    static int num(Element e, String name, int def) {
        String t = text(e, name);
        if (t == null) return def;
        try { return Math.round(Float.parseFloat(t)); } catch (Exception x) { return def; }
    }

    static Element find(Element e, String path) {
        for (String p : path.split("/")) { e = child(e, p); if (e == null) return null; }
        return e;
    }

    public static SongData parse(byte[] data) throws Exception {
        String s = new String(data, StandardCharsets.UTF_8);
        if (s.length() > 0 && s.charAt(0) == '﻿') s = s.substring(1);
        s = s.replaceAll("(?s)<!DOCTYPE[^>]*>", "");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception ignored) {}
        f.setNamespaceAware(false);
        f.setValidating(false);
        DocumentBuilder db = f.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();
        if (root.getTagName().equals("score-timewise")) throw new IllegalArgumentException("score-timewise desteklenmiyor; partwise olarak dışa aktar");
        SongData sd = new SongData();
        sd.format = "MusicXML";
        String title = text(find(root, "work"), "work-title");
        if (title == null) title = text(root, "movement-title");
        sd.title = title == null ? "" : title;
        NodeList creators = root.getElementsByTagName("creator");
        for (int i = 0; i < creators.getLength(); i++) {
            Element c = (Element) creators.item(i);
            if ("composer".equals(c.getAttribute("type")) || sd.artist.isEmpty()) sd.artist = c.getTextContent().trim();
        }
        Map<String, String> names = new HashMap<>();
        NodeList sps = root.getElementsByTagName("score-part");
        for (int i = 0; i < sps.getLength(); i++) {
            Element sp = (Element) sps.item(i);
            String nm = text(sp, "part-name");
            names.put(sp.getAttribute("id"), nm == null ? "" : nm);
        }
        boolean first = true;
        for (Node pn = root.getFirstChild(); pn != null; pn = pn.getNextSibling()) {
            if (!(pn instanceof Element) || !((Element) pn).getTagName().equals("part")) continue;
            Element part = (Element) pn;
            SongData.Track tr = new SongData.Track();
            String nm = names.get(part.getAttribute("id"));
            tr.name = nm == null ? part.getAttribute("id") : nm;
            readPart(part, tr, sd, first);
            first = false;
            String low = tr.name.toLowerCase();
            if (low.contains("drum") || low.contains("davul") || low.contains("percuss")) tr.drums = true;
            sd.tracks.add(tr);
        }
        if (sd.tracks.isEmpty()) throw new IllegalArgumentException("Parça (part) bulunamadı");
        return sd;
    }

    private static final int[] STEP = {9, 11, 0, 2, 4, 5, 7}; // A B C D E F G

    private static void readPart(Element part, SongData.Track tr, SongData sd, boolean fillMeasures) {
        int divisions = 1, beats = 4, beatType = 4, transpose = 0;
        float mStart = 0;
        boolean hasTab = false;
        String voice1 = null;
        int mIndex = 0;
        for (Node mn = part.getFirstChild(); mn != null; mn = mn.getNextSibling()) {
            if (!(mn instanceof Element) || !((Element) mn).getTagName().equals("measure")) continue;
            Element m = (Element) mn;
            float cursor = 0, maxCursor = 0, prevStart = 0;
            SongData.Ev prev = null;
            for (Node cn = m.getFirstChild(); cn != null; cn = cn.getNextSibling()) {
                if (!(cn instanceof Element)) continue;
                Element c = (Element) cn;
                switch (c.getTagName()) {
                    case "attributes": {
                        divisions = num(c, "divisions", divisions);
                        Element time = child(c, "time");
                        if (time != null) { beats = num(time, "beats", beats); beatType = num(time, "beat-type", beatType); }
                        Element tp = child(c, "transpose");
                        if (tp != null) transpose = num(tp, "chromatic", 0) + 12 * num(tp, "octave-change", 0);
                        Element sdt = child(c, "staff-details");
                        if (sdt != null) {
                            int lines = num(sdt, "staff-lines", 6);
                            for (Node tn = sdt.getFirstChild(); tn != null; tn = tn.getNextSibling()) {
                                if (!(tn instanceof Element) || !((Element) tn).getTagName().equals("staff-tuning")) continue;
                                Element st = (Element) tn;
                                int line = Integer.parseInt(st.getAttribute("line").trim());
                                String step = text(st, "tuning-step");
                                int alt = num(st, "tuning-alter", 0), oct = num(st, "tuning-octave", 2);
                                int string = lines + 1 - line; // MusicXML: 1. çizgi en kalın tel
                                if (step != null && string >= 1 && string <= 6) tr.tuning[string] = (oct + 1) * 12 + STEP[step.charAt(0) - 'A'] + alt;
                            }
                            tr.strings = lines;
                        }
                        break;
                    }
                    case "backup": cursor -= num(c, "duration", 0) / (float) divisions; break;
                    case "forward": cursor += num(c, "duration", 0) / (float) divisions; break;
                    case "note": {
                        if (child(c, "grace") != null || child(c, "cue") != null) break;
                        String v = text(c, "voice");
                        if (v == null) v = "1";
                        String staff = text(c, "staff");
                        boolean isChord = child(c, "chord") != null;
                        float dur = num(c, "duration", 0) / (float) divisions;
                        float at = isChord ? prevStart : cursor;
                        if (!isChord) { prevStart = cursor; cursor += dur; maxCursor = Math.max(maxCursor, cursor); }
                        if (voice1 == null && child(c, "rest") == null) voice1 = v;
                        if (staff != null && !staff.equals("1")) break; // tab çizgisi ikinci porte: aynı notalar
                        if (voice1 != null && !v.equals(voice1)) break;
                        if (child(c, "rest") != null) { prev = null; break; }
                        Element pitch = child(c, "pitch");
                        if (pitch == null) break;
                        String step = text(pitch, "step");
                        int midi = (num(pitch, "octave", 4) + 1) * 12 + STEP[step.charAt(0) - 'A'] + num(pitch, "alter", 0) + transpose;
                        boolean tieStop = false;
                        for (Node tn = c.getFirstChild(); tn != null; tn = tn.getNextSibling())
                            if (tn instanceof Element && ((Element) tn).getTagName().equals("tie") && "stop".equals(((Element) tn).getAttribute("type"))) tieStop = true;
                        Element tech = find(c, "notations/technical");
                        int string = -1, fret = -1;
                        String legato = null, bend = null;
                        if (tech != null) {
                            string = num(tech, "string", -1); fret = num(tech, "fret", -1);
                            Element h = child(tech, "hammer-on"), p = child(tech, "pull-off");
                            if ((h != null && "start".equals(h.getAttribute("type"))) || (p != null && "start".equals(p.getAttribute("type")))) legato = "h";
                            Element b = child(tech, "bend");
                            if (b != null) { int al = num(b, "bend-alter", 2); if (al > 0) bend = "b" + al; }
                        }
                        Element sl = find(c, "notations/slide");
                        if (sl == null) sl = find(c, "notations/glissando");
                        if (sl != null && "start".equals(sl.getAttribute("type"))) legato = "s";
                        if (string >= 1 && string <= 6 && fret >= 0) { hasTab = true; midi = tr.tuning[string] + fret; }
                        SongData.Ev ev;
                        if (isChord && prev != null) ev = prev;
                        else {
                            ev = new SongData.Ev();
                            ev.beat = mStart + at; ev.dur = dur;
                            tr.evs.add(ev);
                            prev = ev;
                        }
                        ev.notes.add(new int[]{string, fret, tieStop ? 1 : 0, midi});
                        if (legato != null) ev.legato = legato;
                        if (bend != null) ev.label = bend;
                        break;
                    }
                }
            }
            float len = beats * 4f / beatType;
            if (maxCursor > len + 0.01f && child(m, "attributes") == null) len = maxCursor;
            if (fillMeasures) { sd.mStart.add(mStart); sd.mLen.add(len); }
            mStart += len;
            mIndex++;
        }
        // tel/perde bilgisi olmayan notalara parmak pozisyonu ata
        assignFrets(tr);
        // boş ölçüleri at
        java.util.Iterator<SongData.Ev> it = tr.evs.iterator();
        while (it.hasNext()) if (it.next().notes.isEmpty()) it.remove();
    }

    /** Perdesi bilinmeyen notalar için el pozisyonuna en yakın tel/perde seçer. */
    static void assignFrets(SongData.Track tr) {
        float pos = 3;
        for (SongData.Ev e : tr.evs) {
            boolean[] used = new boolean[7];
            for (int[] n : e.notes) if (n[0] >= 1 && n[0] <= 6 && n[1] >= 0) used[n[0]] = true;
            // tiz sesten başla
            e.notes.sort((a, b) -> b[3] - a[3]);
            java.util.Iterator<int[]> it = e.notes.iterator();
            while (it.hasNext()) {
                int[] n = it.next();
                if (n[0] >= 1 && n[0] <= 6 && n[1] >= 0) continue;
                int best = -1; float bc = 1e9f;
                for (int s = 1; s <= 6; s++) {
                    if (used[s]) continue;
                    int f = n[3] - tr.tuning[s];
                    if (f < 0 || f > 19) continue;
                    float cost = Math.abs(f - pos) + (f == 0 ? -0.5f : 0) + (f > 12 ? 2 : 0);
                    if (cost < bc) { bc = cost; best = s; }
                }
                if (best < 0) { it.remove(); continue; }
                n[0] = best; n[1] = n[3] - tr.tuning[best];
                used[best] = true;
            }
            float sum = 0; int cnt = 0;
            for (int[] n : e.notes) if (n[1] > 0) { sum += n[1]; cnt++; }
            if (cnt > 0) pos = pos * 0.5f + sum / cnt * 0.5f;
        }
        // bağ: perde bilgisini önceki aynı sesten al
        for (int i = 0; i < tr.evs.size(); i++) {
            for (int[] n : tr.evs.get(i).notes) if (n[2] == 1) {
                boolean found = false;
                for (int k = i - 1; k >= 0 && !found; k--) for (int[] q : tr.evs.get(k).notes) if (q[3] == n[3]) found = true;
                if (!found) n[2] = 0;
            }
        }
    }
}
