package com.ckos.ble;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * 航向获取：加速度计+磁力计融合陀螺仪抗干扰
 * 磁干扰时自动切换陀螺仪，2秒后恢复磁力计
 */
public class SensorFusionManager implements SensorEventListener {
    private static final String TAG = "SensorFusion";
    private SensorManager sensorManager;

    private Sensor accelerometer;
    private Sensor magnetometer;
    private Sensor gyroscope;

    private float[] accelData = new float[3];
    private float[] magnetData = new float[3];

    private float currentAzimuth = 0;
    private float gyroAzimuth = 0;
    private long lastTimestamp = 0;

    private boolean useGyro = false;
    private long interferenceStartTime = 0;
    private static final long GYRO_TIMEOUT = 2000; // 最大信任陀螺仪2秒

    public interface OnHeadingChangedListener {
        void onHeadingChanged(float azimuth);
    }
    private OnHeadingChangedListener listener;

    public SensorFusionManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        lastTimestamp = System.currentTimeMillis();
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelData, 0, 3);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magnetData, 0, 3);
                // 检测磁干扰（磁场强度异常）
                float magnitude = (float) Math.sqrt(
                        magnetData[0]*magnetData[0] +
                                magnetData[1]*magnetData[1] +
                                magnetData[2]*magnetData[2]);

                if (magnitude < 20 || magnitude > 70) { // 地球磁场约25-65微特斯拉
                    if (!useGyro) {
                        useGyro = true;
                        gyroAzimuth = currentAzimuth;
                        interferenceStartTime = currentTime;
                        Log.w(TAG, "磁干扰 detected, switch to gyro");
                    }
                } else {
                    if (useGyro && currentTime - interferenceStartTime > GYRO_TIMEOUT) {
                        useGyro = false; // 恢复磁力计
                        Log.d(TAG, "Restore magnetic compass");
                    }
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                if (useGyro && lastTimestamp != 0) {
                    float dt = (currentTime - lastTimestamp) / 1000.0f;
                    // 陀螺仪Z轴积分（绕Z轴旋转）
                    gyroAzimuth += Math.toDegrees(event.values[2] * dt);
                    gyroAzimuth = (gyroAzimuth + 360) % 360;
                }
                break;
        }

        // 计算航向
        if (!useGyro) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, accelData, magnetData)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                // orientation[0] 是弧度，-π到π，正北为0，顺时针为正
                currentAzimuth = (float) Math.toDegrees(orientation[0]);
                currentAzimuth = (currentAzimuth + 360) % 360;
            }
        } else {
            currentAzimuth = gyroAzimuth;
        }

        lastTimestamp = currentTime;

        if (listener != null) {
            listener.onHeadingChanged(currentAzimuth);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void setOnHeadingChangedListener(OnHeadingChangedListener listener) {
        this.listener = listener;
    }

    public float getCurrentAzimuth() {
        return currentAzimuth;
    }
}