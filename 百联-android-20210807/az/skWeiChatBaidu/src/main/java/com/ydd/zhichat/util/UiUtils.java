package com.ydd.zhichat.util;

import android.view.View;
import android.widget.TextView;

/**
 * Created by Administrator on 2017/10/26.
 */

public class UiUtils {
    private static final int MIN_CLICK_DELAY_TIME = 600;
    private static long lastClickTime;
    private static int clickedView;

    public static void updateNum(TextView numTv, int unReadNum) {
        if (numTv == null) {
            return;
        }
        if (unReadNum < 1) {
            numTv.setText("");
            numTv.setVisibility(View.INVISIBLE);
        } else if (unReadNum > 99) {
            numTv.setText("99+");
            numTv.setVisibility(View.VISIBLE);
        } else {
            numTv.setText(String.valueOf(unReadNum));
            numTv.setVisibility(View.VISIBLE);
        }
    }

    /**
     * @deprecated {@link UiUtils#isNormalClick(android.view.View)}
     */
    @Deprecated
    public static boolean isNormalClick() {
        boolean isNormal = false;
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastClickTime) >= MIN_CLICK_DELAY_TIME) {
            isNormal = true;
        }
        lastClickTime = currentTime;
        return isNormal;
    }

    public static boolean isNormalClick(View view) {
        // hashCode确保同一个view计算出来是一样的，不同view也几乎不会遇到相同hashCode的情况，
        long currentTime = System.currentTimeMillis();
        if (clickedView != view.hashCode()) {
            // 点击不同的view，不限制时间间隔，
            clickedView = view.hashCode();
            lastClickTime = currentTime;
            return true;
        }
        // 同一个view多次点击，限制连续点击时间，
        clickedView = view.hashCode();
        boolean isNormal = false;
        if ((currentTime - lastClickTime) >= MIN_CLICK_DELAY_TIME) {
            isNormal = true;
        }
        lastClickTime = currentTime;
        return isNormal;
    }
}
