package app.minis.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.minis.glass.effects.blur
import app.minis.glass.effects.lens
import app.minis.glass.effects.vibrancy
import app.minis.glass.token.GlassLevel
import app.minis.glass.token.GlassTokens

/**
 * 当前页面的 backdrop（内容层快照）。由 [GlassHost] 提供。
 *
 * **一条铁律**：一个页面只允许一份 backdrop。每个玻璃元素单独录制一层，
 * 等于把整页内容多画 N 遍 —— 中端机会直接掉到 20fps。
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** 当前生效的玻璃档位。用户设置与设备能力取交集后的结果。 */
val LocalGlassQuality = compositionLocalOf { GlassQuality.Standard }

/** 是否深色。令牌分两套，这里只用来选色，不参与重组逻辑。 */
val LocalGlassDark = compositionLocalOf { false }

/**
 * [GLASS] 所有玻璃表面的唯一入口。
 *
 * 没有 backdrop（比如预览、或被压在不透明层上）时自动退化成半透明表面，
 * 保证任何情况下都不会画出"空白"或崩溃。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassTokens.panel(LocalGlassDark.current),
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val quality = LocalGlassQuality.current

    val glassModifier =
        if (backdrop == null || quality == GlassQuality.Off) {
            // 退化路径：纯半透明 + 裁剪。观感等同上游，功能不受影响。
            modifier.background(level.tint, shape).clip(shape)
        } else {
            modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        if (GlassCapability.supportsBlur(quality)) {
                            blur(level.blurRadius.toPx())
                        }
                        if (GlassCapability.supportsRefraction(quality) &&
                            level.refractionHeight > 0.dp
                        ) {
                            lens(
                                refractionHeight = level.refractionHeight.toPx(),
                                refractionAmount = level.refractionAmount.toPx(),
                                chromaticAberration = level.chromaticAberration,
                            )
                        }
                    },
                    highlight = { level.highlight },
                    shadow = { level.shadow },
                    innerShadow = { level.innerShadow },
                )
        }

    Box(glassModifier.clip(shape), content = content)
}
