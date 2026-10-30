package com.openminis.app.ui.glass

import android.view.WindowManager
import android.os.Build
import android.view.View
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView

/**
 * [GLASS] 给「独立窗口」里的界面（菜单 / 弹窗）开启系统级背景模糊。
 *
 * 为什么必须这么做：菜单是**独立窗口**（Compose Popup / Dialog），在窗口内部
 * 根本采样不到下面页面的像素 —— 想在窗口里画"采样背后内容"的玻璃是死路。
 * Android 12+ 提供了系统级方案：窗口标记 `FLAG_BLUR_BEHIND` + `blurBehindRadius`，
 * 由系统在窗口后面做实时模糊，我们只需要在上面叠高光/描边/淡色。
 *
 * 注意：
 *  - 需要 API 31+；
 *  - 部分机型（省电模式、低端机）会忽略这个请求，所以调用方必须同时画好
 *    "没有模糊也不会难看"的底色（见 GlassSurface 的退化路径）。
 */
fun Modifier.windowBackgroundBlur(radiusPx: Int = 48): Modifier = this

@Composable
fun ApplyWindowBackgroundBlur(radiusPx: Int = 48) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    LaunchedEffect(view) {
        runCatching { applyBlur(view, radiusPx) }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyBlur(view: View, radiusPx: Int) {
    // Compose 的 Popup 把内容放进一个由 WindowManager 直接挂载的容器，
    // 它的 layoutParams 就是那个窗口的 LayoutParams。
    val root = view.rootView
    val lp = root.layoutParams as? LayoutParams ?: return
    lp.flags = lp.flags or LayoutParams.FLAG_BLUR_BEHIND
    lp.blurBehindRadius = radiusPx
    val wm = root.context.getSystemService(WindowManager::class.java) ?: return
    runCatching { wm.updateViewLayout(root, lp) }
}
