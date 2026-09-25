package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MiniPanelService extends Service {
    static final int NOTIF_ID=9201;
    static final String CHANNEL="t3r0za_panel";
    final Handler handler=new Handler();
    final Set<String> games=new HashSet<>(Arrays.asList(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    ));

    WindowManager wm;
    LinearLayout panel;
    View hiddenBubble;
    WindowManager.LayoutParams panelParams;
    boolean attached=false;
    boolean hidden=false;
    boolean expanded=false;
    boolean launchedGame=false;

    TextView gameState;
    TextView overallState;
    TextView fpsState;
    TextView refreshState;
    TextView thermalState;
    TextView ramState;
    TextView touchState;
    TextView dnsState;
    TextView sessionState;
    TextView sensitivityState;
    Runnable ticker;

    int dp(float v){
        return (int)(v*getResources().getDisplayMetrics().density+0.5f);
    }

    GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    TextView tv(String value,float size){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    Button btn(String value){
        Button b=new Button(this);
        b.setText(value);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(5),0,dp(5),0);
        b.setBackground(bg(Color.rgb(20,38,47),12));
        return b;
    }

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForegroundNow();
        showCompact();
    }

    void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null){
                NotificationChannel c=new NotificationChannel(
                    CHANNEL,"T3R0ZA Mini Panel",NotificationManager.IMPORTANCE_LOW);
                c.setDescription("T3R0ZA in-game performance controls");
                nm.createNotificationChannel(c);
            }
        }
    }

    void startForegroundNow(){
        Notification.Builder b=Build.VERSION.SDK_INT>=26
            ?new Notification.Builder(this,CHANNEL)
            :new Notification.Builder(this);
        Intent open=new Intent(this,MainActivity.class);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23) flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi=PendingIntent.getActivity(this,9202,open,flags);
        b.setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("T3R0ZA MINI PANEL")
            .setContentText("کنترل عملکرد بازی")
            .setOngoing(true)
            .setContentIntent(pi);
        startForeground(NOTIF_ID,b.build());
    }

    void showCompact(){
        expanded=false;
        if(!preparePanel()) return;

        panel.removeAllViews();

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title=tv("T3R0ZA  ⚡",14);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextColor(Color.rgb(39,215,165));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));

        gameState=tv("● GAME: OFF",9);
        gameState.setTextColor(Color.rgb(139,157,169));
        header.addView(gameState,new LinearLayout.LayoutParams(dp(82),dp(34)));

        Button hide=btn("−");
        hide.setOnClickListener(v->hidePanel());
        header.addView(hide,new LinearLayout.LayoutParams(dp(38),dp(34)));

        panel.addView(header);
        installDrag(title);

        overallState=tv("● READY",9);
        overallState.setTextColor(Color.rgb(86,183,255));
        panel.addView(overallState,new LinearLayout.LayoutParams(-1,dp(22)));

        Button launch=btn("🎮 LAUNCH GAME");
        launch.setTextSize(12);
        launch.setTextColor(Color.rgb(39,215,165));
        launch.setOnClickListener(v->launchGame());
        panel.addView(launch,new LinearLayout.LayoutParams(-1,dp(42)));

        TextView hint=tv("Free Fire اجرا شود تا کنترل‌های کامل باز شوند.",8);
        hint.setTextColor(Color.rgb(139,157,169));
        panel.addView(hint);

        updateWindowSize(dp(300),dp(176));
        updateLiveState();
        ensureTicker();
    }

    boolean preparePanel(){
        if(!Settings.canDrawOverlays(this)){
            stopSelf();
            return false;
        }

        if(wm==null) wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(panel==null){
            panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(10),dp(8),dp(10),dp(8));
            panel.setBackground(bg(Color.argb(244,8,18,24),18));
        }
        if(!attached){
            int type=Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE;

            panelParams=new WindowManager.LayoutParams(
                dp(300),dp(176),type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            panelParams.gravity=Gravity.TOP|Gravity.END;
            panelParams.x=dp(8);
            panelParams.y=dp(86);
            try{
                wm.addView(panel,panelParams);
                attached=true;
            }catch(Exception e){
                stopSelf();
                return false;
            }
        }
        return true;
    }

    void showExpanded(){
        expanded=true;
        if(!preparePanel()) return;

        panel.removeAllViews();

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title=tv("T3R0ZA  ⚡",14);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextColor(Color.rgb(39,215,165));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));

        gameState=tv("● GAME: OFF",9);
        header.addView(gameState,new LinearLayout.LayoutParams(dp(82),dp(34)));

        Button collapse=btn("−");
        collapse.setOnClickListener(v->showCompact());
        header.addView(collapse,new LinearLayout.LayoutParams(dp(38),dp(34)));

        Button hide=btn("×");
        hide.setOnClickListener(v->hidePanel());
        header.addView(hide,new LinearLayout.LayoutParams(dp(38),dp(34)));

        panel.addView(header);
        installDrag(title);

        overallState=tv("● ONLINE",9);
        overallState.setTextColor(Color.rgb(39,215,165));
        panel.addView(overallState,new LinearLayout.LayoutParams(-1,dp(22)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,dp(2),0,dp(4));

        addSection(body,"⚡ PERFORMANCE");
        fpsState=addStatus(body,"FPS","— NOT SUPPORTED");
        addStatus(body,"FRAME","● MONITOR");
        refreshState=addStatus(body,"REFRESH",getRefreshState());
        thermalState=addStatus(body,"THERMAL",getThermalState());
        ramState=addStatus(body,"RAM",getRamState());

        addSection(body,"👆 TOUCH / INPUT");
        touchState=addStatus(body,"RESPONSE","— NOT SUPPORTED");
        addStatus(body,"INPUT MODE","FAST PROFILE");
        addStatus(body,"TOUCH STATUS","— GAME INPUT NOT EXPOSED");

        addSection(body,"🎯 AIM / RECOIL");
        addStatus(body,"RESPONSE","FAST PROFILE");
        addStatus(body,"RECOIL HELP","MANUAL ONLY");
        addStatus(body,"STABILITY","FRAME + TOUCH MONITOR");
        addSensitivity(body);

        addSection(body,"🌐 NETWORK");
        addStatus(body,"PING","— NOT MEASURED");
        addStatus(body,"JITTER","— NOT MEASURED");
        addStatus(body,"LOSS","— NOT MEASURED");
        dnsState=addStatus(body,"DNS","○ OFF");

        Button dns=btn("DNS  ON / OFF");
        dns.setOnClickListener(v->toggleDns());
        body.addView(dns,new LinearLayout.LayoutParams(-1,dp(34)));

        addSection(body,"🧠 SMART MODE");
        addSwitch(body,"AUTO OPTIMIZE","autoPerformance",true);
        sessionState=addStatus(body,"GAME SESSION",isGameForeground()?"● ACTIVE":"○ WAITING");

        Button refresh=btn("MAX REFRESH REQUEST");
        refresh.setOnClickListener(v->MainActivity.refreshFromMini());
        body.addView(refresh,new LinearLayout.LayoutParams(-1,dp(34)));

        Button touch=btn("TOUCH RESPONSE TEST");
        touch.setOnClickListener(v->MainActivity.touchLabFromMini());
        body.addView(touch,new LinearLayout.LayoutParams(-1,dp(34)));

        Button open=btn("OPEN MAIN PANEL");
        open.setOnClickListener(v->openPanel());
        body.addView(open,new LinearLayout.LayoutParams(-1,dp(34)));

        Button compact=btn("COMPACT MODE");
        compact.setOnClickListener(v->showCompact());
        body.addView(compact,new LinearLayout.LayoutParams(-1,dp(34)));

        TextView note=tv(
            "کمک به هدشات فقط به شکل دستی: پاسخ‌گویی، ثبات فریم، لمس و کنترل. " +
            "بدون auto-aim، auto-shoot، تزریق یا تغییر فایل بازی.",8);
        note.setTextColor(Color.rgb(139,157,169));
        body.addView(note);

        scroll.addView(body);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        Button openFull=btn("↗ FULL CONTROLS");
        openFull.setOnClickListener(v->openPanel());
        panel.addView(openFull,new LinearLayout.LayoutParams(-1,dp(32)));

        updateWindowSize(dp(318),dp(500));
        updateLiveState();
        ensureTicker();
    }

    void addSection(LinearLayout body,String value){
        TextView t=tv(value,10);
        t.setTextColor(Color.rgb(86,183,255));
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(6),0,dp(3));
        body.addView(t);
    }

    TextView addStatus(LinearLayout body,String name,String value){
        TextView t=tv(name+"    "+value,9);
        t.setTextColor(Color.rgb(239,245,248));
        t.setPadding(0,dp(3),0,dp(3));
        body.addView(t,new LinearLayout.LayoutParams(-1,dp(24)));
        return t;
    }

    void addSwitch(LinearLayout body,String title,String key,boolean defaultValue){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t=tv(title,9);
        t.setTextColor(Color.rgb(239,245,248));
        row.addView(t,new LinearLayout.LayoutParams(0,dp(34),1));

        boolean value=getSharedPreferences("t3r0za",MODE_PRIVATE).getBoolean(key,defaultValue);
        Switch sw=new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((buttonView,isChecked)->{
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean(key,isChecked).apply();
            MainActivity.applyMiniSwitch(key,isChecked);
        });
        row.addView(sw,new LinearLayout.LayoutParams(dp(58),dp(34)));
        body.addView(row);
    }

    void addSensitivity(LinearLayout body){
        sensitivityState=addStatus(body,"SENSITIVITY","50%");
        SeekBar bar=new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(getSharedPreferences("t3r0za",MODE_PRIVATE).getInt("aim_profile",50));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean fromUser){
                sensitivityState.setText("SENSITIVITY    "+value+"%  •  PROFILE ONLY");
                if(fromUser){
                    getSharedPreferences("t3r0za",MODE_PRIVATE)
                        .edit().putInt("aim_profile",value).apply();
                }
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        body.addView(bar,new LinearLayout.LayoutParams(-1,dp(32)));
    }

    String getRefreshState(){
        try{
            android.view.Display d=((WindowManager)getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay();
            float hz=d==null?0f:d.getRefreshRate();
            return hz>0?Math.round(hz)+" Hz":"— NOT SUPPORTED";
        }catch(Exception e){
            return "— NOT SUPPORTED";
        }
    }

    String getThermalState(){
        if(Build.VERSION.SDK_INT<29) return "— NOT SUPPORTED";
        try{
            android.os.PowerManager pm=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            if(pm==null) return "— NOT SUPPORTED";
            int s=pm.getCurrentThermalStatus();
            if(s<=android.os.PowerManager.THERMAL_STATUS_LIGHT) return "NORMAL";
            if(s==android.os.PowerManager.THERMAL_STATUS_MODERATE) return "MODERATE";
            if(s==android.os.PowerManager.THERMAL_STATUS_SEVERE) return "SEVERE";
            if(s==android.os.PowerManager.THERMAL_STATUS_CRITICAL) return "CRITICAL";
            return "EMERGENCY";
        }catch(Exception e){
            return "— NOT SUPPORTED";
        }
    }

    String getRamState(){
        try{
            android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(am==null) return "— NOT SUPPORTED";
            android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return (mi.availMem/1048576L)+" MB FREE";
        }catch(Exception e){
            return "— NOT SUPPORTED";
        }
    }

    boolean isGameForeground(){
        if(Build.VERSION.SDK_INT<21) return false;
        try{
            android.app.AppOpsManager ops=(android.app.AppOpsManager)getSystemService(APP_OPS_SERVICE);
            if(ops==null) return false;
            int mode=Build.VERSION.SDK_INT>=29
                ?ops.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),getPackageName())
                :ops.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),getPackageName());
            if(mode!=android.app.AppOpsManager.MODE_ALLOWED) return false;

            UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            if(usm==null) return false;
            long end=System.currentTimeMillis();
            UsageEvents events=usm.queryEvents(end-60000L,end);
            UsageEvents.Event e=new UsageEvents.Event();
            String last=null;
            long lastTime=0L;
            while(events.hasNextEvent()){
                events.getNextEvent(e);
                int type=e.getEventType();
                if((type==UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT>=29 && type==UsageEvents.Event.ACTIVITY_RESUMED)) &&
                   e.getTimeStamp()>=lastTime){
                    lastTime=e.getTimeStamp();
                    last=e.getPackageName();
                }
            }
            return last!=null && games.contains(last);
        }catch(Exception e){
            return false;
        }
    }

    void updateWindowSize(int width,int height){
        if(!attached || panelParams==null) return;
        panelParams.width=width;
        panelParams.height=height;
        try{wm.updateViewLayout(panel,panelParams);}catch(Exception ignored){}
    }

    void installDrag(View handle){
        if(handle==null || panelParams==null) return;
        handle.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;
            int startX,startY;
            boolean moved;
            public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getRawX();
                        downY=e.getRawY();
                        startX=panelParams.x;
                        startY=panelParams.y;
                        moved=false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if(Math.abs(e.getRawX()-downX)>dp(6) ||
                           Math.abs(e.getRawY()-downY)>dp(6)) moved=true;
                        panelParams.x=startX+(int)(downX-e.getRawX());
                        panelParams.y=startY+(int)(e.getRawY()-downY);
                        try{wm.updateViewLayout(panel,panelParams);}catch(Exception ignored){}
                        return true;
                    case MotionEvent.ACTION_UP:
                        return moved;
                    default:
                        return true;
                }
            }
        });
    }

    void ensureTicker(){
        if(ticker==null){
            ticker=()->{
                if(attached && !hidden){
                    updateLiveState();
                    handler.postDelayed(ticker,1000L);
                }
            };
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    void updateLiveState(){
        boolean game=isGameForeground();
        if(gameState!=null){
            gameState.setText(game?"● GAME: ON":"● GAME: OFF");
            gameState.setTextColor(game?Color.rgb(39,215,165):Color.rgb(139,157,169));
        }
        if(overallState!=null){
            overallState.setText(game?"● ONLINE":"● READY");
            overallState.setTextColor(game?Color.rgb(39,215,165):Color.rgb(86,183,255));
        }
        if(refreshState!=null) refreshState.setText("REFRESH    "+getRefreshState());
        if(thermalState!=null) thermalState.setText("THERMAL    "+getThermalState());
        if(ramState!=null) ramState.setText("RAM       "+getRamState());
        if(sessionState!=null) sessionState.setText("GAME SESSION    "+(game?"● ACTIVE":"○ WAITING"));

        if(game && !launchedGame){
            launchedGame=true;
            if(!expanded) showExpanded();
        }
        if(!game) launchedGame=false;

        boolean dns= DnsTunnelService.instance!=null;
        if(dnsState!=null){
            dnsState.setText("DNS       "+(dns?"● LOCKED":"○ OFF"));
            dnsState.setTextColor(dns?Color.rgb(39,215,165):Color.rgb(139,157,169));
        }
    }

    void launchGame(){
        String[] packages={"com.dts.freefireth","com.dts.freefiremax"};
        boolean found=false;
        for(String pkg:packages){
            try{
                Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
                if(i!=null){
                    found=true;
                    startActivity(i);
                    break;
                }
            }catch(Exception ignored){}
        }
        if(found){
            overallState.setText("◐ LAUNCHING");
            overallState.setTextColor(Color.rgb(255,181,71));
        }else{
            overallState.setText("— GAME NOT INSTALLED");
            overallState.setTextColor(Color.rgb(255,104,104));
        }
    }

    void toggleDns(){
        if(DnsTunnelService.instance!=null){
            try{stopService(new Intent(this,DnsTunnelService.class));}catch(Exception ignored){}
            return;
        }

        boolean armed=getSharedPreferences("t3r0za",MODE_PRIVATE)
            .getBoolean("stableDns",false);
        String dns=getSharedPreferences("t3r0za",MODE_PRIVATE)
            .getString("selected_dns",null);

        if(!armed || dns==null || dns.isEmpty()){
            openPanel();
            return;
        }

        Intent prep=VpnService.prepare(this);
        if(prep!=null){
            openPanel();
            return;
        }

        Intent i=new Intent(this,DnsTunnelService.class);
        i.putExtra(DnsTunnelService.EXTRA_DNS,dns);
        try{
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i);
            else startService(i);
        }catch(Exception ignored){}
    }

    void openPanel(){
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    void hidePanel(){
        if(hidden) return;
        hidden=true;
        if(ticker!=null) handler.removeCallbacks(ticker);
        if(wm!=null && panel!=null && attached){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        attached=false;

        TextView bubble=tv("T3",11);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        bubble.setTextColor(Color.rgb(39,215,165));
        bubble.setBackground(bg(Color.rgb(20,38,47),15));
        bubble.setOnClickListener(v->restorePanel());
        hiddenBubble=bubble;

        int type=Build.VERSION.SDK_INT>=26
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            dp(52),dp(52),type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;
        p.x=dp(8);
        p.y=dp(90);
        try{wm.addView(hiddenBubble,p);}catch(Exception e){stopSelf();}
    }

    void restorePanel(){
        if(wm!=null && hiddenBubble!=null){
            try{wm.removeView(hiddenBubble);}catch(Exception ignored){}
        }
        hiddenBubble=null;
        hidden=false;
        showCompact();
    }

    @Override public void onDestroy(){
        if(ticker!=null) handler.removeCallbacks(ticker);
        if(wm!=null && panel!=null && attached){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        if(wm!=null && hiddenBubble!=null){
            try{wm.removeView(hiddenBubble);}catch(Exception ignored){}
        }
        attached=false;
        panel=null;
        hiddenBubble=null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){
        return null;
    }
}