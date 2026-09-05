package com.survival.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;

public class ScreenCaptureService extends Service {
    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private static final int NOTIFY_ID = 2002;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private FloatingOverlayView overlayView;
    private WindowManager windowManager;
    private int screenWidth, screenHeight, densityDpi;
    private MarkerDetector detector = new MarkerDetector();
    private volatile boolean running = false;
    private Runnable captureRunnable;
    private boolean isSetupDone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFY_ID, getNotification());

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        densityDpi = metrics.densityDpi;
        windowManager = wm;

        overlayView = new FloatingOverlayView(this, windowManager);
        overlayView.show();

        backgroundThread = new HandlerThread("ScreenCaptureThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");
            if (resultCode != -1 && data != null && !isSetupDone) {
                MediaProjectionManager projMgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                mediaProjection = projMgr.getMediaProjection(resultCode, data);
                setupVirtualDisplay();
                isSetupDone = true;
                startCaptureLoop();
            }
        }
        return START_STICKY;
    }

    private void setupVirtualDisplay() {
        if (mediaProjection == null) return;
        // +1 buffer để tránh mất frame khi VirtualDisplay ghi cùng lúc reader đọc
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCaptureDisplay",
                screenWidth, screenHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );
    }

    private void startCaptureLoop() {
        if (running) return;
        running = true;
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                captureAndDetect();
                backgroundHandler.postDelayed(this, 140);
            }
        };
        backgroundHandler.post(captureRunnable);
    }

    private void captureAndDetect() {
        if (imageReader == null || mediaProjection == null) return;
        Image image = imageReader.acquireLatestImage();
        if (image == null) return;
        Bitmap bitmap;
        try {
            bitmap = imageToBitmap(image);
        } finally {
            image.close();
        }
        if (bitmap == null) return;

        float[] markerPos = detector.detect(bitmap);
        if (markerPos != null) {
            overlayView.updateTarget(markerPos[0], markerPos[1]);
        } else {
            overlayView.updateTarget(-1, -1);
        }
        bitmap.recycle();
    }

    /**
     * Chuyển Image (YUV/RGBA từ ImageReader) sang Bitmap, có xử lý rowStride/pixelStride.
     * ImageReader với RGBA_8888 thường có padding cuối mỗi dòng (rowStride > width * pixelStride),
     * nếu copy thẳng buffer sẽ làm ảnh bị lệch/nghiêng dần theo chiều dọc.
     * Hàm này crop đúng phần dữ liệu thật của từng dòng trước khi ghép lại thành Bitmap đúng kích thước.
     */
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) return null;

        Image.Plane plane = planes[0];
        java.nio.ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        // Tạo bitmap rộng hơn để chứa cả padding, sau đó crop lại đúng kích thước thật
        int bitmapWidth = screenWidth + rowPadding / pixelStride;
        Bitmap fullBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        fullBitmap.copyPixelsFromBuffer(buffer);

        if (rowPadding == 0) {
            return fullBitmap;
        }

        // Crop bỏ phần padding thừa bên phải mỗi dòng
        Bitmap cropped = Bitmap.createBitmap(fullBitmap, 0, 0, screenWidth, screenHeight);
        fullBitmap.recycle();
        return cropped;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacks(captureRunnable);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (overlayView != null) {
            overlayView.hide();
            overlayView = null;
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
        isSetupDone = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Foreground Service for MediaProjection");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture Active")
                .setContentText("Tracking marker on screen")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
