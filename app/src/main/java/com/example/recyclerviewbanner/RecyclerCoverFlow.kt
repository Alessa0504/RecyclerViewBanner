package com.example.recyclerviewbanner

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.example.recyclerviewbanner.manager.CoverFlowLayoutManger

/**
 * @Description:
 * @Author: zouji
 * @CreateDate: 2023/7/1 12:58
 */
class RecyclerCoverFlow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    //布局器构建者
    private var mManagerBuilder: CoverFlowLayoutManger.Builder? = null

    init {
        setup()
    }

    /**
     * 初始化RecyclerView
     */
    private fun setup() {
        createManageBuilder()
        layoutManager = mManagerBuilder?.build()
        overScrollMode = OVER_SCROLL_NEVER
    }

    /**
     * 创建布局构建器
     */
    private fun createManageBuilder() {
        if (mManagerBuilder == null) {
            mManagerBuilder = CoverFlowLayoutManger.Builder()
        }
    }

    override fun isChildrenDrawingOrderEnabled(): Boolean {
        return true   //开启重新排序，才会调用getChildDrawingOrder。setChildrenDrawingCacheEnabled已弃用
    }

    /**
     * 用于控制子视图之间的绘制顺序。如果您需要自定义子视图的绘制顺序，则可以重写该方法
     * 在默认情况下，子视图的绘制顺序与它们添加到RecyclerView中的顺序相同。返回值必须是子视图的索引
     * @param childCount
     * @param i
     * @return
     */
    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        val centerPos = getCoverFlowLayoutManager().getCenterPosition()   //center item的position
        val itemPos = getCoverFlowLayoutManager().getChildActualPos(i)   //获取 RecyclerView 中第i个子view的位置
        val dist = itemPos - centerPos  //距离中间item的间隔数
        //为了让中间的item绘制在最上面的图层，也就得让它处于最后绘制的item
        //当item处于center item左边时，center item是下一个绘制的所以会在item上方，可以正序绘制
        //当item处于center item右边时，center item是上一个绘制的所以会在item下方，必须逆序绘制
        var order = if (dist < 0) {  // [< 0] 说明 item 位于中间 item 左边，按循序绘制即可
            i
        } else {   // [>= 0] 说明 item 位于中间 item 右边，需要将顺序颠倒绘制
            childCount - 1 - dist
        }
        //处理越界
        if (order < 0) {
            order = 0
        } else if (order > childCount - 1) {
            order = childCount - 1
        }
        return order
    }

    /**
     * 获取LayoutManager
     */
    fun getCoverFlowLayoutManager(): CoverFlowLayoutManger {
        return layoutManager as CoverFlowLayoutManger
    }

    /**
     * touch事件无响应
     * @param ev
     * @return
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return false
    }
}