package com.enoli.perde;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Ders / tekrar / ünite sınavı oturumu. */
public class LessonActivity extends Activity implements ExerciseView.Host {
    public static final String MODE = "mode", ID = "id";
    static long skipMicUntil = 0;

    private final List<Ex> queue = new ArrayList<>();
    private final Set<Ex> failedOnce = new HashSet<>();
    private final Set<String> gradedKeys = new HashSet<>();
    private int total, doneCount, mistakes, hearts = 3;
    private String mode, id;
    private Curriculum cur;
    private Progress prog;
    private AudioIn audio;
    private ExerciseView exView;
    private Views.Bar bar;
    private LinearLayout feedback;
    private FrameLayout root;
    private Views.Confetti confetti;
    private ScrollView scroll;
    private Runnable pendingMic;
    private TextView heartsView;
    private Ex current;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        cur = Curriculum.get(this);
        prog = Progress.get(this);
        Sfx.enabled = prog.sfxOn;
        audio = new AudioIn();
        mode = getIntent().getStringExtra(MODE);
        id = getIntent().getStringExtra(ID);
        buildUi();
        buildQueue();
        total = queue.size();
        if (total == 0) { finish(); return; }
        showNext();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        LinearLayout top = Ui.hbox(this);
        top.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 8));
        TextView close = Ui.text(this, "✕", 22, Ui.MUTED, true);
        close.setPadding(0, 0, p, 0);
        close.setOnClickListener(v -> confirmExit());
        top.addView(close);
        bar = new Views.Bar(this);
        top.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(this, 16), 1));
        heartsView = Ui.text(this, "", 16, Ui.RED, true);
        heartsView.setPadding(p / 2, 0, 0, 0);
        top.addView(heartsView);
        col.addView(top);
        scroll = new ScrollView(this);
        exView = new ExerciseView(this);
        exView.setPadding(p, p / 2, p, Ui.dp(this, 160));
        scroll.addView(exView);
        col.addView(scroll, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        root.addView(col);
        feedback = Ui.vbox(this);
        feedback.setPadding(p, p, p, p + Ui.dp(this, 8));
        feedback.setVisibility(View.GONE);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        root.addView(feedback, flp);
        confetti = new Views.Confetti(this);
        root.addView(confetti, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);
        Ui.applySystemInsets(root);
    }

    private boolean calOffered;

    static boolean needsTiming(Ex e) {
        switch (e.type) {
            case PLAY_SEQ: case CHANGES: case IMPROV: return true;
            case RHYTHM: return !e.tapMode && e.freeCount == 0;
            default: return false;
        }
    }

    static boolean isMic(Ex.Type t) {
        switch (t) {
            case INFO: case CHOICE: case FRET_TAP: return false;
            default: return true;
        }
    }

    private void buildQueue() {
        Random r = new Random();
        if ("lesson".equals(mode)) {
            Curriculum.Lesson l = cur.byId.get(id);
            for (int i = 0; i < l.cards.size(); i++) {
                Ex e = new Ex();
                e.type = Ex.Type.INFO;
                e.title = i == 0 ? l.title : l.title + " (" + (i + 1) + ")";
                e.body = l.cards.get(i);
                queue.add(e);
            }
            for (String s : l.specs) for (Ex e : Gen.make(s, r)) addWithWarmups(e);
        } else if ("review".equals(mode)) {
            for (Progress.Card c : prog.dueCards(15)) {
                List<Ex> l = Gen.make(c.key, r);
                if (l.isEmpty()) continue;
                Ex e = l.get(r.nextInt(l.size()));
                queue.add(e);
            }
        } else if ("test".equals(mode)) {
            Curriculum.Unit u = null;
            for (Curriculum.Unit x : cur.units) if (x.id.equals(id)) u = x;
            for (String s : cur.unitTestSpecs(u, r)) {
                List<Ex> l = Gen.make(s, r);
                if (!l.isEmpty()) queue.add(l.get(r.nextInt(l.size())));
            }
        } else if ("tuner".equals(mode) || "calibrate".equals(mode)) {
            queue.addAll(Gen.make("g: " + ("tuner".equals(mode) ? "tune" : "calibrate"), r));
        }
        if (System.currentTimeMillis() < skipMicUntil) dropMic();
        if ("calibrate".equals(mode) || "tuner".equals(mode)) calOffered = true;
        heartsView.setText("test".equals(mode) ? "♥ " + hearts : "");
    }

    /** Zamanlı görevlerden önce aynı görevin yavaş ve toleranslı hâllerini ekler (kademeli zorluk). */
    private void addWithWarmups(Ex e) {
        boolean timed = needsTiming(e) || e.tapMode;
        if (timed && e.type != Ex.Type.IMPROV && e.bpm >= 50) {
            float base = Math.max(prog.timingWindowMs, e.tolMs);
            float[] f = {0.7f, 0.85f};
            float[] tolAdd = {90, 45};
            for (int i = 0; i < f.length; i++) {
                Ex w = e.copy();
                w.bpm = Math.max(40, Math.round(e.bpm * f[i]));
                if (w.bpm >= e.bpm) continue;
                w.tolMs = Math.min(280, base + tolAdd[i]);
                w.title = "Isınma " + (i + 1) + "/" + f.length + " — " + Math.round(w.bpm) + " BPM";
                queue.add(w);
            }
        }
        queue.add(e);
    }

    private void dropMic() {
        for (int i = queue.size() - 1; i >= 0; i--) { Ex q = queue.get(i); if (isMic(q.type) && !q.tapMode) queue.remove(i); }
    }

    private float bonusFor(Ex e) {
        if (!"review".equals(mode) || e.key == null) return 0;
        Progress.Card c = prog.cards.get(e.key);
        return c == null ? 0 : c.bpmBonus;
    }

    private void showNext() {
        feedback.setVisibility(View.GONE);
        bar.value = total == 0 ? 1 : (float) doneCount / total;
        bar.invalidate();
        if (queue.isEmpty()) { finishSession(); return; }
        current = queue.remove(0);
        if (!prog.calibrated && !calOffered && needsTiming(current)) {
            // zamanlı ilk görevden önce gecikme ayarı yapılmadıysa önce onu yap
            calOffered = true;
            queue.add(0, current);
            current = Gen.make("g: calibrate", new Random()).get(0);
            current.title = "Önce gecikme ayarı";
            total++;
        }
        exView.stop();
        exView.bind(current, this, bonusFor(current));
        scroll.scrollTo(0, 0);
    }

    // ---------------------------------------------------------------- Host
    @Override public AudioIn audio() { return audio; }

    @Override public void withMic(Runnable then) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { then.run(); return; }
        pendingMic = then;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }

    @Override public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        if (rc == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && pendingMic != null) pendingMic.run();
        else if (rc == 7) showFeedback(false, "Mikrofon izni olmadan çalma görevleri yapılamaz. Ayarlardan izin verebilir ya da \"Şu an çalamam\" ile geçebilirsin.", true);
        pendingMic = null;
    }

    @Override public void skipMicTasks() {
        skipMicUntil = System.currentTimeMillis() + 3600_000L;
        dropMic();
        showNext();
    }

    @Override public void onResult(Ex ex, boolean pass, String message) {
        boolean first = !failedOnce.contains(ex);
        if (ex.type == Ex.Type.INFO) { doneCount++; showNext(); return; }
        if ("review".equals(mode) && ex.key != null && !gradedKeys.contains(ex.key)) {
            gradedKeys.add(ex.key);
            prog.review(ex.key, pass ? 4 : 0, ex.type == Ex.Type.PLAY_SEQ || ex.type == Ex.Type.CHANGES || ex.type == Ex.Type.RHYTHM);
        }
        if (pass) {
            doneCount++;
        } else {
            mistakes++;
            failedOnce.add(ex);
            if ("test".equals(mode)) {
                hearts--;
                heartsView.setText("♥ " + Math.max(0, hearts));
                if (hearts <= 0) { showFeedback(false, message + "\n\nCanların bitti. Üniteyi derslerle çalışıp tekrar dene.", true); testFailed = true; return; }
                doneCount++;
            } else {
                queue.add(ex); // Duolingo usulü: yanlış soru sona eklenir
                if (needsTiming(ex) || ex.tapMode) {
                    float oldBpm = ex.bpm;
                    ex.bpm = Math.max(40, ex.bpm - 6);
                    ex.tolMs = Math.min(260, (ex.tolMs > 0 ? ex.tolMs : prog.timingWindowMs) + 35);
                    if (ex.bpm < oldBpm) message = (message == null ? "" : message + "\n\n") + "Bir sonraki denemede kolaylaştırdım: " + Math.round(ex.bpm) + " BPM, daha geniş zamanlama.";
                }
            }
        }
        showFeedback(pass, message, false);
    }

    private boolean testFailed;

    private void showFeedback(boolean pass, String message, boolean terminal) {
        feedback.removeAllViews();
        feedback.setBackground(Ui.rounded(pass ? 0xFF1E3311 : 0xFF3A1717, Ui.dp(this, 22)));
        String head = pass ? pickPraise() : pickComfort();
        LinearLayout top = Ui.hbox(this);
        Art.Mascot mm = new Art.Mascot(this);
        mm.setMood(pass ? Art.Mascot.Mood.HAPPY : Art.Mascot.Mood.SAD);
        top.addView(mm, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        LinearLayout txt = Ui.vbox(this);
        txt.setPadding(Ui.dp(this, 10), 0, 0, 0);
        TextView h = Ui.text(this, head, 22, pass ? Ui.GREEN : Ui.RED, true);
        txt.addView(h);
        if (message != null && !message.isEmpty()) {
            TextView m = Ui.text(this, message, 15, Ui.TEXT, false);
            txt.addView(m, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 0));
        }
        top.addView(txt, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        feedback.addView(top);
        LinearLayout row = Ui.hbox(this);
        if (!pass && !terminal && isMic(current.type)) {
            Button retry = Ui.button(this, "Tekrar dene", Ui.BLUE, Ui.BLUE_D);
            retry.setOnClickListener(v -> { queue.remove(current); queue.add(0, current); showNext(); });
            LinearLayout.LayoutParams lp = Ui.weight(1); lp.rightMargin = Ui.dp(this, 8);
            row.addView(retry, lp);
        }
        Button cont = pass ? Ui.primary(this, "Devam") : Ui.button(this, terminal ? "Kapat" : "Anladım", Ui.RED, Ui.RED_D);
        cont.setOnClickListener(v -> { if (terminal) finish(); else showNext(); });
        row.addView(cont, Ui.weight(1));
        feedback.addView(row, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 14, 0));
        feedback.setVisibility(View.VISIBLE);
        Ui.slideUp(feedback);
        if (pass) Sfx.good(); else Sfx.bad();
    }

    private String pickPraise() {
        String[] s = {"Harika!", "Süper!", "Tam isabet!", "Mükemmel!", "Böyle devam!", "Rock'n'roll!", "Temiz!"};
        return s[new Random().nextInt(s.length)];
    }

    private String pickComfort() {
        String[] s = {"Olmadı, ama yaklaştın!", "Tekrar deneyelim!", "Hata = öğrenme!", "Bir dahakine!"};
        return s[new Random().nextInt(s.length)];
    }

    private void confirmExit() {
        if (queue.isEmpty() && feedback.getVisibility() == View.VISIBLE) { finish(); return; }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Çıkmak istiyor musun?")
                .setMessage("Bu oturumdaki ilerlemen kaydedilmeyecek.")
                .setPositiveButton("Çık", (d, w) -> finish())
                .setNegativeButton("Devam et", null).show();
    }

    @Override public void onBackPressed() { confirmExit(); }

    // ---------------------------------------------------------------- Bitiş
    private void finishSession() {
        exView.stop();
        audio.stop();
        int xp; int stars;
        stars = mistakes == 0 ? 3 : mistakes <= 2 ? 2 : 1;
        String title;
        boolean leveled;
        if ("lesson".equals(mode)) {
            Curriculum.Lesson l = cur.byId.get(id);
            boolean firstTime = !prog.isDone(l.id);
            prog.completeLesson(l, stars);
            xp = (firstTime ? 15 : 8) + Math.min(20, total) + stars * 5;
            if (stars == 3) { prog.unlock("mukemmel"); if (prog.perfectLessons >= 10) prog.unlock("mukemmel10"); }
            prog.unlock("ilk");
            checkUnitAchievements(l.unit);
            title = "Ders tamamlandı!";
        } else if ("review".equals(mode)) {
            xp = 5 + gradedKeys.size() * 3;
            title = "Tekrar tamamlandı!";
        } else if ("test".equals(mode)) {
            Curriculum.Unit u = null;
            for (Curriculum.Unit x : cur.units) if (x.id.equals(id)) u = x;
            for (Curriculum.Lesson l : u.lessons) if (!prog.isDone(l.id)) prog.completeLesson(l, 1);
            checkUnitAchievements(u);
            xp = 40;
            title = "Ünite geçildi!";
        } else {
            setResult(RESULT_OK);
            finish();
            return;
        }
        int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hr >= 23 || hr < 4) prog.unlock("gece");
        leveled = prog.addXp(xp);
        showEnd(title, stars, xp, leveled);
    }

    private void checkUnitAchievements(Curriculum.Unit u) {
        for (Curriculum.Lesson l : u.lessons) if (!prog.isDone(l.id)) return;
        switch (u.id) {
            case "u04": prog.unlock("ritim"); break;
            case "u05": prog.unlock("akor"); break;
            case "u07": prog.unlock("power"); break;
            case "u08": prog.unlock("kulak"); break;
            case "u12": prog.unlock("pent"); break;
            case "u13": prog.unlock("barre"); break;
            case "u19": prog.unlock("mod"); break;
            case "u26": prog.unlock("usta"); break;
        }
        prog.save();
    }

    private void showEnd(String title, int stars, int xp, boolean leveled) {
        root.removeAllViews();
        LinearLayout col = Ui.vbox(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 20);
        col.setPadding(p, Ui.dp(this, 60), p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        root.addView(sv);
        confetti = new Views.Confetti(this);
        root.addView(confetti, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Art.Mascot hero = new Art.Mascot(this);
        hero.setMood(Art.Mascot.Mood.CHEER);
        col.addView(hero, new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150)));
        TextView t = Ui.text(this, title, 30, Ui.YELLOW, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t);
        Ui.pop(t);
        if (!"review".equals(mode)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3; i++) sb.append(i < stars ? "★" : "☆");
            TextView st = Ui.text(this, sb.toString(), 54, Ui.YELLOW, true);
            st.setGravity(Gravity.CENTER);
            col.addView(st);
        }
        LinearLayout stats = Ui.hbox(this);
        stats.setGravity(Gravity.CENTER);
        LinearLayout xpBox = statBox("XP", "+0", Ui.YELLOW);
        stats.addView(xpBox, statLp());
        Ui.countUp((TextView) xpBox.getChildAt(1), "+", xp, "");
        stats.addView(statBox("Seri", "🔥 " + prog.streak, Ui.ORANGE), statLp());
        int acc = total == 0 ? 100 : Math.round(100f * Math.max(0, total - mistakes) / total);
        stats.addView(statBox("Doğruluk", "%" + Math.max(0, acc), Ui.GREEN), statLp());
        col.addView(stats, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 20, 10));
        if (leveled) {
            TextView lv = Ui.text(this, "🎸 SEVİYE ATLADIN! Seviye " + prog.level() + " — " + Progress.rank(prog.level()), 18, Ui.PURPLE, true);
            lv.setGravity(Gravity.CENTER);
            col.addView(lv, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 10));
        }
        for (String a : prog.newlyUnlocked) {
            String[] info = Progress.achInfo(a);
            TextView at = Ui.text(this, "🏅 Yeni rozet: " + info[1] + " (+20 pena)", 16, Ui.BLUE, true);
            at.setGravity(Gravity.CENTER);
            col.addView(at, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 0));
        }
        prog.newlyUnlocked.clear();
        // değişken ödül: sandık
        Art.Chest chest = new Art.Chest(this);
        TextView reward = Ui.text(this, "🎁 Sandığa dokun!", 18, Ui.ORANGE, true);
        reward.setGravity(Gravity.CENTER);
        chest.setOnClickListener(v -> {
            if (chest.isOpen()) return;
            chest.openNow();
            reward.setText(openChest());
            reward.setTextColor(Ui.TEXT);
            Ui.pop(reward);
            confetti.burst();
            hero.pop();
            Sfx.chest();
        });
        col.addView(chest, new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(this, 130)));
        col.addView(reward, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 6));
        TextView goal = Ui.text(this, "Günlük hedef: " + Math.min(prog.todayXp, prog.dailyGoal) + " / " + prog.dailyGoal + " XP" + (prog.todayXp >= prog.dailyGoal ? "  ✓" : ""), 15, Ui.MUTED, false);
        goal.setGravity(Gravity.CENTER);
        col.addView(goal, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 10));
        Button cont = Ui.primary(this, "Devam");
        cont.setOnClickListener(v -> { setResult(RESULT_OK); finish(); });
        col.addView(cont, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 16, 0));
        confetti.post(() -> confetti.burst());
        Sfx.fanfare();
    }

    private String openChest() {
        Random r = new Random();
        int roll = r.nextInt(100);
        prog.chestsOpened++;
        if (prog.chestsOpened >= 10) prog.unlock("sandik");
        String s;
        if (roll < 65) { int n = 5 + r.nextInt(16); prog.picks += n; s = "🎸 +" + n + " pena"; }
        else if (roll < 85) { prog.boostUntil = System.currentTimeMillis() + 15 * 60_000L; s = "⚡ 15 dakika ÇİFT XP!"; }
        else if (roll < 95) { prog.freezes++; s = "🧊 Seri dondurucu kazandın!"; }
        else { prog.picks += 50; s = "💎 JACKPOT: +50 pena!"; }
        prog.save();
        return s;
    }

    private LinearLayout statBox(String label, String value, int color) {
        LinearLayout b = Ui.vbox(this);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.outlined(Ui.SURFACE, color, Ui.dp(this, 2), Ui.dp(this, 14)));
        b.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        TextView l = Ui.text(this, label, 12, color, true); l.setGravity(Gravity.CENTER);
        TextView v = Ui.text(this, value, 20, Ui.TEXT, true); v.setGravity(Gravity.CENTER);
        b.addView(l); b.addView(v);
        return b;
    }

    private LinearLayout.LayoutParams statLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        return lp;
    }

    @Override protected void onPause() {
        super.onPause();
        if (pendingMic == null && exView != null && current != null && isMic(current.type) && feedback.getVisibility() != View.VISIBLE) {
            exView.stop();
            audio.stop();
            // geri dönüldüğünde aynı alıştırmayı baştan kur
            queue.add(0, current);
            resumeNeeded = true;
        }
    }

    private boolean resumeNeeded;

    @Override protected void onResume() {
        super.onResume();
        if (resumeNeeded) { resumeNeeded = false; showNext(); }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (exView != null) exView.stop();
        audio.stop();
    }

    public static void start(Activity a, String mode, String id) {
        Intent i = new Intent(a, LessonActivity.class);
        i.putExtra(MODE, mode);
        i.putExtra(ID, id);
        a.startActivityForResult(i, 1);
    }
}
