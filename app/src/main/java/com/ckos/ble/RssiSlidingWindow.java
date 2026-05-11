package com.ckos.ble;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 15组滑动窗口滤波器（FIFO队列）
 * V2.0技术方案：队列长度15，满队列时精度最佳
 */
public class RssiSlidingWindow {
    private final int windowSize = 15;
    private Queue<Integer> rssiQueue = new LinkedList<>();

    public void addRssi(int rssi) {
        rssiQueue.offer(rssi);
        if (rssiQueue.size() > windowSize) {
            rssiQueue.poll();
        }
    }

    public double getFilteredRssi() {
        if (rssiQueue.isEmpty()) return -100;
        double sum = 0;
        for (int rssi : rssiQueue) {
            sum += rssi;
        }
        return sum / rssiQueue.size();
    }

    public int getDataCount() {
        return rssiQueue.size();
    }

    public void clear() {
        rssiQueue.clear();
    }

    public boolean isReady() {
        return rssiQueue.size() >= 3; // 最少3个数据即可开始计算
    }

    public boolean isFull() {
        return rssiQueue.size() >= windowSize;
    }
}