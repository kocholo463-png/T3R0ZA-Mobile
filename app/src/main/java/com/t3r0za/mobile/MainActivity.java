package com.t3r0za.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    static volatile MainActivity instance;
    final int BG=Color.rgb(6,12,17);
    final int CARD=Color.rgb(13,23,30);
    final int CARD2=Color.rgb(17,31,39);
    final int TEXT=Color.rgb(239,245,248);
    final int MUTED=Color.rgb(139,157,169);
    final int ACCENT=Color.rgb(39,215,165);
    final int BLUE=Color.rgb(86,183,255);
    final int WARN=Color.rgb(255,181,71);
    final int BAD=Color.rgb(255,104,104);
    LinearLayout root;
    TextView status;
    TextView detail;
    TextView metrics;
    Handler handler=new Handler(Looper.getMainLooper());
    Runnable ticker;
    long lastFrameNs;
    int frameCount;
    float labFps;
    boolean runningLab=false;
    boolean dnsLaunchBusy=false;
    String pendingDns=null;
    boolean autoPerformance=true;
    boolean liveInput=true;
    boolean stableDns=false;
    boolean thermalGuard=true;
    boolean miniPanel=false;
    int telemetryIntervalMs=1200;

    int dp(float v){
        return (int)(v*getResources().getDisplayMetrics().density+0.5f);
    }

    TextView tv(String s,float sp){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        return t;
    }

    GradientDrawable bg(int color,float r){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    LinearLayout card(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16),dp(14),dp(16),dp(14));
        l.setBackground(bg(CARD,18));
        return l;
    }

    LinearLayout.LayoutParams lp(int top,int bottom){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(top),0,dp(bottom));
        return p;
    }

    Button btn(String text){
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setPadding(dp(10),0,dp(10),0);
        b.setBackground(bg(CARD2,16));
        return b;
    }

    TextView section(String s){
        TextView t=tv(s,12);
        t.setTextColor(BLUE);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        instance=this;
        loadCorePreferences();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        requestBestRefresh();
        buildHome();
        startTelemetry();
    }

    void loadCorePreferences(){
        android.content.SharedPreferences p=getSharedPreferences("t3r0za",MODE_PRIVATE);
        autoPerformance=p.getBoolean("autoPerformance",autoPerformance);
        liveInput=p.getBoolean("liveInput",liveInput);
        stableDns=p.getBoolean("stableDns",stableDns);
        thermalGuard=p.getBoolean("thermalGuard",thermalGuard);
    }

    static void applyMiniSwitch(String key,boolean value){
        MainActivity a=instance;
        if(a!=null) a.applyMiniSwitchInternal(key,value);
    }

    void applyMiniSwitchInternal(String key,boolean value){
        getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean(key,value).apply();
        if("autoPerformance".equals(key)){
            autoPerformance=value;
            if(value){
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                requestBestRefresh();
            }else{
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }else if("liveInput".equals(key)){
            liveInput=value;
        }else if("stableDns".equals(key)){
            stableDns=value;
        }else if("thermalGuard".equals(key)){
            thermalGuard=value;
        }
    }

    void requestBestRefresh(){
        try{
            Display d=getWindowManager().getDefaultDisplay();
            float best=d.getRefreshRate();
            if(Build.VERSION.SDK_INT>=30){
                Window w=getWindow();
                WindowManager.LayoutParams p=w.getAttributes();
                p.preferredRefreshRate=best;
                w.setAttributes(p);
            }
        }catch(Exception ignored){}
    }

    void buildHome(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(30));
        root.setBackgroundColor(BG);

        LinearLayout hero=card();
        TextView title=tv("T3R0ZA",32);
        title.setTextColor(ACCENT);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        hero.addView(title);
        pulseTitle(title);
        TextView sub=tv("PHONE HEADSHOT + PERFORMANCE LAB",11);
        sub.setTextColor(MUTED);
        hero.addView(sub,lp(2,0));
        status=tv("● READY",14);
        status.setTextColor(ACCENT);
        hero.addView(status,lp(14,0));
        detail=tv("اندازه‌گیری واقعی لمس، دما، حافظه و نرخ نوسازی",10);
        detail.setTextColor(MUTED);
        hero.addView(detail,lp(4,0));
        root.addView(hero);

        root.addView(section("LIVE DEVICE TELEMETRY"),lp(10,6));
        LinearLayout mc=card();
        metrics=tv("",12);
        metrics.setTextColor(TEXT);
        mc.addView(metrics);
        root.addView(mc,lp(0,8));

        root.addView(section("HEADSHOT LAB"),lp(10,6));
        LinearLayout hc=card();
        TextView ht=tv("Manual Aim Calibration",18);
        ht.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        hc.addView(ht);
        TextView hd=tv("این بخش مسیر واقعی لمس خودت را اندازه می‌گیرد و برای کم‌کردن رد شدن از سر، overshoot و undershoot را تحلیل می‌کند.",10);
        hd.setTextColor(MUTED);
        hc.addView(hd,lp(3,10));
        Button lab=btn("🎯 START HEADSHOT CALIBRATION");
        lab.setTextSize(15);
        lab.setTextColor(ACCENT);
        lab.setBackground(bg(Color.rgb(12,48,42),18));
        lab.setOnClickListener(v->showHeadshotLab());
        hc.addView(lab,lp(0,8));
        root.addView(hc);

        root.addView(section("DNS STABILITY LAB"),lp(10,6));
        LinearLayout dc=card();
        TextView dt=tv("Resolver Benchmark + Fallback",18);
        dt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        dc.addView(dt);
        TextView dd=tv("چند resolver واقعی را از مسیر فعلی شبکه تست می‌کند و latency، packet loss و پایداری را مقایسه می‌کند. برنامه هنگام بازی DNS را مدام عوض نمی‌کند تا باعث reconnect و timeout نشود.",10);
        dd.setTextColor(MUTED);
        dc.addView(dd,lp(3,10));
        Button db=btn("🌐 RUN DNS STABILITY TEST");
        db.setTextColor(ACCENT);
        db.setOnClickListener(v->runDnsBenchmark());
        dc.addView(db,lp(0,8));
        root.addView(dc);

        root.addView(section("PERFORMANCE CONTROL"),lp(10,6));
        LinearLayout pc=card();

        Button ready=btn("⚡ GAME READY CHECK");
        ready.setOnClickListener(v->gameReady());
        pc.addView(ready);

        Button max=btn("⟳ REQUEST MAX DISPLAY REFRESH");
        max.setOnClickListener(v->{
            requestBestRefresh();
            updateTelemetry();
            setState("● DISPLAY REFRESH REQUESTED","برای پنل آزمایش، بیشترین نرخ نوسازی گزارش‌شده درخواست شد.",true);
        });
        pc.addView(max,lp(0,8));

        Button scan=btn("🔎 FULL PERFORMANCE SCAN");
        scan.setOnClickListener(v->fullPerformanceScan());
        pc.addView(scan,lp(0,8));

        Button launch=btn("▶ LAUNCH FREE FIRE + AUTO DNS SESSION");
        launch.setOnClickListener(v->{ if(stableDns) launchWithBestDns(); else launchFF(); });
        pc.addView(launch,lp(0,8));

        Button stopDns=btn("■ STOP DNS SESSION");
        stopDns.setOnClickListener(v->stopDnsSession());
        pc.addView(stopDns,lp(0,8));

        Button mini=btn("🎮 ENABLE MINI IN-GAME PANEL");
        mini.setOnClickListener(v->toggleMiniPanel());
        pc.addView(mini,lp(0,8));

        Button usage=btn("⚙ ENABLE AUTO-STOP PERMISSION");
        usage.setOnClickListener(v->openUsageAccess());
        pc.addView(usage,lp(0,8));

        Button battery=btn("🔋 REQUEST BATTERY OPTIMIZATION EXEMPTION");
        battery.setOnClickListener(v->requestBatteryOptimizationExemption());
        pc.addView(battery,lp(0,8));

        root.addView(pc);

        root.addView(section("T3R0ZA CONTROL CORE"),lp(10,6));
        LinearLayout cc=card();

        addSwitchRow(cc,"AUTO PERFORMANCE SESSION","پایش و درخواست نرخ نوسازی مناسب؛ بدون تغییر فایل یا حافظه بازی.",autoPerformance,v->{
            autoPerformance=v;
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean("autoPerformance",v).apply();
            if(v){
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                requestBestRefresh();
                setState("● PERFORMANCE CORE ON","پایش عملکرد فعال است؛ فقط قابلیت‌های مجاز Android استفاده می‌شوند.",true);
            }else{
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                setState("● PERFORMANCE CORE OFF","پایش خودکار غیرفعال شد.",true);
            }
        });

        addSwitchRow(cc,"LIVE INPUT MONITOR","اندازه‌گیری Touch event، jitter و touch-to-next-frame در پنل.",liveInput,v->{
            liveInput=v;
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean("liveInput",v).apply();
            setState(v?"● INPUT MONITOR ON":"● INPUT MONITOR OFF",v?"اندازه‌گیری ورودی فعال شد.":"اندازه‌گیری ورودی غیرفعال شد.",true);
        });

        addSwitchRow(cc,"MINI IN-GAME PANEL","پنل کوچک روی بازی؛ فقط کنترل‌های واقعی T3R0ZA مثل DNS، مانیتور و بازگشت به پنل.",miniPanel,v->{
            miniPanel=v;
            if(v) startMiniPanelIfAllowed(); else stopMiniPanel();
        });

        addSwitchRow(cc,"STABLE DNS SESSION","DNS فقط در صورت تأیید کاربر و برای Session مشخص؛ بدون تعویض مداوم.",stableDns,v->{
            stableDns=v;
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean("stableDns",v).apply();
            setState(v?"● DNS SESSION ARMED":"● DNS SESSION OFF",v?"برای Session بعدی DNS پایدار فعال است.":"DNS VPN خودکار غیرفعال شد.",true);
        });

        addSwitchRow(cc,"THERMAL GUARD","در فشار حرارتی بالا هشدار می‌دهد و از ادعای Boost غیرواقعی جلوگیری می‌کند.",thermalGuard,v->{
            thermalGuard=v;
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean("thermalGuard",v).apply();
            setState(v?"● THERMAL GUARD ON":"● THERMAL GUARD OFF",v?"محافظ حرارتی فعال است.":"محافظ حرارتی غیرفعال شد.",true);
        });

        addSliderRow(cc,"MONITORING DEPTH","سبک", "عمیق", telemetryIntervalMs, 500, 3000, value->{
            telemetryIntervalMs=value;
        });

        addSliderRow(cc,"DISPLAY REQUEST","سیستم", "بالاترین", 100, 0, 100, value->{
            if(value>=70) requestBestRefresh();
        });

        root.addView(cc);

        TextView note=tv("پنل داخل بازی فقط یک کنترل سریع سیستم است: DNS session، وضعیت مانیتور و بازگشت به T3R0ZA. هیچ لمس خودکار، auto-aim، auto-shoot، تزریق یا تغییر فایل بازی انجام نمی‌شود.",9);
        note.setTextColor(MUTED);
        root.addView(note,lp(12,0));

        scroll.addView(root);
        setContentView(scroll);
    }

    void pulseTitle(TextView title){
        title.animate().alpha(0.62f).setDuration(900).withEndAction(()->{
            title.animate().alpha(1f).setDuration(900).withEndAction(()->pulseTitle(title)).start();
        }).start();
    }

    void addSwitchRow(LinearLayout parent,String title,String desc,boolean checked,CompoundButton.OnCheckedChangeListener listener){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout textBox=new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        TextView t=tv(title,13);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        textBox.addView(t);
        TextView d=tv(desc,9);
        d.setTextColor(MUTED);
        textBox.addView(d,lp(2,0));
        row.addView(textBox,new LinearLayout.LayoutParams(0,-2,1));
        Switch sw=new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.addView(sw);
        parent.addView(row,lp(2,8));
    }

    void addSliderRow(LinearLayout parent,String title,String left,String right,int value,int min,int max,IntChangeListener listener){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t=tv(title,13);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(t);
        SeekBar seek=new SeekBar(this);
        seek.setMax(max-min);
        seek.setProgress(Math.max(0,Math.min(max-min,value-min)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){ if(fromUser) listener.accept(p+min); }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        box.addView(seek);
        LinearLayout labels=new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        TextView l=tv(left,9); l.setTextColor(MUTED);
        TextView r=tv(right,9); r.setTextColor(MUTED);
        labels.addView(l,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(r);
        box.addView(labels);
        parent.addView(box,lp(8,8));
    }

    interface IntChangeListener { void onChange(int value); }

    void setState(String a,String b,boolean good){
        if(status!=null){
            status.setText(a);
            status.setTextColor(good?ACCENT:BAD);
        }
        if(detail!=null) detail.setText(b);
    }

    void startTelemetry(){
        stopTelemetry();
        ticker=()->{
            updateTelemetry();
            handler.postDelayed(ticker,telemetryIntervalMs);
        };
        handler.post(ticker);
    }

    void stopTelemetry(){
        if(ticker!=null) handler.removeCallbacks(ticker);
        ticker=null;
    }

    void updateTelemetry(){
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long total=mi.totalMem/1048576L;
        long avail=mi.availMem/1048576L;
        long used=total-avail;

        BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
        int batt=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        boolean saver=pm.isPowerSaveMode();
        String thermal=thermalName(pm);

        float hz=getWindowManager().getDefaultDisplay().getRefreshRate();
        boolean ignoring=false;
        try{ignoring=pm.isIgnoringBatteryOptimizations(getPackageName());}catch(Exception ignored){}

        metrics.setText(
            "RAM       "+used+" / "+total+" MB\\n"+
            "BATTERY   "+batt+"%\\n"+
            "REFRESH   "+String.format(Locale.US,"%.0f",hz)+" Hz\\n"+
            "LAB FPS   "+(labFps>0?String.format(Locale.US,"%.1f",labFps):"--")+" FPS\\n"+
            "THERMAL   "+thermal+"\\n"+
            "POWER     "+(saver?"SAVER ON":"NORMAL")+"\\n"+
            "BATTERY OPT  "+(ignoring?"BYPASS ACTIVE":"SYSTEM MANAGED")
        );

        if(thermalGuard && (thermal.equals("CRITICAL")||thermal.equals("EMERGENCY"))){
            setState("● THERMAL ALERT","دستگاه در محدوده فشار حرارتی بالا است؛ کالیبراسیون را بعد از خنک‌شدن انجام بده.",false);
        }else if(saver){
            setState("● POWER SAVER ON","Battery Saver می‌تواند عملکرد بازی را محدود کند.",false);
        }else{
            setState("● READY","داده‌های عملکردی دستگاه لحظه‌ای در حال به‌روزرسانی هستند.",true);
        }
    }

    String thermalName(PowerManager pm){
        if(Build.VERSION.SDK_INT<29) return "N/A";
        int s=pm.getCurrentThermalStatus();
        if(s==PowerManager.THERMAL_STATUS_NONE) return "NORMAL";
        if(s==PowerManager.THERMAL_STATUS_LIGHT) return "LIGHT";
        if(s==PowerManager.THERMAL_STATUS_MODERATE) return "MODERATE";
        if(s==PowerManager.THERMAL_STATUS_SEVERE) return "SEVERE";
        if(s==PowerManager.THERMAL_STATUS_CRITICAL) return "CRITICAL";
        return "EMERGENCY";
    }

    void fullPerformanceScan(){
        setState("● FULL SCAN RUNNING","در حال بررسی وضعیت واقعی دستگاه و بسته Free Fire...",true);
        new Thread(()->{
            String gamePackage=findFreeFirePackage();
            String gameState=gamePackage==null?"NOT INSTALLED":"INSTALLED: "+gamePackage;
            String gameVersion="--";
            String targetSdk="--";
            if(gamePackage!=null){
                try{
                    android.content.pm.PackageInfo pi=getPackageManager().getPackageInfo(gamePackage,0);
                    gameVersion=pi.versionName==null?"--":pi.versionName;
                    targetSdk=String.valueOf(pi.applicationInfo.targetSdkVersion);
                }catch(Exception ignored){}
            }

            ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total=mi.totalMem/1048576L;
            long avail=mi.availMem/1048576L;
            long used=total-avail;

            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            String thermal=thermalName(pm);
            boolean saver=pm.isPowerSaveMode();
            float hz=getWindowManager().getDefaultDisplay().getRefreshRate();

            String batteryOpt="UNKNOWN";
            try{
                batteryOpt=pm.isIgnoringBatteryOptimizations(getPackageName())?"IGNORED":"SYSTEM";
            }catch(Exception ignored){}

            String result=
                "FREE FIRE\n"+
                "Package: "+gameState+"\n"+
                "Version: "+gameVersion+"\n"+
                "Target SDK: "+targetSdk+"\n\n"+
                "DEVICE\n"+
                "RAM free: "+avail+" MB / "+total+" MB\n"+
                "RAM used: "+used+" MB\n"+
                "Refresh: "+String.format(Locale.US,"%.0f",hz)+" Hz\n"+
                "Thermal: "+thermal+"\n"+
                "Power saver: "+(saver?"ON":"OFF")+"\n"+
                "T3R0ZA battery policy: "+batteryOpt+"\n\n"+
                "RESULT\n"+
                buildScanVerdict(thermal,saver,avail,total,hz,gamePackage!=null);

            handler.post(()->{
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("T3R0ZA PERFORMANCE SCAN")
                    .setMessage(result)
                    .setPositiveButton("OK",null)
                    .show();
                boolean good=gamePackage!=null && !saver &&
                    (thermal.equals("NORMAL")||thermal.equals("LIGHT")) &&
                    avail>Math.max(300,total/10);
                setState(good?"● SCAN READY":"● SCAN FOUND LIMITS",
                    good?"شرایط فعلی برای تست بازی مناسب‌تر است.":"یک یا چند عامل سیستمی می‌تواند پایداری عملکرد را محدود کند.",
                    good);
            });
        }).start();
    }

    String findFreeFirePackage(){
        String[] pkgs={"com.dts.freefireth","com.dts.freefiremax"};
        for(String p:pkgs){
            try{
                getPackageManager().getPackageInfo(p,0);
                return p;
            }catch(Exception ignored){}
        }
        return null;
    }

    String buildScanVerdict(String thermal,boolean saver,long avail,long total,float hz,boolean installed){
        StringBuilder s=new StringBuilder();
        if(!installed) s.append("Free Fire روی دستگاه پیدا نشد. ");
        if(saver) s.append("Power Saver روشن است و ممکن است عملکرد را محدود کند. ");
        if(thermal.equals("SEVERE")||thermal.equals("CRITICAL")||thermal.equals("EMERGENCY")){
            s.append("فشار حرارتی بالاست و حفظ FPS پایدار سخت‌تر می‌شود. ");
        }else if(thermal.equals("MODERATE")){
            s.append("دما متوسط است؛ پایداری را زیر بار دوباره بررسی کن. ");
        }
        if(avail<Math.max(300,total/10)) s.append("RAM آزاد کم است. ");
        if(hz>0 && hz<90) s.append("نرخ نوسازی گزارش‌شده زیر 90Hz است. ");
        if(s.length()==0) s.append("محدودیت واضحی از داده‌های قابل‌دسترسی سیستم دیده نشد. ");
        s.append("این اسکن به فایل‌ها یا حافظه داخلی Free Fire دسترسی نمی‌گیرد و تنظیمات/فایل‌های بازی را دستکاری نمی‌کند.");
        return s.toString();
    }

    void gameReady(){
        requestBestRefresh();
        updateTelemetry();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        String thermal=thermalName(pm);
        if(thermal.equals("NORMAL")||thermal.equals("LIGHT")){
            setState("● GAME READY","رفرش و وضعیت حرارتی برای شروع تست مناسب‌تر است.",true);
        }else{
            setState("● GAME READY: WATCH","قبل از تست هدشات، ابتدا فشار حرارتی را پایین بیاور.",false);
        }
    }

    static final String[] DNS_SERVERS={
        "1.1.1.1","1.0.0.1","8.8.8.8","8.8.4.4","9.9.9.9","149.112.112.112",
        "208.67.222.222","208.67.220.220","94.140.14.14","94.140.15.15",
        "76.76.2.0","76.76.10.0","185.228.168.9","185.228.169.9",
        "1.1.1.2","1.0.0.2","9.9.9.10","149.112.112.10","208.67.222.123","208.67.220.123"
    };

    void runDnsBenchmark(){
        benchmarkBestDns(false);
    }

    void launchWithBestDns(){
        if(dnsLaunchBusy){
            Toast.makeText(this,"DNS session is already starting",Toast.LENGTH_SHORT).show();
            return;
        }
        dnsLaunchBusy=true;
        setState("● DNS BENCHMARK","قبل از بازشدن بازی، resolverهای واقعی یک‌بار تست می‌شوند...",true);
        new Thread(()->{
            String best=benchmarkAndChoose();
            handler.post(()->{
                dnsLaunchBusy=false;
                if(best==null){
                    setState("● DNS NOT SELECTED","هیچ resolver قابل‌اعتمادی از شبکه فعلی پاسخ نداد؛ بازی بدون DNS VPN اجرا می‌شود.",false);
                    launchFF();
                    return;
                }
                pendingDns=best;
                setState("● DNS SELECTED",best+" انتخاب شد؛ قبل از ورود به بازی ثابت می‌ماند.",true);
                Intent prep=VpnService.prepare(MainActivity.this);
                if(prep!=null){
                    startActivityForResult(prep,VPN_REQUEST);
                }else{
                    startDnsVpnAndLaunch();
                }
            });
        }).start();
    }

    static final int VPN_REQUEST=7001;

    void benchmarkBestDns(boolean showResult){
        setState("● DNS TEST RUNNING","resolverهای واقعی با چند درخواست و معیار پایداری بررسی می‌شوند...",true);
        new Thread(()->{
            ArrayList<DnsBenchResult> results=benchmarkDnsResults();
            DnsBenchResult best=results.isEmpty()?null:results.get(0);
            final String result=buildDnsBenchmarkReport(results);
            handler.post(()->{
                if(showResult || best!=null){
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("DNS STABILITY RESULT")
                        .setMessage(result)
                        .setPositiveButton("OK",null)
                        .show();
                }
                setState(best==null?"● DNS TEST FAILED":"● DNS TEST COMPLETE",
                    best==null?"resolver قابل‌اعتماد پیدا نشد.":"بهترین resolver بر اساس latency، success rate و jitter انتخاب شد.",
                    best!=null);
            });
        }).start();
    }

    String benchmarkAndChoose(){
        ArrayList<DnsBenchResult> results=benchmarkDnsResults();
        return results.isEmpty()?null:results.get(0).server;
    }

    ArrayList<DnsBenchResult> benchmarkDnsResults(){
        ArrayList<DnsBenchResult> results=new ArrayList<>();
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(8,DNS_SERVERS.length));
        ArrayList<Future<DnsBenchResult>> futures=new ArrayList<>();

        for(String server:DNS_SERVERS){
            futures.add(pool.submit(new Callable<DnsBenchResult>(){
                @Override public DnsBenchResult call(){
                    return measureDnsServer(server);
                }
            }));
        }

        for(Future<DnsBenchResult> future:futures){
            try{
                DnsBenchResult r=future.get(9000,TimeUnit.MILLISECONDS);
                if(r!=null && r.successCount>0) results.add(r);
            }catch(Exception ignored){}
        }

        pool.shutdownNow();
        Collections.sort(results,(a,b)->{
            int scoreCompare=Double.compare(b.score,a.score);
            if(scoreCompare!=0) return scoreCompare;
            int successCompare=Integer.compare(b.successCount,a.successCount);
            if(successCompare!=0) return successCompare;
            return Long.compare(a.avgMs,b.avgMs);
        });
        return results;
    }

    DnsBenchResult measureDnsServer(String server){
        DnsBenchResult r=new DnsBenchResult(server);
        String[] hosts={"example.com","connectivitycheck.gstatic.com"};
        int rounds=3;

        for(int round=0;round<rounds;round++){
            for(String host:hosts){
                long ms=dnsProbe(server,host,900);
                r.totalCount++;
                if(ms>=0){
                    r.successCount++;
                    r.latencies.add(ms);
                }
            }
        }

        if(r.successCount==0){
            r.avgMs=Long.MAX_VALUE;
            r.jitterMs=Long.MAX_VALUE;
            r.score=0;
            return r;
        }

        long sum=0;
        for(long ms:r.latencies) sum+=ms;
        r.avgMs=sum/r.latencies.size();

        long deviationSum=0;
        for(long ms:r.latencies) deviationSum+=Math.abs(ms-r.avgMs);
        r.jitterMs=deviationSum/r.latencies.size();
        double successRate=r.successCount/(double)r.totalCount;
        double latencyFactor=1.0/(1.0+(r.avgMs/80.0));
        double jitterFactor=1.0/(1.0+(r.jitterMs/40.0));
        r.score=(successRate*0.55)+(latencyFactor*0.30)+(jitterFactor*0.15);
        return r;
    }

    String buildDnsBenchmarkReport(ArrayList<DnsBenchResult> results){
        if(results.isEmpty()){
            return "هیچ resolverی پاسخ قابل‌اعتماد نداد.\n\nاتصال فعلی یا شبکه را بررسی کن.";
        }

        StringBuilder s=new StringBuilder();
        DnsBenchResult best=results.get(0);
        s.append("SELECTED DNS\\n");
        s.append(best.server).append("\\n");
        s.append("Score: ").append(String.format(Locale.US,"%.3f",best.score)).append("\\n");
        s.append("Success: ").append(best.successCount).append("/").append(best.totalCount).append("\\n");
        s.append("Average: ").append(best.avgMs).append(" ms\\n");
        s.append("Jitter: ").append(best.jitterMs).append(" ms\\n\\n");
        s.append("TOP STABLE RESOLVERS\\n");

        int count=Math.min(5,results.size());
        for(int i=0;i<count;i++){
            DnsBenchResult r=results.get(i);
            s.append(i+1).append(". ").append(r.server)
                .append("  ")
                .append(String.format(Locale.US,"%.3f",r.score))
                .append("  ")
                .append(r.successCount).append("/").append(r.totalCount)
                .append("  ")
                .append(r.avgMs).append("ms")
                .append("  J").append(r.jitterMs).append("ms\\n");
        }

        s.append("\\nاین اعداد DNS lookup هستند، نه ping داخل مچ. ");
        s.append("حین بازی DNS انتخاب‌شده عوض نمی‌شود.");
        return s.toString();
    }

    static class DnsBenchResult{
        String server;
        int totalCount;
        int successCount;
        long avgMs;
        long jitterMs;
        double score;
        ArrayList<Long> latencies=new ArrayList<>();

        DnsBenchResult(String server){
            this.server=server;
        }
    }

    void startDnsVpnAndLaunch(){
        if(pendingDns==null || pendingDns.isEmpty()){
            launchFF();
            return;
        }
        getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putString("selected_dns",pendingDns).apply();
        Intent i=new Intent(this,DnsTunnelService.class);
        i.putExtra(DnsTunnelService.EXTRA_DNS,pendingDns);
        try{
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            setState("● DNS CONNECTING","DNS ثابت "+pendingDns+" در حال برقراری است؛ وضعیت Tunnel قبل از اجرای بازی تأیید می‌شود.",true);
            waitForDnsTunnelThenLaunch(0);
        }catch(Exception e){
            setState("● DNS SESSION FAILED","VPN سیستم اجازه شروع نداد؛ بازی بدون این DNS اجرا می‌شود.",false);
            launchFF();
        }
    }

    void waitForDnsTunnelThenLaunch(int attempt){
        handler.postDelayed(()->{
            DnsTunnelService s=DnsTunnelService.instance;
            boolean ready=s!=null && s.running && s.vpnInterface!=null;
            if(ready){
                setState("● DNS READY","DNS Tunnel آماده و ثابت است؛ Free Fire اجرا شد.",true);
                launchFF();
                return;
            }
            if(attempt<5){
                waitForDnsTunnelThenLaunch(attempt+1);
                return;
            }
            try{stopService(new Intent(this,DnsTunnelService.class));}catch(Exception ignored){}
            setState("● DNS NOT READY","Tunnel به‌موقع آماده نشد؛ بازی بدون DNS VPN اجرا می‌شود تا شبکه مختل نشود.",false);
            launchFF();
        },attempt==0?450:500);
    }

    void requestBatteryOptimizationExemption(){
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        try{
            if(Build.VERSION.SDK_INT<23){
                setState("● BATTERY POLICY N/A","این نسخه Android کنترل مستقیم این سیاست را ندارد.",true);
                return;
            }
            if(pm.isIgnoringBatteryOptimizations(getPackageName())){
                setState("● BATTERY POLICY OK","T3R0ZA از محدودیت Battery Optimization خارج است.",true);
                return;
            }
            Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:"+getPackageName()));
            startActivity(i);
            setState("● BATTERY PERMISSION REQUESTED","این مجوز اختیاری است و برای پایداری سرویس‌های خود T3R0ZA درخواست می‌شود.",true);
        }catch(Exception e){
            try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception ignored){}
            setState("● BATTERY SETTINGS OPENED","تنظیمات Battery Optimization باز شد.",true);
        }
    }

    void stopDnsSession(){
        try{
            stopService(new Intent(this,DnsTunnelService.class));
            setState("● DNS SESSION OFF","DNS session قطع شد و مسیر شبکه به حالت سیستم برگشت.",true);
        }catch(Exception ignored){}
    }

    void toggleMiniPanel(){
        if(!Settings.canDrawOverlays(this)){
            try{
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));
                Toast.makeText(this,"مجوز نمایش روی بازی را یک‌بار فعال کن.",Toast.LENGTH_LONG).show();
            }catch(Exception e){
                Toast.makeText(this,"Overlay permission unavailable",Toast.LENGTH_SHORT).show();
            }
            return;
        }
        miniPanel=true;
        startMiniPanelIfAllowed();
        setState("● MINI PANEL ON","پنل کوچک روی بازی فعال شد؛ هیچ auto-aim یا کنترل خودکار لمس ندارد.",true);
    }

    void startMiniPanelIfAllowed(){
        if(!Settings.canDrawOverlays(this)) return;
        Intent i=new Intent(this,MiniPanelService.class);
        try{
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        }catch(Exception ignored){}
    }

    void stopMiniPanel(){
        try{stopService(new Intent(this,MiniPanelService.class));}catch(Exception ignored){}
    }

    void openUsageAccess(){
        try{
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }catch(Exception e){
            Toast.makeText(this,"Usage Access settings are not available",Toast.LENGTH_SHORT).show();
        }
    }

    long dnsProbe(String server,String host,int timeoutMs){
        DatagramSocket socket=null;
        try{
            byte[] query=buildDnsQuery(host);
            InetAddress address=InetAddress.getByName(server);
            socket=new DatagramSocket();
            socket.setSoTimeout(timeoutMs);
            DatagramPacket packet=new DatagramPacket(query,query.length,address,53);
            long start=System.nanoTime();
            socket.send(packet);
            byte[] buf=new byte[1500];
            DatagramPacket resp=new DatagramPacket(buf,buf.length);
            socket.receive(resp);
            long end=System.nanoTime();
            if(resp.getLength()<12) return -1;
            if(resp.getData()[0]!=query[0] || resp.getData()[1]!=query[1]) return -1;
            if((resp.getData()[2]&0x80)==0) return -1;
            return Math.max(0,(end-start)/1000000L);
        }catch(Exception e){return -1;}
        finally{if(socket!=null) socket.close();}
    }

    byte[] buildDnsQuery(String host){
        String[] labels=host.split("\\.");
        byte[] b=new byte[512];
        int p=0;
        int id=(int)(System.nanoTime()&0xffff);
        b[p++]=(byte)((id>>8)&255); b[p++]=(byte)(id&255);
        b[p++]=1; b[p++]=0;
        b[p++]=0; b[p++]=1;
        b[p++]=0; b[p++]=0; b[p++]=0; b[p++]=0; b[p++]=0; b[p++]=0;
        for(String label:labels){
            byte[] x=label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            b[p++]=(byte)x.length;
            System.arraycopy(x,0,b,p,x.length); p+=x.length;
        }
        b[p++]=0;
        b[p++]=0; b[p++]=1;
        b[p++]=0; b[p++]=1;
        byte[] q=new byte[p];
        System.arraycopy(b,0,q,0,p);
        return q;
    }

    void launchFF(){
        String[] pkgs={"com.dts.freefireth","com.dts.freefiremax"};
        for(String p:pkgs){
            try{
                Intent i=getPackageManager().getLaunchIntentForPackage(p);
                if(i!=null){
                    if(miniPanel) startMiniPanelIfAllowed();
                    stopTelemetry();
                    startActivity(i);
                    return;
                }
            }catch(Exception ignored){}
        }
        Toast.makeText(this,"Free Fire پیدا نشد",Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VPN_REQUEST){
            if(resultCode==RESULT_OK){
                startDnsVpnAndLaunch();
            }else{
                setState("● VPN NOT APPROVED","بدون تأیید VPN، DNS سیستم تغییر نمی‌کند؛ بازی عادی اجرا می‌شود.",false);
                launchFF();
            }
        }
    }

    @Override protected void onResume(){
        super.onResume();
        loadCorePreferences();
        requestBestRefresh();
        if(miniPanel && Settings.canDrawOverlays(this)) startMiniPanelIfAllowed();
        if(root!=null && !runningLab) startTelemetry();
    }

    @Override protected void onPause(){
        super.onPause();
        stopTelemetry();
    }

    @Override protected void onDestroy(){
        stopTelemetry();
        if(instance==this) instance=null;
        super.onDestroy();
    }

    void showHeadshotLab(){
        runningLab=true;
        stopTelemetry();
        HeadshotView hv=new HeadshotView();
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(14),dp(16),dp(14),dp(18));

        LinearLayout top=card();
        TextView title=tv("HEADSHOT CALIBRATION",22);
        title.setTextColor(ACCENT);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(title);
        TextView info=tv("۱۲ حرکت دستی. هدف را با انگشت بگیر؛ برنامه سرعت، overshoot، undershoot، خطای پایان و نرخ نمونه‌برداری لمس را واقعی اندازه می‌گیرد.",10);
        info.setTextColor(MUTED);
        top.addView(info,lp(2,0));
        page.addView(top,lp(0,8));

        page.addView(hv,new LinearLayout.LayoutParams(-1,0,1));

        Button back=btn("← BACK TO PANEL");
        back.setOnClickListener(v->{runningLab=false;buildHome();startTelemetry();});
        page.addView(back,lp(0,8));
        setContentView(page);
    }

    class HeadshotView extends View {
        Paint p=new Paint(3);
        Paint line=new Paint(3);
        float tx,ty,sx,sy;
        boolean active;
        long downMs;
        long lastEventMs;
        long firstMoveMs;
        int samples;
        int trial=0;
        long lastTouchDispatchNs=0;
        boolean frameMeasurePending=false;
        List<Float> touchFrameMs=new ArrayList<>();
        int totalTrials=12;
        int overshoots=0;
        int undershoots=0;
        float startToTarget;
        float maxProjection;
        float finalError;
        List<Float> errors=new ArrayList<>();
        List<Float> speeds=new ArrayList<>();
        List<Float> sampleHz=new ArrayList<>();
        boolean finished=false;

        HeadshotView(){
            super(MainActivity.this);
            p.setTypeface(Typeface.DEFAULT);
            line.setStrokeWidth(dp(2));
            setBackgroundColor(Color.rgb(8,16,22));
            post(()->nextTarget());
        }

        void nextTarget(){
            if(trial>=totalTrials){
                finished=true;
                invalidate();
                return;
            }
            trial++;
            float w=getWidth();
            float h=getHeight();
            if(w<10||h<10){
                postDelayed(this::nextTarget,100);
                return;
            }
            float margin=dp(52);
            sx=w*0.5f;
            sy=h*0.78f;
            float[] xs={0.23f,0.38f,0.50f,0.62f,0.77f,0.30f,0.70f,0.42f,0.58f,0.20f,0.80f,0.50f};
            float[] ys={0.34f,0.28f,0.22f,0.28f,0.34f,0.18f,0.18f,0.12f,0.12f,0.42f,0.42f,0.08f};
            tx=Math.max(margin,Math.min(w-margin,w*xs[trial-1]));
            ty=Math.max(margin,Math.min(h-margin,h*ys[trial-1]));
            active=false;
            invalidate();
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(finished){
                drawResults(c);
                return;
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.rgb(47,74,88));
            c.drawRoundRect(dp(8),dp(8),getWidth()-dp(8),getHeight()-dp(8),dp(18),dp(18),p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(18,39,48));
            c.drawCircle(sx,sy,dp(38),p);

            p.setColor(ACCENT);
            c.drawCircle(tx,ty,dp(22),p);
            p.setColor(BG);
            c.drawCircle(tx,ty,dp(9),p);

            if(active){
                p.setColor(BLUE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(3));
                c.drawLine(sx,sy,tx,ty,p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(180,255,255,255));
                c.drawCircle(sx,sy,dp(7),p);
            }

            p.setColor(TEXT);
            p.setTextSize(dp(14));
            c.drawText("TRIAL "+trial+" / "+totalTrials,dp(18),dp(28),p);
            p.setTextSize(dp(11));
            p.setColor(MUTED);
            c.drawText("START پایین صفحه → FLICK طبیعی به نقطه سبز",dp(18),dp(48),p);
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            long now=e.getEventTime();
            float x=e.getX();
            float y=e.getY();

            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                active=true;
                if(liveInput) measureTouchToNextFrame();
                downMs=now;
                firstMoveMs=0;
                lastEventMs=now;
                samples=0;
                maxProjection=0;
                startToTarget=(float)Math.hypot(tx-x,ty-y);
                sx=x;
                sy=y;
                invalidate();
                return true;
            }

            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                if(!active) return true;
                if(liveInput) measureTouchToNextFrame();
                int n=e.getHistorySize();
                for(int i=0;i<n;i++){
                    float hx=e.getHistoricalX(i);
                    float hy=e.getHistoricalY(i);
                    recordPoint(hx,hy,e.getHistoricalEventTime(i));
                }
                recordPoint(x,y,now);
                invalidate();
                return true;
            }

            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
                if(!active) return true;
                recordPoint(x,y,now);
                analyzeTrial(x,y,now);
                active=false;
                invalidate();
                postDelayed(this::nextTarget,180);
                return true;
            }
            return true;
        }

        void recordPoint(float x,float y,long time){
            samples++;
            if(firstMoveMs==0 && time>downMs+1) firstMoveMs=time;
            if(lastEventMs>0 && time>lastEventMs){
                long dt=time-lastEventMs;
                if(dt>0 && dt<200) sampleHz.add(1000f/dt);
            }
            lastEventMs=time;
            float dx=tx-sx;
            float dy=ty-sy;
            float len=(float)Math.hypot(dx,dy);
            if(len>1){
                float px=x-sx;
                float py=y-sy;
                float projection=(px*dx+py*dy)/len;
                if(projection>maxProjection) maxProjection=projection;
            }
        }

        void analyzeTrial(float x,float y,long upMs){
            finalError=(float)Math.hypot(tx-x,ty-y);
            errors.add(finalError);
            long dt=Math.max(1,upMs-downMs);
            speeds.add(startToTarget/(dt/1000f));

            if(maxProjection > startToTarget*1.10f) overshoots++;
            else if(finalError > dp(34) && maxProjection < startToTarget*0.88f) undershoots++;
        }

        void drawResults(Canvas c){
            p.setStyle(Paint.Style.FILL);
            p.setColor(TEXT);
            p.setTextSize(dp(23));
            c.drawText("CALIBRATION COMPLETE",dp(18),dp(38),p);

            float avg=mean(errors);
            float med=median(errors);
            float avgSpeed=mean(speeds);
            float hz=mean(sampleHz);
            float refresh=getWindowManager().getDefaultDisplay().getRefreshRate();

            float score=Math.max(0,100f-(med/(dp(22))*35f)-(overshoots*3f)-(undershoots*2f));
            String advice=buildAdvice(med,hz,refresh);

            p.setTextSize(dp(13));
            p.setColor(ACCENT);
            c.drawText("CONTROL SCORE  "+String.format(Locale.US,"%.0f",score)+"/100",dp(18),dp(70),p);

            p.setColor(TEXT);
            p.setTextSize(dp(12));
            int y=104;
            String[] rows={
                "Median end error     "+String.format(Locale.US,"%.1f px",med),
                "Average end error    "+String.format(Locale.US,"%.1f px",avg),
                "Overshoot trials     "+overshoots+" / "+totalTrials,
                "Undershoot trials    "+undershoots+" / "+totalTrials,
                "Average swipe speed  "+String.format(Locale.US,"%.0f px/s",avgSpeed),
                "Touch sample rate    "+(hz>0?String.format(Locale.US,"%.0f Hz",hz):"--"),
                "Display refresh      "+String.format(Locale.US,"%.0f Hz",refresh),
                "Touch → next frame   "+(touchFrameMs.isEmpty()?"--":String.format(Locale.US,"%.1f ms",mean(touchFrameMs)))
            };
            for(String row:rows){
                c.drawText(row,dp(18),dp(y),p);
                y+=dp(24);
            }

            p.setColor(WARN);
            c.drawText("RECOMMENDATION",dp(18),dp(y+10),p);
            p.setColor(TEXT);
            p.setTextSize(dp(11));
            y+=dp(34);
            for(String lineText:wrap(advice,42)){
                c.drawText(lineText,dp(18),dp(y),p);
                y+=dp(18);
            }

            p.setColor(BLUE);
            p.setTextSize(dp(10));
            c.drawText("این نتیجه برای کالیبراسیون دستی است، نه auto-aim.",dp(18),getHeight()-dp(20),p);
        }

        void measureTouchToNextFrame(){
            if(frameMeasurePending) return;
            frameMeasurePending=true;
            lastTouchDispatchNs=System.nanoTime();
            Choreographer.getInstance().postFrameCallback(frameTimeNanos->{
                if(lastTouchDispatchNs>0){
                    float ms=(frameTimeNanos-lastTouchDispatchNs)/1000000f;
                    if(ms>=0 && ms<100) touchFrameMs.add(ms);
                }
                frameMeasurePending=false;
            });
        }

        String buildAdvice(float med,float touchHz,float refresh){
            StringBuilder s=new StringBuilder();
            if(overshoots>=4){
                s.append("حرکت‌ها بیشتر از هدف عبور می‌کنند؛ حساسیت فعلی احتمالاً برای حرکت‌های سریع بالاست. ");
            }else if(undershoots>=4){
                s.append("بخش زیادی از حرکت‌ها قبل از ناحیه هدف متوقف می‌شوند؛ حساسیت فعلی احتمالاً پایین است. ");
            }else{
                s.append("رد شدن و کم‌رسیدن متعادل است؛ تغییرات کوچک بهتر از تغییر شدید هستند. ");
            }

            if(touchHz>0 && refresh>0 && touchHz<refresh*0.55f){
                s.append("نرخ نمونه‌برداری لمس از نرخ نوسازی خیلی عقب‌تر است؛ محدودیت ورودی را جدا از حساسیت در نظر بگیر. ");
            }else{
                s.append("مسیر لمس برای کالیبراسیون مناسب ثبت شده است. ");
            }

            float base=1f;
            if(overshoots>=4) base=0.95f;
            else if(overshoots>=3) base=0.97f;
            else if(undershoots>=4) base=1.05f;
            else if(undershoots>=3) base=1.03f;

            s.append("ضریب شروع پیشنهادی: ").append(String.format(Locale.US,"%.2fx",base)).append(" نسبت به حساسیت فعلی، سپس دوباره تست کن.");
            return s.toString();
        }

        List<String> wrap(String s,int width){
            List<String> out=new ArrayList<>();
            String[] words=s.split(" ");
            String line="";
            for(String word:words){
                if((line+" "+word).trim().length()>width){
                    out.add(line);
                    line=word;
                }else{
                    line=(line+" "+word).trim();
                }
            }
            if(!line.isEmpty()) out.add(line);
            return out;
        }

        float mean(List<Float> xs){
            if(xs.isEmpty()) return 0;
            float sum=0;
            for(float x:xs) sum+=x;
            return sum/xs.size();
        }

        float median(List<Float> xs){
            if(xs.isEmpty()) return 0;
            ArrayList<Float> a=new ArrayList<>(xs);
            Collections.sort(a);
            int m=a.size()/2;
            return a.size()%2==1?a.get(m):(a.get(m-1)+a.get(m))/2f;
        }
    }
}
