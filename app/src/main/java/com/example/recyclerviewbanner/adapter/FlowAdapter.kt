package com.example.recyclerviewbanner.adapter

import android.content.Context
import android.media.Image
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.bumptech.glide.Glide
import com.example.recyclerviewbanner.R

/**
 * @Description:
 * @Author: zouji
 * @CreateDate: 2023/7/2 11:26
 */
class FlowAdapter(context: Context): Adapter<FlowAdapter.ViewHolder>() {

    private var mContext: Context? = null
    private var mData = ArrayList<Int>()

    init {
        mContext = context
    }

    fun setData(data: ArrayList<Int>) {
        mData.clear()
        mData = data
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.layout_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(mContext!!).load(mData[position]).into(holder.img!!)
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val img: ImageView? = itemView.findViewById(R.id.img)
    }
}