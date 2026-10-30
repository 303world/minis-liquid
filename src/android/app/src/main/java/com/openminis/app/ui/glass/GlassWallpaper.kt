package com.openminis.app.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.minis.glass.token.GlassTokens
import com.openminis.app.ui.theme.ChatColors

/**
 * [GLASS] 极淡的层叠渐变背景。
 *
 * **为什么必须有它**：上游的页面底色是纯 `#F2F2F7` / 纯黑。玻璃采样纯色 → 模糊后还是纯色，
 * 效果肉眼不可见，只剩一层发灰的半透明膜。这里给页面铺几团几乎看不出来的色雾，
 * 玻璃才有"色温"可采。
 *
 * 设计约束：
 *  - alpha 全部 ≤ 6%，正常阅读时感觉不到它的存在；
 *  - 只用主题已有的颜色（品牌沙 + 主色），不引入新色相；
 *  - 纯 Canvas 绘制，没有动画、没有额外图层，几乎零成本。
 */
@Composable
fun GlassWallpaper(modifier: Modifier = Modifier) {
    val dark = ChatColors.isDark
    val base = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    // 深色下用更低的 alpha：深底上色雾更容易被看出来。
    val sandAlpha = if (dark) 0.045f else 0.055f
    val accentAlpha = if (dark) 0.035f else 0.04f
    val sand = if (dark) GlassTokens.BrandSandDark else GlassTokens.BrandSand

    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        drawGlow(
            color = sand.copy(alpha = sandAlpha),
            center = Offset(size.width * 0.12f, size.height * 0.06f),
            radius = size.minDimension * 0.95f,
        )
        drawGlow(
            color = accent.copy(alpha = accentAlpha),
            center = Offset(size.width * 0.92f, size.height * 0.35f),
            radius = size.minDimension * 0.8f,
        )
        drawGlow(
            color = sand.copy(alpha = sandAlpha * 0.8f),
            center = Offset(size.width * 0.35f, size.height * 1.02f),
            radius = size.minDimension * 0.9f,
        )
    }
}

private fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float) {
    if (radius <= 0f) return
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius,
        ),
    )
}
