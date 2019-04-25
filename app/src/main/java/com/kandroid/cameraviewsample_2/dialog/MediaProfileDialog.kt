package com.kandroid.cameraviewsample_2.dialog

import android.media.MediaRecorder
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.kandroid.cameraview.base.MediaProfile
import com.kandroid.cameraview.utils.MetricsUtils
import com.kandroid.cameraviewsample_2.R
import kotlinx.android.synthetic.main.fragment_media_profile.view.*

class MediaProfileDialog : DialogFragment() {
    private lateinit var mediaProfile: MediaProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_profile, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val screenWidth = MetricsUtils.getScreenWidth(activity!!)
        val screenHeight = MetricsUtils.getScreenHeight(activity!!)
        val dialogWidth = (screenWidth * 0.8).toInt()
        val dialogHeight = (screenHeight * 0.6).toInt()

        val contentView = view.profileRv

        val lp = contentView.layoutParams?.apply {
            width = dialogWidth
            height = dialogHeight
        } ?: FrameLayout.LayoutParams(dialogWidth, dialogHeight)
        contentView.layoutParams = lp

        contentView.layoutManager = LinearLayoutManager(context)
        contentView.adapter = MediaProfileRecyclerAdapter()
    }

    fun show(fragManager: FragmentManager, profile: MediaProfile) {
        mediaProfile = profile
        show(fragManager, "ProfileDialog")
    }

    inner class MediaProfileRecyclerAdapter : RecyclerView.Adapter<ProfileViewHolder>() {
        override fun getItemCount() = 6

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val itemView = LayoutInflater.from(context).inflate(R.layout.listitem_profile, parent, false)
            return ProfileViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ProfileViewHolder, pos: Int) {
            val videoCodec = when (mediaProfile.videoCodec) {
                MediaRecorder.VideoEncoder.DEFAULT -> "Default"
                MediaRecorder.VideoEncoder.H263 -> "H263"
                MediaRecorder.VideoEncoder.H264 -> "H264"
                MediaRecorder.VideoEncoder.MPEG_4_SP -> "MP4"
                MediaRecorder.VideoEncoder.VP8 -> "VP8"
                MediaRecorder.VideoEncoder.HEVC -> "HEVC"
                else -> "Default"
            }

            val item = when (pos) {
                0 -> ProfileItem("实现类", mediaProfile.cameraImpl)
                1 -> ProfileItem("码率", mediaProfile.videoBitRate.toString())
                2 -> ProfileItem("编码器", videoCodec)
                3 -> ProfileItem("分辨率高度", mediaProfile.originHeight.toString())
                4 -> ProfileItem("分辨率宽度", mediaProfile.originWidth.toString())
                5 -> ProfileItem("帧率", mediaProfile.videoFrameRate.toString())
                6 -> ProfileItem("文件格式", mediaProfile.fileFormatText)
                else -> null
            }
            item?.apply {
                holder.update(this)
            }
        }

    }

    class ProfileItem(
            val label: String,
            val value: String
    )

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelTv: TextView = itemView.findViewById(R.id.labelTv)
        private val valueTv: TextView = itemView.findViewById(R.id.valueTv)

        fun update(item: ProfileItem) {
            labelTv.text = item.label
            valueTv.text = item.value
        }
    }

}