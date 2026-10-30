package com.openminis.app.ui.glass

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.minis.glass.GlassCapability
import app.minis.glass.GlassHost
import app.minis.glass.GlassQuality
import com.openminis.app.ui.theme.ChatColors
import com.openminis.app.ui.settings.getAppearancePrefs

/**
 * [GLASS] 用户可选的玻璃风格。**只能往下调，不能超过设备能力上限。**
 *
 * 0 = 关闭（观感完全等于上游，用于排查"是不是玻璃导致的"）
 * 1 = 标准（默认）
 * 2 = 高（开色散，只在高档设备上给）
 */
const val KEY_GLASS_STYLE = "appearance.glass_style"

const val GLASS_STYLE_OFF = 0
const val GLASS_STYLE_STANDARD = 1
const val GLASS_STYLE_HIGH = 2

fun glassStylePref(context: Context): Int =
    getAppearancePrefs(context).getInt(KEY_GLASS_STYLE, GLASS_STYLE_STANDARD)

/** 用户设置与设备能力取交集：设置再高，设备跑不动也不会开。 */
fun resolveGlassQuality(stylePref: Int): GlassQuality {
    val ceiling = GlassCapability.ceiling()
    if (ceiling == GlassQuality.Off || stylePref == GLASS_STYLE_OFF) return GlassQuality.Off
    if (ceiling == GlassQuality.Lite) return GlassQuality.Lite
    return if (stylePref == GLASS_STYLE_HIGH) GlassQuality.High else GlassQuality.Standard
}

@Composable
fun rememberGlassQuality(): GlassQuality {
    val context = LocalContext.current
    return remember(context) { resolveGlassQuality(glassStylePref(context)) }
}

/**
 * [GLASS] 业务侧的玻璃宿主。
 *
 * 用法（顺序不能反）：
 * ```
 * MinisGlassHost(
 *     content = { 正常页面内容，会被录进 backdrop },
 *     glass   = { 顶栏 / 输入条 / FAB 等浮层，采样上面那层 },
 * )
 * ```
 * 一个页面**只允许一个** MinisGlassHost —— 多份 backdrop 等于把页面多画几遍。
 */
@Composable
fun MinisGlassHost(
    modifier: Modifier = Modifier,
    quality: GlassQuality = rememberGlassQuality(),
    content: @Composable BoxScope.() -> Unit,
    glass: @Composable BoxScope.() -> Unit,
) {
    GlassHost(
        modifier = modifier,
        quality = quality,
        dark = ChatColors.isDark,
        content = content,
        glass = glass,
    )
}

/**
 * [GLASS] Sheet / 弹窗这类"独立窗口"的容器色。
 * 独立窗口采样不到页面内容，靠 [com.openminis.app.ui.glass.ApplyWindowBackgroundBlur]
 * 让系统在窗口后面做实时模糊，这里只给一层半透明色 + 发丝边。
 * 浅色别用太实的白（会把背后模糊全盖住，看起来就是一块白板）。
 */
@Composable
fun glassSheetColor(): androidx.compose.ui.graphics.Color =
    if (ChatColors.isDark) androidx.compose.ui.graphics.Color(0xE61C1C1E)
    else androidx.compose.ui.graphics.Color(0xB8FFFFFF)

@Composable
fun glassSheetBorder(): androidx.compose.foundation.BorderStroke =
    if (ChatColors.isDark) {
        androidx.compose.foundation.BorderStroke(
            0.5.dp,
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
            ),
        )
    } else {
        androidx.compose.foundation.BorderStroke(
            0.5.dp,
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.06f)),
            ),
        )
    }
