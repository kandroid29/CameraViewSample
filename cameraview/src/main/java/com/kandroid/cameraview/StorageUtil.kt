package com.aqrlei.camera.library

import android.os.Environment
import android.support.annotation.IntDef
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author aqrlei on 2019/4/1
 */

object StorageUtil {
    const val PICTURE = 0
    const val VIDEO = 1

    @IntDef(PICTURE, VIDEO)
    @Retention(AnnotationRetention.SOURCE)
    annotation class FileType

    private fun File.existOrCreate(): File {
        if (!exists()) {
            mkdirs()
        }
        return this
    }

    fun formatStorageFile(file: File, @FileType type: Int): File {
        return if (file.canWrite()) {
            file
        } else {
            getStorageFile(type)
        }
    }

    fun getStorageFile(@FileType type: Int): File {
        val stamp = generateTimestamp()
        return if (type == PICTURE) {
            val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .existOrCreate()
            File(file, "PICTURE_$stamp.jpg")
        } else {
            val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .existOrCreate()
            File(file, "VIDEO_$stamp.mp4")
        }
    }

    private fun generateTimestamp(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.ROOT)
        return sdf.format(Date())
    }
}