package com.kandroid.cameraview

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
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

        fun getCamera2Support(context: Context, facing: Int): Boolean {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val innerFacing = when (facing) {
                FACING_BACK -> CameraCharacteristics.LENS_FACING_BACK
                FACING_FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                else -> return false
            }

            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (lensFacing == innerFacing) {
                    val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    return hardwareLevel != null && hardwareLevel != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
                }
            }
            return false
        }

    }


    @IntDef(FACING_BACK, FACING_FRONT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Facing

    @IntDef(FLASH_ON, FLASH_OFF, FLASH_AUTO, FLASH_TORCH, FLASH_RED_EYE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Flash

    var cameraFacing: Int = FACING_BACK
    private lateinit var aspectRatio: AspectRatio
    private var autoFocus: Boolean = false
    private var flash: Int = -1
    var quality: Int = -1

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
        cameraImpl = DefaultCamera(callbacks, preview)
        context.obtainStyledAttributes(attrs, R.styleable.CameraView)?.apply {
            cameraFacing = getInt(R.styleable.CameraView_facing, FACING_BACK)
            aspectRatio = AspectRatio.parse(
                    getString(R.styleable.CameraView_aspectRatio)
                            ?: Constants.DEFAULT_ASPECT_RATIO.toString()
            )
            autoFocus = getBoolean(R.styleable.CameraView_autoFocus, false)
            flash = getInt(R.styleable.CameraView_flash, FLASH_AUTO)
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
        this.quality = quality
        cameraImpl.videoQuality = quality
    }

    fun getVideoQuality() = quality

    fun getVideoQualityText() = cameraImpl.videoQualityText

    fun getVideoProfile() = cameraImpl.mediaProfile

    fun setFacing(@Facing facing: Int) {
        if (cameraFacing != facing) {
            cameraFacing = facing
            cameraImpl.stop()
            if (!cameraImpl.isBestOption(cameraFacing)) {
                chooseCameraImpl()
            }
            cameraImpl.facing = cameraFacing
            cameraImpl.start()
        }
    }

    fun changeAPI(cameraAPI: String) {
        cameraImpl.stop()

        cameraImpl = when (cameraAPI) {
            "Camera1" -> {
                if (cameraImpl !is Camera1) {
                    Camera1(context, callbacks, preview)
                } else cameraImpl
            }
            "Camera2" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    if (cameraImpl !is Camera2) Camera2(callbacks, preview, context) else cameraImpl
                } else {
                    if (cameraImpl !is Camera2Api23) Camera2Api23(callbacks, preview, context) else cameraImpl
                }
            }
            "Default" -> {
                when {
                    !getCamera2Support(context, cameraFacing) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> {
                        Camera1(context, callbacks, preview)
                    }
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                        Camera2(callbacks, preview, context)
                    }
                    else -> {
                        Camera2Api23(callbacks, preview, context)
                    }
                }
            }
            else -> {
                cameraImpl
            }
        }

        cameraImpl.facing = cameraFacing
        cameraImpl.setAspectRatio(aspectRatio)
        cameraImpl.autoFocus = autoFocus
        cameraImpl.flash = flash
        cameraImpl.videoQuality = quality

        cameraImpl.start()
    }

    @Facing
    fun getFacing() = cameraImpl.facing

    fun getSupportedAspectRatio() = cameraImpl.supportedAspectRatios

    private fun setAspectRatio(ratio: AspectRatio) {
        aspectRatio = ratio
        cameraImpl.setAspectRatio(ratio)
    }

    private fun getAspectRatio() = cameraImpl.aspectRatio ?: Constants.DEFAULT_ASPECT_RATIO

    private fun setAutoFocus(autoFocus: Boolean) {
        this.autoFocus = autoFocus
        cameraImpl.autoFocus = autoFocus
    }

    private fun getAutoFocus() = cameraImpl.autoFocus

    private fun setFlash(@Flash flash: Int) {
        this.flash = flash
        cameraImpl.flash = flash
    }

    @Flash
    fun getFlash() = cameraImpl.flash

    fun start() {
        refreshPreview()
        if (!cameraImpl.isBestOption(cameraFacing)) {
            chooseCameraImpl()
        }
        cameraImpl.start()
    }

    private fun chooseCameraImpl() {
        cameraImpl = when {
            !getCamera2Support(context, cameraFacing) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> {
                Camera1(context, callbacks, preview)
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                Camera2(callbacks, preview, context)
            }
            else -> {
                Camera2Api23(callbacks, preview, context)
            }
        }
        cameraImpl.facing = cameraFacing
        cameraImpl.setAspectRatio(aspectRatio)
        cameraImpl.autoFocus = autoFocus
        cameraImpl.flash = flash
        cameraImpl.videoQuality = quality
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

        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

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