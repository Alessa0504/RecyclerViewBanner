package com.example.recyclerviewbanner.manager

import android.animation.ValueAnimator
import android.graphics.Rect
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * @Description:
 * @Author: zouji
 * @CreateDate: 2023/7/1 13:00
 */
class CoverFlowLayoutManger(cstInterval: Float) : RecyclerView.LayoutManager() {

    companion object {
        //最大存储item信息存储数量，超过设置数量，则动态计算来获取
        private const val MAX_RECT_COUNT = 100
    }

    //item 向左移动
    private var leftItemNum = 2

    //item 向右移动
    private var rightItemNum = 1

    //是否右单边轮播
    private var rightOnly = false

    //滑动总偏移量
    private var mOffsetAll: Int = 0

    //Item宽
    private var mDecoratedChildWidth: Int = 0

    //Item高
    private var mDecoratedChildHeight: Int = 0

    //Item间隔与item宽的比例
    private var mIntervalRatio: Float = 0.3f

    //起始ItemX坐标
    private var mStartX: Int = 0

    //起始Item Y坐标
    private var mStartY: Int = 0

    //保存所有的Item的上下左右的偏移量信息
    private var mAllItemFrames = SparseArray<Rect>()

    //记录Item是否出现过屏幕且还没有回收。true表示出现过屏幕上，并且还没被回收
    private var mHasAttachedItems = SparseBooleanArray()

    //RecyclerView的Item回收器
    private var mRecycle: RecyclerView.Recycler? = null

    //RecyclerView的状态器
    private var mState: RecyclerView.State? = null

    //滚动动画
    private var mAnimation: ValueAnimator? = null

    //正显示在中间的Item
    private var mSelectPosition: Int = 0

    init {
        if (cstInterval >= 0) {
            mIntervalRatio = cstInterval
        }
    }

    /**
     * 为子视图生成默认布局参数
     * @return
     */
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * 当 RecyclerView 需要更新其布局时，会调用该方法来重新计算和布置所有可见的视图，并将它们添加到 RecyclerView 的视图层次结构中
     * @param recycler
     * @param state
     */
    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        //如果没有item，直接返回 & 跳过preLayout，preLayout主要用于支持动画

        //如果没有item，直接返回
        //跳过preLayout，preLayout主要用于支持动画
        if (itemCount <= 0 || state!!.isPreLayout) {
            mOffsetAll = 0
            return
        }
        mAllItemFrames.clear()
        mHasAttachedItems.clear()

        //得到子view的宽和高，这边的item的宽高都是一样的，所以只需要进行一次测量
        val scrap = recycler?.getViewForPosition(0)  //获取一个位于0位置(第一个是最中间的视图)的重用视图
        addView(scrap)
        //计算出子视图的期望尺寸，并将其存储在视图的 MeasuredWidth 和 MeasuredHeight 属性中。在测量完成后，可以使用这些值来定位和缩放视图
        scrap?.apply {
            measureChildWithMargins(this, 0, 0)
            //获取测量后布局的宽高
            mDecoratedChildWidth = getDecoratedMeasuredWidth(this)
            mDecoratedChildHeight = getDecoratedMeasuredHeight(this)
            mStartX = ((getHorizontalSpace() - mDecoratedChildWidth) * 1.0f / 2).roundToInt()
            mStartY = ((getVerticalSpace() - mDecoratedChildHeight) * 1.0f / 2).roundToInt()
        }
        var offset = mStartX
        //最多存MAX_RECT_COUNT个item具体位置
        val maxCount = if (itemCount < MAX_RECT_COUNT) itemCount else MAX_RECT_COUNT
        for (i in 0 until maxCount) {
            var frame = mAllItemFrames[i]
            if (frame == null) {
                frame = Rect()
            }
            frame.set(
                offset.toDouble().roundToInt(), mStartY,
                (offset + mDecoratedChildWidth).toDouble().roundToInt(),
                mStartY + mDecoratedChildHeight
            )
            mAllItemFrames.put(i, frame)
            mHasAttachedItems.put(i, false)
            offset += getIntervalDistance() //原始位置累加，否则越后面误差越大
        }
        //在布局之前，将所有的子View先Detach掉，放入到Scrap缓存中，避免重复添加和排列
        recycler?.let {
            detachAndScrapAttachedViews(it)
            if ((mRecycle == null || mState == null) && mSelectPosition != 0) {
                mOffsetAll = calculateOffsetForPosition(mSelectPosition)
            }
            layoutItems(it, state, leftItemNum)
        }
        mRecycle = recycler
        mState = state
    }

    /**
     * 设置item向左/右移动个数
     * @param leftItemNum
     * @param rightItemNum
     * @param rightOnly
     */
    fun setItemScrollTo(leftItemNum: Int, rightItemNum: Int, rightOnly: Boolean) {
        this.leftItemNum = leftItemNum
        this.rightItemNum = rightItemNum
        this.rightOnly = rightOnly
    }

    /**
     * 布局Item
     * 1，先清除已经超出屏幕的item
     * 2，再绘制可以显示在屏幕里面的item
     * @param recycler
     * @param state
     * @param scrollDirection
     */
    private fun layoutItems(recycler: RecyclerView.Recycler, state: RecyclerView.State, scrollDirection: Int) {
        if (state.isPreLayout) return
        val displayFrame =
            Rect(mOffsetAll, 0, mOffsetAll + getHorizontalSpace(), getVerticalSpace())
        var position = 0
        //先绘制所有child，找到中间位置
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child?.let {
                position = if (it.tag != null) {
                    val tag = checkTag(it.tag)
                    tag?.pos ?: 0
                } else {
                    getPosition(it)
                }
                val rect = getFrame(position)
                if (!Rect.intersects(displayFrame, rect)) {    //Item没有在显示区域(最大绘制100张图，也就是显示区域最多100张图)，就说明需要回收
                    removeAndRecycleView(it, recycler)  //回收滑出屏幕的View
                    mHasAttachedItems.delete(position)
                } else {   //Item还在显示区域内，更新滑动后Item的位置
                    layoutItem(child, rect) //更新Item位置
                    mHasAttachedItems.put(position, true)
                }
            }
        }

        if (position == 0) position = getCenterPosition()

        // 再绘制中间位置前后1个item
        val min = if (rightOnly) position else position - 1
        val max = position + 1
        for(i in min .. max) {
            val rect = getFrame(i)
            if (Rect.intersects(displayFrame, rect) && !mHasAttachedItems.get(i)) { //重新加载可见范围内的Item
                // 循环滚动时，计算实际的 item 位置
                var actualPos = i % itemCount
                // 循环滚动时，位置可能是负值，需要将其转换为对应的 item 的值
                if (actualPos < 0) actualPos += itemCount
                val scrap = recycler.getViewForPosition(actualPos)
                checkTag(scrap.tag)
                scrap.tag = TAG(i)
                measureChildWithMargins(scrap, 0, 0)
                if (scrollDirection == rightItemNum) { //item 向右滚动，新增的Item需要添加在最前面
                    addView(scrap, 0)
                } else { //item 向左滚动，新增的item要添加在最后面
                    addView(scrap)
                }
                layoutItem(scrap, rect) //将这个Item布局
                mHasAttachedItems.put(i, true)
            }
        }
    }

    override fun scrollToPosition(position: Int) {
        if (position < 0 || position > itemCount - 1) return
        mOffsetAll = calculateOffsetForPosition(position)
        if (mRecycle == null || mState == null) { //如果RecyclerView还没初始化完，先记录下要滚动的位置
            mSelectPosition = position
        } else {
            layoutItems(mRecycle!!, mState!!, if (position > mSelectPosition) leftItemNum else rightItemNum)
        }
    }

    override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
        val finalOffset = calculateOffsetForPosition(position)
        if (mRecycle == null || mState == null) { //如果RecyclerView还没初始化完，先记录下要滚动的位置
            mSelectPosition = position
        } else {
            startScroll(mOffsetAll, finalOffset)
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return true
    }

    /**
     * 滚动到指定X轴位置
     * @param from X轴方向起始点的偏移量
     * @param to X轴方向终点的偏移量
     */
    private fun startScroll(from: Int, to: Int) {
        if (mAnimation != null && mAnimation?.isRunning == true) {
            mAnimation?.cancel()
        }
        val direction: Int = if (from < to) leftItemNum else rightItemNum
        //该方法会在动画值发生改变时被调用，传递当前的 ValueAnimator 对象作为参数
        mAnimation = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())?.apply {
            this.duration = 500
            this.interpolator = DecelerateInterpolator()
            this.addUpdateListener { animation ->
                mOffsetAll = (animation.animatedValue as Float).roundToInt()
                layoutItems(mRecycle!!, mState!!, direction)
            }
        }
        mAnimation?.start()
    }

    /**
     * 获取中间位置
     * Note:该方法主要用于[getChildDrawingOrder(int, int)][RecyclerCoverFlow]判断中间位置
     */
    fun getCenterPosition(): Int {
        var pos = mOffsetAll / getIntervalDistance()   //轮播是个循环所以要 /getIntervalDistance()
        val more = mOffsetAll % getIntervalDistance()
        if (abs(more) >= getIntervalDistance() * 0.5f) {
            if (more >= 0) pos++ else pos--
        }
        return pos
    }

    /**
     * 获取index child 在 RecyclerCoverFlow 中的位置
     * @param index
     */
    fun getChildActualPos(index: Int): Int {
        val child = getChildAt(index)
        child?.tag?.let { tag ->   //复用
            return checkTag(tag)?.pos ?: 0
        } ?: kotlin.run {
            child?.let { return getPosition(it) }
        }
        return 0
    }

    /**
     * 布局Item位置
     * @param child 要布局的Item
     * @param frame 位置信息
     */
    private fun layoutItem(child: View, frame: Rect) {
        //在使用 RecyclerView 进行自定义布局时，通常需要使用 layoutDecorated() 方法来布局每个子视图
        // - mOffsetAll是因为需要固定展示在中间位置，否则会越绘制越往右，因为offset也是累加的
        layoutDecorated(child, frame.left - mOffsetAll, frame.top, frame.right - mOffsetAll, frame.bottom)
        child.scaleX = computeScale(frame.left - mOffsetAll)    //缩放
        child.scaleY = computeScale(frame.left - mOffsetAll)    //缩放
    }

    /**
     * 计算Item缩放系数
     * @param x Item的偏移量
     * @return 缩放系数
     */
    private fun computeScale(x: Int): Float {
        var scale = 1 - abs(x - mStartX) * 1.0f / abs(mStartX + mDecoratedChildWidth / mIntervalRatio)
        if (scale < 0) scale = 0f
        if (scale > 1) scale = 1f
        return scale
    }

    /**
     * 动态获取Item的位置信息
     * @param index item位置
     * @return item的Rect信息
     */
    private fun getFrame(index: Int): Rect {
        var frame = mAllItemFrames.get(index)
        if (frame == null) {
            frame = Rect()
            val offset = (mStartX + getIntervalDistance() * index).toFloat() //原始位置累加（即累计间隔距离）
            frame.set(offset.roundToInt(), mStartY, (offset + mDecoratedChildWidth).roundToInt(), mStartY + mDecoratedChildHeight)
        }
        return frame
    }

    /**
     * 计算Item所在的位置偏移
     * @param position 要计算Item位置
     */
    private fun calculateOffsetForPosition(position: Int): Int {
        return getIntervalDistance() * position
    }

    /**
     * 获取Item间隔
     */
    private fun getIntervalDistance(): Int {
        return (mDecoratedChildWidth * mIntervalRatio).roundToInt()
    }

    /**
     * 获取整个布局的水平空间大小
     */
    private fun getHorizontalSpace(): Int {
        return width - paddingRight - paddingLeft
    }

    /**
     * 获取整个布局的垂直空间大小
     */
    private fun getVerticalSpace(): Int {
        return height - paddingBottom - paddingTop
    }

    private fun checkTag(tag: Any?): TAG? {
        return if (tag != null) {
            if (tag is TAG) {
                tag
            } else {
                throw IllegalArgumentException("You should not use View#setTag(Object tag), use View#setTag(int key, Object tag) instead!")
            }
        } else {
            null
        }
    }

    //它会在适配器（Adapter）与 RecyclerView 绑定或解绑时被调用，以便通知布局管理器（LayoutManager）进行相应的处理
    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        removeAllViews()
        mRecycle = null
        mState = null
        mOffsetAll = 0
        mSelectPosition = 0
        mHasAttachedItems.clear()
        mAllItemFrames.clear()
    }

    private class TAG internal constructor(var pos: Int)

    internal class Builder {
        var cstIntervalRatio = -1f
        fun setIntervalRatio(ratio: Float): Builder {
            cstIntervalRatio = ratio
            return this
        }

        fun build(): CoverFlowLayoutManger {
            return CoverFlowLayoutManger(cstIntervalRatio)
        }
    }
}