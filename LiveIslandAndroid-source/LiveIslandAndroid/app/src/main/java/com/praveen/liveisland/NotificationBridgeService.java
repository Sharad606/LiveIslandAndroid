package com.praveen.liveisland;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

public class NotificationBridgeService extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        NotificationActionRegistry.put(sbn);
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String category = n.category == null ? "" : n.category;
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName();
        String all = (title + " " + text).toLowerCase(Locale.ROOT);

        IslandEvent.Type type;
        if (Notification.CATEGORY_CALL.equals(category)) type = IslandEvent.Type.CALL;
        else if (Notification.CATEGORY_TRANSPORT.equals(category)) type = IslandEvent.Type.MEDIA;
        else if ("navigation".equals(category) || pkg.contains("maps")) type = IslandEvent.Type.NAVIGATION;
        else if (Notification.CATEGORY_ALARM.equals(category) || all.contains("alarm")) type = IslandEvent.Type.ALARM;
        else if (pkg.contains("bluetooth") && (all.contains("file") || all.contains("transfer") || e.getInt(Notification.EXTRA_PROGRESS_MAX,0)>0)) type = IslandEvent.Type.BLUETOOTH;
        else if (looksLikePayment(all)) type = IslandEvent.Type.PAYMENT;
        else if (e.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) type = IslandEvent.Type.DOWNLOAD;
        else if (Notification.CATEGORY_MESSAGE.equals(category)) type = IslandEvent.Type.MESSAGE;
        else if (all.contains("timer") || all.contains("remaining")) type = IslandEvent.Type.TIMER;
        else type = IslandEvent.Type.OTHER;

        int progress = -1;
        int max = e.getInt(Notification.EXTRA_PROGRESS_MAX,0);
        if (max > 0) progress = Math.max(0, Math.min(100, Math.round(e.getInt(Notification.EXTRA_PROGRESS,0) * 100f / max)));
        IslandService.pushEvent(this, new IslandEvent(type, title.isEmpty()? appLabel(pkg) : title, text, progress, sbn.getKey()));
        if (type == IslandEvent.Type.MEDIA) MediaBridge.refresh(this);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) { NotificationActionRegistry.remove(sbn.getKey()); IslandService.clearEvent(this, sbn.getKey()); }
        MediaBridge.refresh(this);
    }

    private String appLabel(String pkg) { try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString(); } catch (Throwable t) { return pkg; } }
    private static String str(CharSequence c) { return c == null ? "" : c.toString(); }
    private static boolean looksLikePayment(String s) { return s.contains("payment") || s.contains("paid ") || s.contains("received ₹") || s.contains("upi") || s.contains("tap to pay"); }
}
