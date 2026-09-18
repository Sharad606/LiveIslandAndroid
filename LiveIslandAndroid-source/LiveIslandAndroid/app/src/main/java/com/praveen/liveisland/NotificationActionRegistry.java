package com.praveen.liveisland;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.StatusBarNotification;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationActionRegistry {
    private static final Map<String, StatusBarNotification> MAP = new ConcurrentHashMap<>();
    private NotificationActionRegistry() {}
    public static void put(StatusBarNotification n) { if (n != null) MAP.put(n.getKey(), n); }
    public static void remove(String key) { if (key != null) MAP.remove(key); }

    public static boolean open(String key) {
        try {
            StatusBarNotification s = MAP.get(key);
            PendingIntent p = s == null ? null : s.getNotification().contentIntent;
            if (p != null) { p.send(); return true; }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean actionByWords(String key, String... words) {
        try {
            StatusBarNotification s = MAP.get(key);
            Notification.Action[] a = s == null ? null : s.getNotification().actions;
            if (a == null) return false;
            for (Notification.Action x : a) {
                String title = x.title == null ? "" : x.title.toString().toLowerCase();
                for (String w : words) {
                    if (title.contains(w.toLowerCase()) && x.actionIntent != null) { x.actionIntent.send(); return true; }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean action(String key, int index) {
        try {
            StatusBarNotification s = MAP.get(key);
            Notification.Action[] a = s == null ? null : s.getNotification().actions;
            if (a != null && index >= 0 && index < a.length && a[index].actionIntent != null) {
                a[index].actionIntent.send(); return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
