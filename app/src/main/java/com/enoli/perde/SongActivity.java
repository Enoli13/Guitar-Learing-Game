package com.enoli.perde;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Şarkı sayfası: gereken beceriler, tab içe aktarma ve ölçü ölçü mikrofonlu pratik. */
public class SongActivity extends Activity implements ExerciseView.Host {
    private Progress prog;
    private Curriculum.Song song;
    private int customIdx = -1;
    private String key;
    private AudioIn audio;
    private ExerciseView exView;
    private TabImport.Result parsed;
    private int fromM = 0, toM = 0;
    private boolean timed = false;
    private float bpm = 70;
    private LinearLayout practiceBox;
    private Runnable pendingMic;
    private TextView sectionLabel, bpmLabel;

    public static void open(Activity a, String songId, String customIdx) {
        Intent i = new Intent(a, SongActivity.class);
        i.putExtra("song", songId);
        i.putExtra("custom", customIdx);
        a.startActivityForResult(i, 2);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        prog = Progress.get(this);
        audio = new AudioIn();
        Curriculum cur = Curriculum.get(this);
        String sid = getIntent().getStringExtra("song");
        String cid = getIntent().getStringExtra("custom");
        if (sid != null) for (Curriculum.Song s : cur.songs) if (s.id().equals(sid)) song = s;
        if (cid != null) customIdx = Integer.parseInt(cid);
        key = song != null ? song.id() : "custom" + customIdx;
        bpm = prog.songBpm.containsKey(key) ? prog.songBpm.get(key) : 70;

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, Ui.dp(this, 60));
        sv.addView(col);
        setContentView(sv);
        Ui.applySystemInsets(sv);

        TextView back = Ui.text(this, "← Geri", 16, Ui.MUTED, true);
        back.setOnClickListener(v -> finish());
        col.addView(back);
        if (song != null) {
            col.addView(Ui.text(this, song.title, 26, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
            col.addView(Ui.text(this, song.artist + "  •  Zorluk " + song.difficulty + "/5", 14, Ui.MUTED, false));
            LinearLayout info = Ui.card(this);
            info.addView(Ui.text(this, "Gereken beceriler", 16, Ui.TEXT, true));
            info.addView(Ui.text(this, song.skills, 14, Ui.MUTED, false));
            info.addView(Ui.text(this, "💡 " + song.tip, 14, Ui.YELLOW, false), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 8));
            for (String id : song.prereqs) {
                Curriculum.Lesson l = cur.byId.get(id.trim());
                if (l == null) continue;
                boolean d = prog.isDone(l.id);
                TextView t = Ui.text(this, (d ? "✓ " : "○ ") + "Ünite " + (l.unit.index + 1) + " • " + l.title, 14, d ? Ui.GREEN : Ui.TEXT, false);
                t.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
                if (!d) t.setOnClickListener(v -> LessonActivity.start(this, "lesson", l.id));
                info.addView(t);
            }
            col.addView(info, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 0));
        } else {
            col.addView(Ui.text(this, prog.customSongs.get(customIdx)[0], 26, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
        }

        col.addView(Ui.text(this, "Tab", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 4));
        col.addView(Ui.text(this, "Ultimate Guitar / Songsterr'den kopyaladığın ASCII tab'ı (e|---, B|--- satırları) buraya yapıştır. Akor sütunları tek vuruş olarak sayılır. Önce kayıttaki akordu kontrol et ve gitarını ona göre ayarla.", 13, Ui.MUTED, false));
        EditText et = new EditText(this);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(12);
        et.setTextColor(Ui.TEXT);
        et.setHintTextColor(Ui.MUTED);
        et.setHint("e|-----0---|\nB|---1-----|\nG|-0-------|\nD|---------|\nA|-3-------|\nE|---------|");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setMinLines(7);
        et.setGravity(Gravity.TOP);
        et.setHorizontallyScrolling(true);
        et.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12)));
        et.setPadding(p / 2, p / 2, p / 2, p / 2);
        String existing = song != null ? prog.songTabs.get(key) : prog.customSongs.get(customIdx)[1];
        if (existing != null) et.setText(existing);
        col.addView(et, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 8));
        Button imp = Ui.button(this, "📥 Tab'ı içe aktar", Ui.BLUE, Ui.BLUE_D);
        col.addView(imp);
        practiceBox = Ui.vbox(this);
        col.addView(practiceBox, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 0));
        imp.setOnClickListener(v -> {
            String txt = et.getText().toString();
            if (song != null) prog.songTabs.put(key, txt); else prog.customSongs.get(customIdx)[1] = txt;
            prog.unlock("tab");
            prog.save();
            importTab(txt);
        });
        if (existing != null && !existing.isEmpty()) importTab(existing);
    }

    private void importTab(String txt) {
        practiceBox.removeAllViews();
        parsed = TabImport.parse(txt, 4);
        if (parsed.error != null) { practiceBox.addView(Ui.text(this, parsed.error, 14, Ui.RED, true)); return; }
        fromM = 0; toM = Math.min(1, parsed.measures - 1);
        practiceBox.addView(Ui.text(this, "✓ " + parsed.notes.size() + " nota, " + parsed.measures + " ölçü bulundu", 15, Ui.GREEN, true));
        sectionLabel = Ui.text(this, "", 15, Ui.TEXT, true);
        practiceBox.addView(sectionLabel, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
        SeekBar a = new SeekBar(this), z = new SeekBar(this);
        a.setMax(Math.max(0, parsed.measures - 1)); z.setMax(Math.max(0, parsed.measures - 1));
        a.setProgress(fromM); z.setProgress(toM);
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                fromM = a.getProgress(); toM = Math.max(fromM, z.getProgress());
                updateLabels();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { if (z.getProgress() < a.getProgress()) z.setProgress(a.getProgress()); }
        };
        a.setOnSeekBarChangeListener(l); z.setOnSeekBarChangeListener(l);
        practiceBox.addView(Ui.text(this, "Başlangıç ölçüsü", 12, Ui.MUTED, false));
        practiceBox.addView(a);
        practiceBox.addView(Ui.text(this, "Bitiş ölçüsü", 12, Ui.MUTED, false));
        practiceBox.addView(z);
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setText("Tempo modu (metronomla, zamanlama kontrollü)");
        sw.setTextColor(Ui.TEXT);
        sw.setChecked(timed);
        sw.setOnCheckedChangeListener((b, v) -> { timed = v; updateLabels(); });
        practiceBox.addView(sw, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
        bpmLabel = Ui.text(this, "", 14, Ui.TEXT, false);
        practiceBox.addView(bpmLabel);
        SeekBar bs = new SeekBar(this);
        bs.setMax(170); bs.setProgress((int) bpm - 30);
        bs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) { bpm = v + 30; updateLabels(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { prog.songBpm.put(key, bpm); prog.save(); }
        });
        practiceBox.addView(bs);
        Button go = Ui.primary(this, "🎸 Bu bölümü çal");
        go.setOnClickListener(v -> startPractice());
        practiceBox.addView(go, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 10));
        exView = new ExerciseView(this);
        practiceBox.addView(exView);
        updateLabels();
    }

    private void updateLabels() {
        if (sectionLabel != null) sectionLabel.setText("Bölüm: ölçü " + (fromM + 1) + " – " + (toM + 1));
        if (bpmLabel != null) bpmLabel.setText(timed ? "Tempo: " + (int) bpm + " BPM (tempo merdiveni: temiz çalınca +5)" : "Bekleme modu: uygulama her doğru notayı bekler, hız yok");
    }

    private void startPractice() {
        Ex e = new Ex();
        e.notes = TabImport.slice(parsed, fromM, toM, 4);
        if (e.notes.isEmpty()) return;
        e.type = timed ? Ex.Type.PLAY_SEQ : Ex.Type.PLAY_NOTES;
        e.bpm = bpm;
        e.strictOctave = true;
        e.title = "Ölçü " + (fromM + 1) + "–" + (toM + 1);
        e.prompt = timed ? "Metronomla çal — ilk hatada durur" : "Notaları sırayla çal";
        exView.stop();
        exView.bind(e, this, 0);
    }

    @Override public AudioIn audio() { return audio; }

    @Override public void withMic(Runnable then) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { then.run(); return; }
        pendingMic = then;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        if (rc == 7 && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED && pendingMic != null) pendingMic.run();
        pendingMic = null;
    }

    @Override public void onResult(Ex ex, boolean pass, String message) {
        if (pass) {
            Sfx.good();
            prog.addXp(5);
            if (timed) {
                bpm = Math.min(200, bpm + 5);
                prog.songBpm.put(key, bpm);
                prog.save();
                updateLabels();
            }
        } else Sfx.bad();
    }

    @Override public void skipMicTasks() { if (exView != null) exView.stop(); }

    @Override protected void onPause() { super.onPause(); if (exView != null) exView.stop(); audio.stop(); }

    @Override protected void onDestroy() { super.onDestroy(); audio.stop(); setResult(RESULT_OK); }
}
