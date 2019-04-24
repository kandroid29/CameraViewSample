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

package com.kandroid.cameraview.camera1

import android.annotation.SuppressLint
import android.graphics.Rect
import android.hardware.Camera
import android.media.MediaRecorder
import android.support.v4.util.SparseArrayCompat
import android.util.SparseIntArray
import android.widget.Toast
import com.kandroid.cameraview.CameraView
import com.kandroid.cameraview.base.*
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author aqrlei on 2019/3/26
 */

@Suppress("DEPRECATION")
class Camera1(callback: Callback?, preview: PreviewImpl) : CameraViewImpl(callback, preview) {
    companion object {
        private const val INVALID_CAMERA_ID = -1
        private val INTERNAL_FACINGS = SparseIntArray()

        private val FLASH_MODES = SparseArrayCompat<String>()

        init {
            INTERNAL_FACINGS.put(Constants.FACING_BACK, Camera.CameraInfo.CAMERA_FACING_BACK)
            INTERNAL_FACINGS.put(Constants.FACING_FRONT, Camera.CameraInfo.CAMERA_FACING_FRONT)

            FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF)
            FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON)
            FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH)
            FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO)
            FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE)
        }
    }

    private var mCameraId: Int = 0

    private val isPictureCaptureInProgress = AtomicBoolean(false)

    var mCamera: Camera? = null

    private var mCameraParameters: Camera.Parameters? = null

    private val mCameraInfo = Camera.CameraInfo()

    private val mPreviewSizes = SizeMap()

    private val mPictureSizes = SizeMap()

    private var mAspectRatio: AspectRatio? = null

    private var mShowingPreview: Boolean = false

    private var mAutoFocus: Boolean = false

    override val aspectRatio: AspectRatio?
        get() = mAspectRatio

    override var facing: Int = Constants.FACING_BACK
        set(facing) {
            if (this.facing == facing) {
                return
            }
            field = facing
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    private var mFlash: Int = Constants.FLASH_OFF

    private var mDisplayOrientation: Int = 0

    override val isCameraOpened: Boolean
        get() = mCamera != null

    override val supportedAspectRatios: Set<AspectRatio>
        get() {
            val idealAspectRatios = mPreviewSizes
            for (aspectRatio in idealAspectRatios.ratios()) {
                if (mPictureSizes.sizes(aspectRatio) == null) {
                    idealAspectRatios.remove(aspectRatio)
                }
            }
            return idealAspectRatios.ratios()
        }

    override var autoFocus: Boolean
        get() {
            if (!isCameraOpened) {
                return mAutoFocus
            }
            val focusMode = mCameraParameters?.focusMode
            return focusMode != null && focusMode.contains("continuous")
        }
        set(autoFocus) {
            if (mAutoFocus == autoFocus) {
                return
            }
            if (setAutoFocusInternal(autoFocus)) {
                mCamera?.parameters = mCameraParameters
            }
        }

    override var flash: Int
        get() = mFlash
        set(flash) {
            if (flash == mFlash) {
                return
            }
            if (setFlashInternal(flash)) {
                mCamera?.parameters = mCameraParameters
            }
        }


    override fun start(): Boolean {
        super.start()
        chooseCamera()
        openCamera()
        previewImpl.setCallback(object : PreviewImpl.Callback {
            override fun onSurfaceChanged() {
                if (mCamera != null) {
                    configurePreview()
                }
            }
        })
        if (previewImpl.isReady) {
            setUpPreview()
        }
        mShowingPreview = true
        mCamera?.startPreview()
        return true
    }

    override fun release() {
        releaseCamera()
    }

    /**
     * This rewrites [.mCameraId] and [.mCameraInfo].
     */
    private fun chooseCamera() {
        val internalFacing = INTERNAL_FACINGS.get(facing)
        var i = 0
        val count = Camera.getNumberOfCameras()
        while (i < count) {
            Camera.getCameraInfo(i, mCameraInfo)
            if (mCameraInfo.facing == internalFacing) {
                mCameraId = i
                return
            }
            i++
        }
        mCameraId = INVALID_CAMERA_ID
    }

    private fun openCamera() {
        chooseCamera()
        if (mCamera != null) {
            releaseCamera()
            releaseMediaRecorder()
        }
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500L, TimeUnit.MILLISECONDS)) {
                throw  RuntimeException("Time out waiting to lock camera opening")
            }
            synchronized(cameraStateLock) {
                mCamera = Camera.open(mCameraId)
            }
            mCameraParameters = mCamera?.parameters
            // Supported preview sizes
            mCameraParameters?.let { parameters ->
                mPreviewSizes.clear()
                for (size in parameters.supportedPreviewSizes) {
                    mPreviewSizes.add(Size(size.width, size.height))
                }
                videoSizes.clear()
                for (size in parameters.supportedVideoSizes) {
                    videoSizes.add(Size(size.width, size.height))
                }
                // Supported picture sizes;
                mPictureSizes.clear()
                for (size in parameters.supportedPictureSizes) {
                    mPictureSizes.add(Size(size.width, size.height))
                }
            }
            // AspectRatio
            if (mAspectRatio == null) {
                mAspectRatio = Constants.DEFAULT_ASPECT_RATIO
            }
            adjustCameraParameters()
            mCamera?.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation))
            cameraCallback?.onCameraOpened()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            messageCallback(e.message, CameraView.ERROR_CAMERA_OPEN_FAILED)
        } finally {
            cameraOpenCloseLock.release()

        }

    }

    private fun configurePreview() {
        setUpPreview()
        adjustCameraParameters()
    }

    override fun touchFocus(touchX: Int, touchY: Int) {
        synchronized(cameraStateLock) {
            mCamera?.let { camera ->
                mCameraParameters?.let { parameters ->
                    try {
                        val maxNumFocusAreas = parameters.maxNumFocusAreas
                        parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                        if (parameters.focusMode === Camera.Parameters.FOCUS_MODE_AUTO && maxNumFocusAreas > 0) {
                            val halfOfCameraWidth = previewImpl.width / 2
                            val scaleSizeX = 1000 / halfOfCameraWidth.toDouble()//相机坐标相对于点击坐标的x比例
                            val halfOfCameraHeight = previewImpl.height / 2
                            val scaleSizeY = 1000 / halfOfCameraHeight.toDouble()//相机坐标相对于点击坐标的y比例
                            val focusPivotX = ((touchX - halfOfCameraWidth) * scaleSizeX).toInt()//聚焦中心点x轴
                            val focusPivotY = ((touchY - halfOfCameraHeight) * scaleSizeY).toInt()//聚焦中心点y轴
                            val left: Int
                            val top: Int
                            val right: Int
                            val bottom: Int
                            when {
                                focusPivotX <= -900 -> {
                                    left = -1000
                                    right = focusPivotX + 100
                                }
                                focusPivotX >= 900 -> {
                                    left = focusPivotX - 100
                                    right = 1000
                                }
                                else -> {
                                    left = focusPivotX - 100
                                    right = focusPivotX + 100
                                }
                            }
                            when {
                                focusPivotY <= -900 -> {
                                    top = -1000
                                    bottom = focusPivotY + 100
                                }
                                focusPivotY >= 900 -> {
                                    top = focusPivotY - 100
                                    bottom = 1000
                                }
                                else -> {
                                    top = focusPivotY - 100
                                    bottom = focusPivotY + 100
                                }
                            }
                            val focusAreaRect = Rect(left, top, right, bottom)
                            //1000表示权重，(-1000,-1000)表示左上角点的坐标，(1000,1000)表示右下角点的坐标，表示Rect中1就是1个单位的坐标
                            val area = Camera.Area(focusAreaRect, 1000)
                            val focusAreas = ArrayList<Camera.Area>()
                            focusAreas.add(area)
                            parameters.focusAreas = focusAreas
                            camera.parameters = mCameraParameters
                            camera.cancelAutoFocus()
                            camera.autoFocus { _, _ -> }
                        } else {
                            messageCallback("该摄像头不支持自动聚焦", CameraView.TIPS_CAMERA_FEATURE_NOT_SUPPORT)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            }
        }
    }

    override fun stop() {
        if (mCamera != null) {
            mCamera?.stopPreview()
        }
        mShowingPreview = false
        releaseCamera()
        releaseMediaRecorder()
        super.stop()
    }

    @SuppressLint("NewApi")
    fun setUpPreview() {
        try {
            mCamera?.setPreviewTexture(previewImpl.surfaceTexture)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        if (mAspectRatio == null || !isCameraOpened) {
            // Handle this later when camera is opened
            mAspectRatio = ratio
            return true
        } else if (mAspectRatio != ratio) {
            val sizes = mPreviewSizes.sizes(ratio)
            if (sizes == null) {
                throw UnsupportedOperationException("$ratio is not supported")
            } else {
                mAspectRatio = ratio
                adjustCameraParameters()
                return true
            }
        }
        return false
    }

    override fun startRecord(savePath: String, quality: Quality) {
        if (!previewImpl.isReady) return
        if (record.getAndSet(true)) return
        try {
            mediaRecorder = mediaRecorder ?: MediaRecorder()
            mediaRecorder?.reset()
            startTimeLocked()
            mCamera?.unlock()
            mediaRecorder?.setCamera(mCamera)
            setUpMediaRecorder(quality, savePath)
            mediaRecorder?.start()
            cameraCallback?.onRecordStart(record.get())
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            record.set(false)
            messageCallback(e.message, CameraView.ERROR_RECORD_START_FAILED)
            mediaRecorder?.release()
            mediaRecorder = null
            mCamera?.reconnect()
        }

    }

    override fun setMediaRecorderOrientationHint(mediaRecorder: MediaRecorder) {
        when (mCameraInfo.orientation) {
            Constants.LANDSCAPE_90 -> {
                mediaRecorder.setOrientationHint((360 - mDisplayOrientation + Constants.LANDSCAPE_90) % 360)
            }
            Constants.LANDSCAPE_270 -> {
                mediaRecorder.setOrientationHint(Math.abs(mDisplayOrientation - Constants.LANDSCAPE_270))
            }
        }
    }

    override fun stopRecord(restartPreview: Boolean): Boolean {
        if (hitTimeoutLocked() && record.get()) {
            var recordSuccess = true
            try {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
            } catch (e: Exception) {
                e.printStackTrace()
                recordSuccess = false
                messageCallback(e.message, CameraView.ERROR_RECORD_STOP_FAILED)
                mediaRecorder?.release()
                mediaRecorder = null
            }
            cameraCallback?.onRecordDone(recordSuccess, mVideoOutputPath)
            record.set(false)

            mCamera?.reconnect()
        }
        return record.get()
    }

    override fun getVideoSource(): Int = MediaRecorder.VideoSource.CAMERA

    override fun takePicture() {
        if (record.get()) {
            messageCallback("Being recording, cannot take picture", CameraView.TIPS_PICTURE_TAKE_IN_RECORDING)
            return
        }
        if (!isCameraOpened) {
            throw IllegalStateException(
                    "Camera is not ready. Call start() before takePicture().")
        }
        if (autoFocus) {
            mCamera?.cancelAutoFocus()
            mCamera?.autoFocus { _, _ -> takePictureInternal() }
        } else {
            takePictureInternal()
        }
    }

    private fun takePictureInternal() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            mCamera?.takePicture(null, null, null, Camera.PictureCallback { data, camera ->
                isPictureCaptureInProgress.set(false)
                cameraCallback?.onPictureTaken(data)
                camera.cancelAutoFocus()
                camera.startPreview()
            })
        }
    }

    override fun setDisplayOrientation(displayOrientation: Int) {
        if (mDisplayOrientation == displayOrientation) {
            return
        }
        mDisplayOrientation = displayOrientation
        if (isCameraOpened) {
            mCameraParameters?.setRotation(calcCameraRotation(displayOrientation))
            mCamera?.parameters = mCameraParameters
            val needsToStopPreview = mShowingPreview
            if (needsToStopPreview) {
                mCamera?.stopPreview()
            }
            mCamera?.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
            if (needsToStopPreview) {
                mCamera?.startPreview()
            }
        }
    }


    private fun chooseAspectRatio(): AspectRatio? {
        var r: AspectRatio? = null
        for (ratio in mPreviewSizes.ratios()) {
            r = ratio
            if (ratio == Constants.DEFAULT_ASPECT_RATIO) {
                return ratio
            }
        }
        return r
    }

    private fun adjustCameraParameters() {
        var sizes = mPreviewSizes.sizes(mAspectRatio)
        if (sizes == null) { // Not supported
            mAspectRatio = chooseAspectRatio()
            sizes = mPreviewSizes.sizes(mAspectRatio) ?: throw NullPointerException("")
        }
        val size = chooseOptimalSize(sizes) ?: Size(previewImpl.height, previewImpl.width)

        // Always re-apply camera parameters
        // Largest picture size in this ratio
        val pictureSize = mPictureSizes.sizes(mAspectRatio)?.last()
                ?: throw NullPointerException("")
        if (mShowingPreview) {
            mCamera?.stopPreview()
        }
        mCameraParameters?.let { parameters ->
            parameters.zoom = 0
            setFlashInternal(mFlash)
            setAutoFocusInternal(mAutoFocus)
            parameters.setPreviewSize(size.width, size.height)
            parameters.setPictureSize(pictureSize.width, pictureSize.height)
            parameters.setRotation(calcCameraRotation(mDisplayOrientation))
            mCamera?.parameters = mCameraParameters
            if (mShowingPreview) {
                mCamera?.startPreview()
            }
        }

    }

    private fun chooseOptimalSize(sizes: SortedSet<Size>): Size? {
        if (!previewImpl.isReady) { // Not yet laid out
            return sizes.first() // Return the smallest size
        }
        val desiredWidth: Int
        val desiredHeight: Int
        val surfaceWidth = previewImpl.width
        val surfaceHeight = previewImpl.height
        if (isLandscape(mDisplayOrientation)) {
            desiredWidth = surfaceHeight
            desiredHeight = surfaceWidth
        } else {
            desiredWidth = surfaceWidth
            desiredHeight = surfaceHeight
        }
        var result: Size? = null
        for (size in sizes) {
            // Iterate from small to large
            if (desiredWidth <= size.width && desiredHeight <= size.height) {
                return size
            }
            result = size
        }
        return result
    }

    private fun releaseCamera() {
        if (mCamera != null) {
            try {
                cameraOpenCloseLock.acquire()
                synchronized(cameraStateLock) {
                    mCamera?.release()
                    mCamera = null
                }
                cameraCallback?.onCameraClosed()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } finally {
                cameraOpenCloseLock.release()
            }

        }
    }

    private fun releaseMediaRecorder() {
        if (mediaRecorder != null) {
            if (record.getAndSet(false)) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            }
            mediaRecorder = null
        }
    }

    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     *
     * This calculation is used for orienting the preview
     *
     * Note: This is not the same calculation as the camera rotation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    private fun calcDisplayOrientation(screenOrientationDegrees: Int): Int {
        return if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360
        } else {  // back-facing
            (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360
        }
    }

    /**
     * Calculate camera rotation
     *
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     *
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    private fun calcCameraRotation(screenOrientationDegrees: Int): Int {
        return if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (mCameraInfo.orientation + screenOrientationDegrees) % 360
        } else {  // back-facing
            val landscapeFlip = if (isLandscape(screenOrientationDegrees)) 180 else 0
            (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
        }
    }

    /**
     * Test if the supplied orientation is in landscape.
     *
     * @param orientationDegrees Orientation in degrees (0,90,180,270)
     * @return True if in landscape, false if portrait
     */
    private fun isLandscape(orientationDegrees: Int): Boolean {
        return orientationDegrees == Constants.LANDSCAPE_90 || orientationDegrees == Constants.LANDSCAPE_270
    }

    /**
     * @return `true` if [.mCameraParameters] was modified.
     */
    private fun setAutoFocusInternal(autoFocus: Boolean): Boolean {
        mAutoFocus = autoFocus
        return if (isCameraOpened) {
            mCameraParameters?.let { parameters ->
                val modes = parameters.supportedFocusModes
                if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
                } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
                } else {
                    parameters.focusMode = modes[0]
                }
            }
            true
        } else {
            false
        }
    }

    /**
     * @return `true` if [.mCameraParameters] was modified.
     */
    private fun setFlashInternal(flash: Int): Boolean {
        if (isCameraOpened) {
            mCameraParameters?.let { parameters ->
                val modes = parameters.supportedFlashModes
                val mode = FLASH_MODES.get(flash)
                if (modes != null && modes.contains(mode)) {
                    parameters.flashMode = mode
                    mFlash = flash
                    return true
                }
                val currentMode = FLASH_MODES.get(mFlash)
                if (modes == null || !modes.contains(currentMode)) {
                    parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                    mFlash = Constants.FLASH_OFF
                    return true
                }
            }
            return false
        } else {
            mFlash = flash
            return false
        }
    }

}