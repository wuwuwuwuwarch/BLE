package com.ckos.ble;

public class Bluetooth {
    private String name;
    private int rssi;
    private String address;

    public Bluetooth(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public String getName() {
        return name;
    }

    public int getRssi() {
        return rssi;
    }

    public String getAddress() {
        return address;
    }
}
