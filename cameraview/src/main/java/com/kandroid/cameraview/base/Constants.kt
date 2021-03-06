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

/**
 * @author aqrlei on 2019/3/26
 */
object Constants {
    val DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3)

    const val FACING_BACK = 1
    const val FACING_FRONT = 0

    const val FLASH_OFF = 0
    const val FLASH_ON = 1
    const val FLASH_TORCH = 2
    const val FLASH_AUTO = 3
    const val FLASH_RED_EYE = 4

    const val LANDSCAPE_90 = 90
    const val LANDSCAPE_270 = 270

    const val ERROR_CAMERA_OPEN_FAILED = 0X00
    const val ERROR_RECORD_START_FAILED = 0X10
    const val ERROR_RECORD_STOP_FAILED = 0X20
    const val ERROR_CAPTURE_SESSION_FAILED = 0X30


    const val TIPS_PICTURE_TAKE_IN_RECORDING = 0X01
    const val TIPS_CAMERA_FEATURE_NOT_SUPPORT = 0X11


}