package com.ckos.ble;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.ckos.ble.ui.main.FirstFragment;
import com.ckos.ble.ui.main.SecondFragment;
import com.ckos.ble.ui.main.ThirdFragment;
import com.ckos.ble.ui.main.SectionsPagerAdapter;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static List<Bluetooth> bt_list = null;
    public static List<Bluetooth> ble_list = null;
    public static MyAdapter btAdapter = null;
    public static MyAdapter bleAdapter = null;
    public static BluetoothAdapter mBluetoothAdapter = null;
    public static BluetoothLeScanner mBluetoothLeScanner = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setContentView(R.layout.activity_main);

        bt_list = new ArrayList<>();
        btAdapter = new MyAdapter(this, R.layout.list_item, bt_list);
        ble_list = new ArrayList<>();
        bleAdapter = new MyAdapter(this, R.layout.list_item, ble_list);

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        NonSwipeableViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPagerAdapter);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {}

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        try {
                            FirstFragment firstFragment = (FirstFragment) getSupportFragmentManager()
                                    .findFragmentByTag("android:switcher:" + R.id.view_pager + ":0");
                            if (firstFragment != null && firstFragment.isAdded() && firstFragment.getView() != null) {
                                Button scan_button = firstFragment.getView().findViewById(R.id.scan_button);
                                if (scan_button != null && "停止扫描".equals(scan_button.getText().toString())) {
                                    scan_button.callOnClick();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Stop first fragment scan failed", e);
                        }
                        break;
                    case 1:
                        try {
                            SecondFragment secondFragment = (SecondFragment) getSupportFragmentManager()
                                    .findFragmentByTag("android:switcher:" + R.id.view_pager + ":1");
                            if (secondFragment != null && secondFragment.isAdded()) {
                                secondFragment.stopPositioningSafely();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Stop second fragment failed", e);
                        }
                        break;
                    case 2:
                        try {
                            ThirdFragment thirdFragment = (ThirdFragment) getSupportFragmentManager()
                                    .findFragmentByTag("android:switcher:" + R.id.view_pager + ":2");
                            if (thirdFragment != null && thirdFragment.isAdded()) {
                                thirdFragment.stopPositioningSafely();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Stop third fragment failed", e);
                        }
                        break;
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (checkfPermission()) {
            initBluetooth();
        }
    }

    public boolean checkfPermission() {
        List<String> permissionsList = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新的蓝牙权限
            permissionsList.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsList.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            // Android 11 及以下需要传统蓝牙权限
            permissionsList.add(Manifest.permission.BLUETOOTH);
            permissionsList.add(Manifest.permission.BLUETOOTH_ADMIN);
        }

        // 定位权限（所有版本都需要）
        permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        // 可选：如果仍使用短信，可添加 SEND_SMS 权限（注意低版本兼容性）
        // permissionsList.add(Manifest.permission.SEND_SMS);

        List<String> requestPermissionsList = new ArrayList<>();
        for (String permission : permissionsList) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsList.add(permission);
            }
        }

        if (!requestPermissionsList.isEmpty()) {
            ActivityCompat.requestPermissions(this, requestPermissionsList.toArray(new String[0]), 1);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                }
            }
            if (allGranted) {
                initBluetooth();
            } else {
                Toast.makeText(this, "缺少必要权限，无法使用蓝牙功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initBluetooth() {
        if (checkBluetooth()) {
            openBluetooth(this);
        } else {
            Toast.makeText(this, "该手机不支持蓝牙", Toast.LENGTH_LONG).show();
        }
    }

    public static boolean checkBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null;
    }

    public static boolean initBluetoothScanner(Activity activity) {
        if (mBluetoothAdapter == null) return false;
        if (!mBluetoothAdapter.isEnabled()) return false;

        // 仅在 Android 12+ 检查新权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            return mBluetoothLeScanner != null;
        }
        return true;
    }

    public static boolean openBluetooth(Activity activity) {
        if (mBluetoothAdapter == null) return false;
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // 仅在 Android 12+ 检查 BLUETOOTH_CONNECT 权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            activity.startActivityForResult(enableBtIntent, 1);
            return false;
        }
        return initBluetoothScanner(activity);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            initBluetoothScanner(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}