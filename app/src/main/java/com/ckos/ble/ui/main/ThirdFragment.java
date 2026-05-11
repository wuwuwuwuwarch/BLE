package com.ckos.ble.ui.main;

import android.annotation.SuppressLint;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ckos.ble.MainActivity;
import com.ckos.ble.R;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@SuppressLint("MissingPermission")
public class ThirdFragment extends Fragment implements SensorEventListener {
    private static final String TAG = "ThirdFragment";

    // 默认校准参数（可被界面保存值覆盖）
    private int[] oneMeterRssi = new int[]{-55, -76, -60};
    private float[] n_values = new float[]{2.45f, 1.50f, 2.00f};

    private Map<String, BeaconData> beacons = new HashMap<>();
    private String[] beaconNames = new String[3];
    private int selectedBeaconIndex = 0;

    private java.util.Set<String> scannedDeviceNames = new java.util.HashSet<>();
    private List<String> deviceListForSpinner = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter1, spinnerAdapter2, spinnerAdapter3;
    private ArrayAdapter<String> targetAdapter;
    private boolean isScanningForDevices = false;

    private LinkedList<SamplePoint> samplePoints = new LinkedList<>();
    private boolean isCollecting = false;
    private float currentX = 0, currentY = 0;
    private float currentAzimuth = 0;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private boolean isRssiScanning = false;

    private CompassView compassView;
    private TextView tvStatus, tvPosition, tvCalibrating, tvScanStatus, tvPointCount;
    private Spinner spinnerBeacon1, spinnerBeacon2, spinnerBeacon3, spinnerTarget;
    private EditText etStepDistance;
    private Button btnScanDevices, btnMarkPoint, btnStartNav, btnReset;

    private EditText etOneMeterRssi1, etOneMeterRssi2, etOneMeterRssi3;
    private EditText etNValue1, etNValue2, etNValue3;
    private Button btnSaveRssi;
    private TextView tvRealtimeRssi, tvSamplePointsInfo;
    private Button btnScanDevices2;

    private boolean isNavigating = false;
    private boolean isBeaconPositionsCalculated = false;
    private Runnable navigationUpdateRunnable;
    private static final long NAV_UPDATE_INTERVAL = 500;

    private static final int MAX_SAMPLE_POINTS = 15;
    private static final float MAX_ALLOWED_POSITION_CHANGE = 3.0f;

    private float lastValidX = 0, lastValidY = 0;
    private static final float MAX_SINGLE_MOVE = 1.0f;
    private static final float SMOOTH_FACTOR = 0.4f;
    private static final float STILL_SMOOTH = 0.1f;       // 静止强平滑
    private static final float STILL_THRESHOLD = 0.3f;   // 判定静止的移动距离阈值
    private boolean navigationDataReady = false;

    private CoordinateSystemView coordinateSystemView;
    private Button btnResetView;
    private final int[] beaconColors = {Color.RED, Color.GREEN, Color.BLUE};

    private boolean isOriginRecorded = false;
    private float referenceAzimuth = 0;

    // 传感器
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;
    private float[] orientation = new float[3];
    private float[] rotationMatrix = new float[9];

    private long lastUiUpdateTime = 0;
    private static final long UI_UPDATE_INTERVAL = 400;

    private View rootView;
    private ScrollView scrollView;

    // ==================== 卡尔曼滤波器 ====================
    public static class KalmanFilter {
        private final double Q = 0.01;
        private final double R = 2.0;
        private double X;
        private double P = 1.0;
        private boolean initialized = false;

        public KalmanFilter(double initialValue) {
            this.X = initialValue;
        }

        public double filter(double measurement) {
            if (!initialized) {
                X = measurement;
                initialized = true;
                return X;
            }
            P = P + Q;
            double K = P / (P + R);
            X = X + K * (measurement - X);
            P = (1 - K) * P;
            return X;
        }

        public void reset(double value) {
            X = value;
            P = 1.0;
            initialized = false;
        }

        public double getCurrent() { return X; }
        public boolean isInitialized() { return initialized; }
    }

    // ==================== BeaconData ====================
    private class BeaconData {
        String name;
        int oneMeterRssi;
        float n;
        private KalmanFilter rssiFilter = new KalmanFilter(-50.0);
        private int sampleCount = 0;

        Double calculatedX = null, calculatedY = null;
        double lastDistance = 1.0;
        long lastUpdateTime = 0;

        BeaconData(String name, int oneMeterRssi, float n) {
            this.name = name;
            this.oneMeterRssi = oneMeterRssi;
            this.n = n;
        }

        void addRssi(int rawRssi) {
            rssiFilter.filter(rawRssi);
            sampleCount++;
        }

        double getFilteredRssi() {
            if (!rssiFilter.isInitialized()) return -100;
            return rssiFilter.getCurrent();
        }

        double getCurrentDistance() {
            double rssi = getFilteredRssi();
            if (rssi > 0) rssi = -rssi;
            if (rssi < -100) rssi = -100;

            double power = (Math.abs(rssi) - Math.abs(this.oneMeterRssi)) / (10.0 * this.n);
            double dist = Math.pow(10, power);

            if (dist < 0.1) dist = 0.1;
            if (dist > 20.0) dist = 20.0;

            long currentTime = System.currentTimeMillis();
            if (lastUpdateTime > 0) {
                double timeDiff = (currentTime - lastUpdateTime) / 1000.0;
                double maxChange = 3.0 * timeDiff;
                double change = dist - lastDistance;
                if (Math.abs(change) > maxChange) {
                    dist = lastDistance + (Math.signum(change) * maxChange);
                }
            }
            lastDistance = dist;
            lastUpdateTime = currentTime;

            return roundTwoDecimal(dist);
        }

        public int getDataCount() { return sampleCount; }

        public void clear() {
            rssiFilter.reset(-50.0);
            sampleCount = 0;
            lastUpdateTime = 0;
            lastDistance = 1.0;
        }

        public boolean isReady() { return sampleCount >= 5; }
        public boolean isFull() { return sampleCount >= 10; }
    }

    private static class SamplePoint {
        float x, y;
        float azimuth;
        Map<String, Double> distances = new HashMap<>();
        SamplePoint(float x, float y, float azimuth) {
            this.x = x;
            this.y = y;
            this.azimuth = azimuth;
        }
    }

    // ==================== 扫描回调 ====================
    private ScanCallback deviceScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!isAdded()) return;
            String deviceName = result.getDevice().getName();
            if (deviceName == null && result.getScanRecord() != null)
                deviceName = result.getScanRecord().getDeviceName();
            if (deviceName != null && !deviceName.isEmpty()) {
                String trimmedName = deviceName.trim();
                final int rssi = result.getRssi();
                if (scannedDeviceNames.add(trimmedName)) {
                    uiHandler.post(() -> {
                        deviceListForSpinner.add(trimmedName);
                        spinnerAdapter1.notifyDataSetChanged();
                        spinnerAdapter2.notifyDataSetChanged();
                        spinnerAdapter3.notifyDataSetChanged();
                        tvScanStatus.setText("已发现 " + scannedDeviceNames.size() + " 个设备");
                        checkAutoMatch(trimmedName);
                    });
                }
                for (int i = 0; i < 3; i++) {
                    if (trimmedName.equalsIgnoreCase(beaconNames[i])) {
                        final int beaconIndex = i;
                        uiHandler.post(() -> {
                            BeaconData beacon = beacons.get(beaconNames[beaconIndex]);
                            beacon.addRssi(rssi);
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastUiUpdateTime > UI_UPDATE_INTERVAL) {
                                lastUiUpdateTime = currentTime;
                                updateRealtimeRssiDisplay();
                                updateBeaconPositionsDisplay();
                            }
                        });
                        break;
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) onScanResult(0, result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "设备扫描失败: " + errorCode);
            uiHandler.post(() -> {
                isScanningForDevices = false;
                btnScanDevices.setText("🔍 扫描周围信标设备");
                if (btnScanDevices2 != null) btnScanDevices2.setText("🔍 扫描周围信标设备");
                tvScanStatus.setText("扫描失败");
            });
        }
    };

    private ScanCallback rssiScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!isAdded()) return;
            String deviceName = result.getDevice().getName();
            if (deviceName == null && result.getScanRecord() != null)
                deviceName = result.getScanRecord().getDeviceName();
            if (deviceName == null) return;
            String foundName = deviceName.trim();
            final int rssi = result.getRssi();

            for (int i = 0; i < 3; i++) {
                if (foundName.equalsIgnoreCase(beaconNames[i])) {
                    final int index = i;
                    uiHandler.post(() -> {
                        BeaconData beacon = beacons.get(beaconNames[index]);
                        beacon.addRssi(rssi);

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUiUpdateTime > UI_UPDATE_INTERVAL) {
                            lastUiUpdateTime = currentTime;
                            updateRealtimeRssiDisplay();
                            updateBeaconPositionsDisplay();
                        }

                        if (isCollecting && !samplePoints.isEmpty()) {
                            SamplePoint currentPoint = samplePoints.getLast();
                            if (!currentPoint.distances.containsKey(beaconNames[index]) && beacon.isReady()) {
                                double dist = beacon.getCurrentDistance();
                                currentPoint.distances.put(beaconNames[index], dist);
                                updateCalibratingStatus();
                                checkAndCalculateBeaconPositions();
                                safeUpdateSamplePointsInfo();
                            }
                        } else if (isNavigating && isBeaconPositionsCalculated) {
                            updatePhonePositionWithConstraints();
                        }
                        updateCompass();
                        updateCoordinateSystemPhone();
                    });
                    break;
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) onScanResult(0, result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "RSSI扫描失败: " + errorCode);
        }
    };

    // ==================== 生命周期 ====================
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = requireActivity().getSharedPreferences("share", Context.MODE_PRIVATE);
        beaconNames[0] = sp.getString("beacon_1_name", "Beacon_A");
        beaconNames[1] = sp.getString("beacon_2_name", "Beacon_B");
        beaconNames[2] = sp.getString("beacon_3_name", "Beacon_C");

        // 从SharedPreferences加载，若没有则使用代码中的默认值
        oneMeterRssi[0] = sp.getInt("one_meter_rssi_0", -55);
        oneMeterRssi[1] = sp.getInt("one_meter_rssi_1", -76);
        oneMeterRssi[2] = sp.getInt("one_meter_rssi_2", -60);
        n_values[0] = sp.getFloat("n_value_0", 2.45f);
        n_values[1] = sp.getFloat("n_value_1", 1.50f);
        n_values[2] = sp.getFloat("n_value_2", 2.00f);

        for (int i = 0; i < 3; i++)
            beacons.put(beaconNames[i], new BeaconData(beaconNames[i], oneMeterRssi[i], n_values[i]));

        if (savedInstanceState != null) {
            currentX = savedInstanceState.getFloat("currentX", 0);
            currentY = savedInstanceState.getFloat("currentY", 0);
            lastValidX = savedInstanceState.getFloat("lastValidX", currentX);
            lastValidY = savedInstanceState.getFloat("lastValidY", currentY);
            selectedBeaconIndex = savedInstanceState.getInt("selectedBeaconIndex", 0);
            isCollecting = savedInstanceState.getBoolean("isCollecting", false);
            isNavigating = savedInstanceState.getBoolean("isNavigating", false);
            isOriginRecorded = savedInstanceState.getBoolean("isOriginRecorded", false);
            referenceAzimuth = savedInstanceState.getFloat("referenceAzimuth", 0);
            for (int i = 0; i < 3; i++)
                oneMeterRssi[i] = savedInstanceState.getInt("one_meter_rssi_" + i, -60);
            for (int i = 0; i < 3; i++)
                n_values[i] = savedInstanceState.getFloat("n_value_" + i, 2.5f);
            for (int i = 0; i < 3; i++) {
                if (savedInstanceState.getBoolean("beacon_" + i + "_ready", false)) {
                    double x = savedInstanceState.getDouble("beacon_" + i + "_x");
                    double y = savedInstanceState.getDouble("beacon_" + i + "_y");
                    BeaconData b = beacons.get(beaconNames[i]);
                    if (b != null) { b.calculatedX = x; b.calculatedY = y; }
                }
            }
            if (isNavigating) isBeaconPositionsCalculated = true;
            int pointCount = savedInstanceState.getInt("samplePointsCount", 0);
            for (int i = 0; i < pointCount; i++) {
                float x = savedInstanceState.getFloat("sample_x_" + i);
                float y = savedInstanceState.getFloat("sample_y_" + i);
                float az = savedInstanceState.getFloat("sample_az_" + i);
                samplePoints.add(new SamplePoint(x, y, az));
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putFloat("currentX", currentX);
        outState.putFloat("currentY", currentY);
        outState.putFloat("lastValidX", lastValidX);
        outState.putFloat("lastValidY", lastValidY);
        outState.putInt("selectedBeaconIndex", selectedBeaconIndex);
        outState.putBoolean("isCollecting", isCollecting);
        outState.putBoolean("isNavigating", isNavigating);
        outState.putBoolean("isOriginRecorded", isOriginRecorded);
        outState.putFloat("referenceAzimuth", referenceAzimuth);
        for (int i = 0; i < 3; i++)
            outState.putInt("one_meter_rssi_" + i, oneMeterRssi[i]);
        for (int i = 0; i < 3; i++)
            outState.putFloat("n_value_" + i, n_values[i]);
        for (int i = 0; i < 3; i++) {
            String name = beaconNames[i];
            BeaconData b = beacons.get(name);
            if (b != null && b.calculatedX != null) {
                outState.putDouble("beacon_" + i + "_x", b.calculatedX);
                outState.putDouble("beacon_" + i + "_y", b.calculatedY);
                outState.putBoolean("beacon_" + i + "_ready", true);
            }
        }
        outState.putInt("samplePointsCount", samplePoints.size());
        int idx = 0;
        for (SamplePoint p : samplePoints) {
            outState.putFloat("sample_x_" + idx, p.x);
            outState.putFloat("sample_y_" + idx, p.y);
            outState.putFloat("sample_az_" + idx, p.azimuth);
            idx++;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_tab_3, container, false);
        initViews();
        if (isCollecting) {
            startRssiScan();
            btnMarkPoint.setText(isOriginRecorded ? "记录新位置" : "第二步：记录原点");
        }
        if (isNavigating) {
            startNavigationMode();
            btnMarkPoint.setEnabled(false);
            btnMarkPoint.setAlpha(0.5f);
            tvCalibrating.setText("导航模式：实时RSSI定位中");
        }
        safeUpdateSamplePointsInfo();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initSensors();
        checkIfReadyToStart();
        if (currentX != 0 || currentY != 0) {
            updateCompass();
            updateBeaconPositionsDisplay();
            updateCoordinateSystemBeacons();
            updateCoordinateSystemSamplePoints();
            updateCoordinateSystemPhone();
        }
        safeUpdateSamplePointsInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    private void initSensors() {
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            lastAccelerometerSet = true;
        } else if (event.sensor == magnetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            lastMagnetometerSet = true;
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float azimuthInDegrees = (float) Math.toDegrees(orientation[0]);
            azimuthInDegrees = (azimuthInDegrees + 360) % 360;
            currentAzimuth = azimuthInDegrees;

            if (isAdded()) {
                uiHandler.post(() -> updateCompass());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void initViews() {
        scrollView = rootView.findViewById(R.id.scrollView);
        compassView = rootView.findViewById(R.id.compass_view);
        tvStatus = rootView.findViewById(R.id.tv_status);
        tvPosition = rootView.findViewById(R.id.tv_position);
        tvCalibrating = rootView.findViewById(R.id.tv_calibrating);
        tvPointCount = rootView.findViewById(R.id.tv_point_count);
        spinnerBeacon1 = rootView.findViewById(R.id.spinner_beacon_1);
        spinnerBeacon2 = rootView.findViewById(R.id.spinner_beacon_2);
        spinnerBeacon3 = rootView.findViewById(R.id.spinner_beacon_3);
        spinnerTarget = rootView.findViewById(R.id.spinner_target);
        btnScanDevices = rootView.findViewById(R.id.btn_scan_devices);
        tvScanStatus = rootView.findViewById(R.id.tv_scan_status);
        etStepDistance = rootView.findViewById(R.id.et_step_distance);
        btnMarkPoint = rootView.findViewById(R.id.btn_mark_point);
        btnStartNav = rootView.findViewById(R.id.btn_start_nav);
        btnReset = rootView.findViewById(R.id.btn_reset);
        etOneMeterRssi1 = rootView.findViewById(R.id.et_one_meter_rssi_1);
        etOneMeterRssi2 = rootView.findViewById(R.id.et_one_meter_rssi_2);
        etOneMeterRssi3 = rootView.findViewById(R.id.et_one_meter_rssi_3);
        etNValue1 = rootView.findViewById(R.id.et_n_value_1);
        etNValue2 = rootView.findViewById(R.id.et_n_value_2);
        etNValue3 = rootView.findViewById(R.id.et_n_value_3);
        btnSaveRssi = rootView.findViewById(R.id.btn_save_rssi);
        tvRealtimeRssi = rootView.findViewById(R.id.tv_realtime_rssi);
        btnScanDevices2 = rootView.findViewById(R.id.btn_scan_devices_2);
        coordinateSystemView = rootView.findViewById(R.id.coordinate_system_view);
        btnResetView = rootView.findViewById(R.id.btn_reset_view);
        tvSamplePointsInfo = rootView.findViewById(R.id.tv_sample_points_info);

        btnResetView.setOnClickListener(v -> coordinateSystemView.resetView());
        etOneMeterRssi1.setText(String.valueOf(oneMeterRssi[0]));
        etOneMeterRssi2.setText(String.valueOf(oneMeterRssi[1]));
        etOneMeterRssi3.setText(String.valueOf(oneMeterRssi[2]));
        etNValue1.setText(String.valueOf(n_values[0]));
        etNValue2.setText(String.valueOf(n_values[1]));
        etNValue3.setText(String.valueOf(n_values[2]));

        btnSaveRssi.setOnClickListener(v -> saveAllParameters());
        btnScanDevices2.setOnClickListener(v -> toggleDeviceScan());
        btnScanDevices.setOnClickListener(v -> toggleDeviceScan());

        deviceListForSpinner.add("请选择设备...");
        deviceListForSpinner.add(beaconNames[0]);
        deviceListForSpinner.add(beaconNames[1]);
        deviceListForSpinner.add(beaconNames[2]);

        spinnerAdapter1 = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, deviceListForSpinner);
        spinnerAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBeacon1.setAdapter(spinnerAdapter1);
        spinnerBeacon1.setSelection(1);

        spinnerAdapter2 = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, deviceListForSpinner);
        spinnerAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBeacon2.setAdapter(spinnerAdapter2);
        spinnerBeacon2.setSelection(2);

        spinnerAdapter3 = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, deviceListForSpinner);
        spinnerAdapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBeacon3.setAdapter(spinnerAdapter3);
        spinnerBeacon3.setSelection(3);

        targetAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, beaconNames);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTarget.setAdapter(targetAdapter);
        spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedBeaconIndex = position;
                updateCompass();
                updateBeaconPositionsDisplay();
                coordinateSystemView.setTargetBeaconIndex(selectedBeaconIndex);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerBeacon1.setOnItemSelectedListener(new BeaconSelectedListener(0));
        spinnerBeacon2.setOnItemSelectedListener(new BeaconSelectedListener(1));
        spinnerBeacon3.setOnItemSelectedListener(new BeaconSelectedListener(2));

        btnMarkPoint.setOnClickListener(v -> {
            if (!isOriginRecorded) {
                resetForCollection();
                isCollecting = true;
                btnMarkPoint.setText("第二步：记录原点");
                startRssiScan();
                tvCalibrating.setText("等待RSSI数据稳定...");
            } else {
                if (samplePoints.size() >= MAX_SAMPLE_POINTS) {
                    Toast.makeText(requireContext(), "已达最大采样点数", Toast.LENGTH_SHORT).show();
                }
                addNewSamplePoint();
            }
        });

        btnStartNav.setOnClickListener(v -> {
            if (!isOriginRecorded) recordOrigin();
            else startNavigation();
        });

        btnReset.setOnClickListener(v -> resetAll());
    }

    private void saveAllParameters() {
        try {
            oneMeterRssi[0] = Integer.parseInt(etOneMeterRssi1.getText().toString().trim());
            oneMeterRssi[1] = Integer.parseInt(etOneMeterRssi2.getText().toString().trim());
            oneMeterRssi[2] = Integer.parseInt(etOneMeterRssi3.getText().toString().trim());
            for (int i = 0; i < 3; i++) {
                if (oneMeterRssi[i] > 0) oneMeterRssi[i] = -oneMeterRssi[i];
            }

            n_values[0] = Float.parseFloat(etNValue1.getText().toString().trim());
            n_values[1] = Float.parseFloat(etNValue2.getText().toString().trim());
            n_values[2] = Float.parseFloat(etNValue3.getText().toString().trim());

            SharedPreferences sp = requireActivity().getSharedPreferences("share", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            for (int i = 0; i < 3; i++) {
                editor.putInt("one_meter_rssi_" + i, oneMeterRssi[i]);
                editor.putFloat("n_value_" + i, n_values[i]);
            }
            editor.apply();

            for (int i = 0; i < 3; i++) {
                BeaconData bd = beacons.get(beaconNames[i]);
                if (bd != null) {
                    bd.oneMeterRssi = oneMeterRssi[i];
                    bd.n = n_values[i];
                }
            }
            Toast.makeText(requireContext(), "所有校准参数已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "输入格式错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetForCollection() {
        samplePoints.clear();
        coordinateSystemView.updateSamples(new ArrayList<>());
        isCollecting = true;
        isOriginRecorded = false;
        referenceAzimuth = 0;
        currentX = 0;
        currentY = 0;
        lastValidX = 0;
        lastValidY = 0;
        for (BeaconData b : beacons.values()) {
            b.calculatedX = null;
            b.calculatedY = null;
            b.clear();
        }
        isBeaconPositionsCalculated = false;
        navigationDataReady = false;
        updatePointCountDisplay();
        tvCalibrating.setText("准备采集，请面对前方点击记录原点");
        tvPosition.setText("信标位置：未计算");
        btnStartNav.setText("记录原点");
        btnMarkPoint.setText("第二步：记录原点");
        updateCoordinateSystemBeacons();
        updateCoordinateSystemSamplePoints();
        updateCoordinateSystemPhone();
        safeUpdateSamplePointsInfo();
    }

    private void recordOrigin() {
        referenceAzimuth = currentAzimuth;
        samplePoints.add(new SamplePoint(0, 0, 0));
        isOriginRecorded = true;
        btnStartNav.setText("开始导航");
        btnMarkPoint.setText("记录新位置");
        tvCalibrating.setText("原点已记录，请移动后点击【记录新位置】");
        updatePointCountDisplay();
        updateCoordinateSystemSamplePoints();
        updateCoordinateSystemPhone();
        safeUpdateSamplePointsInfo();
    }

    private float normalizeAngle(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private void addNewSamplePoint() {
        String stepStr = etStepDistance.getText().toString();
        if (stepStr.isEmpty()) {
            Toast.makeText(requireContext(), "请输入距离", Toast.LENGTH_SHORT).show();
            return;
        }
        float step = Float.parseFloat(stepStr);
        SamplePoint last = samplePoints.getLast();

        float relAz = normalizeAngle(currentAzimuth - referenceAzimuth);
        float rad = (float) Math.toRadians(relAz);
        float newX = last.x + step * (float) Math.sin(rad);
        float newY = last.y + step * (float) Math.cos(rad);

        if (samplePoints.size() >= MAX_SAMPLE_POINTS) samplePoints.removeFirst();
        samplePoints.add(new SamplePoint(newX, newY, relAz));

        currentX = newX;
        currentY = newY;
        lastValidX = newX;
        lastValidY = newY;

        tvCalibrating.setText(String.format("已添加点%d: (%.1f, %.1f)", samplePoints.size(), newX, newY));
        updatePointCountDisplay();
        updateCoordinateSystemSamplePoints();
        updateCoordinateSystemPhone();
        safeUpdateSamplePointsInfo();
        Toast.makeText(requireContext(), "正在采集该点信标距离...", Toast.LENGTH_SHORT).show();
    }

    private void startNavigation() {
        BeaconData target = beacons.get(beaconNames[selectedBeaconIndex]);
        if (target == null || target.calculatedX == null) {
            Toast.makeText(requireContext(), "请先完成信标坐标解算", Toast.LENGTH_SHORT).show();
            return;
        }
        isCollecting = false;
        isNavigating = true;
        isBeaconPositionsCalculated = true;
        lastValidX = currentX;
        lastValidY = currentY;
        navigationDataReady = false;
        for (BeaconData b : beacons.values()) b.clear();
        stopRssiScan();
        startNavigationMode();
        tvCalibrating.setText("导航模式 → " + target.name);
        tvCalibrating.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        btnMarkPoint.setEnabled(false);
        btnMarkPoint.setAlpha(0.5f);
        btnStartNav.setText("导航中");
        btnStartNav.setEnabled(false);
    }

    // ==================== 迭代重加权最小二乘解算信标坐标 ====================
    private void checkAndCalculateBeaconPositions() {
        boolean anyChanged = false;
        for (String name : beaconNames) {
            List<SamplePoint> validPoints = new ArrayList<>();
            for (SamplePoint p : samplePoints) {
                if (p.distances.containsKey(name) && p.distances.get(name) > 0)
                    validPoints.add(p);
            }
            if (validPoints.size() < 3) continue;

            int m = validPoints.size() - 1;
            double[] w = new double[m];
            double[][] A = new double[m][2];
            double[] b = new double[m];

            SamplePoint p0 = validPoints.get(0);
            double d0 = p0.distances.get(name);

            for (int idx = 1; idx < validPoints.size(); idx++) {
                SamplePoint pi = validPoints.get(idx);
                double di = pi.distances.get(name);
                int i = idx - 1;
                A[i][0] = 2 * (p0.x - pi.x);
                A[i][1] = 2 * (p0.y - pi.y);
                b[i] = di*di - d0*d0 + p0.x*p0.x - pi.x*pi.x + p0.y*p0.y - pi.y*pi.y;
                w[i] = 1.0;
            }

            double[] res = null;
            for (int iter = 0; iter < 3; iter++) {
                res = solveWeightedLS(A, b, w);
                if (res == null) break;

                for (int idx = 1; idx < validPoints.size(); idx++) {
                    SamplePoint pi = validPoints.get(idx);
                    double obs = pi.distances.get(name);
                    double pred = Math.hypot(pi.x - res[0], pi.y - res[1]);
                    double error = Math.abs(obs - pred);
                    if (error < 0.5) {
                        w[idx - 1] = 1.0;
                    } else if (error < 2.0) {
                        w[idx - 1] = 1.0 / error;
                    } else {
                        w[idx - 1] = 0.1 / error;
                    }
                }
            }

            if (res != null) {
                BeaconData beacon = beacons.get(name);
                if (beacon.calculatedX == null) {
                    beacon.calculatedX = res[0];
                    beacon.calculatedY = res[1];
                    anyChanged = true;
                } else {
                    double change = Math.hypot(res[0] - beacon.calculatedX, res[1] - beacon.calculatedY);
                    if (change < MAX_ALLOWED_POSITION_CHANGE) {
                        beacon.calculatedX = beacon.calculatedX * 0.7 + res[0] * 0.3;
                        beacon.calculatedY = beacon.calculatedY * 0.7 + res[1] * 0.3;
                        anyChanged = true;
                    }
                }
            }
        }

        int calculatedCount = 0;
        for (String n : beaconNames) if (beacons.get(n).calculatedX != null) calculatedCount++;
        isBeaconPositionsCalculated = (calculatedCount == 3);
        if (calculatedCount > 0) {
            tvCalibrating.setText(calculatedCount == 3 ? "✓ 所有信标坐标已解算" : String.format("已解算%d/3个信标", calculatedCount));
            tvCalibrating.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            if (calculatedCount == 3) btnStartNav.setEnabled(true);
            updateCompass();
            updateBeaconPositionsDisplay();
            updateCoordinateSystemBeacons();
        }
    }

    private double[] solveWeightedLS(double[][] A, double[] b, double[] w) {
        int m = A.length;
        double s00 = 0, s01 = 0, s11 = 0, r0 = 0, r1 = 0;
        for (int i = 0; i < m; i++) {
            double wi = w[i];
            s00 += wi * A[i][0] * A[i][0];
            s01 += wi * A[i][0] * A[i][1];
            s11 += wi * A[i][1] * A[i][1];
            r0  += wi * A[i][0] * b[i];
            r1  += wi * A[i][1] * b[i];
        }
        double det = s00 * s11 - s01 * s01;
        if (Math.abs(det) < 1e-10) return null;
        double x = (r0 * s11 - s01 * r1) / det;
        double y = (s00 * r1 - r0 * s01) / det;
        return new double[]{x, y};
    }

    private void updatePhonePositionWithConstraints() {
        BeaconData b1 = beacons.get(beaconNames[0]);
        BeaconData b2 = beacons.get(beaconNames[1]);
        BeaconData b3 = beacons.get(beaconNames[2]);
        if (b1 == null || b2 == null || b3 == null ||
                b1.calculatedX == null || b2.calculatedX == null || b3.calculatedX == null)
            return;

        if (!navigationDataReady) {
            if (b1.isReady() && b2.isReady() && b3.isReady()) {
                navigationDataReady = true;
                double[] pos = calculateTrilateration(
                        b1.calculatedX, b1.calculatedY, b1.getCurrentDistance(),
                        b2.calculatedX, b2.calculatedY, b2.getCurrentDistance(),
                        b3.calculatedX, b3.calculatedY, b3.getCurrentDistance());
                if (pos != null) {
                    currentX = (float) pos[0];
                    currentY = (float) pos[1];
                    lastValidX = currentX;
                    lastValidY = currentY;
                }
            } else return;
        }

        double[] pos = calculateTrilateration(
                b1.calculatedX, b1.calculatedY, b1.getCurrentDistance(),
                b2.calculatedX, b2.calculatedY, b2.getCurrentDistance(),
                b3.calculatedX, b3.calculatedY, b3.getCurrentDistance());
        if (pos == null) return;
        float newX = (float) pos[0], newY = (float) pos[1];

        float move = (float) Math.hypot(newX - lastValidX, newY - lastValidY);
        if (move > MAX_SINGLE_MOVE * 2) {
            float r = MAX_SINGLE_MOVE / move;
            newX = lastValidX + (newX - lastValidX) * r;
            newY = lastValidY + (newY - lastValidY) * r;
        }

        float finalMove = (float) Math.hypot(newX - lastValidX, newY - lastValidY);
        float smooth = (finalMove < STILL_THRESHOLD) ? STILL_SMOOTH : SMOOTH_FACTOR;

        currentX = lastValidX * (1 - smooth) + newX * smooth;
        currentY = lastValidY * (1 - smooth) + newY * smooth;
        lastValidX = currentX;
        lastValidY = currentY;
        updateCoordinateSystemPhone();
    }

    private double[] calculateTrilateration(double x1, double y1, double d1,
                                            double x2, double y2, double d2,
                                            double x3, double y3, double d3) {
        double[][] A = {{2 * (x1 - x2), 2 * (y1 - y2)},
                {2 * (x1 - x3), 2 * (y1 - y3)}};
        double[] b = {d2 * d2 - d1 * d1 + x1 * x1 - x2 * x2 + y1 * y1 - y2 * y2,
                d3 * d3 - d1 * d1 + x1 * x1 - x3 * x3 + y1 * y1 - y3 * y3};
        double[] w = {1.0, 1.0};
        return solveWeightedLS(A, b, w);
    }

    private void updateCompass() {
        if (!isAdded() || compassView == null) return;
        float relativeAzimuth = normalizeAngle(currentAzimuth - referenceAzimuth);
        String targetName = beaconNames[selectedBeaconIndex];
        BeaconData target = beacons.get(targetName);
        if (target == null) return;
        boolean calibrated = (target.calculatedX != null && target.calculatedY != null);
        double targetAzimuth = 0, dist = 0;
        if (calibrated) {
            double dx = target.calculatedX - currentX;
            double dy = target.calculatedY - currentY;
            dist = Math.hypot(dx, dy);
            if (dist > 0.1) {
                targetAzimuth = Math.toDegrees(Math.atan2(dx, dy));
                targetAzimuth = normalizeAngle((float) targetAzimuth);
            } else {
                targetAzimuth = relativeAzimuth;
            }
        }
        compassView.setTarget(relativeAzimuth, (float) targetAzimuth, (float) dist, target.name, calibrated);
        compassView.invalidate();
        tvStatus.setText(String.format("手机: (%.1f, %.1f) 朝向: %.0f°", currentX, currentY, relativeAzimuth));
    }

    private void updateBeaconPositionsDisplay() {
        if (!isAdded()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            String name = beaconNames[i];
            BeaconData b = beacons.get(name);
            double currentDist = b.getCurrentDistance();
            sb.append(name).append(": ");
            if (b.calculatedX != null)
                sb.append(String.format("(%.1f,%.1f) ", b.calculatedX, b.calculatedY));
            sb.append(String.format("→ %.1fm", currentDist));
            if (i == selectedBeaconIndex) sb.append(" ★");
            if (i < 2) sb.append("\n");
        }
        tvPosition.setText(sb.toString());
    }

    private void updateRealtimeRssiDisplay() {
        if (tvRealtimeRssi == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            BeaconData b = beacons.get(beaconNames[i]);
            if (b != null && b.getDataCount() > 0) {
                sb.append(String.format("%d号:%.0fdBm ", i + 1, b.getFilteredRssi()));
            }
        }
        tvRealtimeRssi.setText(sb.toString());
    }

    private void updateCalibratingStatus() {
        if (samplePoints.isEmpty()) return;
        SamplePoint last = samplePoints.getLast();
        int collected = last.distances.size();
        tvCalibrating.setText(collected >= 3 ? String.format("✓ 点%d: 数据已采集", samplePoints.size())
                : String.format("点%d: 已采集%d/3个信标", samplePoints.size(), collected));
    }

    private void updatePointCountDisplay() {
        if (tvPointCount != null)
            tvPointCount.setText(String.format("已采集点数：%d / %d", samplePoints.size(), MAX_SAMPLE_POINTS));
    }

    private void updateCoordinateSystemBeacons() {
        List<CoordinateSystemView.BeaconPoint> points = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BeaconData b = beacons.get(beaconNames[i]);
            if (b != null && b.calculatedX != null && b.calculatedY != null) {
                points.add(new CoordinateSystemView.BeaconPoint(
                        b.calculatedX.floatValue(), b.calculatedY.floatValue(),
                        b.name, beaconColors[i]));
            }
        }
        coordinateSystemView.updateBeacons(points);
        coordinateSystemView.setTargetBeaconIndex(selectedBeaconIndex);
    }

    private void updateCoordinateSystemSamplePoints() {
        List<CoordinateSystemView.SamplePoint> points = new ArrayList<>();
        for (SamplePoint p : samplePoints)
            points.add(new CoordinateSystemView.SamplePoint(p.x, p.y));
        coordinateSystemView.updateSamples(points);
    }

    private void updateCoordinateSystemPhone() {
        float displayAzimuth = normalizeAngle(currentAzimuth - referenceAzimuth);
        coordinateSystemView.updatePhone(new CoordinateSystemView.PhonePoint(currentX, currentY, displayAzimuth));
    }

    private void safeUpdateSamplePointsInfo() {
        if (scrollView == null || tvSamplePointsInfo == null) return;
        final int lastY = scrollView.getScrollY();
        updateSamplePointsInfo();
        scrollView.post(() -> scrollView.scrollTo(0, lastY));
    }

    private void updateSamplePointsInfo() {
        if (tvSamplePointsInfo == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== 采样点详情 ===\n");
        sb.append("序号 | 坐标(x,y) | 朝向° | 距离A | 距离B | 距离C\n");
        sb.append("---\n");
        int idx = 1;
        for (SamplePoint p : samplePoints) {
            sb.append(String.format("%3d | (%5.1f,%5.1f) | %5.0f° |",
                    idx, p.x, p.y, p.azimuth));
            Double dA = p.distances.get(beaconNames[0]);
            Double dB = p.distances.get(beaconNames[1]);
            Double dC = p.distances.get(beaconNames[2]);
            sb.append(dA != null ? String.format(" %4.1fm", dA) : "    - ");
            sb.append("|");
            sb.append(dB != null ? String.format(" %4.1fm", dB) : "    - ");
            sb.append("|");
            sb.append(dC != null ? String.format(" %4.1fm", dC) : "    - ");
            sb.append("\n");
            idx++;
        }
        tvSamplePointsInfo.setText(sb.toString());
    }

    private void toggleDeviceScan() { if (!isScanningForDevices) startDeviceScan(); else stopDeviceScan(); }

    private void startDeviceScan() {
        if (!MainActivity.checkBluetooth()) return;
        if (MainActivity.mBluetoothLeScanner == null)
            if (!MainActivity.initBluetoothScanner(requireActivity())) return;
        try {
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            MainActivity.mBluetoothLeScanner.startScan(null, settings, deviceScanCallback);
            isScanningForDevices = true;
            btnScanDevices.setText("⏹ 停止扫描");
            uiHandler.postDelayed(this::stopDeviceScan, 8000);
        } catch (Exception e) { Log.e(TAG, "扫描失败", e); }
    }

    private void stopDeviceScan() {
        if (isScanningForDevices && MainActivity.mBluetoothLeScanner != null) {
            try { MainActivity.mBluetoothLeScanner.stopScan(deviceScanCallback); } catch (Exception e) {}
        }
        isScanningForDevices = false;
        btnScanDevices.setText("🔍 扫描周围信标设备");
    }

    private void startRssiScan() {
        if (MainActivity.mBluetoothLeScanner == null) return;
        try {
            MainActivity.mBluetoothLeScanner.startScan(null,
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    rssiScanCallback);
            isRssiScanning = true;
        } catch (Exception e) {}
    }

    private void stopRssiScan() {
        if (isRssiScanning && MainActivity.mBluetoothLeScanner != null) {
            try { MainActivity.mBluetoothLeScanner.stopScan(rssiScanCallback); isRssiScanning = false; } catch (Exception e) {}
        }
    }

    private void startNavigationMode() {
        startRssiScan();
        navigationUpdateRunnable = new Runnable() {
            @Override public void run() {
                if (isNavigating && isAdded()) {
                    updateCompass();
                    updateBeaconPositionsDisplay();
                    if (!isRssiScanning) startRssiScan();
                }
                uiHandler.postDelayed(this, NAV_UPDATE_INTERVAL);
            }
        };
        uiHandler.post(navigationUpdateRunnable);
    }

    private void stopNavigationMode() {
        isNavigating = false;
        stopRssiScan();
        if (navigationUpdateRunnable != null) uiHandler.removeCallbacks(navigationUpdateRunnable);
    }

    private void checkAutoMatch(String deviceName) {
        for (int i = 0; i < 3; i++) {
            if (deviceName.equalsIgnoreCase(beaconNames[i])) {
                int index = deviceListForSpinner.indexOf(deviceName);
                if (index >= 0) {
                    switch (i) {
                        case 0: spinnerBeacon1.setSelection(index); break;
                        case 1: spinnerBeacon2.setSelection(index); break;
                        case 2: spinnerBeacon3.setSelection(index); break;
                    }
                    Toast.makeText(requireContext(), "自动匹配: " + deviceName, Toast.LENGTH_SHORT).show();
                }
            }
        }
        checkIfReadyToStart();
    }

    private void checkIfReadyToStart() {
        boolean ready = !beaconNames[0].isEmpty() && !beaconNames[1].isEmpty() && !beaconNames[2].isEmpty();
        btnMarkPoint.setEnabled(ready);
        btnMarkPoint.setAlpha(ready ? 1.0f : 0.5f);
    }

    private void resetAll() {
        isCollecting = false;
        isNavigating = false;
        isBeaconPositionsCalculated = false;
        navigationDataReady = false;
        isOriginRecorded = false;
        referenceAzimuth = 0;
        stopNavigationMode();
        stopRssiScan();
        samplePoints.clear();
        btnMarkPoint.setText("标记原点(0,0)");
        btnMarkPoint.setEnabled(true);
        btnMarkPoint.setAlpha(1.0f);
        btnStartNav.setText("开始导航");
        btnStartNav.setEnabled(true);
        tvCalibrating.setText("信标已配置");
        tvCalibrating.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        tvPosition.setText("信标位置：未计算");
        for (BeaconData b : beacons.values()) { b.clear(); b.calculatedX = null; b.calculatedY = null; }
        currentX = 0; currentY = 0; lastValidX = 0; lastValidY = 0;
        selectedBeaconIndex = 0; spinnerTarget.setSelection(0);
        updatePointCountDisplay();
        coordinateSystemView.resetView();
        updateCoordinateSystemBeacons();
        updateCoordinateSystemSamplePoints();
        updateCoordinateSystemPhone();
        safeUpdateSamplePointsInfo();
    }

    private double roundTwoDecimal(double value) {
        try { return new BigDecimal(value).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue(); }
        catch (Exception e) { return 0.0; }
    }

    private class BeaconSelectedListener implements AdapterView.OnItemSelectedListener {
        private int index;
        BeaconSelectedListener(int index) { this.index = index; }
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selected = deviceListForSpinner.get(position);
            if ("请选择设备...".equals(selected)) return;
            for (int i = 0; i < 3; i++) {
                if (i != index && selected.equals(beaconNames[i])) {
                    Toast.makeText(requireContext(), "信标重复", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Map<String, double[]> saved = new HashMap<>();
            for (String n : beaconNames) {
                BeaconData b = beacons.get(n);
                if (b != null && b.calculatedX != null) saved.put(n, new double[]{b.calculatedX, b.calculatedY});
            }
            beaconNames[index] = selected;
            beacons.clear();
            for (int i = 0; i < 3; i++) {
                beacons.put(beaconNames[i], new BeaconData(beaconNames[i], oneMeterRssi[i], n_values[i]));
                if (saved.containsKey(beaconNames[i])) {
                    double[] c = saved.get(beaconNames[i]);
                    beacons.get(beaconNames[i]).calculatedX = c[0];
                    beacons.get(beaconNames[i]).calculatedY = c[1];
                }
            }
            saveBeaconNames();
            checkIfReadyToStart();
            if (selectedBeaconIndex == index) {
                updateCompass();
                updateBeaconPositionsDisplay();
                updateCoordinateSystemBeacons();
            }
        }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }

    private void saveBeaconNames() {
        SharedPreferences sp = requireActivity().getSharedPreferences("share", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        for (int i = 0; i < 3; i++) editor.putString("beacon_" + (i+1) + "_name", beaconNames[i]);
        editor.apply();
    }

    public void stopPositioningSafely() {
        stopDeviceScan();
        stopRssiScan();
        stopNavigationMode();
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    public static ThirdFragment newInstance(int index) { return new ThirdFragment(); }
}