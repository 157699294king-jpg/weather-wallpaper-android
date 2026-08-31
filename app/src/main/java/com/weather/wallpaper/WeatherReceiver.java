package com.weather.wallpaper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.*;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public class WeatherReceiver extends BroadcastReceiver {
    private static final String ACTION_STATUS="com.weather.wallpaper.STATUS";
    @Override public void onReceive(Context c,Intent i){PendingResult p=goAsync();new Thread(()->{update(c);p.finish();}).start();}
    public static void runNow(Context c){new Thread(()->update(c.getApplicationContext())).start();}
    public static void schedule(Context c){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p=PendingIntent.getBroadcast(c,2,new Intent(c,WeatherReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        a.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+5000,3*60*60*1000L,p);
    }
    @SuppressWarnings("MissingPermission") static void update(Context c){
        try{
            if(c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&
               c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                throw new Exception("请先允许位置权限");
            state(c,"正在获取当前位置…");
            LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            Location loc=null;
            for(String p:lm.getProviders(true)){Location x=lm.getLastKnownLocation(p);if(x!=null&&(loc==null||x.getTime()>loc.getTime()))loc=x;}
            if(loc==null)loc=freshLocation(lm);
            if(loc==null)throw new Exception("无法定位，请开启手机定位服务后重试");
            state(c,"正在连接天气服务…");
            String q="https://api.open-meteo.com/v1/forecast?latitude="+loc.getLatitude()+"&longitude="+loc.getLongitude()+"&current=weather_code&timezone=auto";
            HttpURLConnection h=(HttpURLConnection)new URL(q).openConnection();h.setConnectTimeout(12000);h.setReadTimeout(12000);
            if(h.getResponseCode()!=200)throw new Exception("天气服务响应异常 "+h.getResponseCode());
            StringBuilder out=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(h.getInputStream()))){String line;while((line=r.readLine())!=null)out.append(line);}
            int code=new JSONObject(out.toString()).getJSONObject("current").getInt("weather_code");
            String key=key(code),cn=name(key),uri=MainActivity.wallpapers(c).getString(key,null);
            Bitmap b;
            boolean custom=uri!=null;
            if(custom){
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(uri))){
                    b=BitmapFactory.decodeStream(in);
                }
            }else{
                state(c,"正在下载 "+cn+" 超清默认壁纸…");
                HttpURLConnection image=(HttpURLConnection)new URL(defaultUrl(key)).openConnection();
                image.setConnectTimeout(15000);image.setReadTimeout(25000);
                image.setRequestProperty("User-Agent","WeatherWallpaper/1.2");
                try(InputStream in=new BufferedInputStream(image.getInputStream())){b=BitmapFactory.decodeStream(in);}
            }
            if(b==null)throw new Exception("壁纸图片无法读取");
            WallpaperManager.getInstance(c).setBitmap(b,null,true,WallpaperManager.FLAG_SYSTEM);
            state(c,"当前天气："+cn+" · "+(custom?"自定义":"超清默认")+"壁纸已同步");
        }catch(Exception e){state(c,"同步失败："+(e.getMessage()==null?"未知错误":e.getMessage()));}
    }
    @SuppressWarnings("MissingPermission") private static Location freshLocation(LocationManager lm){
        final Location[] result=new Location[1];final CountDownLatch latch=new CountDownLatch(1);
        LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location l){result[0]=l;latch.countDown();}};
        try{
            String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
            lm.requestSingleUpdate(provider,listener,Looper.getMainLooper());latch.await(12,TimeUnit.SECONDS);
        }catch(Exception ignored){}finally{try{lm.removeUpdates(listener);}catch(Exception ignored){}}
        return result[0];
    }
    static String defaultUrl(String key){
        String id;
        switch(key){
            case"clear":id="photo-1500530855697-b586d89ba3ee";break;
            case"cloudy":id="photo-1499346030926-9a72daac6c63";break;
            case"overcast":id="photo-1534088568595-a066f410bcda";break;
            case"rain":id="photo-1519692933481-e162a57d6721";break;
            case"storm":id="photo-1500674425229-f692875b0ab7";break;
            case"snow":id="photo-1483664852095-d6cc6870702d";break;
            default:id="photo-1487621167305-5d248087c724";
        }
        return "https://images.unsplash.com/"+id+"?auto=format&fit=crop&w=2160&h=3840&q=90";
    }
    static String key(int c){if(c==0)return"clear";if(c<=2)return"cloudy";if(c==3)return"overcast";if(c==45||c==48)return"fog";if(c>=71&&c<=86)return"snow";if(c>=95)return"storm";return"rain";}
    static String name(String k){switch(k){case"clear":return"晴天";case"cloudy":return"多云";case"overcast":return"阴天";case"rain":return"雨天";case"storm":return"雷雨";case"snow":return"雪天";default:return"雾霾";}}
    static void state(Context c,String s){
        c.getSharedPreferences("state",0).edit().putString("status",s).apply();
        Intent i=new Intent(ACTION_STATUS);i.setPackage(c.getPackageName());i.putExtra("status",s);c.sendBroadcast(i);
    }
}
