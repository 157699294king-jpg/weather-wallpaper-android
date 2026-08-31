package com.weather.wallpaper;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String[] TYPES = {"晴天", "多云", "阴天", "雨天", "雷雨", "雪天", "雾霾"};
    private static final String[] KEYS = {"clear", "cloudy", "overcast", "rain", "storm", "snow", "fog"};
    private TextView status;
    private String choosing;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(244,247,251));
        root.addView(text("天气壁纸", 28, true));
        TextView sub = text("让桌面随天空一起变化", 15, false);
        sub.setTextColor(Color.DKGRAY); root.addView(sub);
        status = text("正在等待首次天气更新…", 16, true);
        status.setPadding(dp(16),dp(16),dp(16),dp(16));
        status.setBackground(round(Color.WHITE, 18));
        LinearLayout.LayoutParams sp = lp(-1,-2); sp.setMargins(0,dp(20),0,dp(14)); root.addView(status,sp);
        root.addView(text("为每种天气设置壁纸", 18, true));
        for (int i=0;i<TYPES.length;i++) {
            final String key=KEYS[i];
            Button v = new Button(this);
            v.setAllCaps(false); v.setText(TYPES[i] + savedMark(key)); v.setTextSize(16);
            v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); v.setPadding(dp(18),0,dp(14),0);
            v.setBackground(round(Color.WHITE, 16)); v.setOnClickListener(x -> choose(key));
            LinearLayout.LayoutParams p=lp(-1,dp(56)); p.setMargins(0,dp(8),0,0); root.addView(v,p);
        }
        Button now = new Button(this); now.setText("立即识别天气并更换"); now.setTextColor(Color.WHITE);
        now.setTextSize(16); now.setBackground(round(Color.rgb(59,130,246),18));
        now.setOnClickListener(v->{ status.setText("正在获取天气…"); WeatherReceiver.runNow(this); });
        LinearLayout.LayoutParams np=lp(-1,dp(58)); np.setMargins(0,dp(18),0,0); root.addView(now,np);
        TextView note=text("自动更新：每 3 小时检查一次。部分手机需在系统设置中允许后台运行。",13,false);
        note.setTextColor(Color.GRAY); note.setPadding(0,dp(12),0,dp(24)); root.addView(note);
        ScrollView sc=new ScrollView(this); sc.addView(root); setContentView(sc);
        WeatherReceiver.schedule(this); refreshStatus();
    }
    private void choose(String key) {
        choosing=key; Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,20);
    }
    @Override protected void onActivityResult(int r,int c,Intent data) {
        super.onActivityResult(r,c,data);
        if(r==20&&c==RESULT_OK&&data!=null){
            Uri u=data.getData();
            getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(0).edit().putString(choosing,u.toString()).apply(); recreate();
        }
    }
    private String savedMark(String k){ return getPreferences(0).contains(k) ? "    ✓ 已设置" : "    选择图片 ›"; }
    private void refreshStatus(){ String s=getSharedPreferences("state",0).getString("status",""); if(!s.isEmpty()) status.setText(s); }
    static android.content.SharedPreferences wallpapers(Context c){ return c.getSharedPreferences(MainActivity.class.getName(),0); }
    private TextView text(String s,int z,boolean bold){ TextView v=new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(Color.rgb(25,35,55)); if(bold)v.setTypeface(null,1); return v; }
    private GradientDrawable round(int color,int radius){ GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}
