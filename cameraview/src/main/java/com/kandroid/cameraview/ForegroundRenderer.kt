/*
 * Copyright (C) 2018 AqrLei
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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