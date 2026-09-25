package com.t3r0za.panel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildHome();
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(8, 12, 20));

        TextView logo = label("⚡", 34, Color.rgb(99, 230, 255));
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        TextView title = label("PANEL T3R0ZA", 24, Color.WHITE);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView sub = label(
                "پنل جمع‌وجور برای اجرای بازی و پایش عملکرد",
                13,
                Color.rgb(140, 152, 174)
        );
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, new LinearLayout.LayoutParams(-1, dp(42)));

        Button launch = new Button(this);
        launch.setText("🎮  لانچ گیم و پنل");
        launch.setTextColor(Color.WHITE);
        launch.setAllCaps(false);
        launch.setBackgroundResource(R.drawable.bg_button);
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-1, dp(54));
        buttonParams.topMargin = dp(10);
        root.addView(launch, buttonParams);

        TextView note = label(
                "پنل بعد از لانچ شناور می‌شود و با کشیدن هدر جابه‌جا می‌شود.",
                12,
                Color.rgb(140, 152, 174)
        );
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, dp(54)));

        launch.setOnClickListener(view -> startFlow());
        setContentView(root);
    }

    private void startFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                    this,
                    "اول مجوز نمایش روی برنامه‌ها را فعال کن.",
                    Toast.LENGTH_LONG
            ).show();

            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(settingsIntent);
            return;
        }

        Intent game = findGame();
        Intent serviceIntent = new Intent(this, OverlayService.class);
        startForegroundService(serviceIntent);

        if (game != null) {
            startActivity(game);
        } else {
            Toast.makeText(
                    this,
                    "Free Fire پیدا نشد؛ خود پنل باز می‌شود.",
                    Toast.LENGTH_LONG
            ).show();
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
}
