package com.t3r0za.mobile;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.io.File;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root;
    TextView status;
    TextView stats;
    TextView statusDetail;
    Handler h=new Handler(Looper.getMainLooper());
    Runnable ticker;
    Switch monitorSwitch;
    Switch screenSwitch;

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}

    TextView tv(String s,float sp){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(242,246,248));
        return t;
    }

    GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    Button btn(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setTextSize(13);
        b.setTextColor(Color.rgb(242,246,248));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),0,dp(10),0);
        b.setBackground(bg(Color.rgb(20,36,45),16));
        return b;
    }

    LinearLayout.LayoutParams lp(int t,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(t),0,dp(b));
        return p;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(7,16,20));
        getWindow().setNavigationBarColor(Color.rgb(7,16,20));
        build();
        updateStats();
        startMonitor();
    }

    void build(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(20),dp(18),dp(28));
        root.setBackgroundColor(Color.rgb(7,16,20));

        LinearLayout hero=card(Color.rgb(13,23,29));
        TextView title=tv("T3R0ZA",31);
        title.setTextColor(Color.rgb(39,215,165));
        title.setTypeface(null,1);
        hero.addView(title);
        TextView sub=tv("PHONE PERFORMANCE PANEL",11);
        sub.setTextColor(Color.rgb(141,160,173));
        hero.addView(sub,lp(2,0));
        status=tv("● READY",14);
        status.setTextColor(Color.rgb(39,215,165));
        hero.addView(status,lp(14,0));
        statusDetail=tv("سیستم آماده است",11);
        statusDetail.setTextColor(Color.rgb(174,190,198));
        hero.addView(statusDetail,lp(4,0));
        root.addView(hero,lp(0,12));

        root.addView(section("LIVE METRICS"),lp(4,6));
        LinearLayout metricCard=card(Color.rgb(13,23,29));
        stats=tv("",13);
        stats.setTextColor(Color.rgb(210,222,228));
        metricCard.addView(stats);
        root.addView(metricCard,lp(0,10));

        root.addView(section("CONTROL"),lp(4,6));

        LinearLayout row=toggleRow("LIVE MONITOR","اندازه‌گیری لحظه‌ای وضعیت دستگاه",true);
        monitorSwitch=(Switch)((LinearLayout)row.getChildAt(0)).getChildAt(1);
        monitorSwitch.setOnCheckedChangeListener((v,checked)->{
            if(checked){
                startMonitor();
                setStatus("● MONITOR ON","پایش لحظه‌ای فعال شد",true);
            }else{
                stopMonitor();
                setStatus("● MONITOR OFF","پایش لحظه‌ای خاموش شد",false);
            }
        });
        root.addView(row,lp(0,8));

        row=toggleRow("KEEP SCREEN AWAKE","تا وقتی T3R0ZA باز است صفحه خاموش نشود",false);
        screenSwitch=(Switch)((LinearLayout)row.getChildAt(0)).getChildAt(1);
        screenSwitch.setOnCheckedChangeListener((v,checked)->{
            if(checked){
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setStatus("● SCREEN AWAKE","جلوگیری از خاموش‌شدن صفحه فعال شد",true);
            }else{
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setStatus("● SCREEN NORMAL","حالت عادی صفحه برگشت",true);
            }
        });
        root.addView(row,lp(0,10));

        Button launch=btn("اجرای مستقیم FREE FIRE");
        launch.setOnClickListener(v->launchFF());
        root.addView(launch,lp(0,8));

        Button game=btn("باز کردن تنظیمات Game اندروید");
        game.setOnClickListener(v->openIntent("android.settings.GAME_SETTINGS"));
        root.addView(game,lp(0,8));

        Button battery=btn("تنظیمات باتری");
        battery.setOnClickListener(v->openIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        root.addView(battery,lp(0,8));

        Button optimization=btn("Battery Optimization");
        optimization.setOnClickListener(v->batteryOptimization());
        root.addView(optimization,lp(0,8));

        Button thermal=btn("بررسی دما و Thermal");
        thermal.setOnClickListener(v->thermalInfo());
        root.addView(thermal,lp(0,8));

        Button display=btn("بررسی Refresh Rate");
        display.setOnClickListener(v->displayInfo());
        root.addView(display,lp(0,8));

        Button network=btn("بررسی Network");
        network.setOnClickListener(v->networkInfo());
        root.addView(network,lp(0,8));

        Button refresh=btn("به‌روزرسانی همه اطلاعات");
        refresh.setOnClickListener(v->updateStats());
        root.addView(refresh,lp(0,12));

        TextView note=tv("هر گزینه فقط کاری را انجام می‌دهد که اندروید واقعاً اجازه بدهد؛ عدد ساختگی یا FPS تقلبی نمایش داده نمی‌شود.",10);
        note.setTextColor(Color.rgb(127,145,154));
        root.addView(note,lp(8,0));

        scroll.addView(root);
        setContentView(scroll);
    }

    TextView section(String s){
        TextView t=tv(s,12);
        t.setTextColor(Color.rgb(86,183,255));
        t.setTypeface(null,1);
        return t;
    }

    LinearLayout card(int color){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16),dp(14),dp(16),dp(14));
        l.setBackground(bg(color,18));
        return l;
    }

    LinearLayout toggleRow(String title,String desc,boolean checked){
        LinearLayout outer=card(Color.rgb(13,23,29));
        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView a=tv(title,13);
        a.setTypeface(null,1);
        textBox.addView(a);
        TextView d=tv(desc,10);
        d.setTextColor(Color.rgb(141,160,173));
        textBox.addView(d,lp(3,0));
        line.addView(textBox,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);
        sw.setChecked(checked);
        line.addView(sw,new LinearLayout.LayoutParams(-2,-2));
        outer.addView(line);
        return outer;
    }

    void setStatus(String a,String b,boolean good){
        status.setText(a);
        status.setTextColor(good?Color.rgb(39,215,165):Color.rgb(255,107,107));
        statusDetail.setText(b);
    }

    void startMonitor(){
        stopMonitor();
        ticker=()->{updateStats();h.postDelayed(ticker,2000);};
        h.post(ticker);
    }

    void stopMonitor(){
        if(ticker!=null) h.removeCallbacks(ticker);
        ticker=null;
    }

    @Override protected void onDestroy(){
        stopMonitor();
        super.onDestroy();
    }

    void updateStats(){
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total=mi.totalMem/1048576;
        long avail=mi.availMem/1048576;
        long used=total-avail;

        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
        int battery=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        boolean saver=pm.isPowerSaveMode();

        float hz=getWindowManager().getDefaultDisplay().getRefreshRate();

        StatFs fs=new StatFs(getFilesDir().getAbsolutePath());
        long freeMb=(fs.getAvailableBlocksLong()*fs.getBlockSizeLong())/1048576;

        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        String net="OFFLINE";
        if(Build.VERSION.SDK_INT>=23){
            Network n=cm.getActiveNetwork();
            NetworkCapabilities nc=cm.getNetworkCapabilities(n);
            if(nc!=null){
                if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) net="WIFI";
                else if(nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) net="MOBILE";
                else net="CONNECTED";
            }
        }

        String thermal="N/A";
        if(Build.VERSION.SDK_INT>=29){
            int s=pm.getCurrentThermalStatus();
            thermal=s==PowerManager.THERMAL_STATUS_NONE?"NORMAL":s==PowerManager.THERMAL_STATUS_LIGHT?"LIGHT":s==PowerManager.THERMAL_STATUS_MODERATE?"MODERATE":s==PowerManager.THERMAL_STATUS_SEVERE?"SEVERE":s==PowerManager.THERMAL_STATUS_CRITICAL?"CRITICAL":"EMERGENCY";
        }

        stats.setText(
            "RAM  "+used+" / "+total+" MB\n"+
            "Battery  "+battery+"%\n"+
            "Refresh  "+String.format(Locale.US,"%.0f",hz)+" Hz\n"+
            "Storage free  "+freeMb+" MB\n"+
            "Network  "+net+"\n"+
            "Thermal  "+thermal+"\n"+
            "Power saver  "+(saver?"ON":"OFF")
        );

        if(thermal.equals("CRITICAL") || thermal.equals("EMERGENCY")){
            setStatus("● THERMAL ALERT","دمای سیستم بالاست؛ برای بازی فشار را کم کن.",false);
        }else if(saver){
            setStatus("● POWER SAVER ON","Battery Saver فعال است و ممکن است عملکرد را محدود کند.",false);
        }else{
            setStatus("● READY","وضعیت سیستم در محدوده عادی است.",true);
        }
    }

    void launchFF(){
        String[] pkgs={"com.dts.freefireth","com.dts.freefiremax"};
        for(String p:pkgs){
            try{
                Intent i=getPackageManager().getLaunchIntentForPackage(p);
                if(i!=null){
                    startActivity(i);
                    setStatus("● FREE FIRE LAUNCHED","بازی اجرا شد.",true);
                    return;
                }
            }catch(Exception ignored){}
        }
        Toast.makeText(this,"Free Fire نصب نیست",Toast.LENGTH_SHORT).show();
    }

    void openIntent(String action){
        try{
            startActivity(new Intent(action));
        }catch(Exception e){
            Toast.makeText(this,"این صفحه روی نسخه اندروید شما موجود نیست",Toast.LENGTH_SHORT).show();
        }
    }

    void batteryOptimization(){
        try{
            Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:"+getPackageName()));
            startActivity(i);
        }catch(Exception e){
            openIntent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        }
    }

    void thermalInfo(){
        if(Build.VERSION.SDK_INT>=29){
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            int s=pm.getCurrentThermalStatus();
            String x=s==PowerManager.THERMAL_STATUS_NONE?"NORMAL":s==PowerManager.THERMAL_STATUS_LIGHT?"LIGHT":s==PowerManager.THERMAL_STATUS_MODERATE?"MODERATE":s==PowerManager.THERMAL_STATUS_SEVERE?"SEVERE":s==PowerManager.THERMAL_STATUS_CRITICAL?"CRITICAL":"EMERGENCY";
            boolean good=x.equals("NORMAL") || x.equals("LIGHT");
            setStatus("● THERMAL: "+x,good?"وضعیت دما مناسب است":"دستگاه گرم است؛ کاهش فشار می‌تواند کمک کند.",good);
        }else{
            setStatus("● THERMAL API N/A","اندروید این دستگاه اطلاعات Thermal را ارائه نمی‌کند.",true);
        }
    }

    void displayInfo(){
        float hz=getWindowManager().getDefaultDisplay().getRefreshRate();
        Toast.makeText(this,String.format(Locale.US,"Refresh Rate: %.0f Hz",hz),Toast.LENGTH_SHORT).show();
    }

    void networkInfo(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        String x="آفلاین";
        if(Build.VERSION.SDK_INT>=23){
            Network n=cm.getActiveNetwork();
            NetworkCapabilities nc=cm.getNetworkCapabilities(n);
            if(nc!=null){
                if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) x="Wi‑Fi متصل";
                else if(nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) x="دیتای موبایل متصل";
                else x="شبکه متصل";
            }
        }
        Toast.makeText(this,x,Toast.LENGTH_SHORT).show();
    }
}