package app.minis.glass.effects

import androidx.compose.ui.graphics.RenderEffect
import app.minis.glass.BackdropEffectScope
import app.minis.glass.RuntimeShader
import app.minis.glass.internal.RuntimeShaderEffect
import app.minis.glass.internal.chain
import app.minis.glass.isRenderEffectSupported
import app.minis.glass.isRuntimeShaderSupported
import kotlin.contracts.ExperimentalContracts

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return

    renderEffect = renderEffect.chain(effect)
}

@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return

    val effect =
        RuntimeShaderEffect(
            runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
            uniformShaderName = uniformShaderName
        )
    renderEffect = renderEffect.chain(effect)
}
