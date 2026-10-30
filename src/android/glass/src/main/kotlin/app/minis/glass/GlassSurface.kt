package app.minis.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import app.minis.glass.effects.blur
import app.minis.glass.effects.lens
import app.minis.glass.effects.vibrancy
import app.minis.glass.shape.RoundedCornerRadiiProvider
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

/** 是否深色。只影响退化路径的底色，玻璃本身靠采样内容自适应。 */
val LocalGlassDark = compositionLocalOf { false }

/**
 * [GLASS] 所有玻璃表面的唯一入口。
 *
 * tint 的画法照抄参考库 `LiquidButton`：先 `BlendMode.Hue` 上色，再叠一层 0.75 alpha，
 * 而不是直接铺一层半透明色块 —— 前者保留背景明暗层次，后者会把玻璃压成一张色卡。
 *
 * 没有 backdrop（预览、或被压在不透明层上）或档位为 [GlassQuality.Off] 时，
 * 自动退化成半透明表面，保证任何情况下都不破相、不崩溃。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassTokens.Panel,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val quality = LocalGlassQuality.current
    val isDark = LocalGlassDark.current

    val glassModifier =
        if (backdrop == null || quality == GlassQuality.Off) {
            // 退化路径：中性半透明底（明暗各一套），观感接近上游的卡片。
            val fallback =
                if (isDark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f)
            modifier.background(fallback, shape).clip(shape)
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
                        // [GLASS] 折射要用 SDF，只有圆角形状（和实现了 RoundedCornerRadiiProvider
                        // 的自研形状）才有。这里必须挡一下 —— 否则 RectangleShape 之类的
                        // 会直接在首帧抛 UnsupportedOperationException 把 App 打死（已踩过）。
                        val canRefract = shape is CornerBasedShape || shape is RoundedCornerRadiiProvider
                        if (canRefract &&
                            GlassCapability.supportsRefraction(quality) &&
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
                    onDrawSurface = tintDrawer(level.tint, isDark),
                )
        }

    Box(glassModifier.clip(shape), content = content)
}

/** 参考库 LiquidButton 的上色方式；tint 未指定时只叠极淡的一层，避免玻璃发灰。 */
private fun tintDrawer(tint: Color, isDark: Boolean): DrawScope.() -> Unit = {
    if (tint.isSpecified) {
        drawRect(tint, blendMode = BlendMode.Hue)
        drawRect(tint.copy(alpha = 0.75f))
    } else {
        drawRect(
            if (isDark) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f),
        )
    }
}
