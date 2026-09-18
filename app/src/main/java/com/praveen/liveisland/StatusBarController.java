package com.praveen.liveisland;

import android.content.Context;
import android.graphics.Rect;

public final class StatusBarController {
    private final Context context;
    private String last = "";
    public StatusBarController(Context context) { this.context = context.getApplicationContext(); }

    public void update(Rect island, int screenWidth, boolean expanded, IslandService.AnchorEdge edge) {
        if (!expanded || edge != IslandService.AnchorEdge.TOP || island == null) { restore(); return; }
        boolean left = island.left < (int)(screenWidth * 0.43f);
        boolean right = island.right > (int)(screenWidth * 0.57f);
        StringBuilder flags = new StringBuilder();
        if (left) flags.append(" clock notification-icons");
        if (right) flags.append(" system-icons");
        if (flags.length() == 0) { restore(); return; }
        run("cmd statusbar send-disable-flag" + flags);
    }

    public void restore() { run("cmd statusbar send-disable-flag none"); }

    private void run(String command) {
        if (!ShizukuBridge.granted()) { last = ""; return; }
        if (command.equals(last)) return;
        last = command;
        ShizukuBridge.exec(context, command, null);
    }
}
