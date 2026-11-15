package com.enoli.perde;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** Günlük hatırlatma (Hook modelindeki dış tetikleyici). */
public class Reminder extends BroadcastReceiver {
    static final String CH = "perde_gunluk";

    public static void schedule(Context c) {
        Progress p = Progress.get(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, Reminder.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!p.reminderOn) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.reminderHour);
        cal.set(Calendar.MINUTE, p.reminderMinute);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_MONTH, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    @Override public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) { schedule(c); return; }
        Progress p = Progress.get(c);
        if (p.lastActiveDay == Progress.today()) return; // bugün zaten çalıştı
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Günlük pratik", NotificationManager.IMPORTANCE_DEFAULT));
        int due = p.dueCount();
        String title = p.displayStreak() > 0 ? "🔥 " + p.displayStreak() + " günlük serin tehlikede!" : "🎸 Gitar zamanı!";
        String text = due > 0 ? due + " tekrar kartı seni bekliyor. Sadece 2 dakika?" : "Gitarını al, sadece bir ders. Küçük adım, büyük ses.";
        PendingIntent open = PendingIntent.getActivity(c, 2, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(text)
                .setContentIntent(open).setAutoCancel(true).build();
        try { nm.notify(1, n); } catch (SecurityException ignored) {}
    }
}
