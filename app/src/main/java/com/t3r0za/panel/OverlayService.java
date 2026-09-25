package com.t3r0za.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private WindowManager wm;
    private View panel;
    private WindowManager.LayoutParams params;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(1001, notification());
        showPanel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("t3r0za", "PANEL T3R0ZA", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "t3r0za")
                : new Notification.Builder(this);
        return b.setContentTitle("PANEL T3R0ZA")
                .setContentText("مینی پنل فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .build();
    }

    private TextView text(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        return t;
    }

    private void showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundColor(Color.rgb(18,24,39));

        TextView head = text("⚡ T3R0ZA   ⠿", 16);
        head.setTextColor(Color.rgb(102,227,255));
        box.addView(head, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView close = text("✕ بستن پنل", 12);
        box.addView(close, new LinearLayout.LayoutParams(-1, dp(32)));
        close.setOnClickListener(v -> stopSelf());

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(text("⚡ عملکرد", 14));
        content.addView(text("نرخ نوسازی: " + refreshRate() + " Hz\nFPS بازی را بدون ابزار اختصاصی نمی‌شود صادقانه اندازه گرفت.", 12));

        content.addView(text("👆 تاچ و ورودی", 14));
        content.addView(text("پاسخ ورودی: پایش\nتأخیر سخت‌افزار صفحه قابل جعل یا صفر کردن نیست.", 12));

        content.addView(text("🎯 ایم و کنترل دستی", 14));
        content.addView(text("کنترل نشانه: دستی\nپاسخ ورودی: پایش\nAuto Aim / Auto Headshot: ندارد", 12));

        content.addView(text("🌐 نت", 14));
        TextView dns = text("DNS: تست کن", 13);
        content.addView(dns);
        dns.setOnClickListener(v -> testDns(dns));

        content.addView(text("🌡️ وضعیت دستگاه", 14));
        content.addView(text(deviceStats(), 12));
        content.addView(text("🧠 هوشمند: فقط قابلیت‌های واقعی و مجاز", 13));

        scroll.addView(content);
        box.addView(scroll, new LinearLayout.LayoutParams(dp(290), dp(430)));

        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                dp(310), dp(540), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(8);
        params.y = dp(70);

        panel = box;
        wm.addView(panel, params);
        addDrag(head);
    }

    private void addDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    sx = e.getRawX();
                    sy = e.getRawY();
                    ox = params.x;
                    oy = params.y;
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = ox + (int)(sx - e.getRawX());
                    params.y = oy + (int)(e.getRawY() - sy);
                    if (wm != null && panel != null) wm.updateViewLayout(panel, params);
                    return true;
                }
                return true;
            }
        });
    }

    private float refreshRate() {
        if (wm != null && wm.getDefaultDisplay() != null) return wm.getDefaultDisplay().getRefreshRate();
        return getResources().getDisplayMetrics().refreshRate;
    }

    private String deviceStats() {
        Debug.MemoryInfo m = new Debug.MemoryInfo();
        Debug.getMemoryInfo(m);
        Intent i = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int temp = i == null ? 0 : i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        BatteryManager bm = (BatteryManager)getSystemService(BATTERY_SERVICE);
        int level = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return "RAM پنل: " + (m.getTotalPss() / 1024) + " MB\nدما: " + (temp / 10f) + "°C\nباتری: " + level + "%";
    }

    private void testDns(TextView target) {
        target.setText("DNS: در حال تست...");
        executor.execute(() -> {
            long start = System.nanoTime();
            boolean ok;
            try {
                InetAddress.getByName("one.one.one.one");
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            String result = ok
                    ? "DNS: پاسخ گرفت • " + ms + "ms • بدون تعویض خودکار"
                    : "DNS: پاسخ نگرفت";
            target.post(() -> target.setText(result));
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}