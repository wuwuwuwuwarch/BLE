package com.ckos.ble.ui.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 罗盘指针View
 * 红色指针始终指向目标信标方向
 */
public class CompassView extends View {
    private Paint paint;
    private float pointerAngle = 0; // 指针旋转角度（相对屏幕顶部）
    private String targetName = "Target";
    private float distance = 0;
    private boolean isCalibrated = false;

    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置罗盘指向
     * @param phoneAzimuth 手机当前朝向（正北为0，顺时针）
     * @param targetAzimuth 目标信标相对于正北的角度
     * @param distance 距离（米）
     * @param name 目标名称
     * @param calibrated 是否已完成坐标校准
     */
    public void setTarget(float phoneAzimuth, float targetAzimuth, float distance,
                          String name, boolean calibrated) {
        // 计算指针旋转角度：目标角度 - 手机朝向 = 相对角度
        this.pointerAngle = (targetAzimuth - phoneAzimuth + 360) % 360;
        this.distance = distance;
        this.targetName = name;
        this.isCalibrated = calibrated;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 2 - 40;

        // 绘制外圆
        paint.setColor(Color.parseColor("#333333"));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 绘制刻度
        paint.setStrokeWidth(3);
        for (int i = 0; i < 360; i += 30) {
            float angle = (float) Math.toRadians(i);
            float startX = centerX + (radius - 15) * (float) Math.sin(angle);
            float startY = centerY - (radius - 15) * (float) Math.cos(angle);
            float stopX = centerX + radius * (float) Math.sin(angle);
            float stopY = centerY - radius * (float) Math.cos(angle);
            canvas.drawLine(startX, startY, stopX, stopY, paint);
        }

        // 绘制方向文字
        paint.setTextSize(35);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        canvas.drawText("N", centerX - 15, centerY - radius - 20, paint);
        canvas.drawText("S", centerX - 15, centerY + radius + 45, paint);
        canvas.drawText("W", centerX - radius - 50, centerY + 10, paint);
        canvas.drawText("E", centerX + radius + 20, centerY + 10, paint);

        if (!isCalibrated) {
            // 未校准状态：灰色指针
            paint.setColor(Color.GRAY);
            canvas.drawCircle(centerX, centerY, 15, paint);

            paint.setTextSize(40);
            paint.setColor(Color.RED);
            String msg = "校准中...";
            canvas.drawText(msg, centerX - paint.measureText(msg)/2, centerY + radius + 80, paint);
            return;
        }

        // 绘制指针（三角形）
        canvas.save();
        canvas.rotate(pointerAngle, centerX, centerY);

        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(centerX, centerY - radius + 50); // 尖端
        path.lineTo(centerX - 25, centerY + 30);      // 左下
        path.lineTo(centerX + 25, centerY + 30);     // 右下
        path.close();

        paint.setColor(Color.parseColor("#FF4444")); // 红色指针
        canvas.drawPath(path, paint);

        canvas.restore();

        // 绘制中心点
        paint.setColor(Color.BLUE);
        canvas.drawCircle(centerX, centerY, 12, paint);

        // 绘制信息文字
        paint.setColor(Color.BLACK);
        paint.setTextSize(45);
        String info = String.format("%s  %.1fm  %.0f°", targetName, distance, pointerAngle);
        canvas.drawText(info, centerX - paint.measureText(info)/2, height - 60, paint);

        // 接近提示
        if (distance < 1.5) {
            paint.setColor(Color.GREEN);
            paint.setTextSize(50);
            String near = "已接近目标！";
            canvas.drawText(near, centerX - paint.measureText(near)/2, 60, paint);
        }
    }
}