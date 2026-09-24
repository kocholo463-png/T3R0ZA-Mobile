package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MiniPanelService extends Service {
    static final int NOTIF_ID=9201;
    static final String CHANNEL="t3r0za_panel";
    WindowManager wm;
    View panel;
    boolean attached=false;
    boolean dnsOn=false;

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

    void showPanel(){
        if(!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(8,6,8,6);
        box.setBackgroundColor(Color.argb(235,12,24,31));

        TextView title=new TextView(this);
        title.setText("T3");
        title.setTextColor(Color.rgb(39,215,165));
        title.setTextSize(12);
        box.addView(title,new LinearLayout.LayoutParams(34,48));

        Button dns=smallButton("DNS");
        dns.setOnClickListener(v->toggleDns(dns));
        box.addView(dns);

        Button panelBtn=smallButton("PANEL");
        panelBtn.setOnClickListener(v->openPanel());
        box.addView(panelBtn);

        Button stop=smallButton("×");
        stop.setOnClickListener(v->stopSelf());
        box.addView(stop);

        panel=box;

        int type=Build.VERSION.SDK_INT>=26
            ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            :WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;
        p.x=8;
        p.y=120;

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
        }catch(Exception e){
            stopSelf();
        }
    }

    Button smallButton(String text){
        Button b=new Button(this);
        b.setText(text);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setPadding(8,0,8,0);
        return b;
    }

    void toggleDns(Button button){
        if(dnsOn || DnsTunnelService.instance!=null){
            try{stopService(new Intent(this,DnsTunnelService.class));}catch(Exception ignored){}
            dnsOn=false;
            button.setText("DNS");
            return;
        }
        String dns=getSharedPreferences("t3r0za",MODE_PRIVATE).getString("selected_dns",null);
        if(dns==null || dns.isEmpty()){
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
            if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
            dnsOn=true;
            button.setText("DNS✓");
        }catch(Exception ignored){}
    }

    void openPanel(){
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    @Override public void onDestroy(){
        if(wm!=null && panel!=null && attached){
            try{wm.removeView(panel);}catch(Exception ignored){}
        }
        attached=false;
        panel=null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }
}
