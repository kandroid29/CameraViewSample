package com.kandroid.cameraview.base

import android.media.CamcorderProfile
import android.media.MediaRecorder

class MediaProfile(
        val fileFormat: Int,

        val audioSampleRate: Int,
        val audioChannels: Int,
        val audioBitRate: Int,
        val audioCodec: Int,

        val videoBitRate: Int,
        val videoCodec: Int,
        val originWidth: Int,
        val originHeight: Int,
        var videoFrameWidth: Int,
        var videoFrameHeight: Int,
        val videoFrameRate: Int
) {
    companion object {
        fun from(profile: CamcorderProfile): MediaProfile {
            return MediaProfile(
                    profile.fileFormat,
                    profile.audioSampleRate,
                    profile.audioChannels,
                    profile.audioBitRate,
                    profile.audioCodec,
                    profile.videoBitRate,
                    profile.videoCodec,
                    profile.videoFrameWidth,
                    profile.videoFrameHeight,
                    -1, -1,
                    profile.videoFrameRate
            )
        }
    }

    val fileFormatText: String
        get() {
            return when(fileFormat) {
                MediaRecorder.OutputFormat.DEFAULT -> "Default"
                MediaRecorder.OutputFormat.THREE_GPP -> "3GP"
                MediaRecorder.OutputFormat.MPEG_4 -> "MP4"
                else -> "Unknown"
            }
        }
}