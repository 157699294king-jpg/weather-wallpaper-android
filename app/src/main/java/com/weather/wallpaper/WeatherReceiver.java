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
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

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
                throw new Exception("请先允许精确位置权限");
            state(c,"正在获取当前位置…");
            LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            Location loc=null;
            for(String p:lm.getProviders(true)){Location x=lm.getLastKnownLocation(p);if(x!=null&&(loc==null||x.getTime()>loc.getTime()))loc=x;}
            if(loc==null)loc=freshLocation(lm);
            if(loc==null)throw new Exception("无法定位，请开启手机定位服务");

            Place place=resolvePlace(c,loc);
            state(c,place.display+" · 正在匹配中国气象局站点…");
            String station=findCmaStation(place.query);
            if(station==null&&place.city!=null)station=findCmaStation(place.city);
            if(station==null)throw new Exception("中国气象局未匹配到当地气象站");

            JSONObject data=getJson("https://weather.cma.cn/api/weather/view?stationid="+station).getJSONObject("data");
            JSONObject now=data.optJSONObject("now");
            JSONArray daily=data.optJSONArray("daily");
            String description="";
            if(daily!=null&&daily.length()>0){
                JSONObject today=daily.getJSONObject(0);
                description=today.optString("dayText",today.optString("nightText",""));
            }
            if(description.isEmpty())description="多云";
            String key=weatherKey(description);
            String temp=now==null?"":now.optString("temperature","");
            String weatherLabel=description+(temp.isEmpty()?"":" "+temp+"℃");

            String uri=MainActivity.wallpapers(c).getString(key,null);
            Bitmap b; boolean custom=uri!=null;
            if(custom){
                try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(uri))){b=BitmapFactory.decodeStream(in);}
            }else{
                File cache=new File(c.getFilesDir(),"default_"+key+".jpg");
                if(cache.exists())b=BitmapFactory.decodeFile(cache.getAbsolutePath());
                else{
                    state(c,place.display+" · 正在下载 "+description+" 超清默认壁纸…");
                    HttpURLConnection image=open(defaultUrl(key));
                    try(InputStream in=new BufferedInputStream(image.getInputStream())){b=BitmapFactory.decodeStream(in);}
                    if(b!=null)try(FileOutputStream out=new FileOutputStream(cache)){b.compress(Bitmap.CompressFormat.JPEG,92,out);}
                }
            }
            if(b==null)throw new Exception("壁纸图片无法读取");
            WallpaperManager.getInstance(c).setBitmap(b,null,true,WallpaperManager.FLAG_SYSTEM);
            state(c,place.display+" · "+weatherLabel+" · "+(custom?"自定义":"超清默认")+"壁纸已同步");
        }catch(Exception e){state(c,"同步失败："+(e.getMessage()==null?"未知错误":e.getMessage()));}
    }

    static class Place{
        String display,query,city;
        Place(String d,String q,String c){display=d;query=q;city=c;}
    }
    static Place resolvePlace(Context c,Location loc){
        try{
            Geocoder g=new Geocoder(c,Locale.CHINA);
            List<Address> list=g.getFromLocation(loc.getLatitude(),loc.getLongitude(),1);
            if(list!=null&&!list.isEmpty()){
                Address a=list.get(0);LinkedHashSet<String> parts=new LinkedHashSet<>();
                add(parts,a.getAdminArea());add(parts,a.getLocality());add(parts,a.getSubAdminArea());
                add(parts,a.getSubLocality());add(parts,a.getThoroughfare());add(parts,a.getFeatureName());
                StringBuilder display=new StringBuilder();for(String p:parts){if(display.length()>0)display.append(" ");display.append(p);}
                String query=first(a.getSubAdminArea(),a.getLocality(),a.getAdminArea());
                return new Place(display.length()>0?display.toString():"当前位置",query,a.getLocality());
            }
        }catch(Exception ignored){}
        return new Place("当前位置","北京","北京");
    }
    static void add(Set<String>s,String v){if(v!=null&&!v.trim().isEmpty())s.add(v.trim());}
    static String first(String...v){for(String x:v)if(x!=null&&!x.trim().isEmpty())return x.trim();return"北京";}

    static String findCmaStation(String name)throws Exception{
        String url="https://weather.cma.cn/api/autocomplete?q="+URLEncoder.encode(name,"UTF-8");
        JSONObject root=getJson(url);JSONArray data=root.optJSONArray("data");
        if(data==null||data.length()==0)return null;
        String row=data.getString(0);String[] p=row.split("\\|");
        return p.length>0?p[0]:null;
    }
    static JSONObject getJson(String url)throws Exception{
        HttpURLConnection h=open(url);
        if(h.getResponseCode()!=200)throw new Exception("中国气象局服务响应 "+h.getResponseCode());
        StringBuilder out=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(h.getInputStream()))){String line;while((line=r.readLine())!=null)out.append(line);}
        JSONObject root=new JSONObject(out.toString());
        if(root.has("code")&&root.optInt("code",0)!=0)throw new Exception("中国气象局数据暂不可用");
        return root;
    }
    static HttpURLConnection open(String url)throws Exception{
        HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();
        h.setConnectTimeout(15000);h.setReadTimeout(25000);
        h.setRequestProperty("User-Agent","Mozilla/5.0 WeatherWallpaper/1.4");
        h.setRequestProperty("Referer","https://weather.cma.cn/");
        return h;
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

    static String weatherKey(String s){
        if(s.contains("雷"))return"storm";
        if(s.contains("雪")||s.contains("冰雹"))return"snow";
        if(s.contains("雨"))return"rain";
        if(s.contains("雾")||s.contains("霾")||s.contains("沙")||s.contains("尘"))return"fog";
        if(s.contains("阴"))return"overcast";
        if(s.contains("多云"))return"cloudy";
        return"clear";
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
        return"https://images.unsplash.com/"+id+"?auto=format&fit=crop&w=2160&h=3840&q=90";
    }
    static void state(Context c,String s){
        c.getSharedPreferences("state",0).edit().putString("status",s).apply();
        Intent i=new Intent(ACTION_STATUS);i.setPackage(c.getPackageName());i.putExtra("status",s);c.sendBroadcast(i);
    }
}
