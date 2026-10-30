package app.minis.glassdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.minis.glass.GlassHost
import app.minis.glass.GlassQuality
import app.minis.glass.GlassSurface
import app.minis.glass.token.GlassTokens

/**
 * [GLASS] M1 演示：在可滚动内容之上放几块玻璃，用来判断"像不像 iOS 26"。
 *
 * 三个可现场切换的开关：
 *  - 档位（Standard / Lite / Off）—— 验证降级路径不破相
 *  - 明暗 —— 两套令牌都要能看
 * 内容丰富度是刻意的：玻璃要有东西可采样，纯色背景上看不出模糊。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DemoApp() }
    }
}

@Composable
private fun DemoApp() {
    var dark by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(GlassQuality.Standard) }

    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    val pageBg = if (dark) Color(0xFF08090A) else Color(0xFFF2F2F7)

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(pageBg)) {
            GlassHost(
                modifier = Modifier.fillMaxSize(),
                quality = quality,
                dark = dark,
                content = { DemoFeed(dark) },
                glass = {
                    TopBar(quality)
                    BottomControls(
                        dark = dark,
                        quality = quality,
                        onQuality = { quality = it },
                        onToggleDark = { dark = !dark },
                    )
                },
            )
        }
    }
}

/** 被采样的内容层：一堆彩色卡片，滚动时能明显看到玻璃后面的颜色在动。 */
@Composable
private fun DemoFeed(dark: Boolean) {
    val palettes = listOf(
        listOf(Color(0xFF3B82F6), Color(0xFF9333EA)),
        listOf(Color(0xFFF97316), Color(0xFFEF4444)),
        listOf(Color(0xFF10B981), Color(0xFF14B8A6)),
        listOf(Color(0xFFEAB308), Color(0xFFF59E0B)),
        listOf(Color(0xFF6366F1), Color(0xFF0EA5E9)),
        listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 96.dp, bottom = 200.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(18) { index ->
            val colors = palettes[index % palettes.size]
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(colors),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(18.dp),
            ) {
                Text(
                    text = "内容块 #${index + 1}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "滚动这块内容，观察上方玻璃条与底部控件的模糊、折射和高光是否跟着动。",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun TopBar(quality: GlassQuality) {
    Box(Modifier.fillMaxWidth().systemBarsPadding().padding(16.dp)) {
        GlassSurface(
            level = GlassTokens.Bar,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Liquid Glass", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Text(quality.name, fontSize = 12.sp, color = Color.Black.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun BoxScope.BottomControls(
    dark: Boolean,
    quality: GlassQuality,
    onQuality: (GlassQuality) -> Unit,
    onToggleDark: () -> Unit,
) {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // 圆形玻璃按钮（FAB 的形态），品牌沙色 tint —— 对应 iOS 新建会话按钮
        GlassSurface(
            level = GlassTokens.Button.copy(
                tint = if (dark) GlassTokens.BrandSandDark else GlassTokens.BrandSand,
            ),
            shape = CircleShape,
            modifier = Modifier.size(56.dp).clickable { onToggleDark() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (dark) "☾" else "☀", fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        // 底部工具条
        GlassSurface(
            level = GlassTokens.Bar,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlassQuality.entries.filter { it != GlassQuality.High }.forEach { q ->
                    val selected = q == quality
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                color = if (selected) {
                                    if (dark) Color.White.copy(alpha = 0.18f)
                                    else Color.Black.copy(alpha = 0.10f)
                                } else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { onQuality(q) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (q) {
                                GlassQuality.Standard -> "标准"
                                GlassQuality.Lite -> "仅模糊"
                                GlassQuality.Off -> "关闭"
                                else -> q.name
                            },
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
