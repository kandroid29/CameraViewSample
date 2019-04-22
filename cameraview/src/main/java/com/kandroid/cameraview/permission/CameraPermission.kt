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

package com.kandroid.cameraview.permission

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v4.app.FragmentActivity
import android.widget.Toast
import com.kandroid.cameraview.R

/**
 * @author  aqrLei on 2018/8/16
 */
@TargetApi(Build.VERSION_CODES.M)
class CameraPermission(private val activity: FragmentActivity) : PermissionImpl() {
    companion object {
        const val REQUEST_CAMERA_PERMISSIONS = 1
        private val CAMERA_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
        )
    }

    override fun hasAllPermissionsGranted(): Boolean {
        activity.let { context ->
            CAMERA_PERMISSIONS.forEach {
                if (ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    override fun requestPermissions() {
        if (shouldShowRationale()) {
            PermissionConfirmationDialog.newInstance()
                    .show(activity.supportFragmentManager, "dialog", CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS)
        } else {
            activity.requestPermissions(CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS)
        }
    }

    override fun shouldShowRationale(): Boolean {
        for (permission in CAMERA_PERMISSIONS) {
            if (activity.shouldShowRequestPermissionRationale(permission)) {
                return true
            }
        }
        return false
    }

    override fun showMissingPermissionError(handlerTask: (() -> Unit)?) {
        handlerTask?.apply {
            invoke()
        } ?: activity.apply {
            Toast.makeText(this, R.string.request_permission, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}