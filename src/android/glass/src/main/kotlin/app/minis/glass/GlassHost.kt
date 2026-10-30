package app.minis.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.minis.glass.backdrops.layerBackdrop
import app.minis.glass.backdrops.rememberLayerBackdrop

/**
 * [GLASS] 页面级玻璃容器。
 *
 * 结构（顺序不能改）：
 * ```
 * Box
 * ├── 内容层  ← Modifier.layerBackdrop(backdrop)，每帧把自己的绘制结果录进离屏层
 * └── 玻璃层  ← 在内容层之后绘制，采样上面那层
 * ```
 * 玻璃元素必须在内容层**之后**绘制，否则采样到的是上一帧或空白。
 *
 * 参数 [quality] 是**上限**：设备能力（[GlassCapability.ceiling]）与用户设置取较小值。
 */
@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    quality: GlassQuality = GlassCapability.ceiling(),
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
    glass: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalGlassBackdrop provides backdrop,
        LocalGlassQuality provides quality,
        LocalGlassDark provides dark,
    ) {
        Box(modifier) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
                content()
            }
            glass()
        }
    }
}
