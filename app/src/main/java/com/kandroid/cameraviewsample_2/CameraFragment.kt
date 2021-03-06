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

package com.kandroid.cameraviewsample_2


import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.CamcorderProfile
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.aqrlei.camera.library.StorageUtil
import com.kandroid.cameraview.CameraView
import com.kandroid.cameraview.DefaultForegroundRenderer
import com.kandroid.cameraview.permission.CameraPermission
import com.kandroid.cameraviewsample_2.dialog.MediaProfileDialog
import com.kandroid.cameraviewsample_2.widget.BottomOptionSheet
import kotlinx.android.synthetic.main.fragment_camera_view.*
import java.io.FileOutputStream
import java.util.*

/**
 * @author  aqrLei on 2018/8/13
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraFragment : Fragment(), View.OnClickListener {
    companion object {
        val CLARITY_OPTION_NAMES = listOf("Low", "High", "QCIF", "CIF", "480P", "720P", "1080P", "QVGA", "2160P")
        val CLARITY_OPTION_VALUES = listOf(
                CamcorderProfile.QUALITY_LOW, CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_QCIF, CamcorderProfile.QUALITY_CIF,
                CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_1080P, CamcorderProfile.QUALITY_QVGA, CamcorderProfile.QUALITY_2160P
        )

        fun newInstance() = CameraFragment()
    }

    private var timer: Timer? = null
    private var countDownSeconds: Int = 0
    private var isRecording = false
    private var cameraAPI: String = "Default"
    private val cameraAPIOptions: List<String> by lazy { createAPIOptions() }

    private val profileDialog: MediaProfileDialog by lazy { MediaProfileDialog() }

    private val cameraCallback = object : CameraView.Callback() {
        override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
            val thread = HandlerThread("PictureSave")
            thread.start()
            val saveFile = StorageUtil.getStorageFile(StorageUtil.PICTURE)
            BitmapFactory.decodeByteArray(data, 0, data.size)
                    .compress(Bitmap.CompressFormat.JPEG, 100, FileOutputStream(saveFile))

            ExifInterface(saveFile.absolutePath).run {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            super.onPictureTaken(cameraView, data)
        }

        override fun onRecordStart(cameraView: CameraView, success: Boolean) {
            super.onRecordStart(cameraView, success)
            isRecording = success
        }

        override fun onRecordDone(cameraView: CameraView, success: Boolean, savePath: String) {
            super.onRecordDone(cameraView, success, savePath)
            Toast.makeText(this@CameraFragment.context, "RecordStart: $savePath", Toast.LENGTH_LONG).show()
            Log.d("CameraTest", "RecordStart: $savePath")
        }

    }

    private lateinit var cameraPermission: CameraPermission

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListener()
        cameraPermission = CameraPermission(this.activity!!)

        //setup video recording quality
        cameraView.quality = CamcorderProfile.QUALITY_720P
        val videoQuality = cameraView.getVideoQuality()
        clarityTv.text = when (videoQuality) {
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
        cameraView.foregroundRenderer = DefaultForegroundRenderer()
    }

    override fun onResume() {
        super.onResume()
        if (!cameraPermission.hasAllPermissionsGranted()) {
            cameraPermission.requestPermissions()
        } else {
            cameraAPITv.text = "Default"
            cameraView.start()
        }
    }

    private fun createAPIOptions(): List<String> {
        return when {
            !CameraView.getCamera2Support(context!!, cameraView.cameraFacing) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP -> {
                listOf("Camera1", "Default")
            }
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                listOf("Camera1", "Camera2", "Default")
            }
            else -> {
                listOf("Camera1", "Camera2", "Default")
            }
        }
    }

    private fun initListener() {
        takePicture.setOnClickListener(this)
        stopRecord.setOnClickListener(this)
        switchCamera.setOnClickListener(this)
        videoProfileTv.setOnClickListener(this)
        clarityTv.setOnClickListener(this)
        cameraAPITv.setOnClickListener(this)
        cameraView.addCallback(cameraCallback)
    }

    private fun setCameraAPI(api: String) {
        cameraAPI = api
        cameraAPITv.text = cameraAPI
        cameraView.changeAPI(cameraAPI)
    }

    override fun onPause() {
        cameraView.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CameraPermission.REQUEST_CAMERA_PERMISSIONS) {
            var grant = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    cameraPermission.showMissingPermissionError()
                    grant = false
                    break
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.videoProfileTv -> {
                profileDialog.show(childFragmentManager, cameraView.getVideoProfile())
            }
            R.id.takePicture -> {
                if (!isRecording) {
                    val file = StorageUtil.getStorageFile(StorageUtil.VIDEO, "_${cameraView.getVideoQualityText()}")
                    cameraView.recordVideo(file.absolutePath, CameraView.QUALITY_HIGH)
                    Toast.makeText(context, "开始录制", Toast.LENGTH_LONG).show()

                    countDownSeconds = 6
                    timer = Timer().also { t ->
                        t.schedule(object : TimerTask() {
                            override fun run() {
                                if (countDownSeconds > 0) {
                                    activity!!.runOnUiThread {
                                        countDownTv.visibility = View.VISIBLE
                                        countDownTv.text = "${countDownSeconds}s"
                                        countDownSeconds--
                                    }
                                } else {
                                    activity!!.runOnUiThread {
                                        countDownTv.visibility = View.GONE
                                        cameraView.stopRecord(true)
                                        isRecording = false
                                        stopRecord.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                                            override fun onHidden(fab: FloatingActionButton?) {
                                                takePicture.show()
                                            }
                                        })
                                    }


                                    t.cancel()
                                    timer = null
                                }
                            }
                        }, 0, 1000L)
                    }
                    takePicture.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                        override fun onHidden(fab: FloatingActionButton?) {
                            stopRecord.show()
                        }
                    })
                }
            }
            R.id.stopRecord -> {
                cameraView.stopRecord(true)
                isRecording = false
                stopRecord.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                    override fun onHidden(fab: FloatingActionButton?) {
                        takePicture.show()
                    }
                })
            }
            R.id.switchCamera -> {
                cameraView.setFacing(
                        if (cameraView.getFacing() == CameraView.FACING_FRONT) CameraView.FACING_BACK else CameraView.FACING_FRONT
                )
            }
            R.id.clarityTv -> {
                BottomOptionSheet.showPop(context!!, CLARITY_OPTION_NAMES) { pos ->
                    setClarity(CLARITY_OPTION_VALUES[pos])
                }
            }
            R.id.cameraAPITv -> {
                BottomOptionSheet.showPop(context!!, cameraAPIOptions) { pos ->
                    setCameraAPI(cameraAPIOptions[pos])
                }
            }
        }
    }

    private fun setClarity(clarity: Int) {
        cameraView.setVideoQuality(clarity)
        clarityTv.text = when (clarity) {
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
    }
}