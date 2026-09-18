package com.praveen.liveisland;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int REQUEST_CODE = 4401;
    private static volatile IPrivilegedShell shell;
    private static volatile boolean binding;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ShizukuBridge() {}

    public static boolean available() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    public static boolean granted() {
        try { return available() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Throwable t) { return false; }
    }

    public static void requestPermission() {
        try { if (available() && !granted()) Shizuku.requestPermission(REQUEST_CODE); } catch (Throwable ignored) {}
    }

    public static void ensureBound(Context context, Runnable ready) {
        if (shell != null) { if (ready != null) ready.run(); return; }
        if (!granted() || binding) return;
        binding = true;
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(context.getPackageName(), PrivilegedUserService.class.getName()))
                    .processNameSuffix("island_shell").daemon(false).tag("live_island_shell").version(1);
            Shizuku.bindUserService(args, new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    shell = IPrivilegedShell.Stub.asInterface(service);
                    binding = false;
                    if (ready != null) ready.run();
                }
                @Override public void onServiceDisconnected(ComponentName name) { shell = null; binding = false; }
            });
        } catch (Throwable t) { binding = false; }
    }

    public static void exec(Context context, String command, Consumer<String> result) {
        ensureBound(context, () -> IO.execute(() -> {
            String out;
            try { out = shell == null ? "Shizuku service unavailable" : shell.exec(command); }
            catch (Throwable t) { out = "error=" + t.getMessage(); shell = null; }
            if (result != null) result.accept(out);
        }));
    }
}
