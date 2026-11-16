package com.galaxyuniversalio;

import android.content.Context;
import android.hardware.usb.*;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.net.Inet4Address;
import java.util.*;

/** Short-lived DNS-SD discovery for cleartext eSCL (_uscan._tcp). */
public final class LanEsclDiscovery {
    public static final class Scanner {
        public final String name, host;
        public final int port;
        Scanner(String n,String h,int p){name=n;host=h;port=p;}
        public String address(){return host+":"+port;}
        @Override public String toString(){return name+" — "+address();}
    }
    public interface Callback { void onFinished(List<Scanner> scanners, String error); }
    private final NsdManager nsd;
    private final WifiManager.MulticastLock lock;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<NsdManager.DiscoveryListener> listeners=new ArrayList<>();
    private final Map<String,Scanner> found=new LinkedHashMap<>();
    private Callback callback;
    private boolean finished;
    public LanEsclDiscovery(Context context) {
        nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        lock=wifi==null?null:wifi.createMulticastLock("GalaxyUniversalIO-eSCL");
    }
    public void start(Callback cb) {
        callback=cb;
        if(nsd==null){finish("Android NSD를 사용할 수 없습니다.");return;}
        try {
            if(lock!=null){lock.setReferenceCounted(false);lock.acquire();}
            // TLS service support is withheld until endpoint hostname and certificate identity
            // are preserved by LanEsclTransport. Never downgrade _uscans to HTTP.
            discover("_uscan._tcp.");
            handler.postDelayed(()->finish(null),8000);
        } catch(RuntimeException e){finish("스캐너 탐색을 시작하지 못했습니다: "+e.getClass().getSimpleName());}
    }
    private void discover(String type) {
        NsdManager.DiscoveryListener l=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onServiceFound(NsdServiceInfo s){if(s.getServiceType().startsWith("_uscan"))try{nsd.resolveService(s,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo i,int e){} public void onServiceResolved(NsdServiceInfo i){if(i.getHost() instanceof Inet4Address){String h=i.getHost().getHostAddress(); if(h!=null) synchronized(found){found.put(i.getServiceName(),new Scanner(i.getServiceName(),h,i.getPort()));}}}});}catch(RuntimeException ignored){}}
            public void onServiceLost(NsdServiceInfo s){synchronized(found){found.remove(s.getServiceName());}}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int e){finish("mDNS 탐색 실패 ("+e+")");}
            public void onStopDiscoveryFailed(String t,int e){}
        };
        listeners.add(l); nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,l);
    }
    public void cancel(){finish(null);}
    private synchronized void finish(String error){if(finished)return;finished=true;handler.removeCallbacksAndMessages(null);for(NsdManager.DiscoveryListener l:listeners)try{nsd.stopServiceDiscovery(l);}catch(RuntimeException ignored){}if(lock!=null&&lock.isHeld())lock.release();List<Scanner> copy; synchronized(found){copy=new ArrayList<>(found.values());}if(callback!=null)callback.onFinished(copy,error);}
}
