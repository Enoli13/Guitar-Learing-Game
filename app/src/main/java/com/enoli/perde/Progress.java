package com.enoli.perde;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

/** Kalıcı ilerleme: XP, seviye, seri, pena (para), dersler, SRS kartları, rozetler, ayarlar. */
public class Progress {
    public int xp, picks, streak, bestStreak, freezes, dailyGoal = 30, todayXp, reviewsDone, perfectLessons;
    public long lastActiveDay = -1, todayDay = -1, boostUntil = 0;
    public final Map<String, Integer> stars = new HashMap<>();
    public final Map<String, Card> cards = new LinkedHashMap<>();
    public final List<String> achievements = new ArrayList<>();
    public final Map<String, String> songTabs = new HashMap<>();
    public final Map<String, Float> songBpm = new HashMap<>();
    public final List<String[]> customSongs = new ArrayList<>(); // {başlık, tab}
    // ayarlar
    public float latencyMs = 0;
    public boolean calibrated = false;
    public float micSensitivity = 0;
    public boolean metronomeAudible = true;
    public boolean strictOctave = false;
    public boolean reminderOn = true;
    public int reminderHour = 19, reminderMinute = 0;
    public boolean onboarded = false;
    public float timingWindowMs = 110;
    public int difficulty = 0;      // 0 kolay, 1 normal, 2 zor
    public boolean sfxOn = true;

    public static class Card {
        public String key;
        public float ease = 2.5f;
        public int interval = 0, reps = 0, lapses = 0, bpmBonus = 0;
        public long due;
    }

    private static Progress inst;
    private final File file;

    private Progress(File f) { file = f; }

    public static synchronized Progress get(Context c) {
        if (inst == null) { inst = new Progress(new File(c.getFilesDir(), "progress.json")); inst.load(); }
        return inst;
    }

    public static long today() {
        Calendar cal = Calendar.getInstance();
        long off = TimeZone.getDefault().getOffset(cal.getTimeInMillis());
        return (cal.getTimeInMillis() + off) / 86_400_000L;
    }

    // ---------- Seviye ----------
    public int level() { return 1 + (int) Math.floor(Math.sqrt(xp / 25.0)); }
    public static int xpForLevel(int l) { return 25 * (l - 1) * (l - 1); }
    public static String rank(int level) {
        String[] r = {"Çırak", "Oda Gitaristi", "Garaj Gitaristi", "Kulüp Gitaristi", "Sahne Gitaristi", "Stüdyo Gitaristi", "Virtüöz", "Efsane"};
        return r[Math.min(r.length - 1, (level - 1) / 4)];
    }

    // ---------- XP / seri ----------
    /** XP ekler; seviye atlandıysa true. */
    public boolean addXp(int amount) {
        rollDay();
        if (System.currentTimeMillis() < boostUntil) amount *= 2;
        int before = level();
        xp += amount;
        todayXp += amount;
        touchStreak();
        save();
        return level() > before;
    }

    public void rollDay() {
        long t = today();
        if (todayDay != t) { todayDay = t; todayXp = 0; }
    }

    private void touchStreak() {
        long t = today();
        if (lastActiveDay == t) return;
        if (lastActiveDay == t - 1) streak++;
        else if (lastActiveDay > 0 && t - lastActiveDay - 1 <= freezes) { freezes -= (int) (t - lastActiveDay - 1); streak++; }
        else streak = 1;
        lastActiveDay = t;
        bestStreak = Math.max(bestStreak, streak);
        if (streak >= 3) unlock("seri3");
        if (streak >= 7) unlock("seri7");
        if (streak >= 30) unlock("seri30");
        if (streak >= 100) unlock("seri100");
    }

    /** Seri bugün bozulacak mı? (görüntü için) */
    public int displayStreak() {
        long t = today();
        if (lastActiveDay == t || lastActiveDay == t - 1) return streak;
        if (lastActiveDay > 0 && t - lastActiveDay - 1 <= freezes) return streak;
        return 0;
    }

    // ---------- Dersler ----------
    public boolean isDone(String lessonId) { return stars.containsKey(lessonId); }

    public void completeLesson(Curriculum.Lesson l, int s) {
        Integer prev = stars.get(l.id);
        stars.put(l.id, Math.max(s, prev == null ? 0 : prev));
        if (s == 3) perfectLessons++;
        long t = today();
        for (String spec : l.specs) {
            if (spec.contains("g: calibrate") || spec.contains("g: tune")) continue;
            if (!cards.containsKey(spec)) {
                Card c = new Card();
                c.key = spec; c.due = t + 1; c.interval = 1;
                cards.put(spec, c);
            }
        }
        save();
    }

    // ---------- SRS (SM-2 türevi) ----------
    /** q: 0 = başarısız, 3 = zor (hatalarla), 4 = iyi, 5 = kusursuz. */
    public void review(String key, int q, boolean timed) {
        Card c = cards.get(key);
        if (c == null) return;
        long t = today();
        if (q < 3) {
            c.reps = 0; c.interval = 0; c.lapses++;
            c.ease = Math.max(1.3f, c.ease - 0.2f);
            c.due = t + 1;
            if (timed) c.bpmBonus = Math.max(0, c.bpmBonus - 10);
        } else {
            c.reps++;
            if (c.reps == 1) c.interval = 1;
            else if (c.reps == 2) c.interval = 3;
            else c.interval = Math.max(c.interval + 1, Math.round(c.interval * c.ease));
            c.ease = Math.max(1.3f, c.ease + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)));
            c.due = t + c.interval;
            if (timed && q >= 4) c.bpmBonus = Math.min(80, c.bpmBonus + 5);
            if (c.bpmBonus >= 30) unlock("hiz");
        }
        reviewsDone++;
        if (reviewsDone >= 50) unlock("tekrar50");
        save();
    }

    public List<Card> dueCards(int max) {
        long t = today();
        List<Card> l = new ArrayList<>();
        for (Card c : cards.values()) if (c.due <= t) l.add(c);
        Collections.sort(l, (a, b) -> Long.compare(a.due, b.due));
        if (l.size() > max) l = new ArrayList<>(l.subList(0, max));
        Collections.shuffle(l, new Random());
        return l;
    }

    public int dueCount() {
        long t = today(); int n = 0;
        for (Card c : cards.values()) if (c.due <= t) n++;
        return n;
    }

    // ---------- Rozetler ----------
    public static final String[][] ACH = {
            {"ilk", "İlk Ses", "İlk dersini tamamla"},
            {"akort", "Kulak Ayarı", "Gitarını uygulamayla akort et"},
            {"ritim", "Ritim Kalbi", "Ritim 1 ünitesini bitir"},
            {"akor", "Akor Avcısı", "İlk Akorlar ünitesini bitir"},
            {"power", "Power Up", "Power chord ünitesini bitir"},
            {"kulak", "Kulak Kesik", "Aralıklar ünitesini bitir"},
            {"barre", "Barre Canavarı", "Barre akorlar ünitesini bitir"},
            {"pent", "Pentatonik Ruh", "Minör pentatonik ünitesini bitir"},
            {"mod", "Mod Büyücüsü", "Modlar ünitesini bitir"},
            {"usta", "Usta", "Ustalık sınavını geç"},
            {"seri3", "Kıvılcım", "3 günlük seri"},
            {"seri7", "Alev", "7 günlük seri"},
            {"seri30", "Yangın", "30 günlük seri"},
            {"seri100", "Volkan", "100 günlük seri"},
            {"mukemmel", "Kusursuz", "Bir dersi hiç hata yapmadan bitir"},
            {"mukemmel10", "Mükemmeliyetçi", "10 dersi hatasız bitir"},
            {"tekrar50", "Hafıza Sarayı", "50 tekrar kartı çalış"},
            {"hiz", "Hız Avcısı", "Bir hız görevinde +30 BPM kazan"},
            {"sarki", "Sahneye Hazır", "İlk şarkının kilidini aç"},
            {"tab", "Transkriptör", "Bir şarkı tab'ı ekle"},
            {"gece", "Gece Kuşu", "Gece yarısına doğru pratik yap"},
            {"sandik", "Hazine Avcısı", "10 sandık aç"},
    };
    public int chestsOpened;

    /** Yeni açıldıysa true döner ve 20 pena verir. */
    public boolean unlock(String id) {
        if (achievements.contains(id)) return false;
        achievements.add(id);
        picks += 20;
        newlyUnlocked.add(id);
        return true;
    }
    public final List<String> newlyUnlocked = new ArrayList<>();

    public static String[] achInfo(String id) { for (String[] a : ACH) if (a[0].equals(id)) return a; return new String[]{id, id, ""}; }

    // ---------- Kalıcılık ----------
    public synchronized void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("xp", xp); o.put("picks", picks); o.put("streak", streak); o.put("bestStreak", bestStreak);
            o.put("freezes", freezes); o.put("dailyGoal", dailyGoal); o.put("todayXp", todayXp); o.put("todayDay", todayDay);
            o.put("lastActiveDay", lastActiveDay); o.put("boostUntil", boostUntil); o.put("reviewsDone", reviewsDone);
            o.put("perfectLessons", perfectLessons); o.put("chestsOpened", chestsOpened);
            o.put("latencyMs", latencyMs); o.put("calibrated", calibrated); o.put("micSensitivity", micSensitivity);
            o.put("metronomeAudible", metronomeAudible); o.put("strictOctave", strictOctave); o.put("reminderOn", reminderOn);
            o.put("reminderHour", reminderHour); o.put("reminderMinute", reminderMinute); o.put("onboarded", onboarded);
            o.put("timingWindowMs", timingWindowMs); o.put("difficulty", difficulty); o.put("sfxOn", sfxOn);
            JSONObject st = new JSONObject(); for (Map.Entry<String, Integer> e : stars.entrySet()) st.put(e.getKey(), e.getValue());
            o.put("stars", st);
            JSONArray ca = new JSONArray();
            for (Card c : cards.values()) {
                JSONObject j = new JSONObject();
                j.put("k", c.key); j.put("e", c.ease); j.put("i", c.interval); j.put("r", c.reps); j.put("l", c.lapses); j.put("d", c.due); j.put("b", c.bpmBonus);
                ca.put(j);
            }
            o.put("cards", ca);
            o.put("ach", new JSONArray(achievements));
            JSONObject tabs = new JSONObject(); for (Map.Entry<String, String> e : songTabs.entrySet()) tabs.put(e.getKey(), e.getValue());
            o.put("songTabs", tabs);
            JSONObject sb = new JSONObject(); for (Map.Entry<String, Float> e : songBpm.entrySet()) sb.put(e.getKey(), e.getValue());
            o.put("songBpm", sb);
            JSONArray cs = new JSONArray(); for (String[] s : customSongs) { JSONArray a = new JSONArray(); a.put(s[0]); a.put(s[1]); cs.put(a); }
            o.put("customSongs", cs);
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) { fo.write(o.toString().getBytes(StandardCharsets.UTF_8)); }
            tmp.renameTo(file);
        } catch (Exception ignored) {}
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream fi = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int off = 0; while (off < b.length) { int r = fi.read(b, off, b.length - off); if (r < 0) break; off += r; }
            JSONObject o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            xp = o.optInt("xp"); picks = o.optInt("picks"); streak = o.optInt("streak"); bestStreak = o.optInt("bestStreak");
            freezes = o.optInt("freezes"); dailyGoal = o.optInt("dailyGoal", 30); todayXp = o.optInt("todayXp"); todayDay = o.optLong("todayDay", -1);
            lastActiveDay = o.optLong("lastActiveDay", -1); boostUntil = o.optLong("boostUntil"); reviewsDone = o.optInt("reviewsDone");
            perfectLessons = o.optInt("perfectLessons"); chestsOpened = o.optInt("chestsOpened");
            latencyMs = (float) o.optDouble("latencyMs", 0); calibrated = o.optBoolean("calibrated"); micSensitivity = (float) o.optDouble("micSensitivity", 0);
            metronomeAudible = o.optBoolean("metronomeAudible", true); strictOctave = o.optBoolean("strictOctave"); reminderOn = o.optBoolean("reminderOn", true);
            reminderHour = o.optInt("reminderHour", 19); reminderMinute = o.optInt("reminderMinute", 0); onboarded = o.optBoolean("onboarded");
            timingWindowMs = (float) o.optDouble("timingWindowMs", 110); difficulty = o.optInt("difficulty", 0); sfxOn = o.optBoolean("sfxOn", true);
            JSONObject st = o.optJSONObject("stars");
            if (st != null) for (java.util.Iterator<String> it = st.keys(); it.hasNext(); ) { String k = it.next(); stars.put(k, st.getInt(k)); }
            JSONArray ca = o.optJSONArray("cards");
            if (ca != null) for (int i = 0; i < ca.length(); i++) {
                JSONObject j = ca.getJSONObject(i);
                Card c = new Card();
                c.key = j.getString("k"); c.ease = (float) j.optDouble("e", 2.5); c.interval = j.optInt("i"); c.reps = j.optInt("r");
                c.lapses = j.optInt("l"); c.due = j.optLong("d"); c.bpmBonus = j.optInt("b");
                cards.put(c.key, c);
            }
            JSONArray a = o.optJSONArray("ach");
            if (a != null) for (int i = 0; i < a.length(); i++) achievements.add(a.getString(i));
            JSONObject tabs = o.optJSONObject("songTabs");
            if (tabs != null) for (java.util.Iterator<String> it = tabs.keys(); it.hasNext(); ) { String k = it.next(); songTabs.put(k, tabs.getString(k)); }
            JSONObject sb = o.optJSONObject("songBpm");
            if (sb != null) for (java.util.Iterator<String> it = sb.keys(); it.hasNext(); ) { String k = it.next(); songBpm.put(k, (float) sb.getDouble(k)); }
            JSONArray cs = o.optJSONArray("customSongs");
            if (cs != null) for (int i = 0; i < cs.length(); i++) { JSONArray x = cs.getJSONArray(i); customSongs.add(new String[]{x.getString(0), x.getString(1)}); }
        } catch (Exception ignored) {}
    }
}
