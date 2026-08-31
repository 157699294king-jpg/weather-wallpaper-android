package com.weather.wallpaper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String ACTION_STATUS = "com.weather.wallpaper.STATUS";
    private static final String[] TYPES = {"晴天", "多云", "阴天", "雨天", "雷雨", "雪天", "雾霾"};
    private static final String[] ICONS = {"☀", "☁", "◉", "☂", "ϟ", "❄", "≋"};
    private static final String[] KEYS = {"clear", "cloudy", "overcast", "rain", "storm", "snow", "fog"};
    private TextView status;
    private String choosing;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String value=i.getStringExtra("status");
            if(value!=null) status.setText(value);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(5,11,24));
        getWindow().setNavigationBarColor(Color.rgb(5,11,24));
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(24),dp(20),dp(32));
        root.setBackground(gradient(new int[]{Color.rgb(5,11,24),Color.rgb(10,24,48),Color.rgb(20,12,48)}, GradientDrawable.Orientation.TL_BR,0));

        TextView tag=text("WEATHER  •  SYNC",12,true,Color.rgb(74,222,255));
        tag.setLetterSpacing(.18f); root.addView(tag);
        TextView title=text("天气壁纸",34,true,Color.WHITE); title.setPadding(0,dp(4),0,0); root.addView(title);
        TextView sub=text("让桌面与真实天空实时共振",15,false,Color.rgb(160,179,210)); sub.setPadding(0,0,0,dp(18)); root.addView(sub);

        LinearLayout statusCard=new LinearLayout(this); statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL); statusCard.setPadding(dp(16),dp(15),dp(16),dp(15));
        statusCard.setBackground(card(new int[]{Color.rgb(13,40,68),Color.rgb(25,25,70)},22,Color.rgb(47,211,255),2));
        TextView pulse=text("◉",25,true,Color.rgb(69,236,255)); statusCard.addView(pulse,new LinearLayout.LayoutParams(dp(42),-2));
        LinearLayout statusText=new LinearLayout(this); statusText.setOrientation(LinearLayout.VERTICAL);
        statusText.addView(text("实时天气状态",11,true,Color.rgb(109,226,255)));
        status=text("等待首次天气同步",16,true,Color.WHITE); status.setPadding(0,dp(3),0,0); statusText.addView(status);
        statusCard.addView(statusText,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(statusCard,lpMargins(-1,-2,0,0,0,dp(24)));

        TextView section=text("天气场景库",20,true,Color.WHITE); root.addView(section);
        TextView sectionSub=text("点击卡片，为每种天气绑定专属壁纸",13,false,Color.rgb(132,151,182)); sectionSub.setPadding(0,dp(3),0,dp(8)); root.addView(sectionSub);

        for(int i=0;i<TYPES.length;i++){
            final String key=KEYS[i];
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(15),0,dp(14),0);
            row.setBackground(card(new int[]{Color.rgb(17,30,54),Color.rgb(27,25,60)},18,Color.rgb(46,70,110),1));
            TextView icon=text(ICONS[i],25,true,Color.rgb(89,221,255)); icon.setGravity(Gravity.CENTER);
            icon.setBackground(circle(Color.rgb(21,57,82))); row.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(13),0,0,0);
            words.addView(text(TYPES[i],17,true,Color.WHITE));
            words.addView(text(savedMark(key),12,false,getPreferences(0).contains(key)?Color.rgb(71,239,179):Color.rgb(133,151,181)));
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(text("›",30,false,Color.rgb(102,214,255)));
            row.setOnClickListener(v->choose(key));
            root.addView(row,lpMargins(-1,dp(68),0,dp(7),0,0));
        }

        TextView now=text("⚡  立即同步天气并更换",17,true,Color.WHITE); now.setGravity(Gravity.CENTER);
        now.setBackground(gradient(new int[]{Color.rgb(0,183,255),Color.rgb(93,61,255),Color.rgb(195,54,255)},GradientDrawable.Orientation.LEFT_RIGHT,22));
        now.setElevation(dp(10)); now.setOnClickListener(v->{status.setText("正在连接气象卫星…");WeatherReceiver.runNow(this);});
        root.addView(now,lpMargins(-1,dp(62),0,dp(20),0,0));

        TextView note=text("AUTO SYNC  ·  每 3 小时更新  ·  开机自动恢复",11,true,Color.rgb(100,126,166));
        note.setGravity(Gravity.CENTER); note.setLetterSpacing(.08f); root.addView(note);

        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.addView(root); setContentView(sc);
        WeatherReceiver.schedule(this); refreshStatus();
    }

    @Override protected void onStart(){
        super.onStart();
        IntentFilter f=new IntentFilter(ACTION_STATUS);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(statusReceiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver,f);
        refreshStatus();
    }
    @Override protected void onStop(){ super.onStop(); try{unregisterReceiver(statusReceiver);}catch(Exception ignored){} }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==7&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED) WeatherReceiver.runNow(this);
        else if(r==7) status.setText("需要位置权限才能识别当地天气");
    }
    private void choose(String key){choosing=key;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,20);}
    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==20&&c==RESULT_OK&&data!=null){
            Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(0).edit().putString(choosing,u.toString()).apply();recreate();
        }
    }
    private String savedMark(String k){return getPreferences(0).contains(k)?"已绑定自定义壁纸  ✓":"热门超清默认壁纸";}
    private void refreshStatus(){String s=getSharedPreferences("state",0).getString("status","");if(!s.isEmpty()&&status!=null)status.setText(s);}
    static android.content.SharedPreferences wallpapers(Context c){return c.getSharedPreferences(MainActivity.class.getName(),0);}
    private TextView text(String s,int z,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(color);if(bold)v.setTypeface(Typeface.create("sans",Typeface.BOLD));return v;}
    private GradientDrawable gradient(int[] colors,GradientDrawable.Orientation o,int radius){GradientDrawable g=new GradientDrawable(o,colors);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable card(int[] colors,int radius,int stroke,int width){GradientDrawable g=gradient(colors,GradientDrawable.Orientation.LEFT_RIGHT,radius);g.setStroke(dp(width),stroke);return g;}
    private GradientDrawable circle(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
    private LinearLayout.LayoutParams lpMargins(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
