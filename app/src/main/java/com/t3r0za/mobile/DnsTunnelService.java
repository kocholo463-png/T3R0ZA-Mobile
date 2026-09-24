package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.content.pm.ServiceInfo;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DnsTunnelService extends VpnService {
    static final String EXTRA_DNS="dns";
    static final int NOTIF_ID=9101;
    static final String CHANNEL="t3r0za_dns";
    static volatile DnsTunnelService instance;
    ParcelFileDescriptor vpnInterface;
    Thread worker;
    Thread monitor;
    volatile boolean running=false;
    String dns;
    String[] GAME_PACKAGES={"com.dts.freefireth","com.dts.freefiremax"};

    @Override public void onCreate(){
        super.onCreate();
        instance=this;
        createChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && intent.hasExtra(EXTRA_DNS)) dns=intent.getStringExtra(EXTRA_DNS);
        if(dns==null || dns.trim().isEmpty()){
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNow();
        startTunnel();
        return START_NOT_STICKY;
    }

    void startForegroundNow(){
        Notification.Builder b;
        if(Build.VERSION.SDK_INT>=26) b=new Notification.Builder(this,CHANNEL);
        else b=new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning);
        b.setContentTitle("T3R0ZA DNS SESSION");
        b.setContentText("DNS ثابت: "+dns+" • فقط هنگام بازی");
        b.setOngoing(true);
        b.setCategory(Notification.CATEGORY_SERVICE);
        Notification n=b.build();
        if(Build.VERSION.SDK_INT>=34){
            startForeground(NOTIF_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }else{
            startForeground(NOTIF_ID,n);
        }
    }

    void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel c=new NotificationChannel(CHANNEL,"T3R0ZA DNS",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("DNS session status");
            nm.createNotificationChannel(c);
        }
    }

    void startTunnel(){
        if(running) return;
        running=true;
        worker=new Thread(this::packetLoop,"T3R0ZA-DNS");
        worker.start();
        monitor=new Thread(this::foregroundMonitor,"T3R0ZA-DNS-MONITOR");
        monitor.start();
    }

    void packetLoop(){
        try{
            if(vpnInterface!=null) vpnInterface.close();
            Builder b=new Builder();
            b.setSession("T3R0ZA DNS "+dns);
            b.setMtu(1500);
            b.addAddress("10.77.0.2",32);
            b.addRoute(dns,32);
            b.addDnsServer(dns);
            vpnInterface=b.establish();
            if(vpnInterface==null){stopSelf();return;}

            FileInputStream in=new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out=new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] packet=new byte[32767];

            while(running){
                int len=in.read(packet);
                if(len<=0) continue;
                byte[] response=handleDnsPacket(packet,len);
                if(response!=null) out.write(response);
            }
        }catch(Exception ignored){
        }finally{
            closeTunnel();
        }
    }

    byte[] handleDnsPacket(byte[] packet,int len){
        if(len<28) return null;
        int version=(packet[0]>>4)&15;
        int ihl=(packet[0]&15)*4;
        if(version!=4 || ihl<20 || len<ihl+8) return null;
        int protocol=packet[9]&255;
        if(protocol!=17) return null;

        int dstIpOffset=16;
        String dst=ipString(packet,dstIpOffset);
        if(!dns.equals(dst)) return null;

        int udp=ihl;
        int srcPort=u16(packet,udp);
        int dstPort=u16(packet,udp+2);
        if(dstPort!=53) return null;

        int udpLen=u16(packet,udp+4);
        if(udpLen<8 || udp+udpLen>len) return null;
        int dnsLen=udpLen-8;
        byte[] dnsPayload=Arrays.copyOfRange(packet,udp+8,udp+8+dnsLen);

        DatagramSocket socket=null;
        try{
            socket=new DatagramSocket();
            if(!protect(socket)) return null;
            socket.setSoTimeout(1200);
            InetAddress server=InetAddress.getByName(dns);
            DatagramPacket q=new DatagramPacket(dnsPayload,dnsPayload.length,server,53);
            socket.send(q);

            byte[] buf=new byte[4096];
            DatagramPacket r=new DatagramPacket(buf,buf.length);
            socket.receive(r);

            byte[] answer=Arrays.copyOf(r.getData(),r.getLength());
            byte[] out=new byte[20+8+answer.length];

            out[0]=0x45;
            out[1]=0;
            put16(out,2,out.length);
            put16(out,4,u16(packet,4));
            put16(out,6,0);
            out[8]=64;
            out[9]=17;
            put32(out,12,packet,16);
            put32(out,16,packet,12);
            put16(out,10,ipChecksum(out,0,20));

            put16(out,20,53);
            put16(out,22,srcPort);
            put16(out,24,8+answer.length);
            put16(out,26,0);
            System.arraycopy(answer,0,out,28,answer.length);
            return out;
        }catch(Exception e){
            return null;
        }finally{
            if(socket!=null) socket.close();
        }
    }

    void foregroundMonitor(){
        while(running){
            try{
                Thread.sleep(2000);
                if(!hasUsageAccess()){
                    continue;
                }
                String pkg=currentForegroundPackage();
                if(pkg!=null && !isGame(pkg)){
                    stopSelf();
                    break;
                }
            }catch(Exception e){
            }
        }
    }

    boolean isGame(String pkg){
        for(String g:GAME_PACKAGES) if(g.equals(pkg)) return true;
        return false;
    }

    boolean hasUsageAccess(){
        try{
            android.app.AppOpsManager ops=(android.app.AppOpsManager)getSystemService(APP_OPS_SERVICE);
            int mode;
            if(Build.VERSION.SDK_INT>=29){
                mode=ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName());
            }else{
                mode=ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName());
            }
            return mode==android.app.AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){
            return false;
        }
    }

    String currentForegroundPackage(){
        UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
        long end=System.currentTimeMillis();
        long begin=end-15000;
        UsageEvents events=usm.queryEvents(begin,end);
        if(events==null) return null;
        UsageEvents.Event ev=new UsageEvents.Event();
        String last=null;
        long lastTime=0;
        while(events.hasNextEvent()){
            events.getNextEvent(ev);
            int type=ev.getEventType();
            if(type==UsageEvents.Event.MOVE_TO_FOREGROUND ||
               (Build.VERSION.SDK_INT>=29 && type==UsageEvents.Event.ACTIVITY_RESUMED)){
                if(ev.getTimeStamp()>=lastTime){
                    lastTime=ev.getTimeStamp();
                    last=ev.getPackageName();
                }
            }
        }
        return last;
    }

    String ipString(byte[] p,int off){
        return (p[off]&255)+"."+(p[off+1]&255)+"."+(p[off+2]&255)+"."+(p[off+3]&255);
    }

    int u16(byte[] b,int off){
        return ((b[off]&255)<<8)|(b[off+1]&255);
    }

    void put16(byte[] b,int off,int v){
        b[off]=(byte)((v>>8)&255);
        b[off+1]=(byte)(v&255);
    }

    void put32(byte[] b,int off,byte[] src,int srcOff){
        System.arraycopy(src,srcOff,b,off,4);
    }

    void put32(byte[] b,int off,int v){
        b[off]=(byte)((v>>24)&255);
        b[off+1]=(byte)((v>>16)&255);
        b[off+2]=(byte)((v>>8)&255);
        b[off+3]=(byte)(v&255);
    }

    int ipChecksum(byte[] b,int off,int len){
        long sum=0;
        for(int i=off;i<off+len;i+=2){
            int hi=b[i]&255;
            int lo=(i+1<off+len)?b[i+1]&255:0;
            sum+=((hi<<8)|lo);
            while((sum>>16)!=0) sum=(sum&0xffff)+(sum>>16);
        }
        return (int)(~sum)&0xffff;
    }

    void closeTunnel(){
        running=false;
        try{if(vpnInterface!=null) vpnInterface.close();}catch(Exception ignored){}
        vpnInterface=null;
    }

    @Override public void onRevoke(){
        closeTunnel();
        stopSelf();
        super.onRevoke();
    }

    @Override public void onDestroy(){
        closeTunnel();
        instance=null;
        super.onDestroy();
    }

    public static void stopSession(){
        if(instance!=null) instance.stopSelf();
    }

    @Override public IBinder onBind(Intent intent){
        return super.onBind(intent);
    }
}
