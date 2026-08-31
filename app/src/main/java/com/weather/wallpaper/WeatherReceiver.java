package com.weather.wallpaper;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.*;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class WeatherReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        PendingResult p=goAsync();
        new Thread(()->{ update(c); p.finish(); }).start();
    }
    public static void runNow(Context c){ new Thread(()->update(c.getApplicationContext())).start(); }
    public static void schedule(Context c){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(c,WeatherReceiver.class);
        PendingIntent p=PendingIntent.getBroadcast(c,2,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        a.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+5000,3*60*60*1000L,p);
    }
    @SuppressWarnings("MissingPermission") static void update(Context c){
        try {
            LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            Location loc=null;
            for(String p:lm.getProviders(true)){
                Location x=lm.getLastKnownLocation(p);
                if(x!=null&&(loc==null||x.getTime()>loc.getTime()))loc=x;
            }
            if(loc==null) throw new Exception("暂时无法获取位置，请打开定位后重试");
            String q="https://api.open-meteo.com/v1/forecast?latitude="+loc.getLatitude()+"&longitude="+loc.getLongitude()+"&current=weather_code&timezone=auto";
            HttpURLConnection h=(HttpURLConnection)new URL(q).openConnection();
            h.setConnectTimeout(12000); h.setReadTimeout(12000);
            StringBuilder out=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(h.getInputStream()))){
                String line; while((line=r.readLine())!=null)out.append(line);
            }
            int code=new JSONObject(out.toString()).getJSONObject("current").getInt("weather_code");
            String key=key(code), cn=name(key);
            String uri=MainActivity.wallpapers(c).getString(key,null);
            if(uri==null) state(c,"当前天气："+cn+"；尚未为它设置壁纸");
            else {
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(uri))){
                    Bitmap b=BitmapFactory.decodeStream(in);
                    WallpaperManager.getInstance(c).setBitmap(b,null,true,WallpaperManager.FLAG_SYSTEM);
                }
                state(c,"当前天气："+cn+"；桌面壁纸已更新");
            }
        } catch(Exception e){ state(c,"更新失败："+e.getMessage()); }
    }
    static String key(int c){
        if(c==0)return"clear"; if(c<=2)return"cloudy"; if(c==3)return"overcast";
        if(c==45||c==48)return"fog"; if(c>=71&&c<=86)return"snow";
        if(c>=95)return"storm"; return"rain";
    }
    static String name(String k){
        switch(k){
            case"clear":return"晴天"; case"cloudy":return"多云"; case"overcast":return"阴天";
            case"rain":return"雨天"; case"storm":return"雷雨"; case"snow":return"雪天";
            default:return"雾霾";
        }
    }
    static void state(Context c,String s){ c.getSharedPreferences("state",0).edit().putString("status",s).apply(); }
}
