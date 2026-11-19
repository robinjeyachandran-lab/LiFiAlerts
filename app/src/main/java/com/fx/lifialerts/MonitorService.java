package com.fx.lifialerts;
import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.webkit.*;
import java.util.Locale;
import java.util.regex.*;

public class MonitorService extends Service {
    static final String URL = "https://iot-projects.eweb.org.in/FX/lifi/";
    static final int INTERVAL_MS = 3000;
    static final double TEMP_LIMIT = 40, RAIN_LIMIT = 80, WATER_LIMIT = 70;
    static volatile String last = "Waiting for data...";

    Handler h = new Handler(Looper.getMainLooper());
    WebView wv;
    TextToSpeech tts;
    boolean ttsReady;
    boolean aTemp, aRain, aWater, aQuake;

    final Runnable loop = new Runnable() {
        public void run() { if (wv != null) wv.loadUrl(URL); h.postDelayed(this, INTERVAL_MS); }
    };

    @Override public int onStartCommand(Intent i, int f, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "Monitoring", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel ch = new NotificationChannel("alerts", "Alerts", NotificationManager.IMPORTANCE_HIGH);
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "monitor")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("LiFi Alerts running").setContentText("Watching sensor data").build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);

        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.ENGLISH);
                    tts.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    ttsReady = true;
                }
            });
        }
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
        Double temp = num(t, "TEMPERATURE"), rain = num(t, "RAIN LEVEL"), water = num(t, "WATER LEVEL");
        Matcher q = Pattern.compile("(?i)EARTHQUAKE\\s+([A-Za-z]+)").matcher(t);
        String quake = q.find() ? q.group(1).toUpperCase() : null;
        if (temp == null && rain == null && water == null && quake == null) { last = "Could not read data from the website"; return; }
        last = "Temperature: " + temp + " °C\nRain: " + rain + " %\nWater level: " + water + " %\nEarthquake: " + quake
                + "\nUpdated: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        boolean ht = temp != null && temp > TEMP_LIMIT;
        if (ht && !aTemp) alert(10, "🌡️ High Temperature", "Temperature is " + temp + " °C (above 40)",
                "Warning. High temperature. Temperature is " + temp + " degrees Celsius.");
        aTemp = ht;
        boolean hr = rain != null && rain > RAIN_LIMIT;
        if (hr && !aRain) alert(11, "🌧️ High Rainfall", "Rain level is " + rain + " % (above 80)",
                "Warning. High rainfall. Rain level is " + rain + " percent.");
        aRain = hr;
        boolean hw = water != null && water > WATER_LIMIT;
        if (hw && !aWater) alert(12, "🌊 High Water Level", "Water level is " + water + " % (above 70)",
                "Warning. High water level. Water level is " + water + " percent.");
        aWater = hw;
        boolean eq = quake != null && !quake.equals("NORMAL") && !quake.equals("NO");
        if (eq && !aQuake) alert(13, "🌍 Earthquake Alert", "Earthquake status: " + quake,
                "Danger. Earthquake detected. Please move to a safe place.");
        aQuake = eq;
    }

    void alert(int id, String title, String text, String speech) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title).setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build();
        getSystemService(NotificationManager.class).notify(id, n);
        speak(speech);
    }

    void speak(String text) {
        if (!ttsReady || tts == null) return;
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "a1");
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "a2");
    }

    @Override public void onDestroy() {
        h.removeCallbacks(loop);
        if (wv != null) { wv.destroy(); wv = null; }
        if (tts != null) { tts.shutdown(); tts = null; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
