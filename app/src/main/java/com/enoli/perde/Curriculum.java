package com.enoli.perde;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** assets/curriculum.txt ve songs.txt dosyalarını okur. */
public class Curriculum {
    public static class Unit {
        public String id, title, subtitle;
        public List<Lesson> lessons = new ArrayList<>();
        public int index;
    }

    public static class Lesson {
        public String id, title;
        public Unit unit;
        public List<String> cards = new ArrayList<>();
        public List<String> specs = new ArrayList<>();
        public int globalIndex;
    }

    public static class Song {
        public String title, artist, skills, tip, unit, part;
        public String[] prereqs;
        public int difficulty;
        public boolean suggested, main;
        public Unit unitRef;
        /** Aynı şarkı birden çok ünitede farklı parçasıyla yer alabilir. */
        public String entryKey() { return unit + ":" + id(); }
        public String id() { return (title + "|" + artist).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    }

    public final List<Unit> units = new ArrayList<>();
    public final List<Lesson> lessons = new ArrayList<>();
    public final Map<String, Lesson> byId = new HashMap<>();
    public final List<Song> songs = new ArrayList<>();

    public List<Song> songsOf(Unit u) {
        List<Song> l = new ArrayList<>();
        for (Song s : songs) if (s.unitRef == u) l.add(s);
        return l;
    }

    public Song songByKey(String k) {
        for (Song s : songs) if (s.entryKey().equals(k)) return s;
        for (Song s : songs) if (s.id().equals(k)) return s;
        return null;
    }

    private static Curriculum instance;

    public static synchronized Curriculum get(Context c) {
        if (instance == null) {
            instance = new Curriculum();
            instance.load(c);
        }
        return instance;
    }

    private void load(Context c) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open("curriculum.txt"), StandardCharsets.UTF_8))) {
            parse(br);
        } catch (Exception e) { throw new RuntimeException(e); }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open("songs.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|");
                Song s = new Song();
                s.unit = p[0].trim();
                s.title = p[1].trim(); s.artist = p[2].trim(); s.part = p[3].trim();
                s.difficulty = Integer.parseInt(p[4].trim());
                s.skills = p[5].trim(); s.tip = p[6].trim();
                s.suggested = p.length > 7 && p[7].trim().equals("öneri");
                for (Unit u : units) if (u.id.equals(s.unit)) s.unitRef = u;
                if (s.unitRef == null) continue;
                List<String> pre = new ArrayList<>();
                for (Lesson l : s.unitRef.lessons) if (!l.id.endsWith("_pratik")) pre.add(l.id);
                s.prereqs = pre.toArray(new String[0]);
                boolean first = true;
                for (Song o : songs) if (o.unit.equals(s.unit)) first = false;
                s.main = first;
                songs.add(s);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void parse(BufferedReader br) throws java.io.IOException {
        String line;
        Unit u = null; Lesson l = null;
        StringBuilder card = new StringBuilder();
        while ((line = br.readLine()) != null) {
            String t = line.trim();
            if (t.startsWith("## ")) {
                flush(l, card);
                String[] p = t.substring(3).split("\\|");
                l = new Lesson();
                l.id = p[0].trim(); l.title = p[1].trim(); l.unit = u;
                l.globalIndex = lessons.size();
                u.lessons.add(l); lessons.add(l); byId.put(l.id, l);
            } else if (t.startsWith("# ")) {
                flush(l, card); l = null;
                String[] p = t.substring(2).split("\\|");
                u = new Unit();
                u.id = p[0].trim(); u.title = p[1].trim(); u.subtitle = p.length > 2 ? p[2].trim() : "";
                u.index = units.size();
                units.add(u);
            } else if (t.startsWith(">")) {
                String body = t.substring(1).trim();
                if (body.equals("---")) { flush(l, card); }
                else { if (card.length() > 0) card.append("\n\n"); card.append(body); }
            } else if (t.startsWith("q:") || t.startsWith("g:")) {
                if (l != null) l.specs.add(t);
            }
        }
        flush(l, card);
        addPracticeLessons();
    }

    /** Her ünitenin sonuna, o ünitenin çalma görevlerini bir arada tekrar ettiren bir pratik dersi ekler. */
    private void addPracticeLessons() {
        lessons.clear();
        byId.clear();
        for (Unit u : units) {
            List<String> play = new ArrayList<>();
            for (Lesson l : u.lessons) for (String sp : l.specs) {
                if (sp.startsWith("g: ") && sp.matches("g: (seq|strum|tap|read|sing|changes|spider|scale|arp|chord|playnote|open|bend|improv|ivalplay|octave|staffplay|earback).*")) play.add(sp);
            }
            if (play.size() >= 2) {
                Lesson pr = new Lesson();
                pr.id = u.id + "_pratik";
                pr.title = "Ünite pratiği: " + u.title;
                pr.unit = u;
                pr.cards.add("Bu ünitede öğrendiğin tüm çalma görevlerini tek seferde tekrar ediyoruz. Kas hafızası tekrarla oluşur: aynı hareketi yavaş ve temiz yapmak, hızlı ve kirli yapmaktan her zaman daha değerlidir.");
                pr.specs.addAll(play);
                u.lessons.add(pr);
            }
            for (Lesson l : u.lessons) { l.globalIndex = lessons.size(); lessons.add(l); byId.put(l.id, l); }
        }
    }

    private void flush(Lesson l, StringBuilder card) {
        if (l != null && card.length() > 0) l.cards.add(card.toString());
        card.setLength(0);
    }

    /** Bir ünitenin sınav (atlama) testi için karışık speclər. */
    public List<String> unitTestSpecs(Unit u, java.util.Random r) {
        List<String> all = new ArrayList<>();
        for (Lesson l : u.lessons) for (String s : l.specs) if (!s.contains("calibrate") && !s.contains("tune")) all.add(s);
        java.util.Collections.shuffle(all, r);
        return all.subList(0, Math.min(10, all.size()));
    }
}
