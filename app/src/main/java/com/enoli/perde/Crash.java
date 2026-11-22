package com.enoli.perde;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Çökme kaydı: uygulama çökerse hata dosyaya yazılır, bir sonraki açılışta gösterilir. */
public final class Crash {
    private Crash() {}
    private static boolean installed;

    public static void install(Context c) {
        if (installed) return;
        installed = true;
        final File f = new File(c.getApplicationContext().getFilesDir(), "crash.txt");
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (FileOutputStream o = new FileOutputStream(f)) {
                StringWriter sw = new StringWriter();
                String ver = "?";
                try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable ignored) {}
                sw.append("Perde ").append(ver).append(" • Android ").append(String.valueOf(android.os.Build.VERSION.SDK_INT))
                  .append(" • ").append(android.os.Build.MODEL).append(" • ").append(t.getName()).append('\n');
                e.printStackTrace(new PrintWriter(sw));
                o.write(sw.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** Önceki çökme varsa gösterir (kopyala / paylaş). */
    public static void showIfAny(Activity a) {
        File f = new File(a.getFilesDir(), "crash.txt");
        if (!f.exists()) return;
        String txt;
        try { txt = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (Exception e) { txt = "?"; }
        f.delete();
        final String report = txt;
        String shortTxt = report.length() > 1500 ? report.substring(0, 1500) + "…" : report;
        new AlertDialog.Builder(a)
                .setTitle("Perde geçen sefer çöktü")
                .setMessage("Hata kaydı aşağıda. Kopyalayıp geliştiriciye gönderirsen düzeltilir.\n\n" + shortTxt)
                .setPositiveButton("Kopyala", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Perde hata", report));
                })
                .setNeutralButton("Paylaş", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, report);
                    a.startActivity(Intent.createChooser(i, "Hata kaydını paylaş"));
                })
                .setNegativeButton("Kapat", null)
                .show();
    }
}
