package com.enoli.perde;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Şarkı sayfası: tab/nota içe aktarma (dosya, pano, metin), akor sözlüğüyle düzenlenmiş tab,
 * "Notaya çevir" ve RitimKulak yöntemiyle adım adım öğrenme (dinle → ritmi dokun → notaları bul → tempoda çal).
 */
public class SongActivity extends Activity implements ExerciseView.Host {
    private static final int PICK_FILE = 41;
    private Progress prog;
    private Curriculum.Song song;
    private int customIdx = -1;
    private String key;
    private AudioIn audio;
    private ExerciseView exView;
    private TabImport.Result parsed;
    private int fromM = 0, toM = 0;
    private float bpm = 60;
    private LinearLayout practiceBox, editorBox;
    private EditText editor;
    private Runnable pendingMic;
    private TextView sectionLabel, bpmLabel, titleView;
    private ScoreView preview;
    private SeekBar seekA, seekZ;
    private boolean staffMode = false, showNames = true;
    private int lastStep = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private TextView partLabel;

    public static void open(Activity a, String songId, String customIdx) {
        Intent i = new Intent(a, SongActivity.class);
        i.putExtra("song", songId);
        i.putExtra("custom", customIdx);
        a.startActivityForResult(i, 2);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Crash.install(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Ui.BG);
        prog = Progress.get(this);
        Sfx.enabled = prog.sfxOn;
        audio = new AudioIn();
        Curriculum cur = Curriculum.get(this);
        Intent in = getIntent();
        String sid = in.getStringExtra("song");
        String cid = in.getStringExtra("custom");
        boolean shared = Intent.ACTION_SEND.equals(in.getAction()) || Intent.ACTION_VIEW.equals(in.getAction());
        if (shared) {
            prog.customSongs.add(new String[]{"Paylaşılan şarkı", ""});
            prog.save();
            cid = String.valueOf(prog.customSongs.size() - 1);
        }
        if (sid != null) song = cur.songByKey(sid);
        if (cid != null) customIdx = Integer.parseInt(cid);
        if (song == null && (customIdx < 0 || customIdx >= prog.customSongs.size())) { finish(); return; }
        key = song != null ? song.id() : "custom" + customIdx;
        staffMode = prog.songTabs.containsKey("view:" + key) && "nota".equals(prog.songTabs.get("view:" + key));

        ScrollView sv = new ScrollView(this);
        scroll = sv;
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
            col.addView(Ui.text(this, "Ünite " + (song.unitRef.index + 1) + " • " + song.unitRef.title, 13, Ui.PURPLE, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
            col.addView(Ui.text(this, song.title, 26, Ui.TEXT, true));
            col.addView(Ui.text(this, song.artist + "  •  Zorluk " + song.difficulty + "/5" + (song.suggested ? "  •  öneri" : ""), 14, Ui.MUTED, false));
            LinearLayout info = Ui.card(this);
            info.addView(Ui.text(this, "Bu ünitede çalacağın parça: " + song.part, 16, Ui.TEXT, true));
            info.addView(Ui.text(this, "Öğreneceklerin: " + song.skills, 14, Ui.MUTED, false));
            partLabel = Ui.text(this, "", 14, Ui.GREEN, true);
            info.addView(partLabel, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 0));
            info.addView(Ui.text(this, "Şarkı için gereken dersler (Yol'dakiyle aynı, ilerleme ortak):", 14, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 2));
            info.addView(Ui.text(this, "💡 " + song.tip, 14, Ui.YELLOW, false), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 8));
            for (String id : song.prereqs) {
                Curriculum.Lesson l = cur.byId.get(id.trim());
                if (l == null) continue;
                boolean d = prog.isDone(l.id);
                TextView t = Ui.text(this, (d ? "✓ " : "○ ") + l.title, 14, d ? Ui.GREEN : Ui.TEXT, false);
                t.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
                if (!d) t.setOnClickListener(v -> LessonActivity.start(this, "lesson", l.id));
                info.addView(t);
            }
            col.addView(info, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 0));
        } else {
            titleView = Ui.text(this, prog.customSongs.get(customIdx)[0], 26, Ui.TEXT, true);
            titleView.setOnLongClickListener(v -> { rename(); return true; });
            col.addView(titleView, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 0));
            col.addView(Ui.text(this, "Adı değiştirmek için basılı tut", 12, Ui.MUTED, false));
        }

        // ---- kaynak
        col.addView(Ui.text(this, "Tab / nota getir", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 18, 4));
        col.addView(Ui.text(this,
                "Songsterr gibi siteler kopyalamaya izin vermiyor; dosya ile getirmek hem daha kolay hem ritmi de taşır:\n"
                        + "• Guitar Pro dosyası (.gp, .gp5, .gp4, .gp3) — Ultimate Guitar'ın \"Guitar Pro\" tab'ları, GProTab vb.\n"
                        + "• MusicXML (.musicxml, .mxl, .xml) — MuseScore.com'dan indir, ya da MuseScore/TuxGuitar'dan dışa aktar\n"
                        + "• Düz metin tab (.txt) ya da kopyalayabildiğin her ASCII tab\n"
                        + "Başka bir uygulamadan \"Paylaş → Perde\" ile de gönderebilirsin.", 13, Ui.MUTED, false));
        LinearLayout row = Ui.hbox(this);
        Button file = Ui.button(this, "📂 Dosya aç", Ui.BLUE, Ui.BLUE_D);
        file.setTextSize(15);
        file.setOnClickListener(v -> pickFile());
        Button paste = Ui.secondary(this, "📋 Yapıştır");
        paste.setTextSize(15);
        paste.setOnClickListener(v -> pasteClipboard());
        LinearLayout.LayoutParams w1 = Ui.weight(1); w1.rightMargin = Ui.dp(this, 6);
        row.addView(file, w1);
        row.addView(paste, Ui.weight(1));
        col.addView(row, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 4));
        TextView toggleEd = Ui.text(this, "✏️ Tab'ı elle yaz / düzenle", 15, Ui.BLUE, true);
        toggleEd.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        col.addView(toggleEd);
        editorBox = Ui.vbox(this);
        editorBox.setVisibility(View.GONE);
        toggleEd.setOnClickListener(v -> editorBox.setVisibility(editorBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(12);
        editor.setTextColor(Ui.TEXT);
        editor.setHintTextColor(Ui.MUTED);
        editor.setHint("e|-----0---|\nB|---1-----|\nG|-0-------|\nD|---------|\nA|-3-------|\nE|---------|");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setMinLines(7);
        editor.setGravity(Gravity.TOP);
        editor.setHorizontallyScrolling(true);
        editor.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 12)));
        editor.setPadding(p / 2, p / 2, p / 2, p / 2);
        editorBox.addView(Ui.text(this, "Satırlar e B G D A E sırasıyla; tel adı (ör. D| ya da Eb|) yazarsan akort da okunur. Aynı sütundaki perdeler akor sayılır ve adı bulunur.", 12, Ui.MUTED, false));
        editorBox.addView(editor, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 6));
        Button imp = Ui.button(this, "📥 Metni içe aktar", Ui.BLUE, Ui.BLUE_D);
        imp.setOnClickListener(v -> useText(editor.getText().toString()));
        editorBox.addView(imp);
        col.addView(editorBox);

        practiceBox = Ui.vbox(this);
        col.addView(practiceBox, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 0));

        String existing = stored();
        if (existing != null && !existing.trim().isEmpty()) {
            if (!existing.startsWith("#perde1")) editor.setText(existing);
            importStored(existing);
        } else if (shared) handleShare(in);
        else editorBox.setVisibility(View.GONE);
    }

    private String stored() { return song != null ? prog.songTabs.get(key) : prog.customSongs.get(customIdx)[1]; }

    private void store(String txt) {
        if (song != null) prog.songTabs.put(key, txt); else prog.customSongs.get(customIdx)[1] = txt;
        prog.unlock("tab");
        prog.save();
    }

    private void rename() {
        EditText et = new EditText(this);
        et.setText(prog.customSongs.get(customIdx)[0]);
        new AlertDialog.Builder(this).setTitle("Şarkı adı").setView(et).setPositiveButton("Kaydet", (d, w) -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty()) return;
            prog.customSongs.get(customIdx)[0] = t; prog.save();
            titleView.setText(t);
        }).setNegativeButton("Vazgeç", null).show();
    }

    // ---------------------------------------------------------------- içe aktarma
    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, PICK_FILE); }
        catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.setType("*/*");
            startActivityForResult(Intent.createChooser(g, "Dosya seç"), PICK_FILE);
        }
    }

    @Override protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        if (rc == PICK_FILE && res == RESULT_OK && data != null && data.getData() != null) loadUri(data.getData());
    }

    private void handleShare(Intent in) {
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) { loadUri(u); return; }
            CharSequence t = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (t != null) {
                String s = t.toString();
                if (s.trim().startsWith("http") && !s.contains("\n")) {
                    showError("Paylaşılan şey bir bağlantı (" + s.trim() + "). Bağlantıdan tab okunamıyor; sayfadan Guitar Pro / MusicXML dosyasını indirip \"Dosya aç\" ile seç ya da tab metnini kopyala.");
                    return;
                }
                editor.setText(s);
                useText(s);
            }
        } else if (in.getData() != null) loadUri(in.getData());
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData cd = cm == null ? null : cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) { showError("Panoda bir şey yok. Önce tab metnini kopyala."); return; }
        CharSequence t = cd.getItemAt(0).coerceToText(this);
        if (t == null || t.toString().trim().isEmpty()) { showError("Panoda metin yok."); return; }
        editor.setText(t);
        useText(t.toString());
    }

    private void useText(String txt) {
        TabImport.Result r = TabImport.parseAny(txt);
        if (r.error != null) { showError(r.error); editorBox.setVisibility(View.VISIBLE); return; }
        store(txt);
        show(r);
    }

    private void importStored(String txt) {
        TabImport.Result r = TabImport.parseAny(txt);
        if (r.error != null) { showError(r.error); return; }
        show(r);
    }

    private void loadUri(Uri u) {
        byte[] data;
        try (InputStream is = getContentResolver().openInputStream(u)) {
            if (is == null) throw new IllegalStateException("açılamadı");
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) { o.write(buf, 0, n); if (o.size() > 25 * 1024 * 1024) throw new IllegalStateException("dosya çok büyük"); }
            data = o.toByteArray();
        } catch (Exception e) { showError("Dosya okunamadı: " + e.getMessage()); return; }
        if (TabPack.isPack(data)) {
            TabPack.Result res = new TabPack.Result();
            TabPack.importFile(prog, Curriculum.get(this), "paket.zip", data, res);
            showError("📦 Bu bir tab paketi: " + res.ok + " şarkının tab'ı Şarkı Yolu'na yüklendi. Geri dönüp şarkıları açabilirsin.");
            String mine = stored();
            if (mine != null && !mine.trim().isEmpty()) importStored(mine);
            return;
        }
        SongData sd;
        try { sd = SongFile.load(data); }
        catch (Exception e) { showError(e.getMessage() == null ? "Dosya çözülemedi." : "Dosya çözülemedi: " + e.getMessage()); return; }
        if (sd == null) { String t = new String(data, StandardCharsets.UTF_8); editor.setText(t); useText(t); return; }
        List<Integer> g = sd.guitarTracks();
        if (g.isEmpty()) { showError("Dosyada gitarla çalınacak parça bulunamadı."); return; }
        if (g.size() == 1) { useSongData(sd, g.get(0)); return; }
        String[] names = new String[g.size()];
        for (int i = 0; i < g.size(); i++) {
            SongData.Track t = sd.tracks.get(g.get(i));
            int cnt = 0; for (SongData.Ev e : t.evs) cnt++;
            names[i] = (t.name.isEmpty() ? "Parça " + (g.get(i) + 1) : t.name) + "  (" + cnt + " vuruş" + (t.strings != 6 ? ", " + t.strings + " tel" : "") + ")";
        }
        new AlertDialog.Builder(this).setTitle("Hangi parçayı çalacaksın?").setItems(names, (d, w) -> useSongData(sd, g.get(w))).show();
    }

    private void useSongData(SongData sd, int track) {
        TabImport.Result r = sd.toResult(track);
        if (r.error != null) { showError(r.error); return; }
        store(TabImport.toText(r));
        if (song == null && customIdx >= 0) {
            String cur = prog.customSongs.get(customIdx)[0];
            if ((cur.equals("Paylaşılan şarkı") || cur.isEmpty()) && !r.title.isEmpty()) {
                String t = r.title + (r.artist.isEmpty() ? "" : " — " + r.artist);
                prog.customSongs.get(customIdx)[0] = t; prog.save();
                if (titleView != null) titleView.setText(t);
            }
        }
        editorBox.setVisibility(View.GONE);
        show(r);
    }

    private void showError(String msg) {
        practiceBox.removeAllViews();
        practiceBox.addView(Ui.text(this, msg, 14, Ui.RED, true));
    }

    // ---------------------------------------------------------------- şarkı görünümü
    private void show(TabImport.Result r) {
        stopAll();
        parsed = r;
        practiceBox.removeAllViews();
        if (song != null) { prog.songTabs.put("len:" + key, String.valueOf(r.measures)); prog.save(); }
        updatePart();
        fromM = Math.min(prog.songTabs.containsKey("from:" + key) ? parseInt(prog.songTabs.get("from:" + key)) : 0, r.measures - 1);
        toM = Math.min(Math.max(fromM, fromM + 1), r.measures - 1);
        if (!prog.songBpm.containsKey(key)) bpm = r.tempo > 0 ? Math.max(40, Math.round(r.tempo * 0.6f / 5) * 5) : 60;
        else bpm = prog.songBpm.get(key);

        LinearLayout info = Ui.card(this);
        StringBuilder sb = new StringBuilder("✓ " + r.format);
        if (!r.trackName.isEmpty()) sb.append(" • ").append(r.trackName);
        sb.append("\n").append(r.measures).append(" ölçü, ").append(r.notes.size()).append(" vuruş");
        int ch = r.chordCount();
        if (ch > 0) sb.append(", ").append(ch).append(" akor adlandırıldı");
        info.addView(Ui.text(this, sb.toString(), 14, Ui.GREEN, true));
        int[] tun = r.tuning;
        TextView tt = Ui.text(this, "Akort: " + TabImport.tuningName(tun), 14, r.standardTuning() ? Ui.MUTED : Ui.YELLOW, !r.standardTuning());
        info.addView(tt, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 0));
        if (!r.standardTuning()) info.addView(Ui.text(this, "⚠ Gitarını bu akorda ayarla; uygulama notaları bu akora göre bekler.", 13, Ui.YELLOW, false));
        if (r.tempo > 0) info.addView(Ui.text(this, "Orijinal tempo: " + Math.round(r.tempo) + " BPM", 13, Ui.MUTED, false));
        practiceBox.addView(info);

        // görünüm seçimi
        LinearLayout modes = Ui.hbox(this);
        Button tabB = Ui.button(this, "🎸 Tab", staffMode ? Ui.SURFACE2 : Ui.PURPLE, staffMode ? 0xFF15161F : Ui.darker(Ui.PURPLE, 0.7f));
        Button stfB = Ui.button(this, "🎼 Notaya çevir", staffMode ? Ui.PURPLE : Ui.SURFACE2, staffMode ? Ui.darker(Ui.PURPLE, 0.7f) : 0xFF15161F);
        tabB.setTextSize(15); stfB.setTextSize(15);
        tabB.setOnClickListener(v -> setStaff(false));
        stfB.setOnClickListener(v -> setStaff(true));
        LinearLayout.LayoutParams w1 = Ui.weight(1); w1.rightMargin = Ui.dp(this, 6);
        modes.addView(tabB, w1); modes.addView(stfB, Ui.weight(1));
        practiceBox.addView(modes, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 4));
        practiceBox.addView(Ui.text(this, staffMode
                ? "Nota görünümü: tek sesler porteye yazıldı (gitar bir oktav pes çalar, anahtarın altındaki 8 bunu gösterir). Akorlar portenin üstünde adıyla, ritim çizgisiyle."
                : "Akor sütunları silinip yerine akorun adı yazıldı (mor). Akorun nasıl basılacağını bilmiyorsan adından bul; tek sesler tab olarak kaldı.", 12, Ui.MUTED, false));
        if (staffMode) {
            CheckBox cb = new CheckBox(this);
            cb.setText("Nota adlarını altına yaz (okumayı öğrenirken)");
            cb.setTextColor(Ui.TEXT);
            cb.setChecked(showNames);
            cb.setOnCheckedChangeListener((b2, v) -> { showNames = v; preview.names = v; preview.setMode(preview.mode); });
            practiceBox.addView(cb);
        }
        preview = new ScoreView(this);
        preview.mode = staffMode ? ScoreView.Mode.STAFF : ScoreView.Mode.TAB;
        preview.names = showNames;
        float[] st = new float[r.measures], ln = new float[r.measures];
        for (int m = 0; m < r.measures; m++) { st[m] = r.start(m); ln[m] = r.len(m); }
        preview.set(r.notes, st, ln, 1);
        preview.onMeasure = m -> {
            fromM = m; toM = Math.min(parsed.measures - 1, Math.max(m, m + 1));
            if (seekA != null) { seekA.setProgress(fromM); seekZ.setProgress(toM); }
            updateLabels();
        };
        LinearLayout pv = Ui.vbox(this);
        pv.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 14)));
        pv.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        pv.addView(preview);
        practiceBox.addView(pv, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 4));
        practiceBox.addView(Ui.text(this, "Bir ölçüye dokununca bölüm oradan başlar.", 12, Ui.MUTED, false));

        // bölüm
        sectionLabel = Ui.text(this, "", 16, Ui.TEXT, true);
        practiceBox.addView(sectionLabel, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 14, 0));
        seekA = new SeekBar(this); seekZ = new SeekBar(this);
        seekA.setMax(Math.max(0, r.measures - 1)); seekZ.setMax(Math.max(0, r.measures - 1));
        seekA.setProgress(fromM); seekZ.setProgress(toM);
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) {
                if (!f) return;
                fromM = seekA.getProgress(); toM = Math.max(fromM, seekZ.getProgress());
                if (toM - fromM > 7) { if (s == seekA) toM = fromM + 7; else fromM = toM - 7; }
                updateLabels();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { seekA.setProgress(fromM); seekZ.setProgress(toM); }
        };
        seekA.setOnSeekBarChangeListener(l); seekZ.setOnSeekBarChangeListener(l);
        practiceBox.addView(Ui.text(this, "Başlangıç ölçüsü", 12, Ui.MUTED, false));
        practiceBox.addView(seekA);
        practiceBox.addView(Ui.text(this, "Bitiş ölçüsü (en fazla 8 ölçü)", 12, Ui.MUTED, false));
        practiceBox.addView(seekZ);
        bpmLabel = Ui.text(this, "", 14, Ui.TEXT, false);
        practiceBox.addView(bpmLabel, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 0));
        SeekBar bs = new SeekBar(this);
        bs.setMax(170); bs.setProgress((int) bpm - 30);
        bs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean f) { if (f) { bpm = v + 30; updateLabels(); } }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { prog.songBpm.put(key, bpm); prog.save(); }
        });
        practiceBox.addView(bs);

        if (song != null) {
            practiceBox.addView(Ui.text(this, "Ünitenin parçasını işaretle (" + song.part + "): bu ölçüleri tempoda temiz çalınca şarkı bu ünite için öğrenilmiş sayılır.", 12, Ui.MUTED, false), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 10, 4));
            LinearLayout pr = Ui.hbox(this);
            Button pa = Ui.secondary(this, "📌 Parça başı");
            Button pz = Ui.secondary(this, "📌 Parça sonu");
            pa.setTextSize(13); pz.setTextSize(13);
            pa.setOnClickListener(v -> { int[] rg = SongProgress.part(prog, song, parsed.measures); SongProgress.setPart(prog, song, fromM, Math.max(fromM, rg[1])); updatePart(); });
            pz.setOnClickListener(v -> { int[] rg = SongProgress.part(prog, song, parsed.measures); SongProgress.setPart(prog, song, Math.min(rg[0], toM), toM); updatePart(); });
            LinearLayout.LayoutParams wp = Ui.weight(1); wp.rightMargin = Ui.dp(this, 6);
            pr.addView(pa, wp); pr.addView(pz, Ui.weight(1));
            practiceBox.addView(pr);
        }

        // öğrenme adımları
        practiceBox.addView(Ui.text(this, "Bu bölümü öğren", 20, Ui.TEXT, true), Ui.lpm(this, Ui.MATCH, Ui.WRAP, 16, 2));
        practiceBox.addView(Ui.text(this, "Kulak → ritim → perde → tempo. Her adımı sırayla geç; aşağıdaki kayan şeritte çalacağın her nota adıyla, akorlar adıyla gelir.", 13, Ui.MUTED, false));
        String unitTopic = song != null ? song.unitRef.title.toLowerCase() : "şarkının teorisi";
        String[][] steps = {
                {"👂 1. Dinle", "Bölümü metronomla dinle, şeridi takip et"},
                {"🧠 2. Şarkıdan teori", "Bu bölümün gerçek notalarından " + unitTopic + " soruları"},
                {"👏 3. Ritmi dokun", "Sadece zamanlama: her nota başladığında dokun"},
                {"🔎 4. Notaları bul", "Bekleme modu: doğru notayı/akoru basınca ilerler"},
                {"🎸 5. Tempoda çal", "Metronomla, ilk hatada durur; temiz çalınca +5 BPM"},
        };
        int[] codes = {0, 5, 1, 2, 3};
        for (int i = 0; i < steps.length; i++) {
            final int k = codes[i];
            Button bt = i == 4 ? Ui.primary(this, steps[i][0]) : Ui.button(this, steps[i][0], i == 1 ? Ui.PURPLE : Ui.BLUE, i == 1 ? Ui.darker(Ui.PURPLE, 0.7f) : Ui.BLUE_D);
            bt.setTextSize(16);
            bt.setOnClickListener(v -> startStep(k));
            practiceBox.addView(bt, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 8, 0));
            practiceBox.addView(Ui.text(this, steps[i][1], 12, Ui.MUTED, false));
        }
        exView = new ExerciseView(this);
        practiceBox.addView(exView, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 12, 0));
        updateLabels();
    }

    private void updatePart() {
        if (partLabel == null || song == null) return;
        int total = parsed != null ? parsed.measures : SongProgress.total(prog, song);
        if (total <= 0) { partLabel.setText("Tab'ı yükleyince parçayı işaretleyip çalışmaya başla."); partLabel.setTextColor(Ui.YELLOW); return; }
        int[] rg = SongProgress.part(prog, song, total);
        float f = SongProgress.learned(prog, song);
        String need = parsed != null && parsed.tempo > 0 ? " (en az " + Math.round(Math.min(parsed.tempo * 0.8f, 180)) + " BPM ile)" : "";
        partLabel.setText((f >= 0.9f ? "✓ Öğrenildi! " : "Öğrenilen: %" + Math.round(f * 100) + "  •  ") + "parça ölçü " + (rg[0] + 1) + "–" + (rg[1] + 1) + need);
        partLabel.setTextColor(f >= 0.9f ? Ui.GREEN : Ui.TEXT);
        if (preview != null) { preview.selFrom = fromM; preview.selTo = toM; preview.invalidate(); }
    }

    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    private void setStaff(boolean v) {
        if (v == staffMode) return;
        staffMode = v;
        prog.songTabs.put("view:" + key, v ? "nota" : "tab");
        prog.save();
        int f = fromM, t = toM;
        show(parsed);
        fromM = f; toM = t;
        if (seekA != null) { seekA.setProgress(fromM); seekZ.setProgress(toM); }
        updateLabels();
    }

    private void updateLabels() {
        if (sectionLabel != null) sectionLabel.setText("Bölüm: ölçü " + (fromM + 1) + " – " + (toM + 1));
        if (preview != null) { preview.selFrom = fromM; preview.selTo = toM; preview.invalidate(); }
        String target = parsed != null && parsed.tempo > 0 ? "  •  hedef " + Math.round(parsed.tempo) : "";
        if (bpmLabel != null) bpmLabel.setText("Tempo: " + (int) bpm + " BPM" + target);
    }

    private Ex sectionEx() {
        Ex e = new Ex();
        e.notes = TabImport.slice(parsed, fromM, toM, 4);
        int n = toM - fromM + 1;
        e.measureStarts = new float[n]; e.measureLens = new float[n];
        float a = parsed.start(fromM);
        for (int i = 0; i < n; i++) { e.measureStarts[i] = parsed.start(fromM + i) - a; e.measureLens[i] = parsed.len(fromM + i); }
        e.firstMeasure = fromM + 1;
        e.beatsPerBar = Math.max(1, Math.round(parsed.len(fromM)));
        e.songLane = true;
        e.scoreView = staffMode;
        e.strictOctave = true;
        e.bpm = bpm;
        e.title = "Ölçü " + (fromM + 1) + "–" + (toM + 1);
        return e;
    }

    private void startStep(int k) {
        if (parsed == null) return;
        stopAll();
        prog.songTabs.put("from:" + key, String.valueOf(fromM));
        Ex e = sectionEx();
        if (e.notes.isEmpty()) { exView.removeAllViews(); exView.addView(Ui.text(this, "Bu bölümde nota yok (sus). Başka ölçü seç.", 14, Ui.YELLOW, true)); return; }
        lastStep = k;
        switch (k) {
            case 0: listen(e); return;
            case 5: drills = SongDrills.make(parsed, e.notes, song != null ? song.unit : null, 6, new java.util.Random());
                drillIdx = 0; drillOk = 0; nextDrill(); return;
            case 1:
                e.type = Ex.Type.RHYTHM; e.tapMode = true; e.tolMs = 150;
                e.prompt = "Ritmi dokun";
                break;
            case 2:
                e.type = Ex.Type.PLAY_NOTES;
                e.prompt = "Notaları sırayla bul — acele yok";
                break;
            default:
                e.type = Ex.Type.PLAY_SEQ;
                e.prompt = "Metronomla çal — ilk hatada durur";
        }
        exView.stop();
        exView.bind(e, this, 0);
        h.postDelayed(() -> scroll.smoothScrollTo(0, practiceBox.getTop() + exView.getTop() - Ui.dp(this, 8)), 120);
    }

    // ---------------------------------------------------------------- 2. şarkıdan teori
    private List<Ex> drills;
    private int drillIdx, drillOk;

    private void nextDrill() {
        exView.stop();
        if (drills == null || drillIdx >= drills.size()) {
            exView.removeAllViews();
            exView.setOrientation(LinearLayout.VERTICAL);
            int n = drills == null ? 0 : drills.size();
            exView.addView(Ui.text(this, "🧠 " + drillOk + "/" + n + " doğru" + (drillOk == n ? " — bu bölümün teorisini biliyorsun!" : ". Yanlışları tekrar çözmek için yeniden başlat."), 16, drillOk == n ? Ui.GREEN : Ui.YELLOW, true));
            if (drillOk == n && n > 0) { prog.addXp(5); prog.save(); }
            return;
        }
        Ex e = drills.get(drillIdx);
        e.title = "Şarkıdan teori " + (drillIdx + 1) + "/" + drills.size();
        exView.bind(e, this, 0);
        h.postDelayed(() -> scroll.smoothScrollTo(0, practiceBox.getTop() + exView.getTop() - Ui.dp(this, 8)), 120);
    }

    // ---------------------------------------------------------------- 1. dinle
    private Runnable listenTick;

    private void listen(Ex e) {
        exView.stop();
        exView.removeAllViews();
        exView.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, "👂 Dinle: " + e.title + " • " + Math.round(bpm) + " BPM", 16, Ui.TEXT, true);
        exView.addView(t);
        ScoreView sc = new ScoreView(this);
        sc.mode = staffMode ? ScoreView.Mode.STAFF : ScoreView.Mode.TAB;
        sc.names = showNames;
        sc.set(e.notes, e.measureStarts, e.measureLens, e.firstMeasure);
        exView.addView(sc, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 6));
        Views.Timeline tl = new Views.Timeline(this);
        tl.beatsPerBar = e.beatsPerBar; tl.countIn = e.beatsPerBar;
        List<Views.Timeline.Block> blocks = new ArrayList<>();
        float tEnd = 0;
        for (Ex.Note n : e.notes) {
            Views.Timeline.Block b = new Views.Timeline.Block();
            b.start = n.beat; b.dur = Math.max(0.12f, n.dur);
            if (n.chord != null) { b.label = n.chord; b.sub = "akor"; b.chord = true; }
            else { b.label = ScoreView.noteName(n.midi); b.sub = n.string > 0 ? n.string + ". tel " + n.fret : ""; }
            blocks.add(b);
            tEnd = Math.max(tEnd, n.beat + n.dur);
        }
        for (int i = 0; i + 1 < blocks.size(); i++) blocks.get(i).dur = Math.min(blocks.get(i).dur, blocks.get(i + 1).start - blocks.get(i).start);
        tl.setBlocks(blocks);
        exView.addView(tl, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 4, 4));
        Button again = Ui.secondary(this, "🔁 Tekrar dinle");
        again.setOnClickListener(v -> startStep(0));
        exView.addView(again, Ui.lpm(this, Ui.MATCH, Ui.WRAP, 6, 0));
        h.postDelayed(() -> scroll.smoothScrollTo(0, practiceBox.getTop() + exView.getTop() - Ui.dp(this, 8)), 120);
        // ses: 1 ölçü sayım + notalar
        float spb = 60f / bpm;
        int count = e.beatsPerBar;
        float end = 0;
        for (float s : e.measureLens) end += s;
        List<AudioOut.Ev> evs = new ArrayList<>();
        for (int b = 0; b < count + (int) Math.ceil(end); b++) evs.add(new AudioOut.Ev(b < count ? 93 : 88, b * spb, 0.05f));
        for (Ex.Note n : e.notes) {
            int[] ms = ScoreView.midis(n);
            java.util.Arrays.sort(ms);
            for (int i = 0; i < ms.length; i++) evs.add(new AudioOut.Ev(ms[i], (count + n.beat) * spb + i * 0.012f, Math.max(0.15f, n.dur * spb)));
        }
        float[] pcm = AudioOut.render(evs);
        AudioOut.play(pcm);
        final long t0 = System.nanoTime() + 60_000_000L;
        final float total = count + Math.max(end, tEnd) + 0.5f;
        tl.started = true;
        listenTick = new Runnable() {
            public void run() {
                float beat = (System.nanoTime() - t0) / 1e9f / spb - count;
                tl.beatPos = beat;
                tl.bigText = beat >= 0 && beat < 1 ? "ŞİMDİ" : null;
                for (int i = 0; i < e.notes.size(); i++) if (e.notes.get(i).beat <= beat && i < tl.state.length) tl.state[i] = 1;
                tl.invalidate();
                sc.cursorBeat = Math.max(0, beat); sc.invalidate();
                if (beat + count < total) h.postDelayed(this, 16);
                else { sc.cursorBeat = -1; sc.invalidate(); }
            }
        };
        h.post(listenTick);
    }

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        AudioOut.stopPlayback();
        if (exView != null) exView.stop();
    }

    // ---------------------------------------------------------------- Host
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
        if (lastStep == 5) {
            if (pass) { drillOk++; Sfx.good(); } else Sfx.bad();
            drillIdx++;
            h.postDelayed(this::nextDrill, pass ? 1100 : 3200);
            return;
        }
        if (pass && lastStep == 3 && song != null && parsed != null && (parsed.tempo <= 0 || bpm >= Math.min(parsed.tempo * 0.8f, 180) - 0.5f)) {
            SongProgress.addPassed(prog, song, fromM, toM);
            updatePart();
        }
        if (pass) {
            Sfx.good();
            prog.addXp(lastStep == 3 ? 8 : 4);
            if (lastStep == 3) {
                float cap = parsed != null && parsed.tempo > 0 ? Math.max(parsed.tempo, 60) : 220;
                bpm = Math.min(cap, bpm + 5);
                prog.songBpm.put(key, bpm);
                updateLabels();
            }
            prog.save();
        } else Sfx.bad();
    }

    @Override public void skipMicTasks() { if (exView != null) exView.stop(); }

    @Override protected void onPause() { super.onPause(); stopAll(); if (audio != null) audio.stop(); }

    @Override protected void onDestroy() { super.onDestroy(); if (audio != null) audio.stop(); setResult(RESULT_OK); }
}
