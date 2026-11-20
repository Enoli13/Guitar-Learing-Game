package com.enoli.perde;

import java.util.TreeSet;

/** Şarkı Yolu ilerlemesi: ünitenin parçası (ölçü aralığı) ve tempoda temiz çalınan ölçüler. */
public final class SongProgress {
    private SongProgress() {}

    public static int total(Progress p, Curriculum.Song s) {
        try { return Integer.parseInt(p.songTabs.get("len:" + s.id())); } catch (Exception e) { return 0; }
    }

    public static boolean hasTab(Progress p, Curriculum.Song s) {
        String t = p.songTabs.get(s.id());
        return t != null && !t.trim().isEmpty();
    }

    public static int[] part(Progress p, Curriculum.Song s, int total) {
        String v = p.songTabs.get("part:" + s.entryKey());
        int a = 0, z = Math.max(0, total - 1);
        if (v != null) try { String[] q = v.split("-"); a = Integer.parseInt(q[0]); z = Integer.parseInt(q[1]); } catch (Exception ignored) {}
        if (total > 0) { a = Math.max(0, Math.min(a, total - 1)); z = Math.max(a, Math.min(z, total - 1)); }
        return new int[]{a, z};
    }

    public static void setPart(Progress p, Curriculum.Song s, int a, int z) {
        p.songTabs.put("part:" + s.entryKey(), a + "-" + z);
        p.save();
    }

    static TreeSet<Integer> passed(Progress p, Curriculum.Song s) {
        TreeSet<Integer> set = new TreeSet<>();
        String v = p.songTabs.get("pass:" + s.id());
        if (v != null) for (String x : v.split(",")) try { set.add(Integer.parseInt(x.trim())); } catch (Exception ignored) {}
        return set;
    }

    public static void addPassed(Progress p, Curriculum.Song s, int a, int z) {
        TreeSet<Integer> set = passed(p, s);
        for (int m = a; m <= z; m++) set.add(m);
        StringBuilder sb = new StringBuilder();
        for (int m : set) { if (sb.length() > 0) sb.append(','); sb.append(m); }
        p.songTabs.put("pass:" + s.id(), sb.toString());
        p.save();
    }

    /** Parçanın tempoda temiz çalınmış kısmı (0..1). Tab yoksa 0. */
    public static float learned(Progress p, Curriculum.Song s) {
        int total = total(p, s);
        if (total <= 0 || !hasTab(p, s)) return 0;
        int[] rg = part(p, s, total);
        TreeSet<Integer> set = passed(p, s);
        int c = 0;
        for (int m = rg[0]; m <= rg[1]; m++) if (set.contains(m)) c++;
        return (float) c / (rg[1] - rg[0] + 1);
    }

    public static boolean isLearned(Progress p, Curriculum.Song s) { return learned(p, s) >= 0.9f; }
}
