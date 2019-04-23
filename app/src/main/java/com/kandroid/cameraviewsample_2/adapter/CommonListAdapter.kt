package com.kandroid.cameraviewsample_2.adapter

import android.content.Context
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

/**
 * @author fxYan
 */
abstract class CommonListAdapter<T>(
        private var context: Context,
        @LayoutRes private var resId: Int,
        private var data: List<T>
) : BaseAdapter() {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(resId, parent, false)
        bindData(view, position, getItem(position))
        return view
    }

    override fun getItem(position: Int): T = data[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = data.size

    abstract fun bindData(view: View, position: Int, data: T)

}