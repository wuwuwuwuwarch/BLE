package com.ckos.ble.ui.main;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.ckos.ble.Bluetooth;
import com.ckos.ble.MainActivity;
import com.ckos.ble.R;

public class FirstFragment extends Fragment {
    public static View root = null;
    private int scan_mode = 1;

    private BroadcastReceiver mReceiver = null;
    private ScanCallback mScanCallback = null;

    private RadioGroup scan_mode_group;
    private TextView scan_info;
    private Button scan_button;
    private ListView list_view;

    public static FirstFragment newInstance(int index) {
        FirstFragment fragment = new FirstFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_tab_1, container, false);
        scan_mode_group = root.findViewById(R.id.scan_mode_group);
        scan_mode_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.scan_mode_1:
                        scan_mode = 1;
                        list_view.setAdapter(MainActivity.bleAdapter);
                        scan_info.setText("找到" + MainActivity.bleAdapter.getCount() + "个BLE设备");
                        break;
                    case R.id.scan_mode_2:
                        scan_mode = 2;
                        list_view.setAdapter(MainActivity.btAdapter);
                        scan_info.setText("找到" + MainActivity.btAdapter.getCount() + "个BT设备");
                        break;
                }
            }
        });

        scan_info = root.findViewById(R.id.scan_info);
        scan_info.setText("找到" + MainActivity.bleAdapter.getCount() + "个BLE设备");

        scan_button = root.findViewById(R.id.scan_button);
        scan_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scan_button.getText().toString().equals("开始扫描")) {
                    for (int i = 0; i < scan_mode_group.getChildCount(); i++) {
                        scan_mode_group.getChildAt(i).setEnabled(false);
                    }
                    startScan();
                } else {
                    for (int i = 0; i < scan_mode_group.getChildCount(); i++) {
                        scan_mode_group.getChildAt(i).setEnabled(true);
                    }
                    stopScan();
                }
            }
        });

        list_view = root.findViewById(R.id.scan_list);
        list_view.setAdapter(MainActivity.bleAdapter);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case BluetoothDevice.ACTION_FOUND:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        int position = 0;
                        while (position < MainActivity.btAdapter.getCount()) {
                            if (MainActivity.btAdapter.getItem(position).getAddress().equals(device.getAddress())) {
                                break;
                            }
                            position++;
                        }
                        if (position == MainActivity.btAdapter.getCount()) {
                            Bluetooth bluetooth = new Bluetooth(device.getName(), device.getAddress());
                            int rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                            bluetooth.setRssi(rssi);
                            MainActivity.btAdapter.add(bluetooth);
                            scan_info.setText("找到" + MainActivity.btAdapter.getCount() + "个BT设备");
                            Toast.makeText(getActivity(), "找到新设备", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        scan_button.setText(R.string.scan_button_2);
                        scan_info.setText("找到0个BT设备");
                        Toast.makeText(getActivity(), "开始扫描", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        scan_button.setText(R.string.scan_button_1);
                        scan_info.setText("找到" + MainActivity.btAdapter.getCount() + "个BT设备");
                        Toast.makeText(getActivity(), "扫描结束", Toast.LENGTH_SHORT).show();
                        // 扫描自然结束时也要清空列表（可选，取决于需求）
                        // 如果希望自然结束也清空，取消下面这行的注释：
                        // clearDeviceList();
                        break;
                    default:
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getActivity().registerReceiver(mReceiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        getActivity().registerReceiver(mReceiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(mReceiver, filter);

        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                int position = 0;
                while (position < MainActivity.bleAdapter.getCount()) {
                    if (MainActivity.bleAdapter.getItem(position).getAddress().equals(device.getAddress())) {
                        break;
                    }
                    position++;
                }
                if (position == MainActivity.bleAdapter.getCount()) {
                    Bluetooth bluetooth = new Bluetooth(device.getName(), device.getAddress());
                    bluetooth.setRssi(rssi);
                    MainActivity.bleAdapter.add(bluetooth);
                    scan_info.setText("找到" + MainActivity.bleAdapter.getCount() + "个BLE设备");
                    Toast.makeText(getActivity(), "找到新设备", Toast.LENGTH_SHORT).show();
                } else {
                    MainActivity.bleAdapter.getItem(position).setRssi(rssi);
                    MainActivity.bleAdapter.notifyDataSetChanged();
                }
            }
        };

        return root;
    }

    /**
     * 清空当前模式的设备列表
     */
    private void clearDeviceList() {
        switch (scan_mode) {
            case 1: // BLE模式
                MainActivity.bleAdapter.clear();
                break;
            case 2: // 经典蓝牙模式
                MainActivity.btAdapter.clear();
                break;
        }
        updateScanInfo();
    }

    /**
     * 更新扫描信息文本
     */
    private void updateScanInfo() {
        switch (scan_mode) {
            case 1:
                scan_info.setText("找到0个BLE设备");
                break;
            case 2:
                scan_info.setText("找到0个BT设备");
                break;
        }
    }

    private void startScan() {
        if (!MainActivity.checkBluetooth()) {
            Toast.makeText(getActivity(), "该手机不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!MainActivity.openBluetooth(getActivity())) {
            return;
        }

        // 关键修复1：开始扫描前清空列表，确保是全新扫描
        clearDeviceList();

        switch (scan_mode) {
            case 1:
                // 使用低延迟模式提高扫描效率
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                MainActivity.mBluetoothLeScanner.startScan(null, settings, mScanCallback);
                scan_button.setText(R.string.scan_button_2);
                scan_info.setText("正在扫描BLE设备...");
                break;
            case 2:
                MainActivity.mBluetoothAdapter.startDiscovery();
                // 注意：经典蓝牙的扫描开始状态通过BroadcastReceiver接收
                break;
            default:
        }
    }

    public void stopScan() {
        switch (scan_mode) {
            case 1:
                if (MainActivity.mBluetoothLeScanner != null) {
                    MainActivity.mBluetoothLeScanner.stopScan(mScanCallback);
                }
                scan_button.setText(R.string.scan_button_1);
                // 关键修复2：停止扫描后清空BLE列表
                MainActivity.bleAdapter.clear();
                scan_info.setText("找到0个BLE设备");
                break;
            case 2:
                if (MainActivity.mBluetoothAdapter != null && MainActivity.mBluetoothAdapter.isDiscovering()) {
                    MainActivity.mBluetoothAdapter.cancelDiscovery();
                }
                scan_button.setText(R.string.scan_button_1);
                // 关键修复3：停止扫描后清空经典蓝牙列表
                MainActivity.btAdapter.clear();
                scan_info.setText("找到0个BT设备");
                break;
            default:
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            getActivity().unregisterReceiver(mReceiver);
        }
    }
}