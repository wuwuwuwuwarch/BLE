package com.ckos.ble.ui.main;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ckos.ble.Bluetooth;
import com.ckos.ble.MainActivity;
import com.ckos.ble.R;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SecondFragment extends Fragment {
    private static final String TAG = "SecondFragment";
    private View root;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private String name;
    private int one_meter_rssi;
    private float n_value;

    private final Handler scanHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTimeoutRunnable;
    private static final long SCAN_TIMEOUT = 10000;
    private boolean isScanning = false;

    private KalmanFilter rssiFilter;
    private long lastUiUpdateTime = 0;
    private static final long UI_UPDATE_INTERVAL = 400;

    private TextView position;
    private EditText name_et;
    private EditText one_meter_rssi_et;
    private EditText n_value_et;
    private Button position_button;

    private ScanCallback mScanCallback;

    public static class KalmanFilter {
        private final double Q;
        private final double R;
        private double X;
        private double P;

        public KalmanFilter(double initialValue, double q, double r) {
            this.X = initialValue;
            this.Q = q;
            this.R = r;
            this.P = 1.0;
        }

        public double filter(double measurement) {
            P = P + Q;
            double K = P / (P + R);
            X = X + K * (measurement - X);
            P = (1 - K) * P;
            return X;
        }

        public void reset(double value) {
            X = value;
            P = 1.0;
        }
    }

    public static SecondFragment newInstance(int index) {
        return new SecondFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rssiFilter = new KalmanFilter(-50.0, 0.01, 2.0);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_tab_2, container, false);

        position = root.findViewById(R.id.position);
        sharedPreferences = requireActivity().getSharedPreferences("share", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        name = sharedPreferences.getString("name", "Beacon_A");
        name_et = root.findViewById(R.id.name);
        name_et.setText(name);

        one_meter_rssi = sharedPreferences.getInt("one_meter_rssi", 69);
        one_meter_rssi_et = root.findViewById(R.id.one_meter_rssi);
        one_meter_rssi_et.setText(String.valueOf(one_meter_rssi));

        n_value = sharedPreferences.getFloat("n_value", 2.5f);
        n_value_et = root.findViewById(R.id.n_value);
        n_value_et.setText(String.valueOf(n_value));

        position_button = root.findViewById(R.id.position_button);
        position_button.setOnClickListener(v -> {
            if (position_button.getText().toString().equals("开始定位")) {
                startPositioning();
            } else {
                stopPositioning();
            }
        });

        initScanCallback();

        return root;
    }

    private void startPositioning() {
        String deviceName = name_et.getText().toString().trim();
        if (deviceName.isEmpty()) {
            Toast.makeText(requireContext(), "请输入设备名！", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            one_meter_rssi = Integer.parseInt(one_meter_rssi_et.getText().toString());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "请输入有效的1米Rssi！", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            n_value = Float.parseFloat(n_value_et.getText().toString());
        } catch (Exception e) {
            Toast.makeText(requireContext(), "请输入有效的n_Value！", Toast.LENGTH_SHORT).show();
            return;
        }

        name = deviceName;
        editor.putString("name", name);
        editor.putInt("one_meter_rssi", one_meter_rssi);
        editor.putFloat("n_value", n_value);
        editor.apply();

        name_et.setEnabled(false);
        one_meter_rssi_et.setEnabled(false);
        n_value_et.setEnabled(false);

        position_button.setText(R.string.position_button_stop);
        position.setTextColor(getResources().getColor(R.color.BLACK));
        position.setText("正在搜索设备...");

        rssiFilter.reset(-50.0);
        startScan();
    }

    private void stopPositioning() {
        name_et.setEnabled(true);
        one_meter_rssi_et.setEnabled(true);
        n_value_et.setEnabled(true);
        position_button.setText(R.string.position_button_start);
        position.setTextColor(getResources().getColor(R.color.colorOff));
        stopScan();
    }

    private void initScanCallback() {
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();

                if (deviceName == null && result.getScanRecord() != null) {
                    deviceName = result.getScanRecord().getDeviceName();
                }
                if (deviceName == null) {
                    deviceName = getDeviceNameFromCache(deviceAddress);
                }
                if (deviceName == null) return;

                String targetName = name.trim();
                String foundName = deviceName.trim();

                if (!foundName.equalsIgnoreCase(targetName)) return;

                cancelScanTimeout();

                int rawRssi = result.getRssi();
                double filteredRssi = rssiFilter.filter(rawRssi);
                double dst = getDistance((int) filteredRssi);
                if (dst < 0.1) dst = 0.1;
                if (dst > 10.0) dst = 10.0;

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUiUpdateTime > UI_UPDATE_INTERVAL) {
                    lastUiUpdateTime = currentTime;
                    position.setText(String.format("信号: %.0fdBm (原始%d)\n距离: %.2fm",
                            filteredRssi, rawRssi, dst));
                    position.setTextColor(getResources().getColor(R.color.colorPrimary));
                    Log.d(TAG, String.format("RSSI原始: %d, 滤波: %.1f, 距离: %.2fm",
                            rawRssi, filteredRssi, dst));
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "扫描失败，错误码: " + errorCode);
                requireActivity().runOnUiThread(() -> {
                    position.setText("扫描失败 (错误码:" + errorCode + ")");
                    position.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                });
            }
        };
    }

    private void startScan() {
        if (!MainActivity.checkBluetooth()) {
            Toast.makeText(requireActivity(), "该手机不支持蓝牙", Toast.LENGTH_LONG).show();
            return;
        }
        if (!MainActivity.openBluetooth(requireActivity())) {
            Toast.makeText(requireActivity(), "请先开启蓝牙", Toast.LENGTH_LONG).show();
            return;
        }

        if (MainActivity.mBluetoothLeScanner == null) {
            Toast.makeText(requireActivity(), "蓝牙未就绪，请稍后再试", Toast.LENGTH_LONG).show();
            MainActivity.initBluetoothScanner(requireActivity());
            return;
        }

        isScanning = true;
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            MainActivity.mBluetoothLeScanner.startScan(null, settings, mScanCallback);
            Log.d(TAG, "开始扫描设备: " + name);
            startScanTimeout();
        } catch (Exception e) {
            Log.e(TAG, "启动扫描失败", e);
            Toast.makeText(requireActivity(), "启动扫描失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopScan() {
        if (isScanning && MainActivity.mBluetoothLeScanner != null) {
            try {
                MainActivity.mBluetoothLeScanner.stopScan(mScanCallback);
            } catch (Exception e) {
                Log.e(TAG, "停止扫描失败", e);
            }
        }
        isScanning = false;
        cancelScanTimeout();
    }

    private void startScanTimeout() {
        cancelScanTimeout();
        scanTimeoutRunnable = () -> {
            if (isScanning && position.getText().toString().contains("正在搜索")) {
                position.setText("暂未发现该设备\n(名称: " + name + ")");
                position.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                Toast.makeText(requireActivity(), "未找到设备，请确认:\n1. 设备是否开启\n2. 设备名称是否正确\n3. 先在搜索页确认能看到该设备", Toast.LENGTH_LONG).show();
            }
        };
        scanHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT);
    }

    private void cancelScanTimeout() {
        if (scanTimeoutRunnable != null) {
            scanHandler.removeCallbacks(scanTimeoutRunnable);
        }
    }

    private String getDeviceNameFromCache(String address) {
        if (address == null) return null;
        if (MainActivity.bleAdapter != null) {
            for (int i = 0; i < MainActivity.bleAdapter.getCount(); i++) {
                Bluetooth b = MainActivity.bleAdapter.getItem(i);
                if (b != null && address.equals(b.getAddress())) {
                    return b.getName();
                }
            }
        }
        if (MainActivity.btAdapter != null) {
            for (int i = 0; i < MainActivity.btAdapter.getCount(); i++) {
                Bluetooth b = MainActivity.btAdapter.getItem(i);
                if (b != null && address.equals(b.getAddress())) {
                    return b.getName();
                }
            }
        }
        return null;
    }

    private double getDistance(int rssi) {
        if (n_value <= 0) n_value = 2.0f;
        if (one_meter_rssi <= 0) one_meter_rssi = 59;
        double power = (Math.abs(rssi) - one_meter_rssi) / (10.0 * n_value);
        double distance = Math.pow(10, power);
        return roundTwoDecimal(distance);
    }

    private double roundTwoDecimal(double value) {
        try {
            BigDecimal bg = new BigDecimal(value);
            return bg.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
        Log.d(TAG, "Fragment view destroyed, scan stopped");
    }

    // 供 MainActivity 调用的安全停止方法
    public void stopPositioningSafely() {
        if (position_button != null && position_button.getText().toString().equals("停止定位")) {
            stopPositioning();
        }
        stopScan();
    }
}