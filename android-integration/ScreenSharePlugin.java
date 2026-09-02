package ai.myraa.mobile.plugins;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Native replacement for navigator.mediaDevices.getDisplayMedia (which
 * does not exist in an Android WebView). Uses Android's own
 * MediaProjection API to capture the device's own screen.
 */
@CapacitorPlugin(name = "ScreenShare")
public class ScreenSharePlugin extends Plugin {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private MediaProjection.Callback projectionCallback;

    @PluginMethod
    public void start(PluginCall call) {
        MediaProjectionManager projectionManager =
            (MediaProjectionManager) getActivity().getSystemService(Activity.MEDIA_PROJECTION_SERVICE);
        Intent intent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(call, intent, "handleScreenCaptureResult");
    }

    @ActivityCallback
    private void handleScreenCaptureResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("User denied screen capture permission");
            return;
        }

        // Android requires a foreground service to be running for the
        // whole capture session, and (Android 14+) requires a callback
        // to be registered before createVirtualDisplay is ever called -
        // skipping either makes the capture silently never start.
        MyraaServiceCoordinator.acquire(getActivity());

        MediaProjectionManager projectionManager =
            (MediaProjectionManager) getActivity().getSystemService(Activity.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(result.getResultCode(), result.getData());

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (virtualDisplay != null) virtualDisplay.release();
                if (imageReader != null) imageReader.close();
                virtualDisplay = null;
                imageReader = null;
                mediaProjection = null;
                MyraaServiceCoordinator.release(getActivity());
            }
        };
        mediaProjection.registerCallback(projectionCallback, null);

        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "MyraaScreenShare",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, null
        );

        JSObject ret = new JSObject();
        ret.put("started", true);
        ret.put("width", width);
        ret.put("height", height);
        call.resolve(ret);
    }

    @PluginMethod
    public void captureFrame(PluginCall call) {
        if (imageReader == null || mediaProjection == null) {
            call.reject("Screen share not started. Call start() first.");
            return;
        }
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            call.reject("No frame available yet");
            return;
        }
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
            String base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP);

            JSObject ret = new JSObject();
            ret.put("image", "data:image/jpeg;base64," + base64);
            call.resolve(ret);
        } finally {
            image.close();
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) {
            if (projectionCallback != null) mediaProjection.unregisterCallback(projectionCallback);
            mediaProjection.stop();
        }
        virtualDisplay = null;
        imageReader = null;
        mediaProjection = null;
        projectionCallback = null;
        MyraaServiceCoordinator.release(getActivity());
        call.resolve();
    }
}
