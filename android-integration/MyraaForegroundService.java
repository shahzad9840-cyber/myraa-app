package ai.myraa.mobile.plugins;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * Keeps MYRAA's process alive while voice or screen share is active,
 * even when the app is backgrounded. Also satisfies Android's
 * requirement (strict since Android 14) that MediaProjection capture
 * and background microphone use both run under a foreground service
 * with a matching declared type.
 */
public class MyraaForegroundService extends Service {

    private static final String CHANNEL_ID = "myraa_active_channel";
    private static final int NOTIFICATION_ID = 4201;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRAA is active")
            .setContentText("Voice and screen sharing stay connected while this is running.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "MYRAA Active", NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
