package com.t3r0za.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.IBinder;
import android.os.PowerManager;
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
    private TextView stateText;
    private boolean compactMode = true;
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startPanelService();
        initThermalMonitor();
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
                .setContentText("پنل شناور سبک فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build();
    }

    private TextView makeText(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        return view;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(5), 0, dp(5), 0);
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
        box.setPadding(dp(8), dp(7), dp(8), dp(7));
        box.setBackgroundResource(R.drawable.bg_panel);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundResource(R.drawable.bg_card);

        TextView title = makeText("⚡ T3R0ZA", 15, Color.rgb(99, 230, 255));
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView expand = makeText("▣", 17, Color.WHITE);
        expand.setGravity(Gravity.CENTER);
        header.addView(expand, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView close = makeText("✕", 17, Color.rgb(255, 107, 122));
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));

        box.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        stateText = makeText(
                "● حالت سبک فعال • آماده بازی",
                10,
                Color.rgb(94, 227, 154)
        );
        box.addView(stateText, new LinearLayout.LayoutParams(-1, dp(26)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER);

        Button refresh = makeButton("↻ بروزرسانی");
        Button lightweight = makeButton("⚡ حالت سبک");
        actionRow.addView(lightweight, new LinearLayout.LayoutParams(0, dp(42), 1));

        LinearLayout.LayoutParams refreshParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        refreshParams.leftMargin = dp(5);
        actionRow.addView(refresh, refreshParams);
        content.addView(actionRow);

        content.addView(section("عملکرد"));
        TextView performanceInfo = makeText(
                "پنل از انیمیشن و پردازش مداوم استفاده نمی‌کند.\n" +
                "در حالت سبک فقط هدر کوچک روی بازی می‌ماند تا سربار خود پنل کم شود.",
                12,
                Color.WHITE
        );
        performanceInfo.setBackgroundResource(R.drawable.bg_card);
        content.addView(performanceInfo, marginParams(dp(82)));

        content.addView(section("نمایشگر"));
        TextView display = makeText(displayStats(), 12, Color.WHITE);
        display.setBackgroundResource(R.drawable.bg_card);
        content.addView(display, marginParams(dp(58)));

        content.addView(section("دستگاه"));
        statsText = makeText(deviceStats(), 12, Color.WHITE);
        statsText.setBackgroundResource(R.drawable.bg_card);
        content.addView(statsText, marginParams(dp(92)));

        content.addView(section("شبکه و DNS"));
        dnsText = makeText("آماده تست واقعی DNS", 12, Color.WHITE);
        dnsText.setBackgroundResource(R.drawable.bg_card);
        content.addView(dnsText, marginParams(dp(70)));

        LinearLayout dnsRow = new LinearLayout(this);
        Button testDns = makeButton("تست 3 DNS");
        Button network = makeButton("وضعیت شبکه");
        dnsRow.addView(testDns, new LinearLayout.LayoutParams(0, dp(42), 1));

        LinearLayout.LayoutParams networkParams =
                new LinearLayout.LayoutParams(0, dp(42), 1);
        networkParams.leftMargin = dp(5);
        dnsRow.addView(network, networkParams);
        content.addView(dnsRow);

        content.addView(section("کنترل دستی"));
        TextView aimInfo = makeText(
                "🎯 تاچ و هدف‌گیری کاملاً دستی است.\n" +
                "این پنل ورودی بازی را تزریق یا خودکار نمی‌کند و Auto Aim / Auto Headshot ندارد.\n" +
                "برای بهبود واقعی کنترل، تنظیمات حساسیت داخل خود بازی باید تنظیم شوند.",
                12,
                Color.WHITE
        );
        aimInfo.setBackgroundResource(R.drawable.bg_card);
        content.addView(aimInfo, marginParams(dp(102)));

        content.addView(section("حرارت"));
        TextView thermalInfo = makeText(
                thermalStatusText(),
                12,
                Color.WHITE
        );
        thermalInfo.setBackgroundResource(R.drawable.bg_card);
        content.addView(thermalInfo, marginParams(dp(56)));

        content.addView(section("پایداری"));
        TextView stability = makeText(
                "وقتی دستگاه گرم شود، پنل خودکار به حالت سبک می‌رود.\n" +
                "تست DNS فقط با درخواست دستی اجرا می‌شود تا فعالیت پس‌زمینه حداقل بماند.",
                12,
                Color.WHITE
        );
        stability.setBackgroundResource(R.drawable.bg_card);
        content.addView(stability, marginParams(dp(80)));

        scroll.addView(content);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(dp(300), dp(364));
        scrollParams.topMargin = dp(6);
        box.addView(scroll, scrollParams);

        Button stop = makeButton("⏹ بستن پنل");
        LinearLayout.LayoutParams stopParams =
                new LinearLayout.LayoutParams(-1, dp(40));
        stopParams.topMargin = dp(6);
        box.addView(stop, stopParams);

        close.setOnClickListener(view -> stopSelf());
        stop.setOnClickListener(view -> stopSelf());

        Runnable refreshAll = () -> {
            display.setText(displayStats());
            statsText.setText(deviceStats());
            thermalInfo.setText(thermalStatusText());
        };

        refresh.setOnClickListener(view -> refreshAll.run());
        network.setOnClickListener(view -> updateNetworkStatus(network));
        testDns.setOnClickListener(view -> runDnsTest());

        View.OnClickListener toggleListener =
                view -> toggleCompact(box, scroll, stop, expand, lightweight);

        expand.setOnClickListener(toggleListener);
        lightweight.setOnClickListener(
                view -> {
                    if (!compactMode) {
                        toggleCompact(box, scroll, stop, expand, lightweight);
                    }
                }
        );

        panel = box;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                dp(320),
                dp(100),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.x = dp(8);
        panelParams.y = dp(70);

        windowManager.addView(panel, panelParams);

        addDrag(header);

        if (!compactMode) {
            expand.performClick();
        }

        stopHeaderAnimation(title);
    }

    private void stopHeaderAnimation(TextView title) {
        title.animate().cancel();
        title.setAlpha(1f);
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
            TextView expand,
            Button lightweight
    ) {
        compactMode = !compactMode;

        if (compactMode) {
            box.removeView(scroll);
            box.removeView(stop);
            expand.setText("▣");
            lightweight.setText("⚡ حالت سبک");
            panelParams.height = dp(100);

            if (stateText != null) {
                stateText.setText("● حالت سبک فعال • کمترین سربار پنل");
            }
        } else {
            int insertIndex = Math.min(2, box.getChildCount());
            box.addView(scroll, insertIndex);
            box.addView(stop);
            expand.setText("▤");
            lightweight.setText("✓ سبک فعال");
            panelParams.height = dp(520);

            if (stateText != null) {
                stateText.setText("● پنل کامل • اسکرول فعال");
            }
        }

        if (windowManager != null && panel != null) {
            windowManager.updateViewLayout(panel, panelParams);
        }
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
                "نرخ نوسازی نمایشگر: %.0f Hz\n" +
                "FPS بازی: این اپ عدد ساختگی یا غیرقابل‌اعتماد نشان نمی‌دهد.",
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

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();

        return String.format(
                Locale.US,
                "RAM مصرفی پنل: %d MB\n" +
                "دما: %.1f°C\n" +
                "باتری: %d%%\n" +
                "حالت ذخیره انرژی: %s",
                memoryInfo.getTotalPss() / 1024,
                temperatureRaw / 10f,
                battery,
                saver ? "فعال" : "خاموش"
        );
    }

    private String thermalStatusText() {
        if (Build.VERSION.SDK_INT < 29 || powerManager == null) {
            return "پایش حرارت: در این نسخه Android در دسترس نیست.";
        }

        int status = powerManager.getCurrentThermalStatus();

        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                return "حرارت: عادی • پنل بدون محدودیت";
            case PowerManager.THERMAL_STATUS_LIGHT:
                return "حرارت: کمی بالا • پایش فعال";
            case PowerManager.THERMAL_STATUS_MODERATE:
                return "حرارت: متوسط • حالت سبک پیشنهاد می‌شود";
            case PowerManager.THERMAL_STATUS_SEVERE:
                return "حرارت: شدید • پنل باید سبک بماند";
            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "حرارت: بحرانی • فعالیت پنل باید حداقل باشد";
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "حرارت: اضطراری • فعالیت اضافه متوقف شود";
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "حرارت: خاموشی اضطراری";
            default:
                return "حرارت: نامشخص";
        }
    }

    private void initThermalMonitor() {
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager == null) {
            return;
        }

        thermalListener = status -> {
            if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
                if (panel != null && !compactMode) {
                    TextView handle = panel.findViewWithTag("t3r0za_expand");
                    if (handle instanceof TextView) {
                        handle.performClick();
                    }
                }

                if (stateText != null) {
                    stateText.post(() ->
                            stateText.setText(
                                    "● گرمای دستگاه بالا رفت • پنل خودکار سبک شد"
                            )
                    );
                }
            }
        };

        powerManager.addThermalStatusListener(
                getMainExecutor(),
                thermalListener
        );
    }

    private void updateNetworkStatus(Button target) {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (manager == null) {
            target.setText("شبکه نامشخص");
            return;
        }

        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(network);

        if (capabilities == null) {
            target.setText("بدون شبکه");
            return;
        }

        String type;

        if (capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
        )) {
            type = "Wi-Fi";
        } else if (capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
        )) {
            type = "دیتا";
        } else {
            type = "دیگر";
        }

        target.setText(type + " • متصل");
    }

    private void runDnsTest() {
        if (compactMode) {
            return;
        }

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

            if (dnsText != null) {
                dnsText.post(() -> dnsText.setText(result.toString()));
            }
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

            DatagramPacket packet = new DatagramPacket(
                    query,
                    query.length,
                    address,
                    53
            );

            long start = System.nanoTime();
            socket.send(packet);

            byte[] buffer = new byte[1024];

            DatagramPacket response = new DatagramPacket(
                    buffer,
                    buffer.length
            );

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
        if (Build.VERSION.SDK_INT >= 29
                && powerManager != null
                && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener);
            } catch (Exception ignored) {
            }
        }

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
