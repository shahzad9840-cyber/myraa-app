package ai.myraa.mobile.plugins;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.concurrent.atomic.AtomicInteger;

public class MyraaServiceCoordinator {
    private static final AtomicInteger activeUsers = new AtomicInteger(0);

    /** Call when a feature (voice or screen share) starts needing the
     *  foreground service. Safe to call even if it's already running. */
    public static void acquire(Context context) {
        if (activeUsers.getAndIncrement() == 0) {
            Intent intent = new Intent(context, MyraaForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }

    /** Call when a feature stops needing it. Only actually stops the
     *  service once nothing else is still using it. */
    public static void release(Context context) {
        int remaining = activeUsers.updateAndGet(v -> Math.max(0, v - 1));
        if (remaining == 0) {
            context.stopService(new Intent(context, MyraaForegroundService.class));
        }
    }
}
