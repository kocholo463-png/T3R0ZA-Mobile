package com.t3r0za.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, metrics;
    TextView status, stats;
    Handler h=new Handler(Looper.getMainLooper());
    Runnable ticker;

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    TextView tv(String s,int sp){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(0xFFF2F6F8); return t; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(13); b.setTextColor(0xFFF2F6F8); b.setAllCaps(false); b.setBackgroundColor(0xFF16252D); b.setPadding(dp(8),0,dp(8),0); return b; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        build();
        updateStats();
        ticker=()->{updateStats();h.postDelayed(ticker,2000);};
        h.postDelayed(ticker,2000);
    }

    void build(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(24)); root.setBackgroundColor(0xFF071014);
        TextView title=tv("T3R0ZA",30); title.setTextColor(0xFF27D7A5); title.setTypeface(null,1); root.addView(title);
        TextView sub=tv("PHONE PERFORMANCE PANEL",12); sub.setTextColor(0xFF8DA0AD); root.addView(sub,space(0,0,0,dp(18)));
        status=tv("● READY",14); status.setTextColor(0xFF27D7A5); root.addView(status,space(0,0,0,dp(12)));
        stats=tv("",14); stats.setTextColor(0xFFB8C7CE); root.addView(stats,card());

        root.addView(tv("CONTROLS",13),space(0,dp(16),0,dp(6)));
        Button launch=btn("LAUNCH FREE FIRE");
        launch.setOnClickListener(v->launchFF());
        root.addView(launch,space(0,0,0,dp(7)));

        Button game=btn("ANDROID GAME SETTINGS");
        game.setOnClickListener(v->openIntent(Settings.ACTION_GAME_SETTINGS));
        root.addView(game,space(0,0,0,dp(7)));

        Button battery=btn("BATTERY SETTINGS");
        battery.setOnClickListener(v->openIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        root.addView(battery,space(0,0,0,dp(7)));

        Button unrestricted=btn("BATTERY OPTIMIZATION");
        unrestricted.setOnClickListener(v->batteryOptimization());
        root.addView(unrestricted,space(0,0,0,dp(7)));

        Button refresh=btn("REFRESH METRICS");
        refresh.setOnClickListener(v->updateStats());
        root.addView(refresh,space(0,0,0,dp(7)));

        Button thermal=btn("THERMAL STATUS");
        thermal.setOnClickListener(v->thermalInfo());
        root.addView(thermal,space(0,0,0,dp(7)));

        Button display=btn("DISPLAY / REFRESH RATE");
        display.setOnClickListener(v->displayInfo());
        root.addView(display,space(0,0,0,dp(7)));

        TextView note=tv("T3R0ZA only uses legitimate Android controls and measurements. It does not modify Free Fire files or claim a fake FPS boost.",11);
        note.setTextColor(0xFF7F919A); root.addView(note,space(0,dp(18),0,0));
        scroll.addView(root); setContentView(scroll);
    }

    LinearLayout.LayoutParams card(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(10));return p;}
    LinearLayout.LayoutParams space(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(l,t,r,b);return p;}

    void updateStats(){
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo(); am.getMemoryInfo(mi);
        long total=mi.totalMem/1048576, avail=mi.availMem/1048576;
        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
        int battery=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean saver=((PowerManager)getSystemService(POWER_SERVICE)).isPowerSaveMode();
        float hz=getWindowManager().getDefaultDisplay().getRefreshRate();
        stats.setText("RAM: "+(total-avail)+" / "+total+" MB used\nBattery: "+battery+"%\nRefresh: "+String.format(Locale.US,"%.0f",hz)+" Hz\nPower saver: "+(saver?"ON":"OFF"));
    }

    void launchFF(){
        String[] pkgs={"com.dts.freefireth","com.dts.freefiremax"};
        for(String p:pkgs){try{Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null){startActivity(i);status.setText("● FREE FIRE LAUNCHED");return;}}catch(Exception ignored){}}
        Toast.makeText(this,"Free Fire is not installed",Toast.LENGTH_SHORT).show();
    }
    void openIntent(String action){try{startActivity(new Intent(action));}catch(Exception e){Toast.makeText(this,"This Android version does not provide this page",Toast.LENGTH_SHORT).show();}}
    void batteryOptimization(){
        try{Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);i.setData(Uri.parse("package:"+getPackageName()));startActivity(i);}
        catch(Exception e){openIntent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);}
    }
    void thermalInfo(){
        if(Build.VERSION.SDK_INT>=29){PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);int s=pm.getCurrentThermalStatus();String x=s==PowerManager.THERMAL_STATUS_NONE?"NORMAL":s==PowerManager.THERMAL_STATUS_LIGHT?"LIGHT":s==PowerManager.THERMAL_STATUS_MODERATE?"MODERATE":s==PowerManager.THERMAL_STATUS_SEVERE?"SEVERE":s==PowerManager.THERMAL_STATUS_CRITICAL?"CRITICAL":"EMERGENCY";status.setText("● THERMAL: "+x);}
        else status.setText("● THERMAL API NOT AVAILABLE");
    }
    void displayInfo(){float hz=getWindowManager().getDefaultDisplay().getRefreshRate();Toast.makeText(this,String.format(Locale.US,"Current refresh rate: %.0f Hz",hz),Toast.LENGTH_SHORT).show();}
}
