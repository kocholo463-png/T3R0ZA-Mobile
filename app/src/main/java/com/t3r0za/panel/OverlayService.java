package com.t3r0za.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private static final String[] DNS_NAMES = {"Cloudflare", "Google", "Quad9"};
    private static final String[] DNS_IPS = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};

    private WindowManager windowManager;
    private View panel;
    private WindowManager.LayoutParams panelParams;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView statsText;
    private TextView dnsText;
    private boolean compactMode;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startPanelService();
        showPanel();
    }

    private void startPanelService() {
        Notification note = createNotification();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    1001,
                    note,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(1001, note);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "t3r0za_panel",
                    "PANEL T3R0ZA",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "t3r0za_panel")
                : new Notification.Builder(this);

        return builder
                .setContentTitle("PANEL T3R0ZA")
                .setContentText("پنل شناور فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
    }

    private TextView makeText(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        return view;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.bg_button);
        return button;
    }

    private void showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackgroundResource(R.drawable.bg_panel);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.bg_card);

        TextView title = makeText("⚡  T3R0ZA", 16, Color.rgb(99, 230, 255));
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView close = makeText("✕", 18, Color.rgb(255, 107, 122));
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        box.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView state = makeText(
                "● آماده • پنل واقعی و سبک",
                11,
                Color.rgb(94, 227, 154)
        );
        box.addView(state, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER);

        Button refresh = makeButton("↻ بروزرسانی");
        Button compact = makeButton("▣ فشرده");

        actionRow.addView(compact, new LinearLayout.LayoutParams(0, dp(44), 1));

        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(0, dp(44), 1);
        refreshParams.leftMargin = dp(5);
        actionRow.addView(refresh, refreshParams);

        content.addView(actionRow);
        content.addView(section("نمایشگر"));

        TextView display = makeText(
                displayStats(),
                12,
                Color.WHITE
        );
        display.setBackgroundResource(R.drawable.bg_card);
        content.addView(display, marginParams(dp(58)));

        content.addView(section("دستگاه"));

        statsText = makeText(deviceStats(), 12, Color.WHITE);
        statsText.setBackgroundResource(R.drawable.bg_card);
        content.addView(statsText, marginParams(dp(74)));

        content.addView(section("شبکه و DNS"));

        dnsText = makeText("آماده تست DNS واقعی", 12, Color.WHITE);
        dnsText.setBackgroundResource(R.drawable.bg_card);
        content.addView(dnsText, marginParams(dp(70)));

        LinearLayout dnsRow = new LinearLayout(this);

        Button testDns = makeButton("تست 3 DNS");
        Button network = makeButton("وضعیت شبکه");

        dnsRow.addView(testDns, new LinearLayout.LayoutParams(0, dp(44), 1));

        LinearLayout.LayoutParams networkParams =
                new LinearLayout.LayoutParams(0, dp(44), 1);
        networkParams.leftMargin = dp(5);
        dnsRow.addView(network, networkParams);

        content.addView(dnsRow);
        content.addView(section("کنترل دستی"));

        TextView aimInfo = makeText(
                "🎯 نشانه و تاچ: دستی\n" +
                "لرزش یا تأخیر نرم‌افزاری ساختگی اعمال نمی‌شود.\n" +
                "Auto Aim / Auto Headshot / Recoil Automation: ندارد.",
                12,
                Color.WHITE
        );
        aimInfo.setBackgroundResource(R.drawable.bg_card);
        content.addView(aimInfo, marginParams(dp(86)));

        content.addView(section("حالت پنل"));

        TextView panelInfo = makeText(
                "فشرده: هدر کوچک برای مزاحمت کمتر روی صفحه.\n" +
                "عادی: اطلاعات کامل با اسکرول بالا و پایین.",
                12,
                Color.WHITE
        );
        panelInfo.setBackgroundResource(R.drawable.bg_card);
        content.addView(panelInfo, marginParams(dp(70)));

        content.addView(makeText(
                "این پنل داده واقعی دستگاه و تست شبکه را نشان می‌دهد؛ FPS خود بازی را بدون API اختصاصی بازی جعل نمی‌کند.",
                10,
                Color.rgb(140, 152, 174)
        ));

        scroll.addView(content);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(dp(300), dp(360));
        scrollParams.topMargin = dp(7);
        box.addView(scroll, scrollParams);

        Button stop = makeButton("⏹ بستن پنل");
        LinearLayout.LayoutParams stopParams =
                new LinearLayout.LayoutParams(-1, dp(42));
        stopParams.topMargin = dp(7);
        box.addView(stop, stopParams);

        close.setOnClickListener(view -> stopSelf());
        stop.setOnClickListener(view -> stopSelf());

        refresh.setOnClickListener(view -> {
            display.setText(displayStats());
            statsText.setText(deviceStats());
        });

        network.setOnClickListener(view -> updateNetworkStatus(network));
        testDns.setOnClickListener(view -> runDnsTest());
        compact.setOnClickListener(
                view -> toggleCompact(box, scroll, stop, compact)
        );

        panel = box;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                dp(320),
                dp(500),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.x = dp(8);
        panelParams.y = dp(70);

        windowManager.addView(panel, panelParams);
        addDrag(header);
        animateHeader(title);
    }

    private LinearLayout.LayoutParams marginParams(int height) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, height);
        params.topMargin = dp(5);
        return params;
    }

    private TextView section(String name) {
        TextView view = makeText(
                "• " + name,
                12,
                Color.rgb(99, 230, 255)
        );
        view.setTypeface(null, 1);
        return view;
    }

    private void toggleCompact(
            LinearLayout box,
            ScrollView scroll,
            Button stop,
            Button compact
    ) {
        compactMode = !compactMode;

        if (compactMode) {
            box.removeView(scroll);
            box.removeView(stop);
            compact.setText("▣ عادی");
            panelParams.height = dp(96);
        } else {
            int insertIndex = Math.min(3, box.getChildCount());
            box.addView(scroll, insertIndex);
            box.addView(stop);
            compact.setText("▣ فشرده");
            panelParams.height = dp(500);
        }

        windowManager.updateViewLayout(panel, panelParams);
    }

    private void animateHeader(TextView title) {
        title.setAlpha(0.72f);
        title.animate()
                .alpha(1f)
                .setDuration(850)
                .withEndAction(() -> pulse(title))
                .start();
    }

    private void pulse(TextView title) {
        if (panel == null) {
            return;
        }

        title.animate()
                .alpha(0.72f)
                .setDuration(850)
                .withEndAction(() -> animateHeader(title))
                .start();
    }

    private void addDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            float startY;
            int startParamX;
            int startParamY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX();
                    startY = event.getRawY();
                    startParamX = panelParams.x;
                    startParamY = panelParams.y;
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    panelParams.x =
                            startParamX + (int) (startX - event.getRawX());
                    panelParams.y =
                            startParamY + (int) (event.getRawY() - startY);

                    if (windowManager != null && panel != null) {
                        windowManager.updateViewLayout(panel, panelParams);
                    }
                    return true;
                }

                return true;
            }
        });
    }

    private String displayStats() {
        float hz = 60f;

        if (windowManager != null
                && windowManager.getDefaultDisplay() != null) {
            hz = windowManager.getDefaultDisplay().getRefreshRate();
        }

        return String.format(
                Locale.US,
                "نرخ نوسازی نمایشگر: %.0f Hz\nFPS بازی: اندازه‌گیری جعلی انجام نمی‌شود.",
                hz
        );
    }

    private String deviceStats() {
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);

        Intent batteryIntent = registerReceiver(
                null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );

        int temperatureRaw = batteryIntent == null
                ? 0
                : batteryIntent.getIntExtra(
                        BatteryManager.EXTRA_TEMPERATURE,
                        0
                );

        BatteryManager batteryManager =
                (BatteryManager) getSystemService(BATTERY_SERVICE);

        int battery = batteryManager == null
                ? -1
                : batteryManager.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY
                );

        return String.format(
                Locale.US,
                "RAM مصرفی پنل: %d MB\nدما: %.1f°C\nباتری: %d%%",
                memoryInfo.getTotalPss() / 1024,
                temperatureRaw / 10f,
                battery
        );
    }

    private void updateNetworkStatus(Button target) {
        android.net.ConnectivityManager manager =
                (android.net.ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);

        if (manager == null) {
            target.setText("شبکه نامشخص");
            return;
        }

        android.net.Network network = manager.getActiveNetwork();
        android.net.NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(network);

        if (capabilities == null) {
            target.setText("بدون شبکه");
            return;
        }

        String type;

        if (capabilities.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_WIFI
        )) {
            type = "Wi-Fi";
        } else if (capabilities.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_CELLULAR
        )) {
            type = "دیتا";
        } else {
            type = "دیگر";
        }

        target.setText(type + " • متصل");
    }

    private void runDnsTest() {
        dnsText.setText("در حال تست واقعی 3 DNS...");

        executor.execute(() -> {
            String bestName = "-";
            String bestIp = "-";
            long best = Long.MAX_VALUE;
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < DNS_IPS.length; i++) {
                long ms = dnsQuery(DNS_IPS[i]);

                if (ms >= 0) {
                    result.append(DNS_NAMES[i])
                            .append(" ")
                            .append(ms)
                            .append("ms\n");

                    if (ms < best) {
                        best = ms;
                        bestName = DNS_NAMES[i];
                        bestIp = DNS_IPS[i];
                    }
                } else {
                    result.append(DNS_NAMES[i])
                            .append(" بدون پاسخ\n");
                }
            }

            if (best != Long.MAX_VALUE) {
                result.append("سریع‌ترین پاسخ: ")
                        .append(bestName)
                        .append(" (")
                        .append(bestIp)
                        .append(")");
            } else {
                result.append("هیچ DNS پاسخی نداد.");
            }

            dnsText.post(() -> dnsText.setText(result.toString()));
        });
    }

    private long dnsQuery(String server) {
        int id = (int) (System.nanoTime() & 0xffff);

        byte[] query = new byte[] {
                (byte) (id >> 8), (byte) id,
                1, 0,
                0, 1,
                0, 0,
                0, 0,
                0, 0,
                7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                3, 'c', 'o', 'm',
                0,
                0, 1,
                0, 1
        };

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1200);

            InetAddress address = InetAddress.getByName(server);
            DatagramPacket packet =
                    new DatagramPacket(
                            query,
                            query.length,
                            address,
                            53
                    );

            long start = System.nanoTime();
            socket.send(packet);

            byte[] buffer = new byte[1024];
            DatagramPacket response =
                    new DatagramPacket(buffer, buffer.length);

            socket.receive(response);
            return (System.nanoTime() - start) / 1_000_000;
        } catch (IOException error) {
            return -1;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();

        if (windowManager != null && panel != null) {
            try {
                windowManager.removeView(panel);
            } catch (Exception ignored) {
            }
        }

        panel = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
