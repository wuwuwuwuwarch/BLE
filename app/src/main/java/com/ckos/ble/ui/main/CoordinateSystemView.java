package com.ckos.ble.ui.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class CoordinateSystemView extends View {
    private float minScale = 40f;
    private float maxScale = 120f;
    private float scale = 100f;

    private float offsetX = 0f, offsetY = 0f;
    private PointF lastTouch = new PointF();
    private int touchCount = 0;
    private float lastSpacing = 0f;

    private List<BeaconPoint> beacons = new ArrayList<>();
    private List<SamplePoint> samplePoints = new ArrayList<>();
    private PhonePoint phonePoint = new PhonePoint(0, 0, 0);
    private int targetBeaconIndex = -1;

    private Paint gridPaint, axisPaint, beaconPaint, samplePaint, phonePaint, linePaint, textPaint;
    private Path phoneArrowPath;

    public static class BeaconPoint {
        public float x, y;
        public String name;
        public int color;
        public BeaconPoint(float x, float y, String name, int color) {
            this.x = x;
            this.y = y;
            this.name = name;
            this.color = color;
        }
    }

    public static class SamplePoint {
        public float x, y;
        public SamplePoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class PhonePoint {
        public float x, y;
        public float azimuth;
        public PhonePoint(float x, float y, float azimuth) {
            this.x = x;
            this.y = y;
            this.azimuth = azimuth;
        }
    }

    public CoordinateSystemView(Context context) {
        super(context);
        init();
    }

    public CoordinateSystemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(1f);

        axisPaint = new Paint();
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(2f);
        axisPaint.setTextSize(24f);

        beaconPaint = new Paint();
        beaconPaint.setStyle(Paint.Style.FILL);
        beaconPaint.setTextSize(28f);
        beaconPaint.setAntiAlias(true);

        samplePaint = new Paint();
        samplePaint.setColor(Color.GRAY);
        samplePaint.setStyle(Paint.Style.FILL);
        samplePaint.setAntiAlias(true);

        phonePaint = new Paint();
        phonePaint.setColor(Color.RED);
        phonePaint.setStyle(Paint.Style.FILL);
        phonePaint.setAntiAlias(true);

        linePaint = new Paint();
        linePaint.setColor(Color.RED);
        linePaint.setStrokeWidth(2f);
        linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));

        textPaint = new Paint();
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(20f);
        textPaint.setAntiAlias(true);

        phoneArrowPath = new Path();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        offsetX = w / 2f;
        offsetY = h / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try {
            drawGrid(canvas);
            drawAxes(canvas);
            drawDirectionLabels(canvas);
            drawSamplePoints(canvas);
            drawBeacons(canvas);
            drawPhoneAndLine(canvas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawGrid(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float step = scale;

        float cx = offsetX;
        float cy = offsetY;

        for (float x = cx; x < w; x += step) canvas.drawLine(x, 0, x, h, gridPaint);
        for (float x = cx - step; x > 0; x -= step) canvas.drawLine(x, 0, x, h, gridPaint);
        for (float y = cy; y < h; y += step) canvas.drawLine(0, y, w, y, gridPaint);
        for (float y = cy - step; y > 0; y -= step) canvas.drawLine(0, y, w, y, gridPaint);
    }

    private void drawAxes(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawLine(0, offsetY, w, offsetY, axisPaint);
        canvas.drawLine(offsetX, 0, offsetX, h, axisPaint);

        float step = scale;
        for (int i = -20; i <= 20; i++) {
            if (i == 0) continue;
            float x = offsetX + i * step;
            float y = offsetY - i * step;
            canvas.drawLine(x, offsetY - 5, x, offsetY + 5, axisPaint);
            canvas.drawText(String.valueOf(i), x + 5, offsetY + 25, axisPaint);
            canvas.drawLine(offsetX - 5, y, offsetX + 5, y, axisPaint);
            canvas.drawText(String.valueOf(i), offsetX + 10, y - 5, axisPaint);
        }
        canvas.drawText("O", offsetX - 25, offsetY + 25, axisPaint);
    }

    private void drawDirectionLabels(Canvas canvas) {
        // 绘制方向指示
        canvas.drawText("↑ Y+ (前方)", offsetX + 10, offsetY - 10, textPaint);
        canvas.drawText("→ X+ (右方)", offsetX + 10, offsetY + 40, textPaint);
    }

    private void drawSamplePoints(Canvas canvas) {
        for (SamplePoint p : samplePoints) {
            float sx = offsetX + p.x * scale;
            float sy = offsetY - p.y * scale;
            canvas.drawCircle(sx, sy, 8, samplePaint);
        }
    }

    private void drawBeacons(Canvas canvas) {
        for (int i = 0; i < beacons.size(); i++) {
            BeaconPoint b = beacons.get(i);
            float sx = offsetX + b.x * scale;
            float sy = offsetY - b.y * scale;
            beaconPaint.setColor(b.color);
            canvas.drawCircle(sx, sy, 14, beaconPaint);
            canvas.drawText(b.name, sx + 18, sy + 5, beaconPaint);
            if (i == targetBeaconIndex) {
                beaconPaint.setStyle(Paint.Style.STROKE);
                beaconPaint.setStrokeWidth(4);
                beaconPaint.setColor(Color.BLACK);
                canvas.drawCircle(sx, sy, 22, beaconPaint);
                beaconPaint.setStyle(Paint.Style.FILL);
                beaconPaint.setColor(b.color);
            }
        }
    }

    private void drawPhoneAndLine(Canvas canvas) {
        if (phonePoint == null) return;
        float sx = offsetX + phonePoint.x * scale;
        float sy = offsetY - phonePoint.y * scale;

        if (targetBeaconIndex >= 0 && targetBeaconIndex < beacons.size()) {
            BeaconPoint t = beacons.get(targetBeaconIndex);
            float tx = offsetX + t.x * scale;
            float ty = offsetY - t.y * scale;
            canvas.drawLine(sx, sy, tx, ty, linePaint);
        }

        canvas.save();
        canvas.translate(sx, sy);
        canvas.rotate(phonePoint.azimuth);
        phoneArrowPath.reset();
        phoneArrowPath.moveTo(0, -25);
        phoneArrowPath.lineTo(-15, 18);
        phoneArrowPath.lineTo(15, 18);
        phoneArrowPath.close();
        canvas.drawPath(phoneArrowPath, phonePaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                touchCount = 1;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    touchCount = 2;
                    lastSpacing = spacing(event);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (touchCount == 1) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    offsetX += dx;
                    offsetY += dy;
                    offsetX = Math.max(-getWidth() * 3, Math.min(getWidth() * 3, offsetX));
                    offsetY = Math.max(-getHeight() * 3, Math.min(getHeight() * 3, offsetY));
                    lastTouch.set(event.getX(), event.getY());
                    invalidate();
                } else if (touchCount == 2 && event.getPointerCount() >= 2) {
                    float newSpace = spacing(event);
                    if (lastSpacing > 0) {
                        float factor = newSpace / lastSpacing;
                        float newScale = scale * factor;
                        if (newScale >= minScale && newScale <= maxScale) {
                            scale = newScale;
                        }
                    }
                    lastSpacing = newSpace;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                touchCount = 0;
                lastSpacing = 0;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private float spacing(MotionEvent event) {
        // 修复闪退：检查指针数量
        if (event.getPointerCount() < 2) return 0;
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    public void resetView() {
        offsetX = getWidth() / 2f;
        offsetY = getHeight() / 2f;
        scale = 100f;
        invalidate();
    }

    public void updateBeacons(List<BeaconPoint> list) {
        beacons = list != null ? list : new ArrayList<>();
        postInvalidate();
    }

    public void updateSamples(List<SamplePoint> list) {
        samplePoints = list != null ? list : new ArrayList<>();
        postInvalidate();
    }

    public void updatePhone(PhonePoint p) {
        phonePoint = p;
        postInvalidate();
    }

    public void setTargetBeaconIndex(int i) {
        targetBeaconIndex = i;
        postInvalidate();
    }
}