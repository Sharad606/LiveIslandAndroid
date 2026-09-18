package com.praveen.liveisland;

import android.content.Context;
import android.content.SharedPreferences;

public final class IslandPreferences {
    private static final String FILE = "live_island";
    private final SharedPreferences p;
    public IslandPreferences(Context c) { p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public boolean enabled() { return p.getBoolean("enabled", false); }
    public void setEnabled(boolean v) { p.edit().putBoolean("enabled", v).apply(); }
    public String qrUri() { return p.getString("qr_uri", ""); }
    public void setQrUri(String v) { p.edit().putString("qr_uri", v == null ? "" : v).apply(); }
    public boolean qrBiometric() { return p.getBoolean("qr_bio", true); }
    public void setQrBiometric(boolean v) { p.edit().putBoolean("qr_bio", v).apply(); }
    public int xOffsetDp() { return p.getInt("x_offset", 0); }
    public int yOffsetDp() { return p.getInt("y_offset", 0); }
    public void setOffsets(int x, int y) { p.edit().putInt("x_offset", x).putInt("y_offset", y).apply(); }
}
