package com.survival.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class FloatingOverlayView implements View.OnTouchListener {
    private Context context;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private LinearLayout container;
    private Button floatingButton;
    private boolean isAutoTrack = false;
    private float targetX = -1, targetY = -1;
    private float currentX = 150, currentY = 300;
    private float dx, dy;
    private boolean isDragging = false;
    private static final float SMOOTH_FACTOR = 0.35f;
    private int lostCounter = 0;
    private static final int LOST_THRESHOLD = 3;

    public FloatingOverlayView(Context context, WindowManager wm) {
        this.context = context;
        this.windowManager = wm;
        createOverlay();
    }

    private void createOverlay() {
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.TRANSPARENT);

        floatingButton = new Button(context);
        floatingButton.setText("○");
        floatingButton.setTextSize(28f);
        floatingButton.setBackgroundColor(Color.argb(220, 0, 120, 255));
        floatingButton.setTextColor(Color.WHITE);
        floatingButton.setPadding(25, 25, 25, 25);
        floatingButton.setOnTouchListener(this);
        floatingButton.setOnClickListener(v -> {
            isAutoTrack = !isAutoTrack;
            floatingButton.setText(isAutoTrack ? "●" : "○");
            Toast.makeText(context, isAutoTrack ? "Tracking ON" : "Tracking OFF", Toast.LENGTH_SHORT).show();
            if (!isAutoTrack) {
                targetX = -1;
                targetY = -1;
                lostCounter = 0;
            }
        });

        container.addView(floatingButton);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = (int) currentX;
        layoutParams.y = (int) currentY;
    }

    public void show() {
        if (container.getParent() == null) {
            windowManager.addView(container, layoutParams);
        }
    }

    public void hide() {
        if (container.getParent() != null) {
            windowManager.removeView(container);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!isAutoTrack) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dx = event.getRawX() - layoutParams.x;
                    dy = event.getRawY() - layoutParams.y;
                    isDragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        layoutParams.x = (int) (event.getRawX() - dx);
                        layoutParams.y = (int) (event.getRawY() - dy);
                        windowManager.updateViewLayout(container, layoutParams);
                        currentX = layoutParams.x;
                        currentY = layoutParams.y;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    break;
            }
            return true;
        }
        return false;
    }

    public void updateTarget(float newX, float newY) {
        if (!isAutoTrack) return;

        if (newX < 0 || newY < 0) {
            lostCounter++;
            if (lostCounter >= LOST_THRESHOLD) {
                lostCounter = LOST_THRESHOLD;
            }
            return;
        }

        lostCounter = 0;
        targetX = newX;
        targetY = newY;

        float deltaX = targetX - currentX;
        float deltaY = targetY - currentY;
        currentX += deltaX * SMOOTH_FACTOR;
        currentY += deltaY * SMOOTH_FACTOR;

        layoutParams.x = (int) currentX;
        layoutParams.y = (int) currentY;
        windowManager.updateViewLayout(container, layoutParams);
    }
}
