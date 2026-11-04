package com.java.myapplication

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/**
 * 支持下拉刷新的 WebView。
 *
 * 只在「页面已滚到顶部」时识别向下拖动，超过阈值触发一次刷新；
 * 页面未到顶或向上滑动时完全不干预，交给 WebView 自身的滚动处理，避免误触。
 */
class ZwWebView(context: Context) : WebView(context) {

    var onPullRefresh: (() -> Unit)? = null

    /** 下拉触发阈值（px，约 90dp） */
    var pullThresholdPx: Float = 260f

    private var downY = 0f
    private var tracking = false
    private var fired = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                fired = false
                tracking = !canScrollVertically(-1)
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && !fired && event.y - downY > pullThresholdPx) {
                    fired = true
                    tracking = false
                    onPullRefresh?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracking = false
            }
        }
        return super.onTouchEvent(event)
    }
}