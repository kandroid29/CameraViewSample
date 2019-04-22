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

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.params.StreamConfigurationMap
import com.kandroid.cameraview.base.PreviewImpl
import com.kandroid.cameraview.base.Size
import com.kandroid.cameraview.base.SizeMap
import com.kandroid.cameraview.camera2.Camera2

/**
 * @author aqrlei on 2019/3/26
 */

@TargetApi(23)
internal class Camera2Api23(callback: Callback?, preview: PreviewImpl, context: Context) :
    Camera2(callback, preview, context) {

    override fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        // Try to get hi-res output sizes
        val outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG)
        if (outputSizes != null) {
            for (size in map.getHighResolutionOutputSizes(ImageFormat.JPEG)) {
                sizes.add(Size(size.width, size.height))
            }
        }
        if (sizes.isEmpty) {
            super.collectPictureSizes(sizes, map)
        }
    }

}