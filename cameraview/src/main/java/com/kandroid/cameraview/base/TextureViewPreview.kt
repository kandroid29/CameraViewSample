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

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import com.kandroid.cameraview.R

/**
 * @author aqrlei on 2019/3/26
 */

class TextureViewPreview(context: Context, parent: ViewGroup) : PreviewImpl() {

    private val textureView: TextureView

    private var mDisplayOrientation: Int = 0

    override val surface: Surface
        get() = Surface(textureView.surfaceTexture)

    override val surfaceTexture: SurfaceTexture
        get() = textureView.surfaceTexture

    override val view: View
        get() = textureView

    override val outputClass: Class<*>
        get() = SurfaceTexture::class.java

    override val isReady: Boolean
        get() = textureView.isAvailable

    init {
        val view = View.inflate(context, R.layout.texture_view, parent)
        textureView = view.findViewById<View>(R.id.textureView) as TextureView
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                updateSurface(width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                updateSurface(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                setSize(0, 0)
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun updateSurface(width: Int, height: Int) {
        setSize(width, height)
        configureTransform()
        dispatchSurfaceChanged()
    }

    // This method is called only from Camera2.
    @TargetApi(15)
    override fun setBufferSize(width: Int, height: Int) {
        textureView.surfaceTexture.setDefaultBufferSize(width, height)
    }

    override fun setDisplayOrientation(displayOrientation: Int) {
        mDisplayOrientation = displayOrientation
        configureTransform()
    }

    /**
     * Configures the transform matrix for TextureView based on [.mDisplayOrientation] and
     * the surface size.
     */
    private fun configureTransform() {
        val matrix = Matrix()

        // 如果屏幕是横向
        if (mDisplayOrientation % 180 == 90) {
            val width = width
            val height = height
            // Rotate the camera preview when the screen is landscape.
            matrix.setPolyToPoly(
                floatArrayOf(
                    0f, 0f, // top left
                    width.toFloat(), 0f, // top right
                    0f, height.toFloat(), // bottom left
                    width.toFloat(), height.toFloat())// bottom right
                , 0,
                if (mDisplayOrientation == 90)
                // Clockwise
                    floatArrayOf(
                        0f, height.toFloat(), // top left
                        0f, 0f, // top right
                        width.toFloat(), height.toFloat(), // bottom left
                        width.toFloat(), 0f)// bottom right
                else
                // mDisplayOrientation == 270
                // Counter-clockwise
                    floatArrayOf(
                        width.toFloat(), 0f, // top left
                        width.toFloat(), height.toFloat(), // top right
                        0f, 0f, // bottom left
                        0f, height.toFloat())// bottom right
                , 0, 4)
        } else if (mDisplayOrientation == 180) {
            matrix.postRotate(180f, width / 2F, height / 2F)
        }
        textureView.setTransform(matrix)
    }

}