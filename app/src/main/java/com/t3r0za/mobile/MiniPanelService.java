package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

public class MiniPanelService extends Service {
    static final int NOTIF_ID=9201;
    static final String CHANNEL="t3r0za_panel";
    static final int DEFAULT_DNS_FAIL_LIMIT=3;
    WindowManager wm;
    View panel;
    View hiddenBubble;
    boolean attached=false;
    boolean hidden=false;
    boolean dnsOn=false;
    Handler handler=new Handler();
    Runnable stateTicker;

    int dp(float v){
        return (int)(v*getResources().getDisplayMetrics().density+0.5f);
    }

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForegroundNow();
        showPanel();
    }

    void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c=new NotificationChannel(CHANNEL,"T3R0ZA Mini Panel",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("T3R0ZA in-game quick controls");
            nm.createNotificationChannel(c);
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
         .setContentText("کنترل سریع داخل بازی فعال است")
         .setOngoing(true)
         .setContentIntent(pi);
        startForeground(NOTIF_ID,b.build());
    }

    GradientDrawable panelBackground(){
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.argb(242,10,20,27));
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1),Color.argb(150,39,215,165));
        return g;
    }

    GradientDrawable buttonBackground(){
        GradientDrawable g=new GradientDrawable();
        g.setColor(Color.argb(215,20,38,47));
        g.setCornerRadius(dp(12));
        return g;
    }

    void showPanel(){
        if(!Settings.canDrawOverlays(this)){
            stopSelf();
            return;
        }

        wm=(WindowManager)getSystemService(WINDOW_SERVICE);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10),dp(8),dp(10),dp(9));
        box.setBackground(panelBackground());

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title=new TextView(this);
        title.setText("T3R0ZA");
        title.setTextColor(Color.rgb(39,215,165));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));

        TextView live=new TextView(this);
        live.setText("●");
        live.setTextColor(Color.rgb(39,215,165));
        live.setTextSize(12);
        top.addView(live,new LinearLayout.LayoutParams(dp(24),dp(34)));

        Button panelBtn=smallButton("PANEL");
        panelBtn.setOnClickListener(v->openPanel());
        top.addView(panelBtn,new LinearLayout.LayoutParams(dp(62),dp(34)));

        Button hide=smallButton("HIDE");
        hide.setOnClickListener(v->hidePanel());
        top.addView(hide,new LinearLayout.LayoutParams(dp(58),dp(34)));

        Button stop=smallButton("×");
        stop.setOnClickListener(v->stopSelf());
        top.addView(stop,new LinearLayout.LayoutParams(dp(38),dp(34)));

        box.addView(top);

        TextView state=miniText("LIVE CONTROL CORE");
        box.addView(state);

        Button dns=smallButton("DNS");
        dns.setTextColor(Color.rgb(39,215,165));
        dns.setOnClickListener(v->toggleDns(dns,state));
        box.addView(dns,new LinearLayout.LayoutParams(-1,dp(38)));

        addMiniSwitch(box,"AUTO PERFORMANCE", "autoPerformance", true, state);
        addMiniSwitch(box,"GAME PERFORMANCE", "performanceSession", false, state);
        addMiniSwitch(box,"LIVE INPUT", "liveInput", false, state);
        addMiniSwitch(box,"STABLE DNS", "stableDns", false, state);
        addMiniSwitch(box,"THERMAL GUARD", "thermalGuard", true, state);

        TextView guardLabel=miniText("DNS GUARD  3 FAIL");
        guardLabel.setTextColor(Color.rgb(86,183,255));
        box.addView(guardLabel,new LinearLayout.LayoutParams(-1,dp(26)));

        SeekBar guard=new SeekBar(this);
        guard.setMax(4);
        int saved=getSharedPreferences("t3r0za",MODE_PRIVATE).getInt("dns_fail_limit",DEFAULT_DNS_FAIL_LIMIT);
        int progress=Math.max(0,Math.min(4,saved-1));
        guard.setProgress(progress);
        guard.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                int limit=p+1;
                guardLabel.setText("DNS GUARD  "+limit+" FAIL");
                if(fromUser){
                    getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putInt("dns_fail_limit",limit).apply();
                    state.setText("● DNS GUARD: "+limit+" خطای پیاپی");
                }
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        box.addView(guard,new LinearLayout.LayoutParams(-1,dp(32)));

        TextView note=miniText("فقط قابلیت‌های واقعی Android؛ بدون auto-aim یا دستکاری بازی.");
        note.setTextColor(Color.rgb(139,157,169));
        box.addView(note);

        panel=box;

        int type=Build.VERSION.SDK_INT>=26
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

        p.gravity=Gravity.TOP|Gravity.END;
        p.x=dp(8);
        p.y=dp(86);

        title.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY;
            int startX,startY;
            public boolean onTouch(View v,MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
                    downX=e.getRawX();
                    downY=e.getRawY();
                    startX=p.x;
                    startY=p.y;
                    return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                    p.x=startX+(int)(downX-e.getRawX());
                    p.y=startY+(int)(e.getRawY()-downY);
                    try{wm.updateViewLayout(box,p);}catch(Exception ignored){}
                    return true;
                }
                return true;
            }
        });

        try{
            wm.addView(box,p);
            attached=true;
            stateTicker=()->{
                boolean active=DnsTunnelService.instance!=null;
                dnsOn=active;
                dns.setText(active?"DNS ✓":"DNS");
                live.setText(active?"●":"○");
                live.setTextColor(active?Color.rgb(39,215,165):Color.rgb(139,157,169));
                if(attached){
                    handler.postDelayed(stateTicker,1200);
                }
            };
            handler.post(stateTicker);
        }catch(Exception e){
            stopSelf();
        }
    }

    TextView miniText(String text){
        TextView t=new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(9);
        t.setPadding(0,dp(2),0,dp(2));
        return t;
    }

    void addMiniSwitch(LinearLayout parent,String title,String key,boolean defaultValue,TextView state){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label=miniText(title);
        row.addView(label,new LinearLayout.LayoutParams(0,dp(34),1));

        boolean value=getSharedPreferences("t3r0za",MODE_PRIVATE).getBoolean(key,defaultValue);
        Switch sw=new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((buttonView,isChecked)->{
            getSharedPreferences("t3r0za",MODE_PRIVATE).edit().putBoolean(key,isChecked).apply();
            MainActivity.applyMiniSwitch(key,isChecked);

            if("stableDns".equals(key)){
                state.setText(isChecked?"● DNS SESSION ARMED":"● DNS SESSION OFF");
            }else if("autoPerformance".equals(key)){
                state.setText(isChecked?"● PERFORMANCE CORE ON":"● PERFORMANCE CORE OFF");
            }else if("thermalGuard".equals(key)){
                state.setText(isChecked?"● THERMAL GUARD ON":"● THERMAL GUARD OFF");
            }else if("liveInput".equals(key)){
                state.setText(isChecked?"● INPUT MONITOR ON":"● INPUT MONITOR OFF");
            }
        });
        row.addView(sw,new LinearLayout.LayoutParams(dp(56),dp(34)));
        parent.addView(row);
    }

    Button smallButton(String text){
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(dp(4),0,dp(4),0);
        b.setBackground(buttonBackground());
        return b;
    }

    void toggleDns(Button button,TextView state){
        if(dnsOn || DnsTunnelService.instance!=null){
            try{stopService(new Intent(this,DnsTunnelService.class));}catch(Exception ignored){}
            dnsOn=false;
            button.setText("DNS");
            state.setText("● DNS SESSION OFF");
            return;
        }

        boolean armed=getSharedPreferences("t3r0za",MODE_PRIVATE).getBoolean("stableDns",true);
        String dns=getSharedPreferences("t3r0za",MODE_PRIVATE).getString("selected_dns",null);

        if(!armed){
            state.setText("● STABLE DNS OFF");
            return;
        }

        if(dns==null || dns.isEmpty()){
            state.setText("● DNS نیاز به BENCHMARK دارد");
            openPanel();
            return;
        }

        Intent prep=VpnService.prepare(this);
        if(prep!=null){
            state.setText("● VPN PERMISSION REQUIRED");
            openPanel();
            return;
        }

        Intent i=new Intent(this,DnsTunnelService.class);
        i.putExtra(DnsTunnelService.EXTRA_DNS,dns);
        try{
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            dnsOn=true;
            button.setText("DNS ✓");
            state.setText("● DNS CONNECTING  "+dns);
        }catch(Exception ignored){
            state.setText("● DNS START FAILED");
        }
    }

    void openPanel(){
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    void hidePanel(){
        if(hidden) return;
        hidden=true;
        if(stateTicker!=null) handler.removeCallbacks(stateTicker);
        if(wm!=null && panel!=null && attached){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        attached=false;
        TextView bubble=new TextView(this);
        bubble.setText("T3");
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.rgb(39,215,165));
        bubble.setTextSize(11);
        bubble.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        bubble.setBackground(buttonBackground());
        bubble.setOnClickListener(v->{
            if(wm!=null && hiddenBubble!=null){
                try{wm.removeView(hiddenBubble);}catch(Exception ignored){}
            }
            hiddenBubble=null;
            hidden=false;
            showPanel();
        });
        hiddenBubble=bubble;

        int type=Build.VERSION.SDK_INT>=26
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            dp(52),dp(52),type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;
        p.x=dp(8);
        p.y=dp(90);
        try{
            wm.addView(hiddenBubble,p);
        }catch(Exception e){
            hiddenBubble=null;
            hidden=false;
            stopSelf();
        }
    }

    @Override public void onDestroy(){
        if(stateTicker!=null) handler.removeCallbacks(stateTicker);
        if(wm!=null && panel!=null && attached){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        if(wm!=null && hiddenBubble!=null){
            try{wm.removeView(hiddenBubble);}catch(Exception ignored){}
        }
        attached=false;
        panel=null;
        hiddenBubble=null;
        hidden=false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){
        return null;
    }
}
