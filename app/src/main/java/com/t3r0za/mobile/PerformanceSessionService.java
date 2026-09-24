package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PerformanceSessionService extends Service {
    static final int NOTIFICATION_ID = 9301;
    static final String CHANNEL_ID = "t3r0za_performance";
    static final long CHECK_INTERVAL_MS = 2000L;
    static final String KEY_PEAK = "peak_refresh_rate";
    static final String KEY_MIN = "min_refresh_rate";
    static final String PREFS = "t3r0za";
    static final String PREF_PEAK_ORIGINAL = "perf_original_peak";
    static final String PREF_MIN_ORIGINAL = "perf_original_min";
    static final String PREF_SAVED = "perf_original_saved";

    final Handler handler = new Handler(Looper.getMainLooper());
    final Set<String> games = new HashSet<>(Arrays.asList(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    ));
    Runnable loop;
    boolean changedForGame = false;
    float appliedRate = 0f;

    int currentThermal() {
        if (Build.VERSION.SDK_INT < 29) return PowerManager.THERMAL_STATUS_NONE;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null ? PowerManager.THERMAL_STATUS_NONE : pm.getCurrentThermalStatus();
    }

    boolean canWriteSettings() {
        return Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this);
    }

    float highestSupportedRefresh() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (dm == null) return 0f;
        Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return 0f;
        float best = display.getRefreshRate();
        if (Build.VERSION.SDK_INT >= 23) {
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getRefreshRate() > best) best = mode.getRefreshRate();
            }
        }
        return best;
    }

    boolean usageAccessAllowed() {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= 29) {
                mode = ops.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName());
            } else {
                mode = ops.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    getPackageName());
            }
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    String lastKnownForegroundPackage = null;

    String foregroundPackage() {
        if (!usageAccessAllowed()) return null;
        try {
            android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return lastKnownForegroundPackage;

            long end = System.currentTimeMillis();
            android.app.usage.UsageEvents events = usm.queryEvents(end - 15 * 60 * 1000L, end);
            if (events == null) return lastKnownForegroundPackage;

            android.app.usage.UsageEvents.Event event =
                new android.app.usage.UsageEvents.Event();

            long lastTime = 0L;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                int type = event.getEventType();
                if (type == android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    lastKnownForegroundPackage = null;
                    continue;
                }

                if (type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 &&
                     type == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)) {
                    if (event.getTimeStamp() >= lastTime) {
                        lastTime = event.getTimeStamp();
                        lastKnownForegroundPackage = event.getPackageName();
                    }
                    continue;
                }

                if (type == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND ||
                    (Build.VERSION.SDK_INT >= 29 &&
                     type == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED)) {
                    if (event.getPackageName() != null &&
                        event.getPackageName().equals(lastKnownForegroundPackage)) {
                        lastKnownForegroundPackage = null;
                    }
                }
            }

            return lastKnownForegroundPackage;
        } catch (Exception e) {
            return lastKnownForegroundPackage;
        }
    }

    void saveOriginalSettings() {
        if (!canWriteSettings()) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_SAVED, false)) return;

        String peak = null;
        String min = null;
        try {
            peak = Settings.System.getString(getContentResolver(), KEY_PEAK);
            min = Settings.System.getString(getContentResolver(), KEY_MIN);
        } catch (Exception ignored) {
        }

        prefs.edit()
            .putString(PREF_PEAK_ORIGINAL, peak)
            .putString(PREF_MIN_ORIGINAL, min)
            .putBoolean(PREF_SAVED, true)
            .apply();
    }

    boolean writeRate(String key, float rate) {
        if (!canWriteSettings() || rate <= 0f) return false;
        try {
            boolean wrote = Settings.System.putString(
                getContentResolver(), key, String.valueOf(rate));
            if (!wrote) return false;
            String verify = Settings.System.getString(getContentResolver(), key);
            return verify != null && !verify.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    void applyForGame() {
        if (!canWriteSettings()) return;

        int thermal = currentThermal();
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            if (changedForGame) restoreOriginalSettings();
            return;
        }

        float best = highestSupportedRefresh();
        if (best <= 0f) return;

        if (changedForGame && Math.abs(appliedRate - best) < 0.5f) return;

        saveOriginalSettings();

        boolean peakOk = writeRate(KEY_PEAK, best);
        boolean minOk = writeRate(KEY_MIN, best);
        if (peakOk || minOk) {
            appliedRate = best;
            changedForGame = true;
            updateNotification("GAME SESSION • " + Math.round(best) + " Hz • THERMAL " + thermalName(thermal));
        }
    }

    String thermalName(int status) {
        if (status == PowerManager.THERMAL_STATUS_NONE) return "NORMAL";
        if (status == PowerManager.THERMAL_STATUS_LIGHT) return "LIGHT";
        if (status == PowerManager.THERMAL_STATUS_MODERATE) return "MODERATE";
        if (status == PowerManager.THERMAL_STATUS_SEVERE) return "SEVERE";
        if (status == PowerManager.THERMAL_STATUS_CRITICAL) return "CRITICAL";
        return "EMERGENCY";
    }

    void restoreOriginalSettings() {
        if (!canWriteSettings()) return;
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(PREF_SAVED, false)) return;

        try {
            String peak = prefs.getString(PREF_PEAK_ORIGINAL, null);
            String min = prefs.getString(PREF_MIN_ORIGINAL, null);

            if (peak == null) {
                Settings.System.putString(getContentResolver(), KEY_PEAK, null);
            } else {
                Settings.System.putString(getContentResolver(), KEY_PEAK, peak);
            }

            if (min == null) {
                Settings.System.putString(getContentResolver(), KEY_MIN, null);
            } else {
                Settings.System.putString(getContentResolver(), KEY_MIN, min);
            }
        } catch (Exception ignored) {
        } finally {
            prefs.edit()
                .remove(PREF_PEAK_ORIGINAL)
                .remove(PREF_MIN_ORIGINAL)
                .putBoolean(PREF_SAVED, false)
                .apply();
        }

        changedForGame = false;
        appliedRate = 0f;
        updateNotification("GAME SESSION • RESTORED");
    }

    void updateNotification(String text) {
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("T3R0ZA PERFORMANCE")
            .setContentText(text)
            .setOngoing(true);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    void startForegroundSafe() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("T3R0ZA PERFORMANCE")
            .setContentText("Game Performance Session فعال است")
            .setOngoing(true);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "T3R0ZA Performance",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("T3R0ZA game performance session");
        nm.createNotificationChannel(channel);
    }

    void tick() {
        String foreground = foregroundPackage();
        boolean isGame = foreground != null && games.contains(foreground);

        if (isGame) {
            applyForGame();
        } else if (changedForGame) {
            restoreOriginalSettings();
        }

        handler.postDelayed(loop, CHECK_INTERVAL_MS);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundSafe();
        loop = this::tick;
        handler.post(loop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (changedForGame) restoreOriginalSettings();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
