package app.minis.glass

import android.os.Build
import androidx.compose.runtime.Immutable

/**
 * [GLASS] 能力档位。玻璃效果的每一层都要能单独关掉，原因有三：
 *  1. AGSL（RuntimeShader，折射要用）需要 API 33；
 *  2. RenderEffect（模糊）需要 API 31；
 *  3. 老 GPU 上大尺寸模糊可能直接把进程干掉（native crash，try/catch 兜不住）。
 */
enum class GlassQuality {
    /** 模糊 + 折射(+色散) + 提饱和 + 高光 + 内阴影。 */
    High,

    /** 模糊 + 折射 + 提饱和 + 高光。默认。 */
    Standard,

    /** 只有模糊 + 半透明 + 静态高光（API 31~32）。 */
    Lite,

    /** 纯半透明表面，观感等同上游（API < 31 / 用户关闭 / 系统"移除透明度"）。 */
    Off,
}

@Immutable
object GlassCapability {

    /** 依据系统能力给出**上限**档位；用户设置只能往下调，不能超过它。 */
    fun ceiling(): GlassQuality = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassQuality.Standard
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassQuality.Lite
        else -> GlassQuality.Off
    }

    /** 是否敢开折射（AGSL）。 */
    fun supportsRefraction(quality: GlassQuality): Boolean =
        quality == GlassQuality.High || quality == GlassQuality.Standard

    /** 是否敢开模糊（RenderEffect）。 */
    fun supportsBlur(quality: GlassQuality): Boolean =
        quality != GlassQuality.Off
}
