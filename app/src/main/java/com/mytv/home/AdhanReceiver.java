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
            String azkar = intent.getStringExtra("azkar");
            if (azkar != null) {
                AzkarActivity.launch(ctx, "morning".equals(azkar));
                scheduleNext(ctx);
                return;
            }
            if (name != null) {
                notifyAdhan(ctx, name);
                AdhanActivity.launch(ctx, name); // works when the TV lets us show over other apps
            }
            ctx.sendBroadcast(new Intent(MainActivity.ACTION_ADHAN_NOW).setPackage(ctx.getPackageName()).putExtra("name", name));
        }
        scheduleNext(ctx);
    }

    /** Minutes after the adhan when the azkar screen opens (time to pray first). */
    static final int AZKAR_AFTER = 25;

    /** Sets one exact alarm for the next event: an adhan, or the morning / evening azkar. */
    static void scheduleNext(Context ctx) {
        PrayerTimes pt = new PrayerTimes(BuildConfig.CITY_LAT, BuildConfig.CITY_LON, BuildConfig.CITY_TZ);
        ZoneOffset zone = MainActivity.zone();
        LocalDateTime now = LocalDateTime.now(zone);
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            LocalDate day = now.toLocalDate().plusDays(dayOffset);
            int[] m = pt.minutesFor(day);
            // events of the day in time order: [minute, kind] kind: 0..5 prayer index, 10 morning azkar, 11 evening azkar
            java.util.List<int[]> ev = new java.util.ArrayList<>();
            for (int i = 0; i < m.length; i++) if (PrayerTimes.IS_PRAYER[i]) ev.add(new int[]{m[i], i});
            ev.add(new int[]{m[0] + AZKAR_AFTER, 10});
            ev.add(new int[]{m[3] + AZKAR_AFTER, 11});
            ev.sort((x, y) -> x[0] - y[0]);
            for (int[] e : ev) {
                LocalDateTime at = day.atStartOfDay().plusMinutes(e[0]);
                if (!at.isAfter(now.plusSeconds(30))) continue;
                Intent i2 = new Intent(ctx, AdhanReceiver.class).setAction(ACTION);
                if (e[1] >= 10) i2.putExtra("azkar", e[1] == 10 ? "morning" : "evening");
                else i2.putExtra("name", PrayerTimes.NAMES[e[1]]);
                setAlarm(ctx, at.toInstant(zone).toEpochMilli(), i2);
                return;
            }
        }
    }

    private static void setAlarm(Context ctx, long ms, Intent i2) {
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i2, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        try {
            PendingIntent show = PendingIntent.getActivity(ctx, 2, new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(ms, show), pi); // exact, on the minute
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        }
    }

    private static void notifyAdhan(Context ctx, String name) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        // if the adhan screen can open on top, it plays the adhan itself, so the notification stays silent
        boolean screen = android.provider.Settings.canDrawOverlays(ctx);
        String channel = screen ? CHANNEL + "_quiet" : CHANNEL;
        NotificationChannel ch = new NotificationChannel(channel, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        if (screen) ch.setSound(null, null);
        else ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن موعد أذان " + name)
                .setContentText("حسب توقيت " + BuildConfig.CITY_NAME)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(7, n);
    }
}
