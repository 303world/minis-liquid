package app.minis.glass.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * [GLASS] 让自定义 [Shape] 把自己的四角像素半径告诉折射着色器。
 *
 * 为什么需要它：`Modifier.drawBackdrop` 的 `lens`（边缘折射）要用 SDF 计算每个像素
 * 的偏移量，而 Compose 的 [Shape] 只提供 `createOutline()` —— 从 Path 反推四角半径
 * 既不可靠也不便宜。所以凡是需要折射的形状，除了实现 [Shape] 之外再实现本接口。
 *
 * 顺序固定为 [topLeft, topRight, bottomRight, bottomLeft]（像素，非 dp）。
 */
interface RoundedCornerRadiiProvider : Shape {

    fun cornerRadii(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): FloatArray
}
