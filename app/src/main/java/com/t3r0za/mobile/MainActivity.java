package com.t3r0za.mobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String[] GAMES = {"com.dts.freefireth", "com.dts.freefiremax"};
    private static final String[] DNS = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};
    private static final int VPN_REQUEST = 8101;

    private final int BG = Color.rgb(7, 10, 18);
    private final int CARD = Color.rgb(16, 22, 34);
    private final int CARD2 = Color.rgb(23, 31, 47);
    private final int TEXT = Color.rgb(239, 244, 255);
    private final int MUTED = Color.rgb(145, 157, 181);
    private final int ACCENT = Color.rgb(151, 91, 255);
    private final int OK = Color.rgb(64, 224, 154);
    private final int WARN = Color.rgb(255, 191, 76);
    private final int BAD = Color.rgb(255, 91, 109);

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private SharedPreferences prefs;

    private LinearLayout root;
    private TextView liveStatus;
    private TextView telemetry;
    private TextView dnsStatus;
    private TextView dnsBenchmark;
    private TextView sessionStatus;
    private TextView networkStatus;

    private String selectedDns = "1.1.1.1";
    private boolean smartMode = true;
    private boolean dnsLock = true;
    private boolean dashboardShown = false;
    private float sensitivity = 0.60f;
    private float swipeResponse = 0.75f;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTelemetry();
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("t3r0za", MODE_PRIVATE);
        selectedDns = prefs.getString("dns", "1.1.1.1");
        smartMode = prefs.getBoolean("smart", true);
        dnsLock = prefs.getBoolean("dns_lock", true);
        sensitivity = prefs.getFloat("sensitivity", 0.60f);
        swipeResponse = prefs.getFloat("swipe", 0.75f);

        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams p = w.getAttributes();
            p.preferredRefreshRate = getWindowManager().getDefaultDisplay().getRefreshRate();
            w.setAttributes(p);
        }

        showStart();
    }

    private void showStart() {
        dashboardShown = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(BG);

        LinearLayout hero = card();
        TextView title = text("T3R0ZA", 34, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(title);
        TextView sub = text("GAME PERFORMANCE CENTER", 11, ACCENT);
        sub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(sub, lp(2, 0));
        TextView state = text(installedGame() == null ? "Free Fire پیدا نشد" : "Free Fire آماده اجراست", 14, installedGame() == null ? WARN : OK);
        state.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(state, lp(18, 0));
        root.addView(hero, lp(0, 12));

        TextView info = text(
                installedGame() == null
                        ? "برای فعال شدن کنترل‌های بازی، Free Fire را روی دستگاه نصب کن."
                        : "لانچ را بزن؛ بعد از اجرای بازی داشبورد کامل و وضعیت واقعی دستگاه باز می‌شود.",
                13, MUTED);
        info.setGravity(Gravity.CENTER);
        root.addView(info, lp(4, 18));

        Button launch = button("🎮 LAUNCH GAME", ACCENT);
        launch.setTextSize(16);
        launch.setEnabled(installedGame() != null);
        launch.setOnClickListener(v -> launchGame());
        root.addView(launch, lp(0, 10));

        Button refresh = button("🔄 بررسی دوباره بازی", CARD2);
        refresh.setOnClickListener(v -> showStart());
        root.addView(refresh, lp(0, 10));

        setContentView(root);
    }

    private void launchGame() {
        String pkg = installedGame();
        if (pkg == null) {
            toast("Free Fire پیدا نشد");
            return;
        }
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            toast("لانچر بازی پیدا نشد");
            return;
        }
        dashboardShown = true;
        showDashboard();
        startActivity(intent);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void showDashboard() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(26));
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);

        addHeader();
        addPerformance();
        addInputAim();
        addNetwork();
        addDns();
        addSmartSession();
        addDevice();

        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void addHeader() {
        LinearLayout c = card();
        LinearLayout top = row();
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("T3R0ZA", 25, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameBox.addView(title);
        TextView sub = text("Free Fire • Session Manager", 11, MUTED);
        nameBox.addView(sub);
        top.addView(nameBox, new LinearLayout.LayoutParams(0, -2, 1));

        liveStatus = text("● آماده", 13, WARN);
        liveStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(liveStatus);
        c.addView(top);
        root.addView(c, lp(0, 10));
    }

    private void addPerformance() {
        LinearLayout c = section("⚡ PERFORMANCE");
        telemetry = text("در حال خواندن وضعیت دستگاه…", 12, TEXT);
        c.addView(telemetry, lp(8, 4));

        Button ready = button("⚙️ GAME READY CHECK", CARD2);
        ready.setOnClickListener(v -> runReadyCheck());
        c.addView(ready, lp(6, 2));

        TextView note = text(
                "FPS داخلی Free Fire از API عمومی Android قابل خواندن یا تنظیم نیست؛ این بخش فقط اندازه‌گیری‌های واقعی دستگاه را نشان می‌دهد.",
                10, MUTED);
        c.addView(note, lp(4, 0));

        root.addView(c, lp(0, 10));
    }

    private void addInputAim() {
        LinearLayout c = section("👆 TOUCH / 🎯 AIM LAB");

        addSlider(c, "حساسیت پروفایل", sensitivity, value -> {
            sensitivity = value;
            prefs.edit().putFloat("sensitivity", value).apply();
        });

        addSlider(c, "پاسخ Swipe", swipeResponse, value -> {
            swipeResponse = value;
            prefs.edit().putFloat("swipe", value).apply();
        });

        addStatus(c, "Aim", "دستی / بدون تزریق ورودی", null);
        addStatus(c, "Recoil", "کنترل دستی", null);
        addStatus(c, "Auto-headshot / Aim-bot", "وجود ندارد", false);

        TextView note = text(
                "این دو اسلایدر پروفایل تمرین و تنظیم دستی را نگه می‌دارند؛ برنامه ورودی را به Free Fire تزریق نمی‌کند و نشانه‌گیری را خودکار نمی‌کند.",
                10, MUTED);
        c.addView(note, lp(4, 0));

        root.addView(c, lp(0, 10));
    }

    private void addNetwork() {
        LinearLayout c = section("🌐 NETWORK");
        networkStatus = text("شبکه: در حال خواندن…", 12, TEXT);
        c.addView(networkStatus);

        Button test = button("⚡ تست Ping / Jitter / Packet Loss", CARD2);
        test.setOnClickListener(v -> runNetworkProbe());
        c.addView(test, lp(8, 2));

        Button openNetwork = button("⚙️ تنظیمات شبکه Android", CARD2);
        openNetwork.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e) {
                toast("تنظیمات شبکه روی این دستگاه باز نشد");
            }
        });
        c.addView(openNetwork, lp(6, 0));

        root.addView(c, lp(0, 10));
    }

    private void addDns() {
        LinearLayout c = section("🧩 DNS SESSION");
        dnsStatus = text("DNS: " + selectedDns, 12, TEXT);
        dnsBenchmark = text("Benchmark: هنوز تست نشده", 12, MUTED);
        c.addView(dnsStatus);
        c.addView(dnsBenchmark, lp(4, 4));

        c.addView(text("DNS انتخابی", 11, MUTED), lp(4, 0));

        LinearLayout chips = row();
        for (String dns : DNS) {
            Button b = button(dns, selectedDns.equals(dns) ? ACCENT : CARD2);
            b.setTextSize(11);
            b.setOnClickListener(v -> {
                if (DnsTunnelService.instance != null && DnsTunnelService.instance.running) {
                    toast("DNS وسط Session عوض نمی‌شود");
                    return;
                }
                selectedDns = b.getText().toString();
                prefs.edit().putString("dns", selectedDns).apply();
                showDashboard();
            });
            chips.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(chips, lp(4, 4));

        addSwitch(c, "🧠 Smart DNS", "قبل از Session، Resolverها را واقعاً تست می‌کند.", smartMode, checked -> {
            smartMode = checked;
            prefs.edit().putBoolean("smart", checked).apply();
        });

        addSwitch(c, "🔒 DNS Lock", "بعد از شروع Session، Resolver خودکار عوض نمی‌شود.", dnsLock, checked -> {
            dnsLock = checked;
            prefs.edit().putBoolean("dns_lock", checked).apply();
        });

        Switch active = new Switch(this);
        active.setText("DNS Session");
        active.setTextColor(TEXT);
        active.setTextSize(13);
        active.setChecked(DnsTunnelService.instance != null && DnsTunnelService.instance.running);
        active.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) startDnsSession();
            else stopDnsSession();
        });
        c.addView(active, lp(4, 0));

        Button bench = button("🔎 Benchmark واقعی DNS", CARD2);
        bench.setOnClickListener(v -> runDnsBenchmark());
        c.addView(bench, lp(7, 2));

        Button privateDns = button("⚙️ مدیریت Private DNS در Android", CARD2);
        privateDns.setOnClickListener(v -> {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    startActivity(new Intent(Settings.ACTION_PRIVATE_DNS_SETTINGS));
                } else {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                }
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
        });
        c.addView(privateDns, lp(6, 0));

        TextView note = text(
                "DNS Session با VpnService واقعی اجرا می‌شود و برای فعال شدن نیاز به تأیید Android دارد. Lock فقط Resolver انتخاب‌شده را ثابت نگه می‌دارد؛ تغییر مخفیانه انجام نمی‌شود.",
                10, MUTED);
        c.addView(note, lp(5, 0));

        root.addView(c, lp(0, 10));
    }

    private void addSmartSession() {
        LinearLayout c = section("🧠 SMART / SESSION");
        sessionStatus = text("Session: در انتظار بازی", 12, TEXT);
        c.addView(sessionStatus);

        addSwitch(c, "Smart Performance", "پایش وضعیت واقعی دستگاه؛ بدون Boost فیک.", smartMode, checked -> {
            smartMode = checked;
            prefs.edit().putBoolean("smart", checked).apply();
        });

        Button scan = button("🧪 Full Device Scan", CARD2);
        scan.setOnClickListener(v -> runReadyCheck());
        c.addView(scan, lp(6, 2));

        Button usage = button("⚙️ دسترسی تشخیص ورود و خروج بازی", CARD2);
        usage.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception e) {
                toast("صفحه Usage Access باز نشد");
            }
        });
        c.addView(usage, lp(6, 0));

        root.addView(c, lp(0, 10));
    }

    private void addDevice() {
        LinearLayout c = section("🌡️ DEVICE");
        TextView t = text("", 12, TEXT);
        c.addView(t);
        updateDeviceText(t);

        Button power = button("🔋 بررسی Power Saver", CARD2);
        power.setOnClickListener(v -> {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && pm.isPowerSaveMode()) toast("Power Saver روشن است؛ برای بازی خاموشش کن.");
            else toast("Power Saver خاموش است.");
        });
        c.addView(power, lp(7, 0));
        root.addView(c, lp(0, 10));
    }

    private void updateDeviceText(TextView t) {
        DeviceSnapshot s = readDevice();
        t.setText(
                "Refresh: " + Math.round(s.refresh) + " Hz\n" +
                "Memory: " + Math.round(s.memory) + "% used\n" +
                "Thermal: " + s.thermal + "\n" +
                "Battery: " + s.battery + "%\n" +
                "Power Saver: " + (s.powerSaver ? "ON" : "OFF") + "\n" +
                "Network: " + s.network
        );
    }

    private void updateTelemetry() {
        if (!dashboardShown) return;

        DeviceSnapshot s = readDevice();
        String game = installedGame();
        boolean fg = game != null && isGameForeground(game);
        boolean dnsActive = DnsTunnelService.instance != null && DnsTunnelService.instance.running;

        if (liveStatus != null) {
            liveStatus.setText(fg ? "● GAME ACTIVE" : "○ WAITING");
            liveStatus.setTextColor(fg ? OK : WARN);
        }

        if (sessionStatus != null) {
            sessionStatus.setText("Session: " + (fg ? "GAME ACTIVE" : "WAITING") +
                    (dnsActive ? " • DNS CONNECTED" : ""));
            sessionStatus.setTextColor(fg ? OK : WARN);
        }

        if (dnsStatus != null) {
            String extra = dnsActive ? " • ● CONNECTED" : " • ○ OFF";
            dnsStatus.setText("DNS: " + selectedDns + extra);
            dnsStatus.setTextColor(dnsActive ? OK : TEXT);
        }

        if (telemetry != null) {
            telemetry.setText(
                    "Display: " + Math.round(s.refresh) + " Hz\n" +
                    "Memory: " + Math.round(s.memory) + "% used\n" +
                    "Thermal: " + s.thermal + "\n" +
                    "Battery: " + s.battery + "%\n" +
                    "Power Saver: " + (s.powerSaver ? "ON" : "OFF") + "\n" +
                    "Network: " + s.network
            );
        }

        if (networkStatus != null) {
            networkStatus.setText("شبکه: " + s.network);
        }
    }

    private void runReadyCheck() {
        DeviceSnapshot s = readDevice();
        String out = "وضعیت آماده‌بودن دستگاه\n\n" +
                "Refresh: " + Math.round(s.refresh) + " Hz\n" +
                "Memory: " + Math.round(s.memory) + "%\n" +
                "Thermal: " + s.thermal + "\n" +
                "Battery: " + s.battery + "%\n" +
                "Power Saver: " + (s.powerSaver ? "ON" : "OFF") + "\n" +
                "Network: " + s.network;
        toast(out);
    }

    private void runNetworkProbe() {
        networkStatus.setText("تست شبکه در حال اجرا…");
        io.execute(() -> {
            ProbeResult r = probeTcp("1.1.1.1", 443, 6);
            runOnUiThread(() -> networkStatus.setText(
                    "Ping avg: " + (r.avg < 0 ? "—" : Math.round(r.avg) + " ms") +
                    " • Jitter: " + (r.jitter < 0 ? "—" : Math.round(r.jitter) + " ms") +
                    " • Loss: " + Math.round(r.loss) + "%"
            ));
        });
    }

    private void runDnsBenchmark() {
        dnsBenchmark.setText("Benchmark در حال اجرا…");
        io.execute(() -> {
            long best = Long.MAX_VALUE;
            String bestDns = null;
            StringBuilder sb = new StringBuilder("DNS: ");
            for (String dns : DNS) {
                long ms = dnsProbe(dns);
                sb.append(dns).append("=")
                        .append(ms < 0 ? "fail" : ms + "ms").append("  ");
                if (ms >= 0 && ms < best) {
                    best = ms;
                    bestDns = dns;
                }
            }
            final String result = sb.toString();
            final String winner = bestDns;
            runOnUiThread(() -> {
                dnsBenchmark.setText(result);
                if (smartMode && !dnsLock && winner != null &&
                        !(DnsTunnelService.instance != null && DnsTunnelService.instance.running)) {
                    selectedDns = winner;
                    prefs.edit().putString("dns", winner).apply();
                    dnsStatus.setText("DNS: " + winner + " • Smart Selected");
                }
            });
        });
    }

    private void startDnsSession() {
        if (DnsTunnelService.instance != null && DnsTunnelService.instance.running) {
            toast("DNS Session همین الان فعاله");
            return;
        }

        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, VPN_REQUEST);
            return;
        }
        launchDnsService();
    }

    private void launchDnsService() {
        Intent service = new Intent(this, DnsTunnelService.class);
        service.putExtra(DnsTunnelService.EXTRA_DNS, selectedDns);
        service.putExtra(DnsTunnelService.EXTRA_PREMATCH_ONLY, false);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        toast("DNS Session برای " + selectedDns + " شروع شد");
    }

    private void stopDnsSession() {
        Intent stop = new Intent(this, DnsTunnelService.class);
        stop.setAction(DnsTunnelService.ACTION_STOP);
        startService(stop);
        toast("DNS Session خاموش شد");
        handler.postDelayed(this::updateTelemetry, 250);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            launchDnsService();
        } else if (requestCode == VPN_REQUEST) {
            toast("مجوز VPN داده نشد؛ DNS Session روشن نشد.");
            showDashboard();
        }
    }

    private DeviceSnapshot readDevice() {
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        float memory = mi.totalMem > 0 ? (mi.totalMem - mi.availMem) * 100f / mi.totalMem : 0f;

        PowerManager pm = getSystemService(PowerManager.class);
        String thermal = "N/A";
        if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            switch (pm.getCurrentThermalStatus()) {
                case PowerManager.THERMAL_STATUS_NONE: thermal = "Cool"; break;
                case PowerManager.THERMAL_STATUS_LIGHT: thermal = "Warm"; break;
                case PowerManager.THERMAL_STATUS_MODERATE: thermal = "Moderate"; break;
                case PowerManager.THERMAL_STATUS_SEVERE: thermal = "Severe"; break;
                case PowerManager.THERMAL_STATUS_CRITICAL: thermal = "Critical"; break;
                case PowerManager.THERMAL_STATUS_EMERGENCY: thermal = "Emergency"; break;
                case PowerManager.THERMAL_STATUS_SHUTDOWN: thermal = "Shutdown"; break;
                default: thermal = "Unknown";
            }
        }

        float refresh = Build.VERSION.SDK_INT >= 30
                ? getWindowManager().getDefaultDisplay().getRefreshRate()
                : 60f;

        BatteryManager bm = getSystemService(BatteryManager.class);
        int battery = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean saver = pm != null && pm.isPowerSaveMode();

        return new DeviceSnapshot(memory, thermal, refresh, battery, saver, networkType());
    }

    private String networkType() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return "Unknown";
        Network n = cm.getActiveNetwork();
        if (n == null) return "OFFLINE";
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        if (c == null) return "UNKNOWN";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi‑Fi";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        return "Other";
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isGameForeground(String pkg) {
        if (!hasUsageAccess()) return false;
        android.app.usage.UsageStatsManager usm =
                getSystemService(android.app.usage.UsageStatsManager.class);
        if (usm == null) return false;
        long end = System.currentTimeMillis();
        android.app.usage.UsageEvents events = usm.queryEvents(end - 15000, end);
        if (events == null) return false;
        android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
        String last = null;
        long lastTime = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            int type = ev.getEventType();
            if (type == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 &&
                            type == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)) {
                if (ev.getTimeStamp() >= lastTime) {
                    lastTime = ev.getTimeStamp();
                    last = ev.getPackageName();
                }
            }
        }
        return pkg.equals(last);
    }

    private String installedGame() {
        for (String pkg : GAMES) {
            try {
                getPackageManager().getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private ProbeResult probeTcp(String host, int port, int count) {
        List<Long> values = new ArrayList<>();
        int failures = 0;
        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 1500);
                values.add((System.nanoTime() - start) / 1000000L);
            } catch (IOException e) {
                failures++;
            }
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        if (values.isEmpty()) return new ProbeResult(-1, -1, 100);
        double avg = values.stream().mapToLong(Long::longValue).average().orElse(-1);
        double jitter = -1;
        if (values.size() > 1) {
            double total = 0;
            for (int i = 1; i < values.size(); i++) {
                total += Math.abs(values.get(i) - values.get(i - 1));
            }
            jitter = total / (values.size() - 1);
        }
        double loss = failures * 100.0 / count;
        return new ProbeResult(avg, jitter, loss);
    }

    private long dnsProbe(String server) {
        try {
            byte[] query = dnsQuery("example.com");
            InetAddress address = InetAddress.getByName(server);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(1400);
                DatagramPacket out = new DatagramPacket(query, query.length, address, 53);
                byte[] buf = new byte[2048];
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                long start = System.nanoTime();
                socket.send(out);
                socket.receive(in);
                if (in.getLength() < 12) return -1;
                return (System.nanoTime() - start) / 1000000L;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private byte[] dnsQuery(String host) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int id = (int) (System.nanoTime() & 0xffff);
        out.write((id >>> 8) & 0xff);
        out.write(id & 0xff);
        out.write(1);
        out.write(0);
        out.write(0);
        out.write(1);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        for (String label : host.split("\\.")) {
            byte[] bytes = label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            out.write(bytes.length);
            out.write(bytes);
        }
        out.write(0);
        out.write(0);
        out.write(1);
        out.write(0);
        out.write(1);
        return out.toByteArray();
    }

    private LinearLayout section(String title) {
        LinearLayout c = card();
        TextView h = text(title, 14, ACCENT);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        c.addView(h);
        return c;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(15), dp(13), dp(15), dp(13));
        l.setBackground(round(CARD, 20));
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void addStatus(LinearLayout parent, String name, String value, Boolean good) {
        LinearLayout r = row();
        TextView a = text(name, 12, TEXT);
        TextView b = text(value, 12, good == null ? TEXT : (good ? OK : BAD));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(b);
        parent.addView(r, lp(4, 0));
    }

    private void addSwitch(LinearLayout parent, String title, String desc, boolean checked,
                           CompoundListener listener) {
        LinearLayout r = row();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 13, TEXT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(t);
        box.addView(text(desc, 9, MUTED), lp(2, 0));
        r.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChange(isChecked));
        r.addView(s);
        parent.addView(r, lp(5, 5));
    }

    private void addSlider(LinearLayout parent, String label, float value, SliderListener listener) {
        TextView tv = text(label + "  " + Math.round(value * 100) + "%", 12, TEXT);
        parent.addView(tv, lp(5, 0));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.round(value * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float v = progress / 100f;
                    tv.setText(label + "  " + progress + "%");
                    listener.onChange(v);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(bar, lp(0, 2));
    }

    private Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(round(color, 16));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, dp(bottom));
        return p;
    }

    private android.graphics.drawable.GradientDrawable round(int color, float radius) {
        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        io.shutdownNow();
        super.onDestroy();
    }

    private static class DeviceSnapshot {
        final float memory;
        final String thermal;
        final float refresh;
        final int battery;
        final boolean powerSaver;
        final String network;

        DeviceSnapshot(float memory, String thermal, float refresh,
                       int battery, boolean powerSaver, String network) {
            this.memory = memory;
            this.thermal = thermal;
            this.refresh = refresh;
            this.battery = battery;
            this.powerSaver = powerSaver;
            this.network = network;
        }
    }

    private static class ProbeResult {
        final double avg;
        final double jitter;
        final double loss;

        ProbeResult(double avg, double jitter, double loss) {
            this.avg = avg;
            this.jitter = jitter;
            this.loss = loss;
        }
    }

    private interface CompoundListener {
        void onChange(boolean checked);
    }

    private interface SliderListener {
        void onChange(float value);
    }
}
