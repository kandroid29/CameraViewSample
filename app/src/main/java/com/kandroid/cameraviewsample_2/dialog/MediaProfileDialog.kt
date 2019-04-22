package com.kandroid.cameraviewsample_2.dialog

import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kandroid.cameraview.utils.MetricsUtils
import com.kandroid.cameraviewsample_2.R
import kotlinx.android.synthetic.main.fragment_media_profile.view.*

class MediaProfileDialog: DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_profile, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.testTv.setOnClickListener {
            val lp = view.layoutParams
            val a = 1
        }

        val contentView = view.content

        val screenWidth = MetricsUtils.getScreenWidth(activity!!)
        val screenHeight = MetricsUtils.getScreenHeight(activity!!)
        val dialogWidth = (screenWidth * 0.8).toInt()
        val dialogHeight = (screenHeight * 0.6).toInt()

        val lp = contentView.layoutParams?.apply {
            width = dialogWidth
            height = dialogHeight
        } ?: FrameLayout.LayoutParams(dialogWidth, dialogHeight)
        contentView.layoutParams = lp
    }
    
}