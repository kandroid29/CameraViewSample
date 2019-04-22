package com.kandroid.cameraview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * @author aqrlei on 2019/4/1
 */
interface ForegroundRenderer {
    fun onDrawMask(view: View, canvas: Canvas?)
}

class DefaultForegroundRenderer : ForegroundRenderer {
    override fun onDrawMask(view: View, canvas: Canvas?) {
        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6F
        paint.color = Color.GREEN
        val squareRect = RectF(
            view.width / 2F - 60F,
            view.height / 2F - 60F,
            view.width / 2F + 60F,
            view.height / 2F + 60F
        )
        canvas?.drawOval(squareRect, paint)


    }
}