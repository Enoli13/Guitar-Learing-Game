package com.enoli.perde;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

/** Guitar Pro 7 / 8 (.gp) okuyucu: zip içindeki Content/score.gpif dosyası. */
public final class Gpif {
    private Gpif() {}

    public static byte[] extract(byte[] zip) throws Exception {
        for (java.util.Map.Entry<String, byte[]> e : Zip.read(zip).entrySet()) if (e.getKey().endsWith("score.gpif")) return e.getValue();
        return null;
    }

    static Element child(Element e, String n) { return MusicXml.child(e, n); }
    static String text(Element e, String n) { return MusicXml.text(e, n); }

    static Map<String, Element> byId(Element root, String group, String item) {
        Map<String, Element> m = new HashMap<>();
        Element g = child(root, group);
        if (g == null) return m;
        for (Node n = g.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && ((Element) n).getTagName().equals(item)) m.put(((Element) n).getAttribute("id"), (Element) n);
        return m;
    }

    static Element prop(Element e, String name) {
        Element ps = child(e, "Properties");
        if (ps == null) return null;
        for (Node n = ps.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element && name.equals(((Element) n).getAttribute("name"))) return (Element) n;
        return null;
    }

    static String[] ids(String s) { return s == null || s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+"); }

    public static SongData parse(byte[] gpif) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(gpif));
        Element root = doc.getDocumentElement();
        SongData sd = new SongData();
        sd.format = "Guitar Pro 7/8";
        Element score = child(root, "Score");
        if (score != null) {
            String t = text(score, "Title"), a = text(score, "Artist");
            sd.title = t == null ? "" : t; sd.artist = a == null ? "" : a;
        }
        // tempo
        Element mt = child(root, "MasterTrack");
        NodeList autos = mt == null ? null : mt.getElementsByTagName("Automation");
        if (autos != null) for (int i = 0; i < autos.getLength(); i++) {
            Element au = (Element) autos.item(i);
            if ("Tempo".equals(text(au, "Type"))) {
                try { sd.tempo = Float.parseFloat(text(au, "Value").split("\\s+")[0]); } catch (Exception ignored) {}
                break;
            }
        }
        Map<String, Element> bars = byId(root, "Bars", "Bar"), voices = byId(root, "Voices", "Voice"),
                beats = byId(root, "Beats", "Beat"), notes = byId(root, "Notes", "Note"), rhythms = byId(root, "Rhythms", "Rhythm");
        // parçalar
        Element tracksEl = child(root, "Tracks");
        if (tracksEl == null) throw new IllegalArgumentException("Parça yok");
        for (Node n = tracksEl.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element) || !((Element) n).getTagName().equals("Track")) continue;
            Element te = (Element) n;
            SongData.Track tr = new SongData.Track();
            String nm = text(te, "Name");
            tr.name = nm == null ? "" : nm;
            Element is = child(te, "InstrumentSet");
            String type = is == null ? null : text(is, "Type");
            if (type != null && type.toLowerCase().contains("drum")) tr.drums = true;
            int[] pitches = null; int capo = 0;
            NodeList staffs = te.getElementsByTagName("Staff");
            if (staffs.getLength() > 0) {
                Element st = (Element) staffs.item(0);
                Element tp = prop(st, "Tuning");
                if (tp != null && text(tp, "Pitches") != null) {
                    String[] a = ids(text(tp, "Pitches"));
                    pitches = new int[a.length];
                    for (int i = 0; i < a.length; i++) pitches[i] = Integer.parseInt(a[i]);
                }
                Element cp = prop(st, "CapoFret");
                if (cp != null && text(cp, "Fret") != null) capo = Integer.parseInt(text(cp, "Fret"));
            }
            if (pitches == null) {
                // eski GP7: Track/Properties
                Element tp = prop(te, "Tuning");
                if (tp != null && text(tp, "Pitches") != null) { String[] a = ids(text(tp, "Pitches")); pitches = new int[a.length]; for (int i = 0; i < a.length; i++) pitches[i] = Integer.parseInt(a[i]); }
            }
            if (pitches != null) {
                tr.strings = pitches.length;
                for (int s = 1; s <= 6; s++) tr.tuning[s] = s <= pitches.length ? pitches[pitches.length - s] + capo : Theory.OPEN[s];
            } else if (!tr.drums) tr.strings = 0;
            sd.tracks.add(tr);
        }
        // ölçüler
        Element mbs = child(root, "MasterBars");
        float start = 0;
        int mi = 0;
        for (Node n = mbs.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element) || !((Element) n).getTagName().equals("MasterBar")) continue;
            Element mb = (Element) n;
            String time = text(mb, "Time");
            float len = 4;
            if (time != null && time.contains("/")) { String[] a = time.split("/"); len = Integer.parseInt(a[0].trim()) * 4f / Integer.parseInt(a[1].trim()); }
            sd.mStart.add(start); sd.mLen.add(len);
            String[] barIds = ids(text(mb, "Bars"));
            for (int t = 0; t < sd.tracks.size() && t < barIds.length; t++) {
                SongData.Track tr = sd.tracks.get(t);
                Element bar = bars.get(barIds[t]);
                if (bar == null) continue;
                String[] vids = ids(text(bar, "Voices"));
                if (vids.length == 0 || vids[0].equals("-1")) continue;
                Element voice = voices.get(vids[0]);
                if (voice == null) continue;
                float pos = start;
                for (String bid : ids(text(voice, "Beats"))) {
                    Element be = beats.get(bid);
                    if (be == null) continue;
                    Element rr = child(be, "Rhythm");
                    float dur = rhythm(rr == null ? null : rhythms.get(rr.getAttribute("ref")));
                    if (child(be, "GraceNotes") != null) continue; // süs notası: süre almaz
                    String ns = text(be, "Notes");
                    if (ns != null && !tr.drums && tr.strings > 0) {
                        SongData.Ev ev = new SongData.Ev();
                        ev.beat = pos; ev.dur = dur;
                        for (String nid : ids(ns)) {
                            Element ne = notes.get(nid);
                            if (ne == null) continue;
                            int[] q = note(ne, tr, ev);
                            if (q != null) ev.notes.add(q);
                        }
                        if (!ev.notes.isEmpty()) {
                            resolveTies(tr, ev);
                            tr.evs.add(ev);
                        }
                    }
                    pos += dur;
                }
            }
            start += len;
            mi++;
        }
        return sd;
    }

    static float rhythm(Element r) {
        if (r == null) return 1;
        String v = text(r, "NoteValue");
        float d;
        switch (v == null ? "" : v) {
            case "Whole": d = 4; break;
            case "Half": d = 2; break;
            case "Eighth": d = 0.5f; break;
            case "16th": d = 0.25f; break;
            case "32nd": d = 0.125f; break;
            case "64th": d = 0.0625f; break;
            case "128th": d = 0.03125f; break;
            case "DoubleWhole": d = 8; break;
            default: d = 1;
        }
        Element dot = child(r, "AugmentationDot");
        if (dot != null) {
            int c = 1;
            try { c = Integer.parseInt(dot.getAttribute("count")); } catch (Exception ignored) {}
            d *= c >= 2 ? 1.75f : 1.5f;
        }
        Element tup = child(r, "PrimaryTuplet");
        if (tup != null) {
            try { d = d * Integer.parseInt(tup.getAttribute("den")) / Integer.parseInt(tup.getAttribute("num")); } catch (Exception ignored) {}
        }
        return d;
    }

    private static int[] note(Element ne, SongData.Track tr, SongData.Ev ev) {
        Element fp = prop(ne, "Fret"), sp = prop(ne, "String");
        if (fp == null || sp == null) return null;
        Element mute = prop(ne, "Muted");
        if (mute != null && child(mute, "Enable") != null) return null;
        int fret = Integer.parseInt(text(fp, "Fret"));
        int sIdx = Integer.parseInt(text(sp, "String"));
        int string = tr.strings - sIdx;
        if (string < 1 || string > 6) return null;
        boolean tie = false;
        Element tie1 = child(ne, "Tie");
        if (tie1 != null && "true".equals(tie1.getAttribute("destination"))) tie = true;
        Element hopo = prop(ne, "HopoOrigin");
        if (hopo != null) ev.legato = "h";
        Element sl = prop(ne, "Slide");
        if (sl != null) {
            try { int fl = Integer.parseInt(text(sl, "Flags")); if ((fl & 3) != 0) ev.legato = "s"; } catch (Exception ignored) {}
        }
        Element bd = prop(ne, "BendDestinationValue"), bm = prop(ne, "BendMiddleValue");
        float bv = 0;
        try { if (bd != null) bv = Float.parseFloat(text(bd, "Float")); if (bm != null) bv = Math.max(bv, Float.parseFloat(text(bm, "Float"))); } catch (Exception ignored) {}
        if (prop(ne, "Bended") != null && bv > 20) ev.label = "b" + Math.max(1, Math.round(bv / 50f));
        return new int[]{string, fret, tie ? 1 : 0, tr.tuning[string] + fret};
    }

    private static void resolveTies(SongData.Track tr, SongData.Ev ev) {
        for (int[] n : ev.notes) {
            if (n[2] != 1) continue;
            boolean found = false;
            for (int i = tr.evs.size() - 1; i >= 0 && i >= tr.evs.size() - 64 && !found; i--)
                for (int[] q : tr.evs.get(i).notes) if (q[0] == n[0]) { found = true; break; }
            if (!found) n[2] = 0;
        }
    }

}
