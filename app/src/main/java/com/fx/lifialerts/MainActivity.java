package com.fx.lifialerts;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    Handler h = new Handler(Looper.getMainLooper());
    Runnable tick = new Runnable() { public void run() { status.setText(MonitorService.last); h.postDelayed(this, 2000); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);
        TextView title = new TextView(this);
        title.setText("LiFi Environmental Alerts");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, 48, 0, 48);
        Button stop = new Button(this);
        stop.setText("Stop monitoring");
        stop.setOnClickListener(v -> stopService(new Intent(this, MonitorService.class)));
        Button start = new Button(this);
        start.setText("Start monitoring");
        start.setOnClickListener(v -> startForegroundService(new Intent(this, MonitorService.class)));
        root.addView(title); root.addView(status); root.addView(start); root.addView(stop);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        startForegroundService(new Intent(this, MonitorService.class));
    }
    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
}
