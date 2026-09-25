package com.t3r0za.panel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "t3r0za_state";
    private static final String PREF_OLD_PEAK = "old_peak";
    private static final String PREF_OLD_MIN = "old_min";
    private static final String KEY_PEAK_REFRESH = "peak_refresh_rate";
    private static final String KEY_MIN_REFRESH = "min_refresh_rate";

    private static final String[] DNS_NAMES = {"Cloudflare", "Google", "Quad9"};
    private static final String[] DNS_IPS = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView systemValue;
    private TextView gameValue;
    private TextView networkValue;
    private TextView thermalValue;
    private TextView actionValue;

    private SharedPreferences prefs;
    private float selectedRefresh = 120f;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(8), dp(5), dp(8), dp(5));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_card);
        return card;
    }

    private LinearLayout.LayoutParams full(int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, height);
        p.topMargin = dp(7);
        return p;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildPanel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            refreshAll();
        } catch (Throwable error) {
            safeStatus("وضعیت سیستم روی این MI/HyperOS در دسترس کامل نیست؛ برنامه فعال ماند.");
        }
    }

    private void buildPanel() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(Color.rgb(5, 8, 14));
        window.setNavigationBarColor(Color.rgb(5, 8, 14));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 12, 20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(24));

        LinearLayout hero = card();
        TextView logo = text("T3R0ZA", 30, Color.rgb(99, 230, 255));
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, 1);
        hero.addView(logo, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView badge = text("⚡ 120 FPS PERFORMANCE PANEL", 13, Color.rgb(155, 123, 255));
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(null, 1);
        hero.addView(badge, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView subtitle = text(
                "Real Android controls • Real Free Fire launch • No auto-aim",
                11,
                Color.rgb(140, 152, 174)
        );
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(32)));
        root.addView(hero);

        LinearLayout fps = card();
        fps.addView(sectionTitle("FPS / DISPLAY CONTROL"));
        systemValue = text("", 12, Color.WHITE);
        fps.addView(systemValue, full(dp(86)));

        LinearLayout rateRow = new LinearLayout(this);
        rateRow.setOrientation(LinearLayout.HORIZONTAL);
        addRateButton(rateRow, "60 Hz", 60f);
        addRateButton(rateRow, "90 Hz", 90f);
        addRateButton(rateRow, "120 Hz", 120f);
        addRateButton(rateRow, "RESTORE", 0f);
        fps.addView(rateRow, full(dp(52)));

        fps.addView(text(
                "فقط Refresh Rate انتخابی اجرا می‌شود. هیچ صفحه Settings باز نمی‌شود و هیچ تنظیم دیگری دستکاری نمی‌شود.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(56)));
        root.addView(fps);

        LinearLayout game = card();
        game.addView(sectionTitle("FREE FIRE"));
        gameValue = text("", 12, Color.WHITE);
        game.addView(gameValue, full(dp(70)));

        Button launch = actionButton("🎮  APPLY + LAUNCH FREE FIRE");
        launch.setOnClickListener(v -> launchGame());
        game.addView(launch, full(dp(52)));

        game.addView(text(
                "قبل از اجرا، refresh انتخاب‌شده برای سیستم تلاش می‌شود اعمال شود؛ سپس بستهٔ واقعی Free Fire باز می‌شود.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(46)));
        root.addView(game);

        LinearLayout network = card();
        network.addView(sectionTitle("NETWORK"));
        networkValue = text("Network diagnostic آماده است.", 12, Color.WHITE);
        network.addView(networkValue, full(dp(76)));

        Button dnsTest = actionButton("⚡  TEST DNS LATENCY");
        dnsTest.setOnClickListener(v -> runDnsTest());
        network.addView(dnsTest, full(dp(46)));

        network.addView(text(
                "DNS فقط latency واقعی را اندازه‌گیری می‌کند و هیچ تنظیم شبکه‌ای را تغییر نمی‌دهد.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(60)));
        root.addView(network);

        LinearLayout thermal = card();
        thermal.addView(sectionTitle("THERMAL / STABILITY"));
        thermalValue = text("", 12, Color.WHITE);
        thermal.addView(thermalValue, full(dp(82)));

        thermal.addView(text(
                "این بخش فقط وضعیت واقعی حرارت و Power Saver را می‌خواند و چیزی را تغییر نمی‌دهد.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(56)));
        root.addView(thermal);

        LinearLayout diagnostics = card();
        diagnostics.addView(sectionTitle("DEVICE / GAME DIAGNOSTICS"));
        diagnostics.addView(text(
                "این پنل فقط داده‌هایی را نشان می‌دهد که Android واقعاً در اختیار اپ قرار می‌دهد؛ کنترل Game Mode برای یک بازی دیگر در اختیار اپ عادی نیست.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(62)));

        Button refresh = actionButton("↻  REFRESH STATUS");
        refresh.setOnClickListener(v -> refreshAll());
        diagnostics.addView(refresh, full(dp(46)));
        root.addView(diagnostics);

        actionValue = text(
                "Ready. هیچ قابلیت Auto Aim / Auto Headshot / Recoil Automation فعال نیست.",
                11,
                Color.rgb(94, 227, 154)
        );
        actionValue.setGravity(Gravity.CENTER);
        root.addView(actionValue, full(dp(56)));

        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 13, Color.rgb(99, 230, 255));
        view.setTypeface(null, 1);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.bg_button);
        return button;
    }

    private void addRateButton(LinearLayout row, String label, float hz) {
        Button button = actionButton(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, p);

        button.setOnClickListener(v -> {
            if (hz == 0f) {
                restoreRefreshSettings();
            } else {
                selectedRefresh = hz;
                applyRefresh(hz, true);
            }
        });
    }

    private void refreshAll() {
        try {
            refreshSystemState();
        } catch (Throwable error) {
            systemValue.setText("Display status unavailable.");
        }

        try {
            refreshGameState();
        } catch (Throwable error) {
            gameValue.setText("Free Fire detection unavailable.");
        }

        try {
            refreshThermalState();
        } catch (Throwable error) {
            thermalValue.setText("Thermal status unavailable.");
        }
    }

    private void safeStatus(String message) {
        if (actionValue != null) {
            actionValue.setText(message);
        }
    }

    private Display getDefaultDisplayCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Display display = getDisplay();
                if (display != null) {
                    return display;
                }
            }

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            return wm == null ? null : wm.getDefaultDisplay();
        } catch (Throwable error) {
            return null;
        }
    }

    private float maxSupportedRefresh() {
        Display display = getDefaultDisplayCompat();
        if (display == null) {
            return 60f;
        }

        float max = display.getRefreshRate();

        if (Build.VERSION.SDK_INT >= 23) {
            try {
                Display.Mode[] modes = display.getSupportedModes();
                if (modes != null) {
                    for (Display.Mode mode : modes) {
                        if (mode != null) {
                            max = Math.max(max, mode.getRefreshRate());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return max;
    }

    private float currentRefresh() {
        Display display = getDefaultDisplayCompat();
        return display == null ? 60f : display.getRefreshRate();
    }

    private void refreshSystemState() {
        float current = currentRefresh();
        float max = maxSupportedRefresh();
        boolean canWrite = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this);

        systemValue.setText(
                String.format(
                        Locale.US,
                        "Current display: %.0f Hz\nMaximum reported: %.0f Hz\nWRITE_SETTINGS: %s\nTarget for launch: %.0f Hz",
                        current,
                        max,
                        canWrite ? "GRANTED" : "NOT GRANTED",
                        Math.min(selectedRefresh, max)
                )
        );
    }

    private void applyRefresh(float requestedHz) {
        float max = maxSupportedRefresh();
        float hz = requestedHz;

        if (hz > max + 0.5f) {
            actionValue.setText(
                    String.format(
                            Locale.US,
                            "120 Hz روی این دستگاه گزارش نشده؛ بیشترین refresh فعلی %.0f Hz است.",
                            max
                    )
            );
            return;
        }

        selectedRefresh = hz;

        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            actionValue.setText("WRITE_SETTINGS داده نشده؛ هیچ تنظیم سیستمی تغییر نکرد.");
            applyOwnWindowRefresh(hz);
            return;
        }

        boolean success = true;

        try {
            saveOriginalRefreshValuesIfNeeded();
            success &= Settings.System.putString(
                    getContentResolver(),
                    KEY_PEAK_REFRESH,
                    String.format(Locale.US, "%.1f", hz)
            );
            success &= Settings.System.putString(
                    getContentResolver(),
                    KEY_MIN_REFRESH,
                    String.format(Locale.US, "%.1f", hz)
            );
        } catch (Throwable error) {
            success = false;
        }

        applyOwnWindowRefresh(hz);

        if (success) {
            actionValue.setText(
                    String.format(
                            Locale.US,
                            "System refresh target set to %.0f Hz.",
                            hz
                    )
            );
            Toast.makeText(
                    this,
                    String.format(Locale.US, "Refresh target: %.0f Hz", hz),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            actionValue.setText("سیستم تغییر Refresh را نپذیرفت؛ هیچ تنظیم دیگری دستکاری نشد.");
        }

        refreshSystemState();
    }

    private void applyOwnWindowRefresh(float hz) {
        if (Build.VERSION.SDK_INT >= 21 && hz > 0f) {
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.preferredRefreshRate = hz;
                getWindow().setAttributes(lp);
            } catch (Throwable ignored) {
            }
        }
    }

    private void saveOriginalRefreshValuesIfNeeded() {
        if (prefs.contains(PREF_OLD_PEAK) || prefs.contains(PREF_OLD_MIN)) {
            return;
        }

        try {
            String peak = Settings.System.getString(getContentResolver(), KEY_PEAK_REFRESH);
            String min = Settings.System.getString(getContentResolver(), KEY_MIN_REFRESH);

            prefs.edit()
                    .putString(PREF_OLD_PEAK, peak == null ? "" : peak)
                    .putString(PREF_OLD_MIN, min == null ? "" : min)
                    .apply();
        } catch (Throwable error) {
            prefs.edit()
                    .putString(PREF_OLD_PEAK, "")
                    .putString(PREF_OLD_MIN, "")
                    .apply();
        }
    }

    private void restoreRefreshSettings() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            actionValue.setText("Restore ممکن نیست چون WRITE_SETTINGS داده نشده.");
            return;
        }

        String oldPeak = prefs.getString(PREF_OLD_PEAK, null);
        String oldMin = prefs.getString(PREF_OLD_MIN, null);

        if (!prefs.contains(PREF_OLD_PEAK) && !prefs.contains(PREF_OLD_MIN)) {
            actionValue.setText("هنوز تغییری توسط T3R0ZA ذخیره نشده است.");
            return;
        }

        try {
            if (oldPeak != null && !oldPeak.isEmpty()) {
                Settings.System.putString(
                        getContentResolver(),
                        KEY_PEAK_REFRESH,
                        oldPeak
                );
            }

            if (oldMin != null && !oldMin.isEmpty()) {
                Settings.System.putString(
                        getContentResolver(),
                        KEY_MIN_REFRESH,
                        oldMin
                );
            }
        } catch (Throwable error) {
            actionValue.setText("Restore توسط سیستم رد شد؛ برنامه همچنان فعال است.");
            return;
        }

        prefs.edit().clear().apply();
        selectedRefresh = currentRefresh();
        actionValue.setText("Refresh settings به مقادیر قبلی برگردانده شد.");
        refreshSystemState();
    }

    private String findGamePackage() {
        String[] packages = {"com.dts.freefireth", "com.dts.freefiremax"};

        for (String packageName : packages) {
            if (getPackageManager().getLaunchIntentForPackage(packageName) != null) {
                return packageName;
            }
        }

        return null;
    }

    private void refreshGameState() {
        String packageName = findGamePackage();

        if (packageName == null) {
            gameValue.setText("Free Fire: نصب نیست یا قابل تشخیص نیست.");
            return;
        }

        gameValue.setText(
                "Detected: " + packageName +
                        "\nLaunch: READY" +
                        "\nGame input/files remain untouched."
        );
    }

    private void refreshThermalState() {
        Debug.MemoryInfo mem = new Debug.MemoryInfo();
        Debug.getMemoryInfo(mem);

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int battery = bm == null ? -1 :
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String saver = pm != null && pm.isPowerSaveMode() ? "ON" : "OFF";
        String thermal = "UNKNOWN";

        if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            thermal = thermalLabel(pm.getCurrentThermalStatus());
        }

        Intent batteryIntent = registerReceiver(
                null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );

        int tempRaw = batteryIntent == null ? 0 :
                batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        String transport = "UNKNOWN";

        if (cm != null && Build.VERSION.SDK_INT >= 23) {
            Network network = cm.getActiveNetwork();
            NetworkCapabilities caps = network == null
                    ? null
                    : cm.getNetworkCapabilities(network);

            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    transport = "WIFI";
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    transport = "MOBILE";
                } else {
                    transport = "OTHER";
                }
            }
        }

        thermalValue.setText(
                String.format(
                        Locale.US,
                        "Thermal: %s\nBattery: %d%% • Power Saver: %s\nPanel RAM: %d MB\nTemperature: %.1f°C\nActive network: %s",
                        thermal,
                        battery,
                        saver,
                        mem.getTotalPss() / 1024,
                        tempRaw / 10f,
                        transport
                )
        );
    }

    private String thermalLabel(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                return "NORMAL";
            case PowerManager.THERMAL_STATUS_LIGHT:
                return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE:
                return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE:
                return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "SHUTDOWN";
            default:
                return "UNKNOWN";
        }
    }

    private void runDnsTest() {
        networkValue.setText("در حال تست پاسخ واقعی DNS...");

        executor.execute(() -> {
            StringBuilder out = new StringBuilder();
            String best = "-";
            long bestMs = Long.MAX_VALUE;

            for (int i = 0; i < DNS_IPS.length; i++) {
                long ms = dnsQuery(DNS_IPS[i]);

                if (ms >= 0) {
                    out.append(DNS_NAMES[i])
                            .append(": ")
                            .append(ms)
                            .append(" ms\n");

                    if (ms < bestMs) {
                        bestMs = ms;
                        best = DNS_NAMES[i];
                    }
                } else {
                    out.append(DNS_NAMES[i]).append(": no response\n");
                }
            }

            if (bestMs != Long.MAX_VALUE) {
                out.append("Fastest measured: ").append(best);
            } else {
                out.append("No DNS response.");
            }

            networkValue.post(() -> networkValue.setText(out.toString()));
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
            socket.setSoTimeout(1600);

            InetAddress address = InetAddress.getByName(server);
            DatagramPacket packet =
                    new DatagramPacket(query, query.length, address, 53);

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

    private void launchGame() {
        String packageName = findGamePackage();

        if (packageName == null) {
            Toast.makeText(
                    this,
                    "Free Fire روی دستگاه پیدا نشد.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        float max = maxSupportedRefresh();
        float launchHz = Math.min(selectedRefresh, max);

        if (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this)) {
            applyRefresh(launchHz);
        } else {
            applyOwnWindowRefresh(launchHz);
            actionValue.setText("Free Fire launch آماده است؛ Refresh سیستم تغییر نکرد چون Write Settings داده نشده.");
        }

        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);

        if (intent == null) {
            Toast.makeText(this, "لانچر Free Fire پیدا نشد.", Toast.LENGTH_LONG).show();
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        actionValue.setText(
                String.format(
                        Locale.US,
                        "Free Fire launched • refresh target requested: %.0f Hz",
                        launchHz
                )
        );
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
