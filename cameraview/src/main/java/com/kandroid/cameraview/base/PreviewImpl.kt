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

package com.kandroid.cameraview.base

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.View

/**
 * @author aqrlei on 2019/3/26
 */
abstract class PreviewImpl {

    private var mCallback: Callback? = null

    var width: Int = 0
        private set

    var height: Int = 0
        private set

    abstract val surface: Surface

    abstract val view: View

    abstract val outputClass: Class<*>

    abstract val isReady: Boolean

    abstract val surfaceTexture: SurfaceTexture

    interface Callback {
        fun onSurfaceChanged()
    }

    fun setCallback(callback: Callback) {
        mCallback = callback
    }

    abstract fun setDisplayOrientation(displayOrientation: Int)

    protected fun dispatchSurfaceChanged() {
        mCallback?.onSurfaceChanged()
    }

    open fun setBufferSize(width: Int, height: Int) {}

    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }
}