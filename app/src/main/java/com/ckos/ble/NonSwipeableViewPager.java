package com.ckos.ble;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

public class NonSwipeableViewPager extends ViewPager {

    public NonSwipeableViewPager(@NonNull Context context) {
        super(context);
    }

    public NonSwipeableViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 禁止拦截触摸事件，从而禁用滑动
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // 禁止处理触摸事件，从而禁用滑动
        return false;
    }
}