package com.kandroid.cameraview.utils

import android.app.Activity
import android.util.DisplayMetrics

object MetricsUtils {
    @JvmStatic
    fun getScreenWidth(activity: Activity): Int {
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    @JvmStatic
    fun getScreenHeight(activity: Activity): Int {
        val metrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }
}