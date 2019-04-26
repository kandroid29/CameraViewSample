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

import android.app.Activity
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.annotation.CallSuper
import android.view.View
import com.kandroid.cameraview.utils.MetricsUtils
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author aqrlei on 2019/3/26
 */
abstract class CameraViewImpl(protected val cameraCallback: Callback?, protected val previewImpl: PreviewImpl) {

    companion object {
        private const val PRE_CAPTURE_TIMEOUT_MS = 1000

        const val UNKNOWN_ERROR = "UnknownError"
    }

    protected var mVideoOrientation: Int = 0
    protected var mVideoOutputPath: String = ""
    private var captureTimer: Long = 0
    protected val record = AtomicBoolean(false)
    protected val videoSizes = SizeMap()
    protected var mediaRecorder: MediaRecorder? = null

    protected val cameraStateLock = Any()
    private var backgroundThread: HandlerThread? = null
    protected var backgroundHandler: Handler? = null
    protected val cameraOpenCloseLock = Semaphore(1)

    private var innerQuality: Quality = Quality.HIGH
    var videoQuality: Int = CamcorderProfile.QUALITY_720P

    val videoQualityText: String
        get() = when (videoQuality) {
            CamcorderProfile.QUALITY_LOW -> "Low"
            CamcorderProfile.QUALITY_HIGH -> "High"
            CamcorderProfile.QUALITY_QCIF -> "QCIF"
            CamcorderProfile.QUALITY_CIF -> "CIF"
            CamcorderProfile.QUALITY_480P -> "480P"
            CamcorderProfile.QUALITY_720P -> "720P"
            CamcorderProfile.QUALITY_1080P -> "1080P"
            CamcorderProfile.QUALITY_QVGA -> "QVGA"
            CamcorderProfile.QUALITY_2160P -> "2160P"
            else -> "Unknown"
        }

    val mediaProfile: MediaProfile
        get() = getCamcorderProfile(videoQuality)

    val view: View
        get() = previewImpl.view

    abstract val isCameraOpened: Boolean

    abstract var facing: Int

    abstract val supportedAspectRatios: Set<AspectRatio>

    abstract val aspectRatio: AspectRatio?

    abstract var autoFocus: Boolean

    abstract var flash: Int

    abstract fun isBestOption(facing: Int): Boolean

    /**
     * @return `true` if the implementation was able to start the camera session.
     */
    @CallSuper
    open fun start(): Boolean {
        startBackgroundThread()
        previewImpl.view.setOnTouchListener { _, event ->
            touchFocus(event.x.toInt(), event.y.toInt())
            true
        }
        return false
    }


    abstract fun touchFocus(touchX: Int, touchY: Int)

    abstract fun release()

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        synchronized(cameraStateLock) {
            backgroundHandler = Handler(backgroundThread?.looper)
        }
    }

    private fun stopBackgroundThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            backgroundThread?.quitSafely()
        } else {
            backgroundThread?.quit()
        }
        try {
            backgroundThread?.join()
            backgroundThread = null
            synchronized(cameraStateLock) {
                backgroundHandler = null
            }

        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @CallSuper
    open fun stop() {
        stopBackgroundThread()
    }

    /**
     * @return `true` if the aspect ratio was changed.
     */
    abstract fun setAspectRatio(ratio: AspectRatio): Boolean

    //TODO prepareForRecord
    /**
     * @param savePath
     * @param quality
     */
    abstract fun startRecord(savePath: String, quality: Quality)

    protected fun setUpMediaRecorder(quality: Quality, savePath: String) {
        mediaRecorder?.also { recorder ->
            innerQuality = quality
            mediaProfile.also { profile ->
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)

                    recorder.setVideoSource(getVideoSource())
                    recorder.setOutputFormat(profile.fileFormat)
                    mVideoOutputPath = savePath
                    recorder.setOutputFile(mVideoOutputPath)

                    recorder.setVideoEncodingBitRate(profile.videoBitRate)
                    recorder.setVideoFrameRate(profile.videoFrameRate)

                    recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight)
                    recorder.setVideoEncoder(profile.videoCodec)

                    recorder.setAudioSamplingRate(profile.audioSampleRate)
                    recorder.setAudioChannels(profile.audioChannels)
                    recorder.setAudioEncodingBitRate(profile.audioBitRate)
                    recorder.setAudioEncoder(profile.audioCodec)
                    setMediaRecorderOrientationHint(recorder)
                    recorder.prepare()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
    }

    abstract fun setMediaRecorderOrientationHint(mediaRecorder: MediaRecorder)

    private fun getCamcorderProfile(quality: Int): MediaProfile {
        val profile = try {
            if (CamcorderProfile.hasProfile(quality)) {
                CamcorderProfile.get(quality)
            } else {
                CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
            }
        } catch (ex: Exception) {
            CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        }

        val mediaProfile = MediaProfile.from(profile)
        mediaProfile.cameraImpl = this.javaClass.simpleName
        val tempSizes = videoSizes.sizes(AspectRatio.of(4, 3))

        tempSizes?.also { sizes ->
            val size = try {
                val activity = previewImpl.view.context as Activity
                val screenWidth = MetricsUtils.getScreenWidth(activity)
                sizes.last { it.height <= screenWidth }
            } catch (ex: Exception) {
                sizes.first()
            }

            mediaProfile.videoFrameWidth = size.width
            mediaProfile.videoFrameHeight = size.height
        }

        return mediaProfile
    }

    abstract fun getVideoSource(): Int

    abstract fun stopRecord(restartPreview: Boolean): Boolean

    protected fun startTimeLocked() {
        captureTimer = SystemClock.elapsedRealtime()
    }

    protected fun hitTimeoutLocked(): Boolean {
        return (SystemClock.elapsedRealtime() - captureTimer) > PRE_CAPTURE_TIMEOUT_MS
    }

    abstract fun takePicture()

    abstract fun setDisplayOrientation(displayOrientation: Int)

    protected fun messageCallback(message: String?, code: Int) {
        cameraCallback?.onError(message ?: UNKNOWN_ERROR, code)
    }

    interface Callback {

        fun onCameraOpened()

        fun onCameraClosed()

        fun onPictureTaken(data: ByteArray)

        fun onRecordStart(success: Boolean)

        fun onRecordDone(success: Boolean, savePath: String)

        fun onError(errorMessage: String, errorCode: Int)
    }

    enum class Quality {
        LOW, MEDIUM, HIGH
    }
}