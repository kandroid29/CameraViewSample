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

import android.annotation.TargetApi
import android.os.Build
import android.util.ArrayMap
import java.util.*

/**
 * @author aqrlei on 2019/3/26
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
class SizeMap {

    private val mRatios = ArrayMap<AspectRatio, SortedSet<Size>>()

    val isEmpty: Boolean
        get() = mRatios.isEmpty()

    /**
     * Add a new [Size] to this collection.
     *
     * @param size The size to add.
     * @return `true` if it is added, `false` if it already exists and is not added.
     */
    fun add(size: Size): Boolean {
        for (ratio in mRatios.keys) {
            if (ratio.matches(size)) {
                val sizes = mRatios[ratio]
                return sizes?.let {
                    if (sizes.contains(size)) {
                        false
                    } else {
                        sizes.add(size)
                        true
                    }
                } ?: false
            }
        }
        // None of the existing ratio matches the provided size; add a new key
        val sizes = TreeSet<Size>()
        sizes.add(size)
        mRatios[AspectRatio.of(size.width, size.height)] = sizes
        return true
    }

    /**
     * Removes the specified aspect ratio and all sizes associated with it.
     *
     * @param ratio The aspect ratio to be removed.
     */
    fun remove(ratio: AspectRatio?) {
        mRatios.remove(ratio)
    }

    fun ratios(): Set<AspectRatio> {
        return mRatios.keys
    }

    fun sizes(ratio: AspectRatio?): SortedSet<Size>? {
        return mRatios[ratio]
    }

    fun clear() {
        mRatios.clear()
    }
}