// Author: 7ANG0N1N3 — https://github.com/7ang0n1n3/lg-signage
package com.signage.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public class SchedulerService extends Service {

    private static final String TAG           = "SchedulerService";
    private static final long   CHECK_INTERVAL = 30_000L; // 30 seconds

    private Handler  handler;
    private Runnable checker;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(2, buildNotification());
        handler = new Handler();
        checker = new Runnable() {
            @Override public void run() {
                checkSchedule();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(checker);
    }

    private Notification buildNotification() {
        String channelId = "signage_scheduler";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                channelId, "Signage Scheduler", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            return new Notification.Builder(this, channelId)
                .setContentTitle("Signage Scheduler")
                .setContentText("Schedule active")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .build();
        } else {
            return new Notification.Builder(this)
                .setContentTitle("Signage Scheduler")
                .setContentText("Schedule active")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .build();
        }
    }

    @Override public int     onStartCommand(Intent i, int f, int s) { return START_STICKY; }
    @Override public IBinder onBind(Intent i)                        { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(checker);
        super.onDestroy();
    }

    private void checkSchedule() {
        try {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String schedJson = prefs.getString("schedule", "[]");
            JSONArray schedule = new JSONArray(schedJson);
            if (schedule.length() == 0) return;

            Calendar now = Calendar.getInstance();
            // Calendar: 1=Sun..7=Sat -> convert to 0=Sun..6=Sat
            int currentDay     = now.get(Calendar.DAY_OF_WEEK) - 1;
            int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            // Find the active entry: the latest scheduled time <= now that applies today.
            // If none today, look at yesterday's latest (schedule carries over midnight).
            JSONObject best      = null;
            int        bestScore = -1;

            for (int i = 0; i < schedule.length(); i++) {
                JSONObject entry = schedule.getJSONObject(i);
                if (!entry.optBoolean("enabled", true)) continue;

                JSONArray days       = entry.optJSONArray("days");
                int       entryMins  = parseTime(entry.optString("time", "00:00"));
                boolean   dayMatch   = dayMatches(days, currentDay);

                if (dayMatch && entryMins <= currentMinutes && entryMins > bestScore) {
                    bestScore = entryMins;
                    best      = entry;
                }
            }

            // Nothing matched today so far — look at yesterday's last entry
            if (best == null) {
                int yesterday = (currentDay + 6) % 7;
                for (int i = 0; i < schedule.length(); i++) {
                    JSONObject entry = schedule.getJSONObject(i);
                    if (!entry.optBoolean("enabled", true)) continue;
                    JSONArray days     = entry.optJSONArray("days");
                    int       entryMins = parseTime(entry.optString("time", "00:00"));
                    if (dayMatches(days, yesterday) && entryMins > bestScore) {
                        bestScore = entryMins;
                        best      = entry;
                    }
                }
            }

            if (best == null) return;

            // Tracking key includes date so the same entry can fire again the next day
            String dateKey    = now.get(Calendar.YEAR) + "-" + now.get(Calendar.MONTH)
                                + "-" + now.get(Calendar.DAY_OF_MONTH);
            String entryId    = best.optString("id", "");
            String trackingId = entryId + "_" + dateKey;
            String appliedId  = prefs.getString("schedule_applied_id", "");
            if (!entryId.isEmpty() && trackingId.equals(appliedId)) return;

            applyEntry(best, prefs);
            prefs.edit().putString("schedule_applied_id", trackingId).apply();

        } catch (Exception e) {
            Log.e(TAG, "Schedule check error", e);
        }
    }

    private void applyEntry(JSONObject entry, SharedPreferences prefs) throws Exception {
        SharedPreferences.Editor ed = prefs.edit();
        String mode = entry.optString("mode", "worldclock");
        ed.putString("display_mode", mode);

        if ("marquee".equals(mode)) {
            if (entry.has("marquee_text"))      ed.putString ("marquee_text",      entry.getString ("marquee_text"));
            if (entry.has("marquee_speed"))     ed.putInt    ("marquee_speed",     entry.getInt    ("marquee_speed"));
            if (entry.has("marquee_direction")) ed.putString ("marquee_direction", entry.getString ("marquee_direction"));
            if (entry.has("marquee_blink"))     ed.putBoolean("marquee_blink",     entry.getBoolean("marquee_blink"));
            if (entry.has("border_style"))      ed.putString ("border_style",      entry.getString ("border_style"));
            if (entry.has("border_color_mode")) ed.putString ("border_color_mode", entry.getString ("border_color_mode"));
            if (entry.has("border_shape"))      ed.putString ("border_shape",      entry.getString ("border_shape"));
            if (entry.has("marquee_color_hex")) {
                String hex = entry.getString("marquee_color_hex").replace("#", "");
                ed.putInt("marquee_color", (int)(0xFF000000L | Long.parseLong(hex, 16)));
            }
            if (entry.has("border_color_hex")) {
                String hex = entry.getString("border_color_hex").replace("#", "");
                ed.putInt("border_color", (int)(0xFF000000L | Long.parseLong(hex, 16)));
            }
        } else if ("crime_eyes".equals(mode)) {
            if (entry.has("crime_eyes_gif")) ed.putString("crime_eyes_gif", entry.getString("crime_eyes_gif"));
        }

        ed.apply();
        sendBroadcast(new Intent("com.signage.app.SETTINGS_CHANGED"));
        Log.i(TAG, "Schedule applied: mode=" + mode + " time=" + entry.optString("time"));
    }

    private static int parseTime(String t) {
        try {
            String[] p = t.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return 0; }
    }

    private static boolean dayMatches(JSONArray days, int day) throws Exception {
        if (days == null || days.length() == 0) return true; // every day
        for (int i = 0; i < days.length(); i++) {
            if (days.getInt(i) == day) return true;
        }
        return false;
    }
}
