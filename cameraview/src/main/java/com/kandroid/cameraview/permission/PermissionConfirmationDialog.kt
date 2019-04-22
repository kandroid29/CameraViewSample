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

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import com.kandroid.cameraview.R


/**
 * @author  aqrLei on 2018/8/14
 */
class PermissionConfirmationDialog : DialogFragment() {
    companion object {
        fun newInstance() = PermissionConfirmationDialog()
    }

    private lateinit var permissions: Array<String>
    private var reqCode: Int = 0
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parent = parentFragment
        return AlertDialog.Builder(activity)
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parent?.requestPermissions(permissions, reqCode)

                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    parent?.activity?.finish()

                }
                .create()
    }

    fun show(manager: FragmentManager?, tag: String?, permissions: Array<String>, reqCode: Int) {
        this.permissions = permissions
        this.reqCode = reqCode
        super.show(manager, tag)
    }
}