package com.kandroid.cameraviewsample_2.widget

import android.content.Context
import android.graphics.Color
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialog
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.FrameLayout
import com.kandroid.cameraviewsample_2.R
import com.kandroid.cameraviewsample_2.adapter.CommonListAdapter
import kotlinx.android.synthetic.main.layout_bottom_pop.view.*
import kotlinx.android.synthetic.main.listitem_bottom_pop.view.*

/**
 * @author: Kandroid
 * @description: 底部选项弹窗（基于BottomSheetDialog）
 */
class BottomOptionSheet private constructor(context: Context) : BottomSheetDialog(context) {
    companion object {
        private val instanceCache = HashMap<Context, BottomOptionSheet>()

        @JvmStatic
        fun showPop(
                context: Context,
                data: List<String>,
                itemClick: (Int) -> Unit,
                textColor: String? = null,
                headerView: View? = null
        ) {
            val view: BottomOptionSheet = instanceCache[context]
                    ?: putInCache(context, BottomOptionSheet(context))
            view.show(data, AdapterView.OnItemClickListener { _, _, pos, _ ->
                view.dismiss()
                itemClick(pos)
            }, textColor, headerView)
        }

        @JvmStatic
        fun showPop(context: Context, data: List<String>, itemClick: (Int) -> Unit) {
            showPop(context, data, itemClick, null, null)
        }

        @JvmStatic
        fun release(context: Context) {
            instanceCache[context]?.apply {
                instanceCache.remove(context)
            }
        }

        private fun putInCache(context: Context, instance: BottomOptionSheet): BottomOptionSheet {
            instanceCache[context] = instance
            return instance
        }
    }

    //    private val dialog: BottomSheetDialog = BottomSheetDialog(context)
    private val contentView: View = LayoutInflater.from(context).inflate(R.layout.layout_bottom_pop, null)
    private var backgroundView: View?

    private val optionData = ArrayList<String>()
    private val listAdapter = ChoiceListAdapter(context, optionData)
    private var headerView: View? = null

    private val onCancel = OnCancelListener()

    init {
        contentView.cancelTv.setOnClickListener(onCancel)
        contentView.outsideLl.setOnClickListener(onCancel)
        contentView.bottomPopLv.adapter = listAdapter
        setContentView(contentView)
        backgroundView = findViewById<FrameLayout>(R.id.design_bottom_sheet)
        disableCancelOnDragDown()
        backgroundView?.setBackgroundColor(Color.TRANSPARENT)
    }

    private val baseContext: Context
        get() = (context as? ContextThemeWrapper)?.baseContext ?: context

    private fun disableCancelOnDragDown() {
        setOnShowListener {
            val behavior = BottomSheetBehavior.from(backgroundView)
            behavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        dismiss()
                    }

                    if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

            })
        }
    }


    fun show(data: List<String>, listener: AdapterView.OnItemClickListener, textColor: String? = null, hView: View? = null) {
        with(contentView.bottomPopLv) {
            this.onItemClickListener = listener
            listAdapter.itemTextColor = textColor
            hView?.apply {
                headerView?.apply {
                    removeHeaderView(this)
                }
                addHeaderView(this)
            }
            optionData.clear()
            optionData.addAll(data)
            this.adapter = listAdapter
        }

        listAdapter.notifyDataSetChanged()
        show()
    }

    fun show(data: List<String>, listener: AdapterView.OnItemClickListener) {
        show(data, listener, null, null)
    }

    override fun dismiss() {
        BottomOptionSheet.release(baseContext)
        super.dismiss()
    }

    inner class OnCancelListener : View.OnClickListener {
        override fun onClick(v: View?) {
            dismiss()
        }
    }

    class ChoiceListAdapter(
            context: Context,
            data: List<String>,
            var itemTextColor: String? = null
    ) : CommonListAdapter<String>(context, R.layout.listitem_bottom_pop, data) {

        override fun bindData(view: View, position: Int, data: String) {
            with(view) {
                if (itemTextColor != null) {
                    bottomPopTv.setTextColor(Color.parseColor(itemTextColor))
                }
                bottomPopTv.text = data
                if (position == 0) {
                    topDivider.visibility = View.VISIBLE
                } else {
                    topDivider.visibility = View.GONE
                }
            }
        }
    }
}
