package com.fx.lifialerts;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    static final String URL = "https://iot-projects.eweb.org.in/FX/lifi/";
    WebView web;
    Handler h = new Handler(Looper.getMainLooper());
    Runnable refresh = new Runnable() {
        public void run() { if (web != null) web.reload(); h.postDelayed(this, 10000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 80, 0, 0);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(URL);
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        startForegroundService(new Intent(this, MonitorService.class));
    }

    @Override protected void onResume() { super.onResume(); h.postDelayed(refresh, 10000); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresh); }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
