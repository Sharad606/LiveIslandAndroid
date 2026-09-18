package com.praveen.liveisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        IslandPreferences p=new IslandPreferences(context);
        if(p.enabled() && Settings.canDrawOverlays(context)) { try { IslandService.start(context); } catch (Throwable ignored) {} }
    }
}
