package com.enoli.perde;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/** Ana ekran: Yol haritası, Tekrar, Şarkılar, Profil. */
public class MainActivity extends Activity {
    private Curriculum cur;
    private Progress prog;
    private FrameLayout content;
    private LinearLayout nav, topBar;
    private int tab = 0;
    private int scrollToY = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Crash.install(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);
        cur = Curriculum.get(this);
        prog = Progress.get(this);
        Sfx.enabled = prog.sfxOn;
        importBundledTabs();
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        topBar = Ui.hbox(this);
        topBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(topBar);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        root.addView(nav);
        setContentView(root);
        Ui.applySystemInsets(root);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
        Reminder.schedule(this);
        if (!prog.onboarded) onboarding();
        else Crash.showIfAny(this);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq == 55 && rs == RESULT_OK && d != null) {
            java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
            if (d.getClipData() != null) for (int i = 0; i < d.getClipData().getItemCount(); i++) uris.add(d.getClipData().getItemAt(i).getUri());
            else if (d.getData() != null) uris.add(d.getData());
            importPack(uris);
        }
        render();
    }

    /** Tab paketi: zip içindeki ya da tek tek seçilen dosyalar, adı şarkı kimliğiyle eşleşen şarkılara yüklenir. */
    private void importPack(java.util.List<android.net.Uri> uris) {
        TabPack.Result res = new TabPack.Result();
        for (android.net.Uri u : uris) {
            try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                byte[] data = MusicXml.readAll(is);
                String name = u.getLastPathSegment() == null ? "" : u.getLastPathSegment();
                try (android.database.Cursor c = getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst()) name = c.getString(0);
                } catch (Exception ignored) {}
                TabPack.importFile(prog, cur, name, data, res);
            } catch (Exception ex) { res.miss.add(String.valueOf(u.getLastPathSegment())); }
        }
        new AlertDialog.Builder(this).setTitle(res.ok > 0 ? "🎉 Tab paketi yüklendi" : "Tab paketi").setMessage(res.ok + " şarkının tab'ı yüklendi." + (res.miss.isEmpty() ? "" : "\nEşleşmeyen dosyalar: " + String.join(", ", res.miss)))
                .setPositiveButton("Tamam", null).show();
        if (res.ok > 0) Sfx.chest();
    }

    private void render() {
        prog.rollDay();
        renderTop();
        renderNav();
        content.removeAllViews();
        switch (tab) {
            case 0: content.addView(pathView()); break;
            case 1: content.addView(reviewView()); break;
            case 2: content.addView(songsView()); break;
            case 3: content.addView(profileView()); break;
        }
    }

    // ---------------------------------------------------------------- Üst çubuk
    private void renderTop() {
        topBar.removeAllViews();
        int ds = prog.displayStreak();
        topBar.addView(chip("🔥 " + ds, ds > 0 && prog.lastActiveDay == Progress.today() ? Ui.ORANGE : Ui.MUTED), Ui.weight(1));
        topBar.addView(chip("🎸 " + prog.picks, Ui.YELLOW), Ui.weight(1));
        topBar.addView(chip("⭐ Sv " + prog.level(), Ui.PURPLE), Ui.weight(1));
        Views.Ring ring = new Views.Ring(this);
        ring.value = (float) prog.todayXp / prog.dailyGoal;
        ring.label = Math.min(99, prog.todayXp) + "";
        ring.color = prog.todayXp >= prog.dailyGoal ? Ui.GREEN : Ui.ORANGE;
        topBar.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        TextView tuner = Ui.text(this, "  🎚", 22, Ui.TEXT, false);
        tuner.setOnClickListener(v -> LessonActivity.start(this, "tuner", null));
        topBar.addView(tuner);
    }

    private TextView chip(String s, int color) {
        TextView t = Ui.text(this, s, 16, color, true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void renderNav() {
        nav.removeAllViews();
        String[][] items = {{"🗺", "Yol"}, {"🧠", "Tekrar"}, {"🎵", "Şarkı Yolu"}, {"👤", "Profil"}};
        for (int i = 0; i < items.length; i++) {
            final int k = i;
            LinearLayout b = Ui.vbox(this);
            b.setGravity(Gravity.CENTER);
            TextView ic = Ui.text(this, items[i][0], 22, Ui.TEXT, false); ic.setGravity(Gravity.CENTER);
            String label = items[i][1];
            if (i == 1 && prog.dueCount() > 0) label += " (" + prog.dueCount() + ")";
            TextView lb = Ui.text(this, label, 12, tab == i ? Ui.BLUE : Ui.MUTED, true); lb.setGravity(Gravity.CENTER);
            b.addView(ic); b.addView(lb);
            if (tab == i) b.setBackground(Ui.rounded(0x221CB0F6, Ui.dp(this, 12)));
            b.setOnClickListener(v -> { tab = k; render(); });
            LinearLayout.LayoutParams lp = Ui.weight(1); lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            nav.addView(b, lp);
        }
    }

    // ---------------------------------------------------------------- Yol
    private Curriculum.Lesson nextLesson() {
        for (Curriculum.Lesson l : cur.lessons) if (!prog.isDone(l.id)) return l;
        return null;
    }

    private boolean unlocked(Curriculum.Lesson l) {
        if (l.globalIndex == 0 || prog.isDone(l.id)) return true;
        // ünite pratiği dersleri yolu kilitlemez: bir önceki normal derse bak
        int i = l.globalIndex - 1;
        while (i > 0 && cur.lessons.get(i).id.endsWith("_pratik")) i--;
        return prog.isDone(cur.lessons.get(i).id);
    }

    private View pathView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p / 2, p, Ui.dp(this, 40));
        sv.addView(col);
        // tek dokunuşla "günün pratiği" (Hooked: eylem)
        Curriculum.Lesson nx = nextLesson();
        int due = prog.dueCount();
        Button daily = Ui.button(this, due > 0 ? "🧠  Günün pratiği: " + due + " tekrar kartı" : nx != null ? "▶  Devam et: " + nx.title : "🏆 Tüm yol tamamlandı!", Ui.BLUE, Ui.BLUE_D);
        daily.setOnClickListener(v -> {
            if (due > 0) LessonActivity.start(this, "review", null);
            else if (nx != null) LessonActivity.start(this, "lesson", nx.id);
        });
        col.addView(daily, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 14));
        if (!prog.calibrated) {
            TextView warn = Ui.text(this, "⚠ Zamanlama görevleri için gecikme ayarı yapılmadı. Profil → Ayarlar → Gecikme ayarı.", 13, Ui.YELLOW, false);
            col.addView(warn, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 0, 10));
        }
        int[] zig = {0, 1, 2, 1, 0, -1, -2, -1};
        int z = 0;
        final View[] currentNode = {null};
        for (Curriculum.Unit u : cur.units) {
            int color = Ui.UNIT_COLORS[u.index % Ui.UNIT_COLORS.length];
            LinearLayout bannerRow = Ui.hbox(this);
            bannerRow.setBackground(Ui.rounded(color, Ui.dp(this, 18)));
            bannerRow.setPadding(p, p, p / 2, p);
            LinearLayout banner = Ui.vbox(this);
            bannerRow.addView(banner, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            bannerRow.addView(new Art.UnitObject(this, u.index), new LinearLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 70)));
            banner.addView(Ui.text(this, "ÜNİTE " + (u.index + 1), 13, 0xCCFFFFFF, true));
            banner.addView(Ui.text(this, u.title, 20, 0xFFFFFFFF, true));
            banner.addView(Ui.text(this, u.subtitle, 14, 0xE6FFFFFF, false));
            int doneN = 0; for (Curriculum.Lesson l : u.lessons) if (prog.isDone(l.id)) doneN++;
            if (doneN < u.lessons.size()) {
                TextView skip = Ui.text(this, "⏩ Bu üniteyi biliyorum — sınavla atla", 13, 0xFFFFFFFF, true);
                skip.setPadding(0, Ui.dp(this, 8), 0, 0);
                skip.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Ünite sınavı")
                        .setMessage(u.title + " ünitesinden karışık 10 soru. 3 canın var. Geçersen tüm dersler açılır.")
                        .setPositiveButton("Başla", (d, w) -> LessonActivity.start(this, "test", u.id)).setNegativeButton("Vazgeç", null).show());
                banner.addView(skip);
            } else banner.addView(Ui.text(this, "✓ Tamamlandı", 13, 0xFFFFFFFF, true));
            col.addView(bannerRow, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 12));
            for (Curriculum.Lesson l : u.lessons) {
                Views.Node n = new Views.Node(this);
                n.color = color;
                boolean un = unlocked(l);
                n.locked = !un;
                n.stars = prog.stars.containsKey(l.id) ? prog.stars.get(l.id) : -1;
                n.current = nx == l;
                n.glyph = prog.isDone(l.id) ? "✓" : glyphFor(l);
                FrameLayout row = new FrameLayout(this);
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL);
                flp.leftMargin = zig[z % zig.length] * Ui.dp(this, 52);
                z++;
                row.addView(n, flp);
                n.setOnClickListener(v -> lessonSheet(l, un));
                col.addView(row);
                TextView t = Ui.text(this, l.title, 12, un ? Ui.TEXT : Ui.MUTED, false);
                t.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Ui.dp(this, 130), Ui.WRAP, Gravity.CENTER_HORIZONTAL);
                tl.leftMargin = flp.leftMargin;
                tl.topMargin = Ui.dp(this, 80);
                if (n.stars <= 0) row.addView(t, tl);
                if (n.current) {
                    currentNode[0] = row;
                    // maskot, sıradaki dersin yanında bekler
                    Art.Mascot mm = new Art.Mascot(this);
                    mm.setMood(Art.Mascot.Mood.IDLE);
                    FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Ui.dp(this, 70), Ui.dp(this, 70), Gravity.CENTER_HORIZONTAL);
                    ml.leftMargin = flp.leftMargin + (flp.leftMargin > 0 ? -Ui.dp(this, 110) : Ui.dp(this, 110));
                    ml.topMargin = Ui.dp(this, 6);
                    row.addView(mm, ml);
                    mm.setOnClickListener(v -> { mm.pop(); lessonSheet(l, un); });
                }
            }
        }
        if (currentNode[0] != null) sv.post(() -> sv.scrollTo(0, Math.max(0, currentNode[0].getTop() - Ui.dp(this, 200))));
        return sv;
    }

    private String glyphFor(Curriculum.Lesson l) {
        boolean mic = false, theory = false;
        for (String s : l.specs) {
            if (s.matches("g: (open|playnote|seq|spider|scale|arp|chord|changes|strum|tap|read|sing|freestrum|improv|earback|bend|octave|ivalplay|staffplay|tune|calibrate).*")) mic = true;
            else theory = true;
        }
        return mic && theory ? "🎸" : mic ? "🎤" : "📖";
    }

    private void lessonSheet(Curriculum.Lesson l, boolean unlocked) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(l.title);
        int micCount = 0;
        for (String s : l.specs) if (!s.startsWith("q:") && !s.matches("g: (alpha|semi|fretname|fretfind|staffname|ival|ivalear|scalespell|degree|chordspell|chordear|chordid|diatonic|keysig|relative|mode|rhythmval|progear|degreeear|chordfunc|tap).*")) micCount++;
        String msg = "Ünite " + (l.unit.index + 1) + ": " + l.unit.title + "\n\n" + l.cards.size() + " bilgi kartı, " + l.specs.size() + " görev grubu" + (micCount > 0 ? " (" + micCount + " tanesi gitarla, mikrofonla)" : "");
        if (prog.isDone(l.id)) msg += "\n\nEn iyi: " + "★★★".substring(0, prog.stars.get(l.id)) + "  — tekrar oynayarak yıldızlarını artırabilirsin.";
        if (!unlocked) msg += "\n\n🔒 Önce önceki dersi bitir ya da ünite sınavıyla atla.";
        b.setMessage(msg);
        if (unlocked) b.setPositiveButton(prog.isDone(l.id) ? "Tekrar oyna" : "Başla", (d, w) -> LessonActivity.start(this, "lesson", l.id));
        b.setNegativeButton("Kapat", null);
        b.show();
    }

    // ---------------------------------------------------------------- Tekrar
    private View reviewView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p / 2, p, p);
        sv.addView(col);
        int due = prog.dueCount();
        int learned = 0, mastered = 0;
        for (Progress.Card c : prog.cards.values()) { if (c.reps > 0) learned++; if (c.interval >= 21) mastered++; }
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Aralıklı Tekrar", 22, Ui.TEXT, true));
        card.addView(Ui.text(this, "Öğrendiğin her bilgi ve beceri bir karttır. Doğru bildikçe kart daha seyrek gelir (1 → 3 → 7 → 18 gün...), unuttuğunda hemen geri döner. Hız görevlerinde her başarılı tekrar temposu +5 BPM artırır.", 14, Ui.MUTED, false), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 10));
        card.addView(Ui.text(this, "Bugün sırası gelen: " + due + "\nToplam kart: " + prog.cards.size() + "   •   Öğrenilen: " + learned + "   •   Kalıcı (21+ gün): " + mastered, 15, Ui.TEXT, false));
        Button go = due > 0 ? Ui.primary(this, "Tekrara başla (" + Math.min(15, due) + " kart)") : Ui.secondary(this, "Bugünlük tekrar yok 🎉");
        go.setEnabled(due > 0);
        go.setOnClickListener(v -> LessonActivity.start(this, "review", null));
        card.addView(go, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 14, 0));
        col.addView(card);
        LinearLayout tools = Ui.card(this);
        tools.addView(Ui.text(this, "Araçlar", 20, Ui.TEXT, true));
        Button tuner = Ui.secondary(this, "🎚  Akort aleti");
        tuner.setOnClickListener(v -> LessonActivity.start(this, "tuner", null));
        tools.addView(tuner, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
        Button cal = Ui.secondary(this, "⏱  Gecikme ayarı (kalibrasyon)");
        cal.setOnClickListener(v -> LessonActivity.start(this, "calibrate", null));
        tools.addView(cal, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
        tools.addView(metronomeTool(), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
        col.addView(tools, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 14, 0));
        return sv;
    }

    private AudioOut.Metronome toolMet;

    private View metronomeTool() {
        LinearLayout box = Ui.vbox(this);
        TextView lbl = Ui.text(this, "🥁 Metronom: 80 BPM", 16, Ui.TEXT, true);
        SeekBar sb = new SeekBar(this);
        sb.setMax(200); sb.setProgress(40);
        Button play = Ui.secondary(this, "Başlat");
        final float[] bpm = {80};
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) { bpm[0] = 40 + v; lbl.setText("🥁 Metronom: " + (int) bpm[0] + " BPM"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { if (toolMet != null) { toolMet.stop(); toolMet = new AudioOut.Metronome(bpm[0], 4); toolMet.start(); } }
        });
        play.setOnClickListener(v -> {
            if (toolMet != null) { toolMet.stop(); toolMet = null; play.setText("Başlat"); }
            else { toolMet = new AudioOut.Metronome(bpm[0], 4); toolMet.start(); play.setText("Durdur"); }
        });
        box.addView(lbl); box.addView(sb); box.addView(play);
        return box;
    }

    @Override protected void onPause() { super.onPause(); if (toolMet != null) { toolMet.stop(); toolMet = null; } }

    // ---------------------------------------------------------------- Şarkılar
    private boolean unitDone(Curriculum.Unit u) { return SongPath.unitDone(prog, u); }

    private int countDone(Curriculum.Unit u, List<SongPath.Step> steps) {
        int c = 0;
        for (SongPath.Step s : steps) if (!s.fin && SongPath.stars(prog, u, s.index) >= 0) c++;
        return c;
    }

    /** Kişisel derlemede assets/tabs/ içine konmuş tab paketi varsa ilk açılışta otomatik yüklenir. */
    private void importBundledTabs() {
        try {
            String[] files = getAssets().list("tabs");
            if (files == null) return;
            for (String f : files) {
                byte[] data;
                try (java.io.InputStream is = getAssets().open("tabs/" + f)) { data = MusicXml.readAll(is); }
                String key = "bundled:" + f + ":" + data.length;
                if (prog.songTabs.containsKey(key)) continue;
                TabPack.Result res = new TabPack.Result();
                TabPack.importFile(prog, cur, f, data, res);
                prog.songTabs.put(key, String.valueOf(res.ok));
                prog.save();
            }
        } catch (Exception ignored) {}
    }

    private void openPackPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 55);
    }

    private void addMascot(FrameLayout row, int nodeLeft, String say, Runnable onTap) {
        Art.Mascot mm = new Art.Mascot(this);
        mm.setMood(Art.Mascot.Mood.CHEER);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER_HORIZONTAL);
        ml.leftMargin = nodeLeft + (nodeLeft > 0 ? -Ui.dp(this, 115) : Ui.dp(this, 115));
        ml.topMargin = Ui.dp(this, 4);
        row.addView(mm, ml);
        TextView b = Ui.text(this, say, 11, Ui.TEXT, true);
        b.setBackground(Ui.rounded(Ui.SURFACE2, Ui.dp(this, 10)));
        b.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.dp(this, 110), Ui.WRAP, Gravity.CENTER_HORIZONTAL);
        bl.leftMargin = ml.leftMargin;
        bl.topMargin = Ui.dp(this, 68);
        row.addView(b, bl);
        mm.setOnClickListener(v -> { mm.pop(); onTap.run(); });
    }

    private View songsView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p / 2, p, Ui.dp(this, 40));
        sv.addView(col);
        // ---- durum
        int unitsDone = 0, crowns = 0, missingMain = 0, mainTotal = 0;
        for (Curriculum.Unit u : cur.units) {
            if (unitDone(u)) unitsDone++;
            if (unitDone(u)) crowns++;
            for (Curriculum.Song sg : cur.songsOf(u)) if (sg.main) { mainTotal++; if (!SongProgress.hasTab(prog, sg)) missingMain++; }
        }
        LinearLayout head = Ui.card(this);
        LinearLayout hr = Ui.hbox(this);
        LinearLayout ht = Ui.vbox(this);
        ht.addView(Ui.text(this, "🎵 Şarkı Yolu", 24, Ui.TEXT, true));
        ht.addView(Ui.text(this, "Yol'dan ayrı bir yol: aynı konular, ama her adımda konuyu şarkının bir bölümü üzerinden öğrenip o bölümü çalarsın. Ünite sonunda bütün bölümleri birleştirip çalınca 👑 ve sandık.", 13, Ui.MUTED, false));
        hr.addView(ht, Ui.weight(1));
        Views.Ring ring = new Views.Ring(this);
        ring.value = (float) unitsDone / Math.max(1, cur.units.size());
        ring.label = unitsDone + "/" + cur.units.size();
        ring.color = Ui.PURPLE;
        hr.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        head.addView(hr);
        head.addView(Ui.text(this, "👑 " + crowns + " şarkı bölümü öğrenildi   •   📄 " + (mainTotal - missingMain) + "/" + mainTotal + " ana şarkının tab'ı hazır", 14, Ui.YELLOW, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
        col.addView(head);
        if (missingMain > 0) {
            LinearLayout pk = Ui.card(this);
            pk.setBackground(Ui.outlined(0xFF3A2A12, Ui.ORANGE, Ui.dp(this, 2), Ui.dp(this, 16)));
            pk.addView(Ui.text(this, "📦 Tab paketini yükle", 18, Ui.ORANGE, true));
            pk.addView(Ui.text(this, "Telif hakkı yüzünden tab'lar uygulamanın içinde gelmez. Bilgisayardaki Perde-tablar.zip dosyasını telefona at (kablo, Telegram, Drive ya da: adb push ~/Projeler/Perde-tablar.zip /sdcard/Download/), sonra aşağıdaki düğmeyle seç. Bir kez yeterli.", 13, Ui.TEXT, false), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 8));
            Button pb = Ui.primary(this, "📦 Paketi seç");
            pb.setOnClickListener(v -> openPackPicker());
            pk.addView(pb);
            col.addView(pk, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
        }
        // ---- yol
        int[] zig = {0, 1, 2, 1, 0, -1, -2, -1};
        int z = 0;
        final View[] currentRow = {null};
        boolean open = true;
        for (Curriculum.Unit u : cur.units) {
            final boolean unlocked = open;
            final boolean done = unitDone(u);
            int color = Ui.UNIT_COLORS[u.index % Ui.UNIT_COLORS.length];
            List<Curriculum.Song> ss = cur.songsOf(u);
            Curriculum.Song main = null;
            for (Curriculum.Song sg : ss) if (sg.main) main = sg;
            // afiş
            LinearLayout bannerRow = Ui.hbox(this);
            bannerRow.setBackground(Ui.rounded(unlocked ? color : 0xFF3A3E52, Ui.dp(this, 18)));
            bannerRow.setPadding(p, p, p / 2, p);
            LinearLayout banner = Ui.vbox(this);
            bannerRow.addView(banner, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            bannerRow.addView(new Art.UnitObject(this, u.index), new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
            banner.addView(Ui.text(this, "ÜNİTE " + (u.index + 1) + (done ? "  ✓" : unlocked ? "" : "  🔒"), 13, 0xCCFFFFFF, true));
            banner.addView(Ui.text(this, u.title, 20, 0xFFFFFFFF, true));
            if (main != null) banner.addView(Ui.text(this, "🎸 " + main.title + " — " + main.artist, 15, 0xFFFFFFFF, true));
            if (main != null) banner.addView(Ui.text(this, main.part + " • " + main.skills, 12, 0xE6FFFFFF, false));
            col.addView(bannerRow, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 22, 12));
            // adımlar: her adım = ünitenin bir konusu + şarkının bir bölümü
            TabImport.Result tr = SongPath.tab(prog, main);
            List<SongPath.Step> steps = SongPath.steps(u, main, tr);
            boolean prevDone = true, currentSet = false;
            for (SongPath.Step st : steps) {
                int sStars = SongPath.stars(prog, u, st.index);
                boolean sd = sStars >= 0;
                final boolean su = unlocked && (sd || prevDone);
                boolean isCur = unlocked && !done && su && !sd && !currentSet;
                if (isCur) currentSet = true;
                FrameLayout row = new FrameLayout(this);
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL);
                flp.leftMargin = zig[z++ % zig.length] * Ui.dp(this, 52);
                String where = st.fromM >= 0 ? "ölçü " + (st.fromM + 1) + (st.toM > st.fromM ? "–" + (st.toM + 1) : "") : "tab gerekli";
                final Curriculum.Song mainF = main;
                Runnable go = () -> {
                    if (tr == null) { SongActivity.open(this, mainF.entryKey(), null); return; }
                    LessonActivity.start(this, "songstep", u.id + ":" + st.index);
                };
                View node;
                if (st.fin) {
                    Views.SongNode sn = new Views.SongNode(this);
                    sn.color = color; sn.locked = !su; sn.noTab = tr == null;
                    sn.learned = sd; sn.value = sd ? 1 : (float) st.index > 0 ? (float) countDone(u, steps) / (steps.size() - 1) : 0;
                    sn.current = isCur;
                    node = sn;
                } else {
                    Views.Node n = new Views.Node(this);
                    n.color = color; n.locked = !su; n.stars = sStars > 0 ? sStars : -1; n.current = isCur;
                    n.glyph = sd ? "✓" : "🎸";
                    node = n;
                }
                row.addView(node, flp);
                node.setOnClickListener(v -> {
                    if (!su) { Ui.shake(v); return; }
                    new AlertDialog.Builder(this).setTitle((st.fin ? "🏁 " : "🎸 ") + st.title)
                            .setMessage(mainF == null ? "" : (mainF.title + " — " + mainF.artist + "\n" + (st.fin ? "Bu ünitede öğrendiğin bütün bölümler: " : "Bölüm: ") + where
                                    + (st.fin ? "" : "\n\nKonu: " + st.lesson.title + "\nÖnce konunun teorisi, sonra bu bölümden sorular, en son bölümü çalmak.")
                                    + (tr == null ? "\n\n📄 Önce şarkının tab'ını yükle." : "")))
                            .setPositiveButton(tr == null ? "Tab yükle" : sd ? "Tekrar oyna" : "Başla", (d, w) -> go.run())
                            .setNegativeButton("Kapat", null).show();
                });
                TextView t = Ui.text(this, (st.fin ? "🏁 Final\n" : "") + st.title + "\n" + where, 12, su ? (st.fin && sd ? Ui.YELLOW : Ui.TEXT) : Ui.MUTED, st.fin);
                t.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Ui.dp(this, 150), Ui.WRAP, Gravity.CENTER_HORIZONTAL);
                tl.leftMargin = flp.leftMargin; tl.topMargin = Ui.dp(this, st.fin ? 116 : 80);
                if (st.fin || sStars <= 0) row.addView(t, tl);
                if (isCur) { currentRow[0] = row; addMascot(row, flp.leftMargin, tr == null ? "Önce tab'ı yükle!" : st.fin ? "Hepsini birleştir!" : "Sıradaki bölüm!", go); }
                col.addView(row, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 0, st.fin ? 12 : 4));
                prevDone = sd;
            }
            // sandık
            final String ck = "chest:" + u.id;
            final boolean claimed = prog.songTabs.containsKey(ck);
            Views.ChestNode cn = new Views.ChestNode(this);
            cn.ready = done; cn.opened = claimed;
            FrameLayout crow = new FrameLayout(this);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL);
            cl.leftMargin = zig[z++ % zig.length] * Ui.dp(this, 52);
            crow.addView(cn, cl);
            cn.setOnClickListener(v -> {
                if (claimed) { Ui.shake(v); return; }
                if (!done) { Ui.shake(v); new AlertDialog.Builder(this).setTitle("🎁 Ünite sandığı").setMessage("Ünitenin bütün adımlarını ve finali bitir; sandık açılır.").setPositiveButton("Tamam", null).show(); return; }
                prog.songTabs.put(ck, "1");
                int n = 20 + new java.util.Random().nextInt(21);
                prog.picks += n;
                prog.addXp(30);
                prog.save();
                Sfx.chest();
                new AlertDialog.Builder(this).setTitle("🎁 " + u.title + " tamamlandı!").setMessage("👑 Şarkı öğrenildi\n🎸 +" + n + " pena\n⭐ +30 XP").setPositiveButton("Harika!", (d2, w2) -> render()).show();
            });
            col.addView(crow, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 0, 6));
            for (Curriculum.Song sg : ss) {
                if (sg.main) continue;
                TextView bt = Ui.text(this, "➕ Bonus: " + sg.title + " — " + sg.artist + (SongProgress.hasTab(prog, sg) ? "" : "  (tab yok)"), 13, unlocked ? Ui.BLUE : Ui.MUTED, true);
                bt.setGravity(Gravity.CENTER);
                bt.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
                if (unlocked) bt.setOnClickListener(v -> SongActivity.open(this, sg.entryKey(), null));
                col.addView(bt);
            }
            // üniteyi sıfırla
            boolean any = claimed;
            int nSteps = SongPath.lessons(u).size();
            for (int i = 0; i <= nSteps; i++) if (SongPath.stars(prog, u, i) >= 0) any = true;
            if (any) {
                TextView rs = Ui.text(this, "↺ Üniteyi sıfırla", 13, Ui.MUTED, true);
                rs.setGravity(Gravity.CENTER);
                rs.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
                final Curriculum.Unit uu = u;
                rs.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("↺ " + uu.title)
                        .setMessage("Bu ünitenin Şarkı Yolu ilerlemesi (adım yıldızları, final, taç ve sandık) silinsin mi? Adımları baştan çalarak geçersin. Kazandığın XP ve penalar kalır; Yol'daki ilerlemen etkilenmez.")
                        .setPositiveButton("Sıfırla", (d2, w2) -> {
                            for (int i = 0; i <= nSteps; i++) prog.songTabs.remove(SongPath.key(uu, i));
                            prog.songTabs.remove("chest:" + uu.id);
                            prog.save();
                            render();
                        })
                        .setNegativeButton("Vazgeç", null).show());
                col.addView(rs);
            }
            if (!done) open = false;
            if (done) prog.unlock("sarki");
        }
        if (currentRow[0] != null) sv.post(() -> sv.scrollTo(0, Math.max(0, currentRow[0].getTop() - Ui.dp(this, 220))));
        prog.save();
        col.addView(Ui.text(this, "Kendi şarkıların", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 6));
        for (int i = 0; i < prog.customSongs.size(); i++) {
            final int k = i;
            String[] cs = prog.customSongs.get(i);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "🎵 " + cs[0], 17, Ui.TEXT, true));
            card.setOnClickListener(v -> SongActivity.open(this, null, String.valueOf(k)));
            col.addView(card, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 4));
        }
        Button add = Ui.secondary(this, "+ Şarkı ekle (Guitar Pro / MusicXML / tab)");
        add.setOnClickListener(v -> {
            EditText et = new EditText(this);
            et.setHint("Şarkı adı");
            new AlertDialog.Builder(this).setTitle("Yeni şarkı").setView(et).setPositiveButton("Ekle", (d, w) -> {
                String t = et.getText().toString().trim();
                if (t.isEmpty()) return;
                prog.customSongs.add(new String[]{t, ""});
                prog.save();
                SongActivity.open(this, null, String.valueOf(prog.customSongs.size() - 1));
            }).setNegativeButton("Vazgeç", null).show();
        });
        col.addView(add, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
        return sv;
    }

    // ---------------------------------------------------------------- Profil
    private View profileView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p / 2, p, Ui.dp(this, 40));
        sv.addView(col);
        int lv = prog.level();
        LinearLayout head = Ui.card(this);
        head.addView(Ui.text(this, "Seviye " + lv + " — " + Progress.rank(lv), 24, Ui.PURPLE, true));
        int a = Progress.xpForLevel(lv), z = Progress.xpForLevel(lv + 1);
        Views.Bar xb = new Views.Bar(this); xb.color = Ui.PURPLE; xb.value = (float) (prog.xp - a) / (z - a);
        head.addView(xb, Ui.lpm(this, Ui.MATCH, Ui.dp(this, 14), 10, 4));
        head.addView(Ui.text(this, prog.xp + " XP  •  sonraki seviyeye " + (z - prog.xp) + " XP", 13, Ui.MUTED, false));
        int lessonsDone = prog.stars.size();
        head.addView(Ui.text(this, "\n📚 Ders: " + lessonsDone + " / " + cur.lessons.size() + "    🔥 En uzun seri: " + prog.bestStreak + "    🧊 Dondurucu: " + prog.freezes + "    🧠 Tekrar: " + prog.reviewsDone, 14, Ui.TEXT, false));
        col.addView(head);
        // rozetler
        col.addView(Ui.text(this, "Rozetler (" + prog.achievements.size() + "/" + Progress.ACH.length + ")", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 8));
        LinearLayout grid = Ui.vbox(this);
        LinearLayout row = null;
        for (int i = 0; i < Progress.ACH.length; i++) {
            if (i % 3 == 0) { row = Ui.hbox(this); grid.addView(row, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 0, 6)); }
            String[] ach = Progress.ACH[i];
            boolean has = prog.achievements.contains(ach[0]);
            LinearLayout b = Ui.vbox(this);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.outlined(has ? 0xFF2A2440 : Ui.SURFACE, has ? Ui.YELLOW : Ui.LINE, Ui.dp(this, 2), Ui.dp(this, 14)));
            b.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
            TextView ic = Ui.text(this, has ? "🏅" : "🔒", 24, Ui.TEXT, false); ic.setGravity(Gravity.CENTER);
            TextView nm = Ui.text(this, ach[1], 12, has ? Ui.TEXT : Ui.MUTED, true); nm.setGravity(Gravity.CENTER);
            b.addView(ic); b.addView(nm);
            b.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(ach[1]).setMessage(ach[2] + (has ? "\n\n✓ Kazanıldı" : "")).setPositiveButton("Tamam", null).show());
            LinearLayout.LayoutParams lp = Ui.weight(1); lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            row.addView(b, lp);
        }
        col.addView(grid);
        // dükkan
        col.addView(Ui.text(this, "Dükkân (🎸 " + prog.picks + " pena)", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 8));
        col.addView(shopItem("🧊 Seri dondurucu", "Bir gün pratik yapamazsan serin bozulmaz.", 50, () -> prog.freezes++));
        col.addView(shopItem("⚡ Çift XP (15 dk)", "15 dakika boyunca kazandığın XP ikiye katlanır.", 30, () -> prog.boostUntil = System.currentTimeMillis() + 15 * 60_000L));
        // ayarlar
        col.addView(Ui.text(this, "Ayarlar", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 8));
        col.addView(settings());
        // hakkında
        LinearLayout about = Ui.card(this);
        about.addView(Ui.text(this, "Nasıl çalışır?", 18, Ui.TEXT, true));
        about.addView(Ui.text(this,
                "• Mikrofon gitarını gerçek zamanlı dinler: perde (YIN algoritması), vuruş anı (enerji atağı) ve akor (kromagram) tespiti yapılır. Hata yaptığın anda görev biter.\n" +
                "• Aralıklı tekrar SM-2 algoritmasına dayanır (Anki gibi).\n" +
                "• Oyunlaştırma; Octalysis (Actionable Gamification), Hook Modeli (tetikleyici → eylem → değişken ödül → yatırım) ve Tiny Habits (küçük başla, anında kutla) ilkeleriyle tasarlandı: günlük hatırlatma, tek dokunuşla günün pratiği, sürpriz sandıklar, seri, rozetler ve şarkı hedefleri.\n" +
                "• Müfredat; klasik gitar metodlarının sıralamasından (nota, porte, akor, ritim) başlayıp hız tekniği (tempo merdiveni), modlar, ileri armoni ve kulak eğitimine kadar uzanır.", 13, Ui.MUTED, false));
        col.addView(about, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 16, 0));
        return sv;
    }

    private View shopItem(String title, String desc, int price, Runnable give) {
        LinearLayout c = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        LinearLayout t = Ui.vbox(this);
        t.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        t.addView(Ui.text(this, desc, 13, Ui.MUTED, false));
        row.addView(t, Ui.weight(1));
        Button buy = Ui.button(this, "🎸 " + price, Ui.YELLOW, Ui.darker(Ui.YELLOW, 0.7f));
        buy.setTextColor(0xFF2A2000);
        buy.setOnClickListener(v -> {
            if (prog.picks < price) { new AlertDialog.Builder(this).setMessage("Yeterli pena yok. Ders bitir, sandık aç!").setPositiveButton("Tamam", null).show(); return; }
            prog.picks -= price; give.run(); prog.save(); Sfx.chest(); render();
        });
        row.addView(buy);
        c.addView(row);
        LinearLayout wrap = Ui.vbox(this);
        wrap.addView(c, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 4));
        return wrap;
    }

    private View settings() {
        LinearLayout c = Ui.card(this);
        c.addView(toggle("Ses efektleri (doğru/yanlış sesleri)", prog.sfxOn, v -> { prog.sfxOn = v; Sfx.enabled = v; }));
        c.addView(toggle("Metronom sesi", prog.metronomeAudible, v -> prog.metronomeAudible = v));
        c.addView(toggle("Katı oktav (oktav hatalarını kabul etme)", prog.strictOctave, v -> prog.strictOctave = v));
        c.addView(toggle("Günlük hatırlatma (" + String.format("%02d:%02d", prog.reminderHour, prog.reminderMinute) + ")", prog.reminderOn, v -> { prog.reminderOn = v; Reminder.schedule(this); }));
        Button time = Ui.secondary(this, "⏰ Hatırlatma saatini değiştir");
        time.setOnClickListener(v -> new android.app.TimePickerDialog(this, (tp, h, m) -> { prog.reminderHour = h; prog.reminderMinute = m; prog.save(); Reminder.schedule(this); render(); }, prog.reminderHour, prog.reminderMinute, true).show());
        c.addView(time, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 8));
        c.addView(slider("Günlük hedef: ", " XP", 10, 100, prog.dailyGoal, v -> prog.dailyGoal = v));
        c.addView(slider("Mikrofon eşiği: ", " dB (gürültülü ortamda artır)", -10, 20, (int) prog.micSensitivity, v -> prog.micSensitivity = v));
        TextView lat = Ui.text(this, "Ölçülen gecikme: " + Math.round(prog.latencyMs) + " ms" + (prog.calibrated ? "" : " (ayarlanmadı)"), 14, Ui.MUTED, false);
        c.addView(lat, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 4));
        Button cal = Ui.secondary(this, "⏱ Gecikme ayarını yap");
        cal.setOnClickListener(v -> LessonActivity.start(this, "calibrate", null));
        c.addView(cal);
        return c;
    }

    interface BoolSet { void set(boolean v); }
    interface IntSet { void set(int v); }

    private View toggle(String label, boolean value, BoolSet s) {
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText(label);
        sw.setTextColor(Ui.TEXT);
        sw.setChecked(value);
        sw.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        sw.setOnCheckedChangeListener((b, v) -> { s.set(v); prog.save(); });
        return sw;
    }

    private View slider(String pre, String post, int min, int max, int value, IntSet s) {
        LinearLayout box = Ui.vbox(this);
        TextView t = Ui.text(this, pre + value + post, 14, Ui.TEXT, false);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean f) { t.setText(pre + (v + min) + post); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) { s.set(b.getProgress() + min); prog.save(); }
        });
        box.addView(t); box.addView(sb);
        box.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        return box;
    }

    // ---------------------------------------------------------------- İlk açılış
    private void onboarding() {
        new AlertDialog.Builder(this)
                .setTitle("Perde'ye hoş geldin 🎸")
                .setMessage("Sıfırdan başlayıp gitar teorisinin ve tekniğinin tamamını öğreneceksin.\n\n" +
                        "• Çalma görevlerinde mikrofon seni dinler; yanlış nota ya da zamansız vuruşta görev o anda biter.\n" +
                        "• Bilgiler aralıklı tekrarla hafızana yerleşir.\n" +
                        "• Her gün küçük bir hedef: 5 dakika bile seriyi korur.\n\n" +
                        "İlk derste gitarını akort edip ses gecikmesini ayarlayacağız. Hazır mısın?")
                .setCancelable(false)
                .setPositiveButton("Başlayalım", (d, w) -> { prog.onboarded = true; prog.save(); })
                .show();
    }
}
