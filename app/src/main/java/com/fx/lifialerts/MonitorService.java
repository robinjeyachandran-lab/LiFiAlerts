package com.fx.lifialerts;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.webkit.*;
import java.util.regex.*;

public class MonitorService extends Service {
    static final String URL = "https://iot-projects.eweb.org.in/FX/lifi/";
    static final int INTERVAL_MS = 3000;           // check every 30 seconds
    static final double TEMP_LIMIT = 40, RAIN_LIMIT = 80, SOIL_LIMIT = 80;
    static volatile String last = "Waiting for data...";

    Handler h = new Handler(Looper.getMainLooper());
    WebView wv;
    boolean aTemp, aRain, aSoil, aQuake;

    final Runnable loop = new Runnable() {
        public void run() { if (wv != null) wv.loadUrl(URL); h.postDelayed(this, INTERVAL_MS); }
    };

    @Override public int onStartCommand(Intent i, int f, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "Monitoring", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel ch = new NotificationChannel("alerts", "Alerts", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "monitor")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("LiFi Alerts running").setContentText("Watching sensor data").build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);
        if (wv == null) {
            wv = new WebView(this);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            wv.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String u) {
                    h.postDelayed(() -> v.evaluateJavascript("document.body.innerText", MonitorService.this::parse), 3000);
                }
            });
            h.post(loop);
        }
        return START_STICKY;
    }

    Double num(String t, String label) {
        Matcher m = Pattern.compile("(?i)" + label + "\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(t);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }

    void parse(String raw) {
        if (raw == null) return;
        String t = raw.replace("\\n", " ").replace("\\t", " ");
        Double temp = num(t, "TEMPERATURE"), rain = num(t, "RAIN LEVEL"), soil = num(t, "WATER LEVEL");
        Matcher q = Pattern.compile("(?i)EARTHQUAKE\\s+([A-Za-z]+)").matcher(t);
        String quake = q.find() ? q.group(1).toUpperCase() : null;
        if (temp == null && rain == null && soil == null && quake == null) { last = "Could not read data from the website"; return; }
        last = "Temperature: " + temp + " °C\nRain: " + rain + " %\nWater level / Water: " + soil + " %\nEarthquake: " + quake
                + "\nUpdated: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        boolean ht = temp != null && temp > TEMP_LIMIT;
        if (ht && !aTemp) alert(10, "🌡️ High Temperature", "Temperature is " + temp + " °C (above 40)");
        aTemp = ht;
        boolean hr = rain != null && rain > RAIN_LIMIT;
        if (hr && !aRain) alert(11, "🌧️ High Rainfall", "Rain level is " + rain + " % (above 80)");
        aRain = hr;
        boolean hs = soil != null && soil > SOIL_LIMIT;
        if (hs && !aSoil) alert(12, "💧 High Water level", "Water Level is " + soil + " % (above 80)");
        aSoil = hs;
        boolean eq = quake != null && !quake.equals("NORMAL") && !quake.equals("NO");
        if (eq && !aQuake) alert(13, "🌍 Earthquake Alert", "Earthquake status: " + quake);
        aQuake = eq;
    }

    void alert(int id, String title, String text) {
        Notification n = new Notification.Builder(this, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title).setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
        getSystemService(NotificationManager.class).notify(id, n);
    }

    @Override public void onDestroy() { h.removeCallbacks(loop); if (wv != null) { wv.destroy(); wv = null; } super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
