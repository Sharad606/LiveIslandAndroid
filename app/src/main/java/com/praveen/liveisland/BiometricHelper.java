package com.praveen.liveisland;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class BiometricHelper {
    private BiometricHelper() {}
    public static void authenticate(Activity activity, String title, String subtitle, Consumer<Boolean> done) {
        if (android.os.Build.VERSION.SDK_INT < 28) { done.accept(false); return; }
        Executor ex = activity.getMainExecutor();
        CancellationSignal cancel = new CancellationSignal();
        BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                .setTitle(title).setSubtitle(subtitle).setNegativeButton("Cancel", ex, (d,w) -> done.accept(false)).build();
        prompt.authenticate(cancel, ex, new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { done.accept(true); }
            @Override public void onAuthenticationError(int errorCode, CharSequence errString) { done.accept(false); }
        });
    }
}
