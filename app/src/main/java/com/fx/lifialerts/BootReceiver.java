package com.fx.lifialerts;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try { c.startForegroundService(new Intent(c, MonitorService.class)); } catch (Exception ignored) {}
    }
}
