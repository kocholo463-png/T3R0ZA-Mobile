package com.t3r0za.panel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildMini();
    }

    private TextView label(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildMini() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(10,14,23));

        TextView title = label("⚡ PANEL T3R0ZA", 22);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView sub = label("مینی پنل گیم • سبک و واقعی", 13);
        sub.setTextColor(Color.rgb(139,151,173));
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(32)));

        Button launch = new Button(this);
        launch.setText("🎮 لانچ گیم");
        launch.setTextColor(Color.WHITE);
        launch.setAllCaps(false);
        root.addView(launch, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView status = label("● بازی: خاموش", 13);
        status.setTextColor(Color.rgb(139,151,173));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(36)));

        launch.setOnClickListener(v -> launchGame(status));
        setContentView(root);
    }

    private void launchGame(TextView status) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "برای پنل شناور، مجوز نمایش روی بازی لازمه.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        Intent game = null;
        String[] packages = {"com.dts.freefireth", "com.dts.freefiremax"};
        for (String pkg : packages) {
            Intent candidate = getPackageManager().getLaunchIntentForPackage(pkg);
            if (candidate != null) {
                game = candidate;
                break;
            }
        }

        if (game == null) {
            Toast.makeText(this, "فری فایر روی گوشی پیدا نشد.", Toast.LENGTH_LONG).show();
            return;
        }

        status.setText("● بازی: فعال");
        try {
            Intent mini = new Intent(this, com.t3r0za.mobile.MiniPanelService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(mini); else startService(mini);
        } catch (Exception e) {
            Toast.makeText(this, "اجرای Mini Panel ناموفق بود.", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(game);
    }
}