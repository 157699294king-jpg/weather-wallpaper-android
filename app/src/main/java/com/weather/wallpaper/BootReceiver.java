package com.weather.wallpaper;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        WeatherReceiver.schedule(c);
        WeatherReceiver.runNow(c);
    }
}
