package com.example.recyclerviewbanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclerviewbanner.adapter.FlowAdapter
import java.util.*

class MainActivity : AppCompatActivity() {
    private var rvFlow: RecyclerCoverFlow? = null
    private var mAdapter: FlowAdapter? = null
    private var mTimer: Timer? = null
    private var currentItem = 0
    private val mImages = intArrayOf(R.mipmap.item1, R.mipmap.item2, R.mipmap.item3, R.mipmap.item4,R.mipmap.item5, R.mipmap.item6)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initList()
    }

    override fun onResume() {
        super.onResume()
        setTimer()
    }

    /**
     * 设置定时轮播
     */
    private fun setTimer() {
        mTimer = Timer()
        mTimer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    currentItem = rvFlow?.getCoverFlowLayoutManager()?.getCenterPosition() ?: 0
                    val nextItem = (currentItem + 1) % Int.MAX_VALUE  //无限轮播
                    rvFlow?.smoothScrollToPosition(nextItem)
                    currentItem = nextItem
                }
            }
        }, 3000, 3000)
    }

    private fun initList() {
        rvFlow = findViewById(R.id.rv_flow)
        mAdapter = FlowAdapter(this@MainActivity)
        mAdapter?.setData(mImages.toList() as ArrayList<Int>)
        rvFlow?.adapter = mAdapter
    }

    override fun onPause() {
        super.onPause()
        mTimer?.cancel()
    }

    override fun onDestroy() {
        mTimer?.let {
            it.cancel()
        }
        mTimer = null
        super.onDestroy()
    }
}