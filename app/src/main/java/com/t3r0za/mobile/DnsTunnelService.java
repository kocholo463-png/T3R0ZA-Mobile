package com.t3r0za.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.VpnService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.content.pm.ServiceInfo;
import android.app.PendingIntent;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class DnsTunnelService extends VpnService {
    static final String EXTRA_DNS="dns";
    static final String EXTRA_PREMATCH_ONLY="prematch_only";
    static final int NOTIF_ID=9101;
    static final String CHANNEL="t3r0za_dns";
    static final String ACTION_STOP="com.t3r0za.mobile.STOP_DNS";
    static final String ACTION_OPEN="com.t3r0za.mobile.OPEN_PANEL";
    static volatile DnsTunnelService instance;

    static final int DNS_TIMEOUT_MS=900;
    static final int DNS_HEALTH_INTERVAL_MS=5000;
    static final long DNS_IDLE_STOP_MS=15000L;
    static final long DNS_PREMATCH_MAX_MS=90000L;
    static final long DNS_MIN_ACTIVE_MS=30000L;
    static final int MAX_CONSECUTIVE_HEALTH_FAILURES=3;

    ParcelFileDescriptor vpnInterface;
    DatagramSocket dnsSocket;
    Thread worker;
    Thread monitor;
    volatile boolean running=false;
    volatile int healthFailures=0;
    volatile long lastDnsLatencyMs=-1;
    volatile long lastDnsPacketAtMs=0L;
    long tunnelStartedAtMs=0L;
    boolean prematchOnly=false;
    volatile long lastHealthAtMs=0;
    String dns;
    InetAddress dnsAddress;
    String[] GAME_PACKAGES={"com.dts.freefireth","com.dts.freefiremax"};

    @Override public void onCreate(){
        super.onCreate();
        instance=this;
        createChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && ACTION_STOP.equals(intent.getAction())){
            stopSelf();
            return START_NOT_STICKY;
        }
        if(intent!=null && intent.hasExtra(EXTRA_DNS)){
            String requested=intent.getStringExtra(EXTRA_DNS);
            if(!running) dns=requested;
        }
        if(intent!=null && intent.hasExtra(EXTRA_PREMATCH_ONLY)){
            prematchOnly=intent.getBooleanExtra(EXTRA_PREMATCH_ONLY,false);
        }
        if(dns==null || dns.trim().isEmpty()){
            stopSelf();
            return START_NOT_STICKY;
        }
        try{
            dnsAddress=InetAddress.getByName(dns);
        }catch(Exception e){
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundNow("DNS ثابت: "+dns+" • Session فعال");
        startTunnel();
        return START_NOT_STICKY;
    }

    void startForegroundNow(String text){
        Notification.Builder b;
        if(Build.VERSION.SDK_INT>=26) b=new Notification.Builder(this,CHANNEL);
        else b=new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning);
        b.setContentTitle("T3R0ZA DNS SESSION");
        b.setContentText(text);
        b.setOngoing(true);
        b.setCategory(Notification.CATEGORY_SERVICE);

        Intent open=new Intent(this,MainActivity.class);
        open.setAction(ACTION_OPEN);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23) flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi=PendingIntent.getActivity(this,9102,open,flags);

        Intent stop=new Intent(this,DnsTunnelService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi=PendingIntent.getService(this,9103,stop,flags);

        b.setContentIntent(openPi);
        b.addAction(new Notification.Action.Builder(null,"OPEN T3R0ZA",openPi).build());
        b.addAction(new Notification.Action.Builder(null,"STOP DNS",stopPi).build());

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
            c.setDescription("DNS session status and health");
            nm.createNotificationChannel(c);
        }
    }

    void startTunnel(){
        if(running) return;
        running=true;
        healthFailures=0;
        tunnelStartedAtMs=System.currentTimeMillis();
        lastDnsPacketAtMs=tunnelStartedAtMs;
        lastDnsLatencyMs=-1;
        lastHealthAtMs=System.currentTimeMillis();

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
            b.addRoute(dnsAddress,32);
            b.addDnsServer(dnsAddress);

            vpnInterface=b.establish();
            if(vpnInterface==null){
                stopSelf();
                return;
            }

            FileInputStream in=new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out=new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] packet=new byte[32767];
            dnsSocket=new DatagramSocket();
            if(!protect(dnsSocket)){
                dnsSocket.close();
                dnsSocket=null;
                stopSelf();
                return;
            }
            bindToActiveNetwork(dnsSocket);
            dnsSocket.setSoTimeout(DNS_TIMEOUT_MS);
            dnsSocket.connect(dnsAddress,53);

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

        String dst=ipString(packet,16);
        if(!dns.equals(dst)) return null;

        int udp=ihl;
        int srcPort=u16(packet,udp);
        int dstPort=u16(packet,udp+2);
        if(dstPort!=53) return null;

        int udpLen=u16(packet,udp+4);
        if(udpLen<8 || udp+udpLen>len) return null;
        int dnsLen=udpLen-8;
        if(dnsLen<=0 || dnsLen>4096) return null;
        byte[] dnsPayload=Arrays.copyOfRange(packet,udp+8,udp+8+dnsLen);

        try{
            if(dnsSocket==null || dnsSocket.isClosed()) return null;

            long start=System.nanoTime();
            lastDnsPacketAtMs=System.currentTimeMillis();
            DatagramPacket q=new DatagramPacket(dnsPayload,dnsPayload.length);
            dnsSocket.send(q);

            byte[] buf=new byte[4096];
            DatagramPacket r=new DatagramPacket(buf,buf.length);
            dnsSocket.receive(r);

            byte[] answer=Arrays.copyOf(r.getData(),r.getLength());
            if(answer.length<12) return null;
            if(dnsPayload.length<2) return null;
            if(answer[0]!=dnsPayload[0] || answer[1]!=dnsPayload[1]) return null;
            if((answer[2]&0x80)==0) return null;

            long latency=(System.nanoTime()-start)/1000000L;
            lastDnsLatencyMs=latency;
            healthFailures=0;

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
            put16(out,26,udpChecksum(out,20,8+answer.length,out,12,out,16));

            return out;
        }catch(Exception e){
            return null;
        }
    }

    void foregroundMonitor(){
        long nextHealth=0;
        while(running){
            try{
                Thread.sleep(1000);
                if(!running) break;

                long now=System.currentTimeMillis();
                if(now>=nextHealth){
                    runHealthCheck();
                    nextHealth=now+DNS_HEALTH_INTERVAL_MS;
                    if(!running) break;
                }

                if(prematchOnly && now-tunnelStartedAtMs>=DNS_PREMATCH_MAX_MS){
                    updateNotification("DNS PRE-MATCH LIMIT\nمحافظ قبل از Match: زمان نشست تمام شد");
                    stopSelf();
                    break;
                }

                if(now-tunnelStartedAtMs>=DNS_MIN_ACTIVE_MS &&
                   now-lastDnsPacketAtMs>=DNS_IDLE_STOP_MS){
                    updateNotification("DNS PRE-MATCH GUARD\nمحافظ قبل از Match: بدون درخواست DNS، تونل خاموش شد");
                    stopSelf();
                    break;
                }

                if(hasUsageAccess()){
                    String pkg=currentForegroundPackage();
                    if(pkg!=null && !isGame(pkg)){
                        stopSelf();
                        break;
                    }
                }
            }catch(Exception ignored){
            }
        }
    }

    void bindToActiveNetwork(DatagramSocket socket){
        if(Build.VERSION.SDK_INT<23 || socket==null) return;
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(cm==null) return;
            Network network=cm.getActiveNetwork();
            if(network!=null){
                network.bindSocket(socket);
                if(Build.VERSION.SDK_INT>=29 && vpnInterface!=null){
                    try{setUnderlyingNetworks(new Network[]{network});}catch(Exception ignored){}
                }
            }
        }catch(Exception ignored){
        }
    }

    void runHealthCheck(){
        if(!running || dnsAddress==null) return;

        long now=System.currentTimeMillis();
        if(now-lastHealthAtMs<DNS_HEALTH_INTERVAL_MS-250) return;
        lastHealthAtMs=now;

        DatagramSocket probe=null;
        try{
            probe=new DatagramSocket();
            if(!protect(probe)){
                healthFailure("VPN protect failed");
                return;
            }
            bindToActiveNetwork(probe);
            probe.setSoTimeout(DNS_TIMEOUT_MS);
            probe.connect(dnsAddress,53);

            byte[] query=buildDnsQuery("connectivitycheck.gstatic.com");
            long start=System.nanoTime();
            probe.send(new DatagramPacket(query,query.length));

            byte[] buf=new byte[1500];
            DatagramPacket response=new DatagramPacket(buf,buf.length);
            probe.receive(response);

            if(response.getLength()<12 || (response.getData()[2]&0x80)==0){
                healthFailure("DNS response invalid");
                return;
            }

            long latency=(System.nanoTime()-start)/1000000L;
            lastDnsLatencyMs=latency;
            healthFailures=0;
            updateNotification("DNS ثابت: "+dns+" • Health OK "+latency+" ms");
        }catch(Exception e){
            healthFailure("DNS timeout/failure");
        }finally{
            if(probe!=null) probe.close();
        }
    }

    int getHealthFailureLimit(){
        int value=getSharedPreferences("t3r0za",MODE_PRIVATE).getInt("dns_fail_limit",MAX_CONSECUTIVE_HEALTH_FAILURES);
        return Math.max(1,Math.min(5,value));
    }

    void healthFailure(String reason){
        healthFailures++;
        int limit=getHealthFailureLimit();
        updateNotification("DNS ثابت: "+dns+" • Health "+healthFailures+"/"+limit+" fail");
        if(healthFailures>=limit){
            closeTunnel();
            stopSelf();
        }
    }

    void updateNotification(String text){
        try{
            startForegroundNow(text);
        }catch(Exception ignored){
        }
    }

    byte[] buildDnsQuery(String host){
        String[] labels=host.split("\\.");
        byte[] b=new byte[512];
        int p=0;
        int id=(int)(System.nanoTime()&0xffff);
        b[p++]=(byte)((id>>8)&255);
        b[p++]=(byte)(id&255);
        b[p++]=1;
        b[p++]=0;
        b[p++]=0;
        b[p++]=1;
        b[p++]=0;
        b[p++]=0;
        b[p++]=0;
        b[p++]=0;
        b[p++]=0;
        b[p++]=0;

        for(String label:labels){
            byte[] x=label.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if(x.length>63 || p+x.length+1>=b.length) return new byte[0];
            b[p++]=(byte)x.length;
            System.arraycopy(x,0,b,p,x.length);
            p+=x.length;
        }

        b[p++]=0;
        b[p++]=0;
        b[p++]=1;
        b[p++]=0;
        b[p++]=1;

        return Arrays.copyOf(b,p);
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

    int udpChecksum(byte[] packet,int udpOff,int udpLen,byte[] srcPacket,int srcIpOff,byte[] dstPacket,int dstIpOff){
        long sum=0;
        for(int i=0;i<4;i+=2){
            sum+=(srcPacket[srcIpOff+i]&255)<<8 | (srcPacket[srcIpOff+i+1]&255);
            sum+=(dstPacket[dstIpOff+i]&255)<<8 | (dstPacket[dstIpOff+i+1]&255);
        }
        sum+=17;
        sum+=udpLen;

        for(int i=udpOff;i<udpOff+udpLen;i+=2){
            int hi=packet[i]&255;
            int lo=(i+1<udpOff+udpLen)?packet[i+1]&255:0;
            sum+=(hi<<8)|lo;
            while((sum>>16)!=0) sum=(sum&0xffff)+(sum>>16);
        }
        int result=(int)(~sum)&0xffff;
        return result==0?0xffff:result;
    }

    void closeTunnel(){
        running=false;
        try{if(dnsSocket!=null) dnsSocket.close();}catch(Exception ignored){}
        dnsSocket=null;
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
