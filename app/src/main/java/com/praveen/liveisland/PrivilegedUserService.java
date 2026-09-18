package com.praveen.liveisland;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class PrivilegedUserService extends IPrivilegedShell.Stub {
    public PrivilegedUserService() {}
    public PrivilegedUserService(Context ignored) {}

    @Override public String exec(String command) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            int code = p.waitFor();
            out.append("exit=").append(code);
        } catch (Throwable t) {
            out.append("error=").append(t.getClass().getSimpleName()).append(':').append(t.getMessage());
        }
        return out.toString();
    }

    @Override public void destroy() { System.exit(0); }
}
