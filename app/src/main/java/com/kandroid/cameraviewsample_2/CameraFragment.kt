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
import kotlinx.android.synthetic.main.fragment_camera_view.*
import java.io.FileOutputStream

/**
 * @author  aqrLei on 2018/8/13
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CameraFragment : Fragment(), View.OnClickListener {
    companion object {
        fun newInstance() = CameraFragment()
    }

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

    private lateinit var mCameraPermission: CameraPermission

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initListener()
        mCameraPermission = CameraPermission(this.activity!!)
        cameraView.foregroundRenderer = DefaultForegroundRenderer()
    }

    override fun onResume() {
        super.onResume()
        if (!mCameraPermission.hasAllPermissionsGranted()) {
            mCameraPermission.requestPermissions()
        } else {
            cameraView.start()
        }
    }

    private var isRecording = false
    private fun initListener() {
        takePicture.setOnClickListener(this)
        stopRecord.setOnClickListener(this)
        switchCamera.setOnClickListener(this)
        videoProfileTv.setOnClickListener(this)
        cameraView.addCallback(cameraCallback)
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
                    mCameraPermission.showMissingPermissionError()
                    grant = false
                    break
                }
            }
            /*if (grant) {
                cameraView.start()
            }*/
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.videoProfileTv -> {
                profileDialog.show(childFragmentManager, "ProfileDialog")
            }
            R.id.takePicture -> {
                if (!isRecording) {
                    val file = StorageUtil.getStorageFile(StorageUtil.VIDEO)
                    cameraView.recordVideo(file.absolutePath, CameraView.QUALITY_HIGH)
                    Toast.makeText(context, "开始录制", Toast.LENGTH_LONG).show()
                    takePicture.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                        override fun onHidden(fab: FloatingActionButton?) {
                            stopRecord.show()
                        }
                    })
                    // camera.takePicture()
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

        }
    }
}