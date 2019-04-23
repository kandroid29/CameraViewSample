package com.kandroid.cameraview

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.*
import android.support.annotation.IntDef
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.kandroid.cameraview.base.*
import com.kandroid.cameraview.camera1.Camera1
import com.kandroid.cameraview.camera2.Camera2
import com.kandroid.cameraview.camera2.Camera2Api23
import java.lang.ref.WeakReference
import java.util.*

/**
 * @author aqrlei on 2019/3/26
 */
class CameraView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    companion object {
        const val FACING_BACK = Constants.FACING_BACK

        const val FACING_FRONT = Constants.FACING_FRONT

        const val FLASH_OFF = Constants.FLASH_OFF
        const val FLASH_ON = Constants.FLASH_ON
        const val FLASH_AUTO = Constants.FLASH_AUTO
        const val FLASH_TORCH = Constants.FLASH_TORCH
        const val FLASH_RED_EYE = Constants.FLASH_RED_EYE

        const val ERROR_RECORD_START_FAILED = Constants.ERROR_RECORD_START_FAILED
        const val ERROR_RECORD_STOP_FAILED = Constants.ERROR_RECORD_STOP_FAILED
        const val ERROR_CAMERA_OPEN_FAILED = Constants.ERROR_CAMERA_OPEN_FAILED
        const val ERROR_CAPTURE_SESSION_FAILED = Constants.ERROR_CAPTURE_SESSION_FAILED
        const val TIPS_PICTURE_TAKE_IN_RECORDING = Constants.TIPS_PICTURE_TAKE_IN_RECORDING
        const val TIPS_CAMERA_FEATURE_NOT_SUPPORT = Constants.TIPS_CAMERA_FEATURE_NOT_SUPPORT

        /**
         * 清晰度
         */
        const val QUALITY_LOW = "1"
        const val QUALITY_MEDIUM = "2"
        const val QUALITY_HIGH = "3"
    }


    @IntDef(FACING_BACK, FACING_FRONT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Facing

    @IntDef(FLASH_ON, FLASH_OFF, FLASH_AUTO, FLASH_TORCH, FLASH_RED_EYE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Flash

    val isSurfaceAvailable: Boolean
        get() = preview.isReady

    private var cameraImpl: CameraViewImpl

    private var adjustViewBounds: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                requestLayout()
            }
        }

    private var mDisplayOrientationDetector: DisplayOrientationDetector? = null

    private val callbacks: CallbackBridge = CallbackBridge()

    private var errorEventHandler = ErrorEventHandler(WeakReference(context))
    private val preview: PreviewImpl


    init {
        preview = createPreview(context)
//        cameraImpl = Camera1(callbacks, preview)
        cameraImpl = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> {
                Camera1(callbacks, preview)
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                Camera2(callbacks, preview, context)
            }
            else -> {
                Camera2Api23(callbacks, preview, context)
            }
        }
        context.obtainStyledAttributes(attrs, R.styleable.CameraView)?.apply {
            setFacing(getInt(R.styleable.CameraView_facing, FACING_BACK))
            setAspectRatio(
                    AspectRatio.parse(
                            getString(R.styleable.CameraView_aspectRatio)
                                    ?: Constants.DEFAULT_ASPECT_RATIO.toString()
                    )
            )
            setAutoFocus(getBoolean(R.styleable.CameraView_autoFocus, true))
            setFlash(getInt(R.styleable.CameraView_flash, FLASH_AUTO))
            adjustViewBounds = getBoolean(R.styleable.CameraView_android_adjustViewBounds, false)
            recycle()
        }
        mDisplayOrientationDetector = object : DisplayOrientationDetector(context) {
            override fun onDisplayOrientationChanged(displayOrientation: Int) {
                cameraImpl.setDisplayOrientation(displayOrientation)
            }
        }
    }

    private fun createPreview(context: Context): PreviewImpl {
        return TextureViewPreview(context, this)
    }

    private fun refreshPreview() {
        if (this.childCount > 0) {
            this.getChildAt(0).invalidate()
        }
    }

    fun setVideoQuality(quality: Int) {
        cameraImpl.videoQuality = quality
    }

    fun getVideoQuality() = cameraImpl.videoQuality

    fun getVideoProfile() = cameraImpl.camcorderProfile

    fun setFacing(@Facing facing: Int) {
        cameraImpl.facing = facing
    }

    @Facing
    fun getFacing() = cameraImpl.facing

    fun getSupportedAspectRatio() = cameraImpl.supportedAspectRatios

    private fun setAspectRatio(ratio: AspectRatio) {
        cameraImpl.setAspectRatio(ratio)
    }

    private fun getAspectRatio() = cameraImpl.aspectRatio ?: Constants.DEFAULT_ASPECT_RATIO

    private fun setAutoFocus(autoFocus: Boolean) {
        cameraImpl.autoFocus = autoFocus
    }

    private fun getAutoFocus() = cameraImpl.autoFocus

    private fun setFlash(@Flash flash: Int) {
        cameraImpl.flash = flash
    }

    @Flash
    fun getFlash() = cameraImpl.flash

    fun start() {
        refreshPreview()
        if (!cameraImpl.start()) {
            val state = onSaveInstanceState()
            cameraImpl = Camera1(callbacks, preview)
            onRestoreInstanceState(state)
            cameraImpl.start()
        }
    }

    fun stop() {
        cameraImpl.stop()
    }


    fun isCameraOpened() = cameraImpl.isCameraOpened

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun takePicture() {
        cameraImpl.takePicture()
    }

    fun recordVideo(savePath: String, quality: String) {
        val innerQuality = when (quality) {
            QUALITY_LOW -> CameraViewImpl.Quality.LOW
            QUALITY_MEDIUM -> CameraViewImpl.Quality.MEDIUM
            QUALITY_HIGH -> CameraViewImpl.Quality.HIGH
            else -> CameraViewImpl.Quality.HIGH
        }
        cameraImpl.startRecord(savePath, innerQuality)
    }

    fun stopRecord(restartPreview: Boolean) = cameraImpl.stopRecord(restartPreview)

    fun release() {
        cameraImpl.release()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            ViewCompat.getDisplay(this)?.let { display ->
                mDisplayOrientationDetector?.enable(display)
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (!isInEditMode) {
            mDisplayOrientationDetector?.disable()
        }
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isInEditMode) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        if (adjustViewBounds) {
            val width = MeasureSpec.getSize(widthMeasureSpec)

            val adjustedHeight = width * 4 / 3

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(adjustedHeight, MeasureSpec.EXACTLY))


            /*if (isCameraOpened()) {
                callbacks.reserveRequestLayoutOnOpen()
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
            val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
            if (widthMode == View.MeasureSpec.EXACTLY && heightMode != View.MeasureSpec.EXACTLY) {
                val ratio = getAspectRatio()
                var height = (View.MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat()).toInt()
                if (heightMode == View.MeasureSpec.AT_MOST) {
                    height = Math.min(height, View.MeasureSpec.getSize(heightMeasureSpec))
                }
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            } else if (widthMode != View.MeasureSpec.EXACTLY && heightMode == View.MeasureSpec.EXACTLY) {
                val ratio = getAspectRatio()
                var width = (View.MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat()).toInt()
                if (widthMode == View.MeasureSpec.AT_MOST) {
                    width = Math.min(width, View.MeasureSpec.getSize(widthMeasureSpec))
                }
                super.onMeasure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }*/

        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        /*val width = measuredWidth
        val height = measuredHeight
        var ratio: AspectRatio = getAspectRatio()
        if (mDisplayOrientationDetector?.lastKnownDisplayOrientation ?: 0 % 180 == 0) {
            ratio = ratio.inverse()
        }
        if (height < width * ratio.y / ratio.x) {
            cameraImpl.view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(width * ratio.y / ratio.x, View.MeasureSpec.EXACTLY)
            )
        } else {
            cameraImpl.view.measure(
                View.MeasureSpec.makeMeasureSpec(height * ratio.x / ratio.y, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
        }*/

    }

    var foregroundRenderer: ForegroundRenderer? = null
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    override fun onDrawForeground(canvas: Canvas?) {
        super.onDrawForeground(canvas)
        foregroundRenderer?.onDrawMask(this, canvas)
    }

    override fun onSaveInstanceState(): Parcelable? {

        val state = SavedState(super.onSaveInstanceState())
        state.apply {
            facing = getFacing()
            ratio = getAspectRatio()
            autoFocus = getAutoFocus()
            flash = getFlash()
        }
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        with(state) {
            super.onRestoreInstanceState(superState)
            setFacing(facing)
            ratio?.let {
                setAspectRatio(it)
            }
            setAutoFocus(autoFocus)
            setFlash(flash)
        }
    }

    class SavedState : View.BaseSavedState {

        @Facing
        internal var facing: Int = 0

        var ratio: AspectRatio? = null

        var autoFocus: Boolean = false

        @Flash
        internal var flash: Int = 0

        constructor(source: Parcel, loader: ClassLoader?) : super(source) {
            facing = source.readInt()
            ratio = source.readParcelable<Parcelable>(loader) as? AspectRatio
            autoFocus = source.readByte().toInt() != 0
            flash = source.readInt()
        }

        constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(facing)
            out.writeParcelable(ratio, 0)
            out.writeByte((if (autoFocus) 1 else 0).toByte())
            out.writeInt(flash)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.ClassLoaderCreator<SavedState> {

                override fun createFromParcel(source: Parcel): SavedState {
                    return createFromParcel(source, null)
                }

                override fun createFromParcel(source: Parcel, loader: ClassLoader?): SavedState {
                    return SavedState(source, loader)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }

    }

    private inner class CallbackBridge : CameraViewImpl.Callback {

        private val mCallbacks = ArrayList<Callback>()

        private var mRequestLayoutOnOpen: Boolean = false

        fun add(callback: Callback) {
            mCallbacks.add(callback)
        }

        fun remove(callback: Callback) {
            mCallbacks.remove(callback)
        }

        override fun onCameraOpened() {
            if (mRequestLayoutOnOpen) {
                mRequestLayoutOnOpen = false
                (this@CameraView.context as? Activity)?.let {
                    it.runOnUiThread { requestLayout() }
                }
            }
            for (callback in mCallbacks) {
                callback.onCameraOpened(this@CameraView)
            }
        }

        override fun onCameraClosed() {
            for (callback in mCallbacks) {
                callback.onCameraClosed(this@CameraView)
            }
        }

        override fun onPictureTaken(data: ByteArray) {
            for (callback in mCallbacks) {
                callback.onPictureTaken(this@CameraView, data)
            }
        }

        override fun onRecordStart(success: Boolean) {
            for (callback in mCallbacks) {
                callback.onRecordStart(this@CameraView, success)
            }
        }

        override fun onRecordDone(success: Boolean, savePath: String) {
            for (callback in mCallbacks) {
                callback.onRecordDone(this@CameraView, success, savePath)
            }
        }

        override fun onError(errorMessage: String, errorCode: Int) {
            var needTransfer = true
            for (callback in mCallbacks) {
                needTransfer = !callback.onError(this@CameraView, errorMessage, errorCode)
                if (!needTransfer) break
            }
            if (needTransfer) {
                errorEventHandler.sendMessage(Message.obtain(errorEventHandler, errorCode).apply {
                    obj = errorMessage
                })
            }
        }

        fun reserveRequestLayoutOnOpen() {
            mRequestLayoutOnOpen = true
        }
    }

    class ErrorEventHandler(private val reference: WeakReference<Context>) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            reference.get()?.let { context ->
                Toast.makeText(context, msg?.obj.toString(), Toast.LENGTH_SHORT).show()
            }
            super.handleMessage(msg)
        }
    }

    abstract class Callback {

        /**
         * Called when camera is opened.
         *
         * @param cameraView The associated [CameraView].
         */
        open fun onCameraOpened(cameraView: CameraView) {}

        /**
         * Called when camera is closed.
         *
         * @param cameraView The associated [CameraView].
         */
        open fun onCameraClosed(cameraView: CameraView) {}

        /**
         * Called when a picture is taken.
         *
         * @param cameraView The associated [CameraView].
         * @param data       JPEG data.
         */
        open fun onPictureTaken(cameraView: CameraView, data: ByteArray) {}

        open fun onRecordStart(cameraView: CameraView, success: Boolean) {}

        open fun onRecordDone(cameraView: CameraView, success: Boolean, savePath: String) {}

        open fun onError(cameraView: CameraView, errorMessage: String, errorCode: Int): Boolean {
            return false
        }
    }


}