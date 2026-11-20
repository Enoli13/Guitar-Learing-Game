package com.enoli.perde;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tab paketi: dosya adı şarkı kimliğiyle eşleşen tab'ları Şarkı Yolu'na yükler. */
public final class TabPack {
    private TabPack() {}

    public static class Result { public int ok; public final List<String> miss = new ArrayList<>(); }

    /** Zip ise paket olarak açar (içinde .perde / .gp / .xml dosyaları). */
    public static boolean isPack(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K') return false;
        try {
            for (String n : Zip.read(data).keySet()) {
                String l = n.toLowerCase(Locale.ROOT);
                if (l.endsWith(".perde") || l.endsWith(".gp3") || l.endsWith(".gp4") || l.endsWith(".gp5") || l.endsWith(".gp")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void importFile(Progress prog, Curriculum cur, String name, byte[] data, Result res) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            if (isPack(data)) files.putAll(Zip.read(data)); else files.put(name, data);
        } catch (Exception e) { res.miss.add(name); return; }
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String base = e.getKey().replaceAll(".*/", "");
            if (base.isEmpty()) continue;
            String stem = base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
            String key = stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            Curriculum.Song song = null;
            for (Curriculum.Song sg : cur.songs) if (sg.id().equals(key)) song = sg;
            if (song == null) { res.miss.add(base); continue; }
            try {
                String txt;
                if (base.endsWith(".perde") || base.endsWith(".txt")) txt = new String(e.getValue(), StandardCharsets.UTF_8);
                else {
                    SongData sd = SongFile.load(e.getValue());
                    if (sd == null) txt = new String(e.getValue(), StandardCharsets.UTF_8);
                    else {
                        List<Integer> g = sd.guitarTracks();
                        if (g.isEmpty()) { res.miss.add(base); continue; }
                        txt = TabImport.toText(sd.toResult(g.get(0)));
                    }
                }
                TabImport.Result r = TabImport.parseAny(txt);
                if (r.error != null) { res.miss.add(base); continue; }
                prog.songTabs.put(song.id(), txt);
                prog.songTabs.put("len:" + song.id(), String.valueOf(r.measures));
                res.ok++;
            } catch (Exception ex) { res.miss.add(base); }
        }
        prog.save();
    }
}
