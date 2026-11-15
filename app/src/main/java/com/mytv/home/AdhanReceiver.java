package com.mytv.home;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Fires at each prayer time: shows a notification with a sound, then schedules the next one. */
public class AdhanReceiver extends BroadcastReceiver {
    static final String ACTION = "com.mytv.home.ADHAN";
    private static final String CHANNEL = "adhan";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            String name = intent.getStringExtra("name");
            if (name != null) notifyAdhan(ctx, name);
            ctx.sendBroadcast(new Intent(MainActivity.ACTION_ADHAN_NOW).setPackage(ctx.getPackageName()).putExtra("name", name));
        }
        scheduleNext(ctx);
    }

    static void scheduleNext(Context ctx) {
        PrayerTimes pt = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ);
        ZoneOffset zone = MainActivity.zone();
        LocalDateTime now = LocalDateTime.now(zone);
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            LocalDate day = now.toLocalDate().plusDays(dayOffset);
            int[] m = pt.minutesFor(day);
            for (int i = 0; i < m.length; i++) {
                if (!PrayerTimes.IS_PRAYER[i]) continue;
                LocalDateTime at = day.atStartOfDay().plusMinutes(m[i]);
                if (at.isAfter(now.plusSeconds(30))) {
                    long ms = at.toInstant(zone).toEpochMilli();
                    Intent i2 = new Intent(ctx, AdhanReceiver.class).setAction(ACTION).putExtra("name", PrayerTimes.NAMES[i]);
                    PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i2,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
                    return;
                }
            }
        }
    }

    private static void notifyAdhan(Context ctx, String name) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText("حسب توقيت " + BuildConfig.CITY_NAME)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(7, n);
    }
}
