package com.kandroid.cameraview

import android.media.MediaRecorder
import com.kandroid.cameraview.base.AspectRatio
import com.kandroid.cameraview.base.CameraViewImpl
import com.kandroid.cameraview.base.PreviewImpl

class DefaultCamera(cameraCallback: Callback?, previewImpl: PreviewImpl) : CameraViewImpl(cameraCallback, previewImpl) {
    override val isCameraOpened: Boolean
        get() = false

    override var facing: Int
        get() = CameraView.FACING_BACK
        set(value) {}

    override val supportedAspectRatios: Set<AspectRatio>
        get() = setOf()

    override val aspectRatio: AspectRatio?
        get() = null

    override var autoFocus: Boolean
        get() = false
        set(value) {}

    override var flash: Int
        get() = -1
        set(value) {}

    override fun isBestOption(facing: Int): Boolean {
        return false
    }

    override fun touchFocus(touchX: Int, touchY: Int) {}

    override fun release() {}

    override fun setAspectRatio(ratio: AspectRatio): Boolean {
        return false
    }

    override fun startRecord(savePath: String, quality: Quality) {}

    override fun setMediaRecorderOrientationHint(mediaRecorder: MediaRecorder) {}

    override fun getVideoSource(): Int {
        return -1
    }

    override fun stopRecord(restartPreview: Boolean): Boolean {
        return false
    }

    override fun takePicture() {}

    override fun setDisplayOrientation(displayOrientation: Int) {}
}