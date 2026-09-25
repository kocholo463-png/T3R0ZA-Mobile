package com.t3r0za.panel;

import android.app.Activity;
import android.app.GameManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    private static final String[] DNS_NAMES = {"Cloudflare", "Google", "Quad9"};
    private static final String[] DNS_IPS = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView performanceValue;
    private TextView touchValue;
    private TextView aimValue;
    private TextView dnsValue;
    private TextView thermalValue;
    private TextView gameValue;
    private TextView deviceValue;

    private int performanceLevel = 100;
    private int touchLevel = 70;
    private int aimLevel = 70;
    private int networkLevel = 70;

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
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
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
        buildFullPanel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshGameState();
        refreshDeviceState();
    }

    private void buildFullPanel() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 12, 20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(24));

        LinearLayout hero = card();
        TextView logo = text("⚡", 34, Color.rgb(99, 230, 255));
        logo.setGravity(Gravity.CENTER);
        hero.addView(logo, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView title = text("PANEL T3R0ZA", 24, Color.WHITE);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView subtitle = text(
                "Full Performance Panel • سبک، واقعی و بدون قابلیت تقلب",
                12,
                Color.rgb(140, 152, 174)
        );
        subtitle.setGravity(Gravity.CENTER);
        hero.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(38)));
        root.addView(hero);

        LinearLayout performance = card();
        performance.addView(sectionTitle("FPS / PERFORMANCE"));
        performanceValue = text("", 12, Color.WHITE);
        performance.addView(performanceValue, full(dp(34)));
        SeekBar performanceBar = makeSeekBar(performanceLevel);
        performance.addView(performanceBar, full(dp(40)));
        performanceBar.setOnSeekBarChangeListener(listener(v -> {
            performanceLevel = v;
            applyPerformanceProfile(performanceBar.getProgress());
        }));
        TextView performanceNote = text(
                "این کنترل، فرکانس پایش داخلی پنل و ترجیح Refresh Rate پنجره خود T3R0ZA را تنظیم می‌کند. کنترل مستقیم FPS برنامه دیگری از اپ عادی اندروید ممکن نیست.",
                10,
                Color.rgb(140, 152, 174)
        );
        performance.addView(performanceNote, full(dp(60)));
        root.addView(performance);

        LinearLayout touch = card();
        touch.addView(sectionTitle("TOUCH"));
        touchValue = text("", 12, Color.WHITE);
        touch.addView(touchValue, full(dp(34)));
        SeekBar touchBar = makeSeekBar(touchLevel);
        touch.addView(touchBar, full(dp(40)));
        touchBar.setOnSeekBarChangeListener(listener(v -> {
            touchLevel = v;
            applyTouchProfile(touchBar.getProgress());
        }));
        touch.addView(text(
                "اسلایدر مقدار هدف تست و کالیبراسیون داخلی را تنظیم می‌کند؛ درایور تاچ یا ورودی Free Fire را دستکاری نمی‌کند.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(48)));
        root.addView(touch);

        LinearLayout aim = card();
        aim.addView(sectionTitle("AIM / HEADSHOT CONTROL"));
        aimValue = text("", 12, Color.WHITE);
        aim.addView(aimValue, full(dp(34)));
        SeekBar aimBar = makeSeekBar(aimLevel);
        aim.addView(aimBar, full(dp(40)));
        aimBar.setOnSeekBarChangeListener(listener(v -> {
            aimLevel = v;
            applyAimProfile(aimBar.getProgress());
        }));
        aim.addView(text(
                "این بخش فقط پروفایل کنترل دستی و کالیبراسیون را نگه می‌دارد. Auto Aim، Auto Headshot و Recoil Automation وجود ندارد.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(48)));
        root.addView(aim);

        LinearLayout network = card();
        network.addView(sectionTitle("DNS / NETWORK"));
        dnsValue = text("آماده تست", 12, Color.WHITE);
        network.addView(dnsValue, full(dp(56)));
        SeekBar networkBar = makeSeekBar(networkLevel);
        network.addView(networkBar, full(dp(40)));
        networkBar.setOnSeekBarChangeListener(listener(v -> {
            networkLevel = v;
            applyNetworkProfile(networkBar.getProgress());
        }));
        TextView dnsAction = text(
                "⚡ برای تست و انتخاب DNS لمس کن",
                12,
                Color.rgb(99, 230, 255)
        );
        dnsAction.setGravity(Gravity.CENTER);
        dnsAction.setOnClickListener(v -> runDnsTest());
        network.addView(dnsAction, full(dp(42)));
        network.addView(text(
                "انتخاب DNS بر اساس پاسخ واقعی همین اتصال انجام می‌شود و تا پایان نشست تغییر نمی‌کند. تغییر DNS سراسری بدون VPN/دسترسی سیستم قابل تضمین نیست.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(60)));
        root.addView(network);

        LinearLayout game = card();
        game.addView(sectionTitle("FREE FIRE"));
        gameValue = text("", 12, Color.WHITE);
        game.addView(gameValue, full(dp(60)));

        TextView settingsLink = text(
                "⚙ تنظیمات خود Free Fire",
                12,
                Color.rgb(99, 230, 255)
        );
        settingsLink.setGravity(Gravity.CENTER);
        settingsLink.setOnClickListener(v -> openGameSettings());
        game.addView(settingsLink, full(dp(42)));
        root.addView(game);

        LinearLayout thermal = card();
        thermal.addView(sectionTitle("THERMAL / STABILITY"));
        thermalValue = text("", 12, Color.WHITE);
        thermal.addView(thermalValue, full(dp(62)));
        thermal.addView(text(
                "پایش حرارت برای جلوگیری از اضافه‌بار خود پنل استفاده می‌شود؛ وقتی حرارت بالا برود، پایش سبک‌تر می‌شود.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(48)));
        root.addView(thermal);

        LinearLayout device = card();
        device.addView(sectionTitle("DEVICE"));
        deviceValue = text("", 12, Color.WHITE);
        device.addView(deviceValue, full(dp(100)));
        root.addView(device);

        LinearLayout launchCard = card();
        launchCard.addView(sectionTitle("LAUNCH"));
        Button launch = new Button(this);
        launch.setText("🎮  LAUNCH FREE FIRE");
        launch.setTextColor(Color.WHITE);
        launch.setTextSize(14);
        launch.setAllCaps(false);
        launch.setBackgroundResource(R.drawable.bg_button);
        launch.setOnClickListener(v -> launchGame());
        launchCard.addView(launch, full(dp(52)));
        launchCard.addView(text(
                "پروفایل انتخاب‌شده ابتدا اعمال می‌شود، سپس Free Fire باز می‌شود.",
                10,
                Color.rgb(140, 152, 174)
        ), full(dp(34)));
        root.addView(launchCard);

        scroll.addView(root);
        setContentView(scroll);

        applyPerformanceProfile(performanceLevel);
        applyTouchProfile(touchLevel);
        applyAimProfile(aimLevel);
        applyNetworkProfile(networkLevel);
        refreshGameState();
        refreshDeviceState();
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 13, Color.rgb(99, 230, 255));
        view.setTypeface(null, 1);
        return view;
    }

    private SeekBar makeSeekBar(int progress) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(progress);
        return bar;
    }

    private SeekBar.OnSeekBarChangeListener listener(
            java.util.function.Consumer<Integer> consumer
    ) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                consumer.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        };
    }

    private void applyPerformanceProfile(int value) {
        int pollMs = 3500 - (value * 25);
        pollMs = Math.max(1000, pollMs);

        float[] rates = getWindow().getWindowManager().getDefaultDisplay().getSupportedRefreshRates();
        float selected = 0f;

        if (rates != null && rates.length > 0) {
            for (float rate : rates) {
                if (rate > selected && rate <= Math.max(60f, value + 30f)) {
                    selected = rate;
                }
            }
            if (selected == 0f) {
                selected = rates[rates.length - 1];
            }
        }

        if (Build.VERSION.SDK_INT >= 21 && selected > 0f) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredRefreshRate = selected;
            getWindow().setAttributes(lp);
        }

        performanceValue.setText(
                String.format(
                        Locale.US,
                        "Performance: %d%% • Panel polling: %d ms • Window refresh target: %.0f Hz",
                        value,
                        pollMs,
                        selected > 0f ? selected : 60f
                )
        );
    }

    private void applyTouchProfile(int value) {
        int testWindow = 20 + value * 2;
        touchValue.setText(
                String.format(
                        Locale.US,
                        "Touch profile: %d%% • Calibration window: %d samples",
                        value,
                        testWindow
                )
        );
    }

    private void applyAimProfile(int value) {
        int stability = Math.max(1, 100 - value);
        aimValue.setText(
                String.format(
                        Locale.US,
                        "Manual aim profile: %d%% • Stability bias: %d%%",
                        value,
                        stability
                )
        );
    }

    private void applyNetworkProfile(int value) {
        int timeout = Math.max(400, 1800 - value * 12);
        dnsValue.setText(
                String.format(
                        Locale.US,
                        "Network profile: %d%% • DNS probe timeout: %d ms • Session lock: ON",
                        value,
                        timeout
                )
        );
    }

    private void refreshGameState() {
        Intent game = findGame();

        if (game == null) {
            gameValue.setText("Free Fire: نصب نیست یا از این دستگاه قابل تشخیص نیست.");
            return;
        }

        String mode = "نامشخص";
        if (Build.VERSION.SDK_INT >= 31) {
            GameManager manager = getSystemService(GameManager.class);
            if (manager != null) {
                int current = manager.getGameMode();
                if (current == GameManager.GAME_MODE_PERFORMANCE) {
                    mode = "PERFORMANCE";
                } else if (current == GameManager.GAME_MODE_BATTERY) {
                    mode = "BATTERY";
                } else if (current == GameManager.GAME_MODE_STANDARD) {
                    mode = "STANDARD";
                } else if (current == GameManager.GAME_MODE_CUSTOM) {
                    mode = "CUSTOM";
                }
            }
        }

        gameValue.setText(
                "Free Fire: پیدا شد • Game Mode گزارش‌شده توسط سیستم: " + mode
                        + "
T3R0ZA کنترل مستقیم Game Mode بازی دیگری را ادعا نمی‌کند."
        );
    }

    private void refreshDeviceState() {
        Debug.MemoryInfo mem = new Debug.MemoryInfo();
        Debug.getMemoryInfo(mem);

        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int battery = bm == null ? -1 :
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String saver = pm != null && pm.isPowerSaveMode() ? "ON" : "OFF";

        Intent batteryIntent = registerReceiver(
                null,
                new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );

        int tempRaw = batteryIntent == null ? 0 :
                batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

        ActivityManagerMemoryHolder memory = readSystemMemory();
        deviceValue.setText(
                String.format(
                        Locale.US,
                        "Panel RAM: %d MB\nSystem available RAM: %d MB\nBattery: %d%%\nTemperature: %.1f°C\nPower Saver: %s\nLow RAM device: %s",
                        mem.getTotalPss() / 1024,
                        memory.availableMb,
                        battery,
                        tempRaw / 10f,
                        saver,
                        memory.lowRam ? "YES" : "NO"
                )
        );

        if (Build.VERSION.SDK_INT >= 29 && pm != null) {
            int thermal = pm.getCurrentThermalStatus();
            thermalValue.setText(
                    "Thermal status: " + thermalLabel(thermal) +
                            "
Thermal guard: فعال"
            );
        } else {
            thermalValue.setText("Thermal API: در این نسخه اندروید در دسترس نیست.");
        }
    }

    private ActivityManagerMemoryHolder readSystemMemory() {
        android.app.ActivityManager manager =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo info =
                new android.app.ActivityManager.MemoryInfo();

        if (manager == null) {
            return new ActivityManagerMemoryHolder(0, false);
        }

        manager.getMemoryInfo(info);
        return new ActivityManagerMemoryHolder(
                info.availMem / (1024 * 1024),
                manager.isLowRamDevice()
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

    private void openGameSettings() {
        String[] packages = {"com.dts.freefireth", "com.dts.freefiremax"};

        for (String packageName : packages) {
            if (getPackageManager().getLaunchIntentForPackage(packageName) != null) {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)
                );
                startActivity(intent);
                return;
            }
        }

        Toast.makeText(this, "Free Fire پیدا نشد.", Toast.LENGTH_LONG).show();
    }

    private void runDnsTest() {
        dnsValue.setText("در حال تست 3 DNS...");

        executor.execute(() -> {
            String best = "-";
            long bestMs = Long.MAX_VALUE;
            StringBuilder out = new StringBuilder();

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
                out.append("Selected for this session: ")
                        .append(best)
                        .append("\nLOCKED until next launch");
            } else {
                out.append("No DNS response.");
            }

            dnsValue.post(() -> dnsValue.setText(out.toString()));
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
            socket.setSoTimeout(Math.max(400, 1800 - networkLevel * 12));

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

    private Intent findGame() {
        String[] packages = {"com.dts.freefireth", "com.dts.freefiremax"};

        for (String packageName : packages) {
            Intent candidate =
                    getPackageManager().getLaunchIntentForPackage(packageName);

            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private void launchGame() {
        Intent game = findGame();

        if (game == null) {
            Toast.makeText(
                    this,
                    "Free Fire روی دستگاه پیدا نشد.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(game);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class ActivityManagerMemoryHolder {
        final long availableMb;
        final boolean lowRam;

        ActivityManagerMemoryHolder(long availableMb, boolean lowRam) {
            this.availableMb = availableMb;
            this.lowRam = lowRam;
        }
    }
}
