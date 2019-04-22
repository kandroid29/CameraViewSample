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

package com.kandroid.cameraview.camera2

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import com.kandroid.cameraview.CameraView
import com.kandroid.cameraview.base.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author aqrlei on 2019/3/26
 */

@SuppressLint("MissingPermission")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
open class Camera2(callback: Callback?, preview: PreviewImpl, private val context: Context) :
        CameraViewImpl(callback, preview) {


    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraDeviceCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            cameraCallback?.onCameraOpened()

            previewImpl.view.post {
                runOrDelay(::startPreviewSession)
            }
        }

        override fun onClosed(camera: CameraDevice) {
            cameraCallback?.onCameraClosed()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            messageCallback("onError: " + camera.id + " (" + error + ")", CameraView.ERROR_CAMERA_OPEN_FAILED)
            Log.e(TAG, "onError: " + camera.id + " (" + error + ")")
            this@Camera2.cameraDevice = null
        }

    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            if (cameraDevice == null) {
                return
            }
            captureSession = session
            updateAutoFocus()
            updateFlash()
            updateView(mCaptureCallback)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            messageCallback("Failed to configure capture session.", CameraView.ERROR_CAPTURE_SESSION_FAILED)
            Log.e(TAG, "Failed to configure capture session.")
        }

        override fun onClosed(session: CameraCaptureSession) {
            if (captureSession != null && captureSession == session) {
                captureSession = null
            }
        }

    }

    private var mCaptureCallback: PictureCaptureCallback = object : PictureCaptureCallback() {

        override fun onPreCaptureRequired() {
            previewRequestBuilder?.let { requestBuilder ->
                requestBuilder.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                )
                setState(STATE_PRE_CAPTURE)
                try {
                    captureSession?.capture(requestBuilder.build(), this, null)
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE
                    )
                } catch (e: CameraAccessException) {
                    messageCallback(e.message, CameraView.ERROR_CAPTURE_SESSION_FAILED)
                    Log.e(TAG, "Failed to run precapture sequence.", e)
                }
            }


        }

        override fun onReady() {
            captureStillPicture()
        }

    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        reader.acquireNextImage().use { image ->
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val buffer = planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                this.cameraCallback?.onPictureTaken(data)
            }
        }
    }

    private var cameraId: String = ""

    private var cameraCharacteristics: CameraCharacteristics? = null

    private var cameraDevice: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    private var previewRequestBuilder: CaptureRequest.Builder? = null

    private var imageReader: ImageReader? = null


    private val previewSizes = SizeMap()

    private val pictureSizes = SizeMap()

    private var cameraFacing: Int = 0

    private var aspectRatio1 = Constants.DEFAULT_ASPECT_RATIO

    private var mAutoFocus: Boolean = false

    override val aspectRatio: AspectRatio?
        get() = aspectRatio1

    override var flash: Int = 0
        set(flash) {
            if (this.flash == flash) {
                return
            }
            val saved = this.flash
            field = flash
            previewRequestBuilder?.let {
                updateFlash()
                captureSession?.let {
                    try {
                        updateView(mCaptureCallback)
                    } catch (e: CameraAccessException) {
                        field = saved
                    }
                }
            }
        }

    private var mDisplayOrientation: Int = 0

    override val isCameraOpened: Boolean
        get() = cameraDevice != null

    override var facing: Int
        get() = cameraFacing
        set(facing) {
            if (cameraFacing == facing) {
                return
            }
            cameraFacing = facing
            if (isCameraOpened) {
                stop()
                start()
            }
        }

    override val supportedAspectRatios: Set<AspectRatio>
        get() = previewSizes.ratios()

    override var autoFocus: Boolean
        get() = mAutoFocus
        set(autoFocus) {
            if (mAutoFocus == autoFocus) {
                return
            }
            mAutoFocus = autoFocus
            previewRequestBuilder?.let {
                updateAutoFocus()
                captureSession?.let {
                    try {
                        updateView(mCaptureCallback)
                    } catch (e: CameraAccessException) {
                        mAutoFocus = !mAutoFocus
                    }
                }
            }
        }

    fun runOrDelay(runnable: () -> Unit) {
        if (previewImpl.isReady) {
            runnable()
        } else {
            previewImpl.setCallback(object : PreviewImpl.Callback {
                override fun onSurfaceChanged() {
                    runnable()
                }
            })
        }
    }

    final override fun start(): Boolean {
        super.start()
        if (!chooseCameraIdByFacing()) {
            return false
        }
        setUpSizes() //设置previewSizes, pictureSizes, videoSizes 和 aspectRatio
        prepareImageReader()

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500L, TimeUnit.MILLISECONDS)) {
                throw  RuntimeException("Time out waiting to lock camera opening")
            }
            cameraManager.openCamera(cameraId, cameraDeviceCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to open camera: $cameraId", e)
        }

        return true
    }

    override fun touchFocus(touchX: Int, touchY: Int) {
        previewRequestBuilder?.let { captureRequestBuilder ->
            val region =
                    getFocusRegion(captureRequestBuilder, previewImpl.width, previewImpl.height, touchX, touchY)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            try {
                if (captureSession != null) {
                    updateView(mCaptureCallback)
                    mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun release() {
        closeDevice()
        closeSession()
    }

    private fun getFocusRegion(
            requestBuilder: CaptureRequest.Builder,
            previewWidth: Int,
            previewHeight: Int,
            x: Int,
            y: Int
    ): MeteringRectangle {

        val isLand = getOrientation() == Constants.LANDSCAPE_90 || getOrientation() == Constants.LANDSCAPE_270

        val realPreviewHeight = if (isLand) previewHeight else previewWidth
        val realPreviewWidth = if (isLand) previewWidth else previewHeight
        val focusX = realPreviewWidth / pictureSize.width.toFloat() * x
        val focusY = realPreviewHeight / pictureSize.height.toFloat() * y
        val totalPicSize = requestBuilder.get(CaptureRequest.SCALER_CROP_REGION)

        val offSetX = (totalPicSize?.width() ?: realPreviewWidth-realPreviewWidth) / 2
        val offSetY = (totalPicSize?.height() ?: realPreviewHeight-realPreviewHeight) / 2
        RectF(focusY + offSetY, focusX + offSetX, focusY + 100 + offSetY, focusX + offSetX + 100)

        return MeteringRectangle(
                Rect(
                        (focusY + offSetY).toInt(),
                        (focusX + offSetX).toInt(),
                        (focusY + 100 + offSetY).toInt(),
                        (focusX + offSetX + 100).toInt()
                ), 1000
        )
    }

    override fun stop() {
        closeSession()
        try {
            cameraOpenCloseLock.acquire()
            if (cameraDevice != null) {
                cameraDevice?.close()
                cameraDevice = null
            }
            if (imageReader != null) {
                imageReader?.close()
                imageReader = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException(e.message)
        } finally {
            cameraOpenCloseLock.release()
        }
        super.stop()
    }

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        if (ratio == aspectRatio1 || !previewSizes.ratios().contains(ratio)) {
            return false
        }
        aspectRatio1 = ratio
        prepareImageReader()
        if (closeSession()) {
            startPreviewSession()
        }
        return true
    }

    override fun startRecord(savePath: String, quality: Quality) {
        if (!previewImpl.isReady) return
        if (record.getAndSet(true)) return
        mediaRecorder = mediaRecorder ?: MediaRecorder()
        mediaRecorder?.also { recorder ->
            recorder.reset()
            startTimeLocked()
            try {
                closeSession()
                setUpMediaRecorder(quality, savePath)
                previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)

                val surfaces = ArrayList<Surface?>()

                //添加TextureView的surface
                val previewSurface = previewImpl.surface
                surfaces.add(previewSurface)
                previewRequestBuilder?.addTarget(previewSurface)

                //添加MediaRecorder的surface
                val recorderSurface = recorder.surface
                surfaces.add(recorderSurface)
                previewRequestBuilder?.addTarget(recorderSurface)

                cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        previewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        updateAutoFocus()
                        updateFlash()
                        updateView(null)
                        recorder.start()
                        cameraCallback?.onRecordStart(record.get())
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        record.set(false)
                        cameraCallback?.onRecordStart(record.get())
                    }
                }, null)
            } catch (e: Exception) {
                mediaRecorder?.release()
                mediaRecorder = null
                record.set(false)
                messageCallback(e.message, CameraView.ERROR_RECORD_START_FAILED)
            }
        }
    }

    override fun getVideoSource(): Int = MediaRecorder.VideoSource.SURFACE


    override fun setMediaRecorderOrientationHint(mediaRecorder: MediaRecorder) {
        mediaRecorder.setOrientationHint(getOrientation())
    }

    private fun getOrientation(): Int {
        val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                ?: 0
        return when (sensorOrientation) {
            Constants.LANDSCAPE_90 -> {
                (360 - mDisplayOrientation + Constants.LANDSCAPE_90) % 360
            }
            Constants.LANDSCAPE_270 -> {
                Math.abs(mDisplayOrientation - Constants.LANDSCAPE_270)
            }
            else -> {
                0
            }
        }
    }

    override fun stopRecord(restartPreview: Boolean): Boolean {
        if (hitTimeoutLocked() && record.get()) {
            var success = true
            try {
                prepareToStopRecord()
                mediaRecorder?.stop()
                mediaRecorder?.reset()

            } catch (e: Exception) {
                success = false
                e.printStackTrace()
                messageCallback(e.message, CameraView.ERROR_RECORD_STOP_FAILED)
                mediaRecorder?.release()
                mediaRecorder = null
            }
            record.set(success)
            cameraCallback?.onRecordDone(success, mVideoOutputPath)
            if (cameraDevice != null && previewImpl.isReady) {
                closeSession()
                if (restartPreview) {
                    startPreviewSession()
                }
            }
        }
        return record.get()
    }

    private fun prepareToStopRecord() {
        try {
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
        } catch (e: CameraAccessException) {
            messageCallback(e.message, CameraView.ERROR_CAPTURE_SESSION_FAILED)
            e.printStackTrace()
        }
    }

    private fun closeSession(): Boolean {
        return if (captureSession != null) {
            captureSession!!.close()
            captureSession = null
            true
        } else false
    }

    private fun closeDevice(): Boolean {
        return cameraDevice?.let { camera ->
            camera.close()
            cameraDevice = null
            true
        } ?: false
    }

    override fun takePicture() {
        if (record.get()) {
            messageCallback("Being Recording ,cannot take picture", CameraView.TIPS_PICTURE_TAKE_IN_RECORDING)
            return
        }
        if (mAutoFocus) {
            lockFocus()
        } else {
            captureStillPicture()
        }
    }

    override fun setDisplayOrientation(displayOrientation: Int) {
        mDisplayOrientation = displayOrientation
        previewImpl.setDisplayOrientation(mDisplayOrientation)
    }

    /**
     *
     * Chooses a camera ID by the specified camera facing ([.cameraFacing]).
     *
     * This rewrites [.cameraId], [.cameraCharacteristics], and optionally
     * [.cameraFacing].
     */
    private fun chooseCameraIdByFacing(): Boolean {
        try {
            val internalFacing = INTERNAL_FACINGS.get(cameraFacing)
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) { // No camera
                throw RuntimeException("No camera available.")
            }
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val level = characteristics.get(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
                )
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue
                }
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                        ?: throw NullPointerException("Unexpected state: LENS_FACING null")
                if (lensFacing == internalFacing) {
                    cameraId = id
                    cameraCharacteristics = characteristics
                    return true
                }
            }
            // Not found
            cameraId = cameraIds[0]
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val level = cameraCharacteristics?.get(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
            )
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false
            }
            val lensFacing = cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)
                    ?: throw NullPointerException("Unexpected state: LENS_FACING null")

            val count = INTERNAL_FACINGS.size()
            for (i in 0 until count) {
                if (INTERNAL_FACINGS.valueAt(i) == lensFacing) {
                    cameraFacing = INTERNAL_FACINGS.keyAt(i)
                    return true
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            cameraFacing = Constants.FACING_BACK
            return true
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to get a list of camera devices", e)
        }

    }

    /**
     *
     * Collects some information from [.cameraCharacteristics].
     *
     * This rewrites [.previewSizes], [.pictureSizes], and optionally,
     * [.aspectRatio1].
     */
    private fun setUpSizes() {
        val map = cameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("Failed to get configuration map: $cameraId")
        previewSizes.clear()
        for (size in map.getOutputSizes(previewImpl.outputClass)) {
            val width = size.width
            val height = size.height
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                previewSizes.add(Size(width, height))
            }
        }

        pictureSizes.clear()
        collectPictureSizes(pictureSizes, map)
        for (ratio in previewSizes.ratios()) {
            if (!pictureSizes.ratios().contains(ratio)) {
                previewSizes.remove(ratio)
            }
        }

        videoSizes.clear()
        for (size in map.getOutputSizes(MediaRecorder::class.java)) {
            videoSizes.add(Size(size.width, size.height))
        }

        if (!previewSizes.ratios().contains(aspectRatio1)) {
            aspectRatio1 = previewSizes.ratios().iterator().next()
        }
    }

    protected open fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        for (size in map.getOutputSizes(ImageFormat.JPEG)) {
            sizes.add(Size(size.width, size.height))
        }
    }

    private val pictureSize: Size
        get() = pictureSizes.sizes(aspectRatio1)?.last() ?: throw Exception("")

    private fun prepareImageReader() {
        if (imageReader != null) {
            imageReader?.close()
        }
        val largest = pictureSize
        imageReader = ImageReader.newInstance(
                largest.width, largest.height,
                ImageFormat.JPEG, /* maxImages */ 2
        )
        imageReader?.setOnImageAvailableListener(onImageAvailableListener, null)
    }

    /**
     *
     * Starts a capture session for camera preview.
     *
     * This rewrites [.previewRequestBuilder].
     *
     * The result will be continuously processed in [.mSessionCallback].
     */
    private fun startPreviewSession() {
        if (!isCameraOpened || !previewImpl.isReady || imageReader == null) {
            return
        }

        Log.d("cameraCallback", "startPreviewSession")
//        val previewSize = chooseOptimalSize()
//        previewImpl.setBufferSize(previewSize.width, previewSize.height)
        val surface = previewImpl.surface
        try {
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(
                    Arrays.asList(surface, imageReader?.surface),
                    mSessionCallback, null
            )
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to start camera session")
        }

    }

    /**
     * Chooses the optimal preview size based on [.previewSizes] and the surface size.
     *
     * @return The picked size for camera preview.
     */
    private fun chooseOptimalSize(): Size {
        val surfaceLonger: Int
        val surfaceShorter: Int
        val surfaceWidth = previewImpl.width
        val surfaceHeight = previewImpl.height
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight
            surfaceShorter = surfaceWidth
        } else {
            surfaceLonger = surfaceWidth
            surfaceShorter = surfaceHeight
        }
        val candidates = previewSizes.sizes(aspectRatio1) ?: throw java.lang.Exception("")

        // Pick the smallest of those big enough
        for (size in candidates) {
            if (size.width >= surfaceLonger && size.height >= surfaceShorter) {
                return size
            }
        }
        // If no size is big enough, pick the largest one.
        return candidates.last()
    }

    /**
     * Updates the internal state of auto-focus to [.mAutoFocus].
     */
    private fun updateAutoFocus() {
        if (mAutoFocus) {
            val modes = cameraCharacteristics?.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
            )
            // Auto focus is not supported
            if (modes == null || modes.isEmpty() || (modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_OFF
                )
            } else {
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }
        } else {
            previewRequestBuilder?.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
            )
        }
    }

    /**
     * Updates the internal state of flash to [.mFlash].
     */
    private fun updateFlash() {
        previewRequestBuilder?.let { requestBuilder ->
            when (flash) {
                Constants.FLASH_OFF -> {
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON
                    )
                    requestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF
                    )
                }
                Constants.FLASH_ON -> {
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    )
                    requestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF
                    )
                }
                Constants.FLASH_TORCH -> {
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON
                    )
                    requestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_TORCH
                    )
                }
                Constants.FLASH_AUTO -> {
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )
                    requestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF
                    )
                }
                Constants.FLASH_RED_EYE -> {
                    requestBuilder.set(
                            CaptureRequest.CONTROL_AE_MODE,
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
                    )
                    requestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF
                    )
                }
            }
        }

    }


    private fun updateView(callback: CameraCaptureSession.CaptureCallback?) {
        cameraDevice ?: return

        previewRequestBuilder?.let { requestBuilder ->
            try {
                /*val thread = HandlerThread("CameraView")
                thread.start()*/
                captureSession?.setRepeatingRequest(requestBuilder.build(), callback, backgroundHandler)
            } catch (e: Exception) {
                messageCallback("Failed to start camera preview", CameraView.ERROR_CAPTURE_SESSION_FAILED)
            }
        }
    }

    /**
     * Locks the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        previewRequestBuilder?.let { requestBuilder ->
            requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START
            )
            try {
                mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING)
                captureSession?.capture(requestBuilder.build(), mCaptureCallback, null)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to lock focus.", e)
            }
        }


    }

    /**
     * Captures a still picture.
     */
    fun captureStillPicture() {
        cameraDevice?.let { cameraDevice ->
            try {
                val captureRequestBuilder = cameraDevice.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE
                )
                imageReader?.let { captureRequestBuilder.addTarget(it.surface) }
                captureRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)
                )
                updateCaptureStillFlash(captureRequestBuilder)
                // Calculate JPEG orientation.
                val sensorOrientation = cameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                        ?: 0
                captureRequestBuilder.set(
                        CaptureRequest.JPEG_ORIENTATION,
                        (sensorOrientation + mDisplayOrientation * (if (cameraFacing == Constants.FACING_FRONT) 1 else -1) +
                                360) % 360
                )
                // Stop preview and capture a still picture.
                captureSession?.stopRepeating()
                captureSession?.capture(
                        captureRequestBuilder.build(),
                        object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult
                            ) {
                                unlockFocus()
                            }
                        }, null
                )
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Cannot capture a still picture.", e)
            }

        }


    }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private fun unlockFocus() {
        previewRequestBuilder?.let { requestBuilder ->
            requestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL
            )
            try {
                captureSession?.capture(requestBuilder.build(), mCaptureCallback, null)
                updateAutoFocus()
                updateFlash()
                previewRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CaptureRequest.CONTROL_AF_TRIGGER_IDLE
                )
                updateView(mCaptureCallback)
                mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to restart camera preview.", e)
            }
        }
    }

    private fun updateCaptureStillFlash(captureRequestBuilder: CaptureRequest.Builder) {
        when (flash) {
            Constants.FLASH_OFF -> {
                captureRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                )
                captureRequestBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF
                )
            }
            Constants.FLASH_ON -> captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            )
            Constants.FLASH_TORCH -> {
                captureRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                )
                captureRequestBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH
                )
            }
            Constants.FLASH_AUTO -> captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
            Constants.FLASH_RED_EYE -> captureRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    /**
     * A [CameraCaptureSession.CaptureCallback] for capturing a still picture.
     */
    abstract class PictureCaptureCallback : CameraCaptureSession.CaptureCallback() {

        private var mState: Int = 0

        internal fun setState(state: Int) {
            mState = state
        }

        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest, partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest, result: TotalCaptureResult
        ) {
            process(result)
        }

        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_LOCKING -> {
                    val af = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
                    if ((af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)) {
                        val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING)
                            onReady()
                        } else {
                            setState(STATE_LOCKED)
                            onPreCaptureRequired()
                        }
                    }
                }
                STATE_PRE_CAPTURE -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if ((ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                    ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED ||
                                    ae == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                    ) {
                        setState(STATE_WAITING)
                    }
                }
                STATE_WAITING -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING)
                        onReady()
                    }
                }
            }
        }

        /**
         * Called when it is ready to take a still picture.
         */
        abstract fun onReady()

        /**
         * Called when it is necessary to run the precapture sequence.
         */
        abstract fun onPreCaptureRequired()

        companion object {

            const val STATE_PREVIEW = 0
            const val STATE_LOCKING = 1
            const val STATE_LOCKED = 2
            const val STATE_PRE_CAPTURE = 3
            const val STATE_WAITING = 4
            const val STATE_CAPTURING = 5
        }

    }

    companion object {

        private const val TAG = "Camera2"

        private val INTERNAL_FACINGS = SparseIntArray()

        init {
            INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
            INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
        }

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080
    }

}