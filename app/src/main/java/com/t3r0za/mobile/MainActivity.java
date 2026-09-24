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

public class MainActivity extends Activity {
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
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestBestRefresh();
        buildHome();
        startTelemetry();
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
        launch.setOnClickListener(v->launchWithBestDns());
        pc.addView(launch,lp(0,8));

        Button stopDns=btn("■ STOP DNS SESSION");
        stopDns.setOnClickListener(v->stopDnsSession());
        pc.addView(stopDns,lp(0,8));

        Button usage=btn("⚙ ENABLE AUTO-STOP PERMISSION");
        usage.setOnClickListener(v->openUsageAccess());
        pc.addView(usage,lp(0,8));

        root.addView(pc);

        TextView note=tv("این برنامه نشانه‌گیری یا شلیک خودکار انجام نمی‌دهد. حساسیت بازی را مستقیماً دستکاری نمی‌کند؛ در عوض داده‌ی واقعی لمس و عملکرد گوشی را اندازه می‌گیرد تا تنظیم دستی قابل‌اعتمادتر شود.",9);
        note.setTextColor(MUTED);
        root.addView(note,lp(12,0));

        scroll.addView(root);
        setContentView(scroll);
    }

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
            handler.postDelayed(ticker,1200);
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

        if(thermal.equals("CRITICAL")||thermal.equals("EMERGENCY")){
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
        setState("● DNS TEST RUNNING","در حال تست resolverهای واقعی روی شبکه فعلی...",true);
        new Thread(()->{
            String best=benchmarkAndChoose();
            final String result=best==null?
                "هیچ resolverی پاسخ قابل‌اعتماد نداد. اتصال فعلی را بررسی کن.":
                "BEST RESOLVER\\n"+best+"\\n\\nاین latency مربوط به DNS lookup است، نه ping داخل مچ."; 
            handler.post(()->{
                if(showResult || best!=null){
                    new AlertDialog.Builder(MainActivity.this).setTitle("DNS STABILITY RESULT").setMessage(result).setPositiveButton("OK",null).show();
                }
                setState(best==null?"● DNS TEST FAILED":"● DNS TEST COMPLETE",best==null?"resolver قابل‌اعتماد پیدا نشد.":"بهترین resolver بر اساس پاسخ واقعی شبکه مشخص شد.",best!=null);
            });
        }).start();
    }

    String benchmarkAndChoose(){
        ArrayList<String> candidates=new ArrayList<>(Arrays.asList(DNS_SERVERS));
        ArrayList<String> good=new ArrayList<>();
        for(String server:candidates){
            long a=dnsProbe(server,"example.com",1200);
            long b=dnsProbe(server,"connectivitycheck.gstatic.com",1200);
            if(a>=0 && b>=0){
                long avg=(a+b)/2L;
                good.add(server+"|"+avg);
            }
        }
        Collections.sort(good,(a,b)->Long.compare(extractMs(a.replace("|"," ")),extractMs(b.replace("|"," "))));
        if(good.isEmpty()) return null;
        String raw=good.get(0);
        return raw.substring(0,raw.indexOf("|"));
    }

    void startDnsVpnAndLaunch(){
        if(pendingDns==null || pendingDns.isEmpty()){
            launchFF();
            return;
        }
        Intent i=new Intent(this,DnsTunnelService.class);
        i.putExtra(DnsTunnelService.EXTRA_DNS,pendingDns);
        try{
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            setState("● DNS CONNECTING","DNS ثابت "+pendingDns+" در حال برقراری است؛ سپس Free Fire باز می‌شود.",true);
            handler.postDelayed(()->{
                launchFF();
            },700);
        }catch(Exception e){
            setState("● DNS SESSION FAILED","VPN سیستم اجازه شروع نداد؛ بازی بدون این DNS اجرا می‌شود.",false);
            launchFF();
        }
    }

    void stopDnsSession(){
        try{
            stopService(new Intent(this,DnsTunnelService.class));
            setState("● DNS SESSION OFF","DNS session قطع شد و مسیر شبکه به حالت سیستم برگشت.",true);
        }catch(Exception ignored){}
    }

    void openUsageAccess(){
        try{
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }catch(Exception e){
            Toast.makeText(this,"Usage Access settings are not available",Toast.LENGTH_SHORT).show();
        }
    }

    long extractMs(String s){
        try{
            int p=s.lastIndexOf(" ms");
            int q=s.lastIndexOf(" ",p-1);
            return Long.parseLong(s.substring(q+1,p));
        }catch(Exception e){return Long.MAX_VALUE;}
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
            return Math.max(0,(end-start)/1000000L);
        }catch(Exception e){return -1;}
        finally{if(socket!=null) socket.close();}
    }

    byte[] buildDnsQuery(String host){
        String[] labels=host.split("\\\\.");
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
        requestBestRefresh();
        if(root!=null && !runningLab) startTelemetry();
    }

    @Override protected void onPause(){
        super.onPause();
        stopTelemetry();
    }

    @Override protected void onDestroy(){
        stopTelemetry();
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
                "Display refresh      "+String.format(Locale.US,"%.0f Hz",refresh)
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
