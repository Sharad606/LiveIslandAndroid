package com.praveen.liveisland;

public final class IslandEvent {
    public enum Type { IDLE, MEDIA, CALL, TIMER, NAVIGATION, MESSAGE, DOWNLOAD, BLUETOOTH, CHARGING, PAYMENT, BIOMETRIC, NFC, ALARM, OTHER }
    public final Type type;
    public final String title;
    public final String subtitle;
    public final int progress; // -1 when not applicable
    public final String notificationKey;

    public IslandEvent(Type type, String title, String subtitle) { this(type, title, subtitle, -1, null); }
    public IslandEvent(Type type, String title, String subtitle, int progress, String notificationKey) {
        this.type = type == null ? Type.OTHER : type;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.progress = progress;
        this.notificationKey = notificationKey;
    }

    public static IslandEvent idle() { return new IslandEvent(Type.IDLE, "", ""); }
}
