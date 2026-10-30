package app.minis.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * [GLASS] 顶栏「渐进模糊」（progressive blur）。
 *
 * **参数与实现照抄盐选音乐 SaltUI**（Moriafly/SaltUI，Apache-2.0）：
 *  - 它自己也不手写模糊，而是用 Haze 库的 `hazeEffect` + `HazeProgressive.verticalGradient`
 *  - 材质参数取自 `SaltHazeStyles.blurryGlass`：blurRadius 45dp、tint 0x80FFFFFF（深色 0x80000000）、noiseFactor 0.01
 *  - 顶栏用 `startIntensity = 1f → endIntensity = 0f` 的垂直渐变，配上
 *    `CompositingStrategy.Offscreen`，让下边缘自然消散
 *
 * 为什么之前那版"看不出区别"：我用手写的 backdrop + 均匀模糊，且渐变区域只有 104dp，
 * 在白底上等于没效果。渐进模糊的关键是**上实下虚 + 足够大的模糊半径**。
 *
 * 用法：内容侧挂 `Modifier.hazeSource(state)`，本组件挂 `hazeEffect`。
 */
@Composable
fun GlassProgressiveBar(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    blurRadius: Dp = 45.dp,
    dark: Boolean = false,
    noiseFactor: Float = 0.01f,
    content: @Composable BoxScope.() -> Unit,
) {
    // SaltUI: light 0x80FFFFFF / dark 0x80000000
    val tint = if (dark) Color(0x80000000) else Color(0x80FFFFFF)

    Box(modifier) {
        if (hazeState != null) Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        blurRadius = blurRadius,
                        tints = listOf(dev.chrisbanes.haze.HazeTint(tint)),
                        noiseFactor = noiseFactor,
                    ),
                ) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                },
        )
        content()
    }
}
