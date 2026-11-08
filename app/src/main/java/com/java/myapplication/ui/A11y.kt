package com.java.myapplication.ui

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * 无障碍支持。
 *
 * 只做两件事：
 *  1. 识别用户是否开着读屏（TalkBack / 选择朗读等触摸探索模式）；
 *  2. 提供读屏可视区域外的主动播报与基础修饰符，
 *     让「弹窗 + 图标按钮 + 结果文本」在读屏下也能被听见。
 */
object A11y {

    /** 读屏是否开启（系统辅助功能打开且处于触摸探索模式） */
    fun screenReaderOn(context: Context?): Boolean {
        if (context == null) return false
        return runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            am.isEnabled && am.isTouchExplorationEnabled
        }.getOrDefault(false)
    }

    /** 主动播报（读屏用户听得到，普通用户无感） */
    fun announce(view: View?, text: String) {
        if (view == null || text.isBlank()) return
        runCatching { view.announceForAccessibility(text) }
    }
}

/** 给纯图标控件补读屏文案 */
fun Modifier.a11yLabel(label: String): Modifier = semantics { contentDescription = label }

/** 结果文本：内容变化时读屏自动朗读 */
fun Modifier.a11yLive(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
