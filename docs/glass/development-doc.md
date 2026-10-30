# Minis Glass 开发文档

> 文档版本：v0.1（2026-10-30）
> 目标上游：`OpenMinis/OpenMinis` @ `1.15` / versionCode 37（clone 快照 2026-10-30，`--depth 30`）
> 参考项目：`Kyant0/AndroidLiquidGlass`（库名 Backdrop）@ 本地 clone 快照
> 状态：技术方案已定稿，设计令牌待与 iOS 提报对齐后冻结（见 §4）

---

## 0. 一句话说清这个项目

把 `OpenMinis/OpenMinis` 的 **Android 端**分叉出来，**业务代码一行不改**，只重做视觉层：用 AGSL 着色器实现「液态玻璃」（背景采样模糊 + 边缘折射 + 镜面高光 + 内阴影 + 自适应明暗），让 Android 端的观感与 iOS 26 版 Minis 一致；源码在 GPLv3 下开源到主人自己的 GitHub 仓库，并长期跟随上游更新。

---

## 1. 项目定义

### 1.1 目标（可验收）

| # | 目标 | 验收标准 |
|---|---|---|
| G1 | 功能零回退 | 上游 `androidTest` 全绿；手动回归清单（§7.4）全过；聊天/沙箱/工具调用/MCP/技能/记忆/备份/终端/tips 全链路可用 |
| G2 | 视觉达到「液态玻璃」 | 顶栏、输入条、Sheet、菜单、弹窗、FAB、会话列表头、设置分组卡片 8 类表面具备：背景实时采样、模糊、边缘折射、1px 高光描边、投地阴影 |
| G3 | 与 iOS 版观感一致 | 按 §4 的令牌表逐项对表（圆角/间距/字号/材质层级/动效曲线），每项有截图对比 |
| G4 | 性能可接受 | 中端机（骁龙 7 系 / 天玑 8000 级）滚动帧率 ≥ 55fps；进聊天页首帧 ≤ 400ms；玻璃元素存在时主线程无 >16ms 长任务堆积 |
| G5 | 可降级 | API<33 设备、低端机、系统「移除动画/降低透明度」开启时自动降级，界面不破相、不崩溃 |
| G6 | 可维护 | 上游发版后 3 天内可完成合并；本 fork 改动面 ≤ 25 个上游文件（§5.8） |
| G7 | 合规开源 | GPLv3 完整履约：保留 LICENSE、标注修改、公开源码、附第三方许可与来源说明 |

### 1.2 非目标（明确不做）

- **不改业务逻辑**：不重构 ChatViewModel、不改 provider 协议、不动沙箱/PRoot、不加新功能。
- **不做 iOS 端**：上游 iOS 代码只作为视觉参照物读取，不编译、不改动。
- **不支持 x86 / armeabi-v7a**：上游只出 `arm64-v8a`，跟随上游。
- **不追上游未发布的内部改动**：只跟 tag/`main`。
- **不做主题商店/自定义皮肤系统**：只做一套液态玻璃 + 一个总开关。

### 1.3 与上游的关系

- 上游是 **iOS + Android 单仓**（`src/ios` 389k 行 Swift，`src/android` 290k 行 Kotlin），GPLv3。
- 我们的 fork **只承载 Android 改动**，`src/ios` 原样跟随上游（合并时不动它）。
- 定位是「视觉发行版」而非「硬分叉」：能用配置开关关掉玻璃、退回上游原样，是设计目标之一（§5.6）。这样上游大改 UI 时我们不会被动。

---

## 2. 上游代码库剖析

### 2.1 仓库布局

```
OpenMinis/
├── BUILDING.md              # 构建说明（含 native 依赖编译顺序）
├── deps/                    # 从源码编的原生依赖：proot / ish / ffmpeg / lame / aoc / rclone
├── docs/specs/              # 设计文档
├── scripts/                 # 构建辅助（prepare_android_sandbox.sh 等）
├── assets/                  # README 截图
└── src/
    ├── ios/                 # SwiftUI 工程（1040 个 .swift / 389,460 行，含测试）
    └── android/             # Kotlin + Compose 工程（本次改造对象）
```

### 2.2 Android 工程硬指标

| 项 | 值 |
|---|---|
| 语言/UI | Kotlin + Jetpack Compose（Material3） |
| Kotlin | 2.1.0 |
| AGP / Gradle | 8.7.3 / 8.11.1 |
| Compose | BOM `2025.09.00`（material3、material-icons-extended、adaptive 1.2.0、navigation-compose 2.8.5） |
| compileSdk / targetSdk / minSdk | 36 / 35 / **26** |
| 产物 ABI | 仅 `arm64-v8a` |
| 应用 ID / 版本 | `com.openminis.app` / 1.15（versionCode 37） |
| 源码规模 | `app/src/main/java/com/openminis/app` 下 **1,497 个 .kt / 289,987 行** |
| 其中 UI 层 | `ui/` **281 个 .kt / 140,402 行** |
| 产品风味（flavor） | `distribution` 维度：`github`（APK）、`store`（AAB，默认只出 release） |
| 变体名 | `githubDebug` / `githubRelease` / `githubPerf`（**不是** `debug`/`release`；有别名任务 `assembleDebug`、`installDebug`、`assembleRelease`→`assembleGithubRelease`） |
| 原生构建 | CMake 3.22.1 + NDK 27.0.12077973，编 `pty_bridge`、`minis_crash_handler`、`jieba_jni` 三个 .so |
| 预置 so | `app/src/main/jniLibs/arm64-v8a/` 里已有 `libproot-loader.so`、`libproot-loader32.so` |
| 预置资产 | `assets/default_mount`、`assets/katex`、`assets/jieba`、`assets/models-dev-api.json`；**缺失**：`assets/proot-aarch64`、`assets/alpine-minirootfs.tar(.gz)`（由 `deps/build_proot.sh` + `scripts/prepare_android_sandbox.sh` 生成） |
| 沙箱 rootfs | 随 APK 打包（`RootfsManager` 从 assets 解压，`ROOTFS_ASSET="alpine-minirootfs.tar.gz"`，回退 `.tar`），不是运行时下载 |
| 加密/数据库 | Room + KSP（`app/schemas`）、DataStore |
| 其他重依赖 | rclone.aar（备份，`app/libs` 本地 flatDir）、JitPack 的 VAD 库、ONNX Runtime、gojni（46MB） |

### 2.3 UI 架构（改造落点）

- **导航**：`ui/navigation/AppNavigation.kt`（1,650 行）集中声明全部路由（`composable(...)`/`dialog(...)`）；`ui/navigation/ChatSplitScaffold.kt`（1,012 行）用 `material3.adaptive` 的 `NavigableListDetailPaneScaffold` 做平板/折叠屏双栏。
- **壳层**：会话列表 `ui/sessions/SessionListScreen.kt`（4,019 行，自绘顶栏 + `DualFabRow` 双 FAB + 文件夹卡片 + 多选工具条）；聊天页 `ui/chat/ChatScreen.kt`（8,760 行，`Scaffold` 在 2973 行，输入区带 `imePadding()`）。
- **主题**：`ui/theme/Theme.kt` 是唯一入口 —— `MinisTheme()` 里 `MaterialTheme(colorScheme, shapes=MinisShapes, typography)` + `CompositionLocalProvider(LocalChatPalette)`。配色已经把 Material3 的 `surface*` 全部改写成 iOS 分组式（浅色页 `#F2F2F7` / 卡白；深色页 `#000` / 卡 `#1C1C1E`），主色是去饱和的 iOS 蓝 `#528AD2`（深色 `#6A94CE`）。**即：上游 Android 已经在做「iOS 化」，只是还停留在静态配色 + 方角卡片阶段。**
- **形状**：`MinisShapes` 已定义 extraSmall→extraLarge（28dp 对话框）。
- **现有模糊**：全 UI 只有 2 个文件用到 `Modifier.blur`（`chat/ContextUsageHintUi.kt`、`chat/ChatComposerWidgets.kt`），没有任何 backdrop 采样、没有 AGSL。**玻璃效果基本是从零开始，但主题收口点很干净。**
- **样式散落程度（改造成本真实指标，来自 `ui-inventory-android.md` 实测）**：
  - `MaterialTheme.colorScheme.*` 引用 **1,221 处**（好：走主题）
  - 硬编码 `Color(0x…)` **523 处**，其中 **419 处在 `theme/` 之外**，集中在 `settings/backup/BackupAndRestoreScreen.kt`(24)、`settings/SettingsScreen.kt`(24)、`chat/ChatToolDetailUI.kt`(22)、`sessions/SessionListScreen.kt`(20)、`chat/ChatModelPickerSheet.kt`(20)
  - `RoundedCornerShape` **289 处 / 18 种取值**；`.background(` 339 处；`fontSize=` 349 处
  - **外壳有三套并行实现**：顶栏 2 套（`SettingsScaffold` 36 处 / 裸 `TopAppBar` 29 处）、分区卡 **3 套同名 `SettingsSection`**（圆角 12/14/16 各不相同）、弹窗 2 类（`MinisAlertDialog` 21 处 / 裸 `AlertDialog` **83 处**）
  - 浮层调用点：`ModalBottomSheet` **29 处**、`MinisMenu` 24 处、`Popup` 10 处
  - → 结论：**不能直接上手糊玻璃**。必须先「收敛」（把 3 套 SettingsSection 合成 1 套、2 套顶栏合成 1 套、2 类弹窗都走同一封装），再换实现。否则要改 200+ 个调用点，且以后每次跟上游都要重新冲突。

### 2.4 构建链路与坑（已验证）

| 事实 | 证据 |
|---|---|
| `provider-customization.properties` 需从 `.example` 复制，留空可编译 | `BUILDING.md` + `app/build.gradle.kts` 顶部 |
| 变体名带 flavor，`./gradlew :app:compileDebugKotlin` **不存在**；要用 `:app:compileGithubDebugKotlin` | `app/build.gradle.kts` 有 `beforeVariants`/`assembleRelease` 别名段 |
| 有原生构建时，AGP 会强制解析 NDK 27.0.12077973；缺 NDK 会**自动下载 ~1GB 且随后失败** | 本机实测：`gradle ... --dry-run` 报 `[CXX1101] NDK ... did not have a source.properties`，日志显示它在 `Preparing "Install NDK"` |
| **NDK 不提供 aarch64 Linux 主机工具链**（只有 x86_64 Linux / macOS / Windows），所以在手机内 PRoot 沙箱里编不了 `pty_bridge` 等 | NDK 官方宿主支持列表；本机 aarch64 实测 |
| 绕法已验证可行：加 `-Pminis.prebuiltNative=true` 跳过 CMake，复用从官方 APK 抽出的 3 个 .so | 本次已在 `app/build.gradle.kts` 打补丁 |
| 官方 release APK 内可直接抽到 `lib/arm64-v8a/*.so`、`assets/proot-aarch64`、`assets/alpine-minirootfs.tar` | 实测 `1.15` APK（42.7MB）解包成功 |

### 2.5 不可忽视的平台约束

1. **AGSL（RuntimeShader）需 API 33**。`minSdk 26` 意味着 26~32 必须有降级路径（31~32 可用 `RenderEffect` 做模糊，26~30 只能半透明 + 渐变高光）。参考库已内建这套判断（`isRuntimeShaderSupported()` = SDK≥33，`isRenderEffectSupported()` = SDK≥31）。
2. **RenderEffect 与硬件加速有崩溃面**（超大 blur radius / 超尺寸 layer 在部分 GPU 上驱动崩溃），必须限制 radius 与 layer 尺寸，并在低端机降档。
3. **WebView 之上的浮层**：Compose 与 WebView 不同渲染层，玻璃元素压在 WebView 上时采样不到 WebView 内容（黑/透明）。需按 §5.3 的供给模型规避。
4. **`enableEdgeToEdge` 已启用**（`MainActivity.kt:563`），玻璃顶栏可以直接铺到状态栏下，节省改造量。
5. **上游自更新**：`buildConfigField RELEASE_REPO = "OpenMinis/OpenMinis"`，fork 若不动它，用户在 App 内检查更新会拉到**上游官方包**（签名不同，安装会失败）。必须改成本 fork 仓库（§6.5）。

---

## 3. 参考项目剖析：Kyant0/AndroidLiquidGlass（Backdrop）

### 3.1 它是什么

- 一套 **Compose Multiplatform** 的「液态玻璃」底层库（**不含**高层组件，作者明确要求自己组装），演示 App 里给了 `LiquidButton` / `LiquidToggle` / `LiquidSlider` / `LiquidBottomTabs` 四个范例组件。
- 目录：`backdrop/`（库，**2,550 行 Kotlin**）+ `app/`（catalog 演示）+ `androidApp`（演示 APK）+ `iosApp`。
- 许可：**Apache-2.0**（对 GPLv3 工程兼容，可自由移植，但需保留许可与归属）。

### 3.2 渲染原理（这是整个视觉方案的地基）

它不是「`Modifier.blur` + 半透明白底」那种廉价毛玻璃。真实链路：

```
内容层                      玻璃元素
┌──────────────┐          ┌──────────────────────┐
│ 页面内容      │──录制──▶ │ drawBackdrop()       │
│ (graphicsLayer)│  Layer  │ 1. 采样该 layer 的像素 │
└──────────────┘          │ 2. RenderEffect 模糊   │
                          │ 3. AGSL 折射/lens 位移 │
                          │ 4. 振动度 vibrancy     │
                          │ 5. 1px 高光描边        │
                          │ 6. 内阴影 + 投地阴影    │
                          └──────────────────────┘
```

关键实现点（都在 `backdrop/src/commonMain/...`）：

- **`Backdrop` 接口**：`DrawScope.drawBackdrop(density, coordinates, layerBlock)` —— 玻璃元素在绘制自身内容前，先把「背后的东西」画进来。
- **`LayerBackdrop`**（`backdrops/LayerBackdrop.kt`）：包一个 `GraphicsLayer`；配合 `Modifier.layerBackdrop(backdrop)` 使用，该修饰符在 `draw()` 里 **每帧** `recordLayer(graphicsLayer) { onDraw(this) }`，用 `positionInWindow()` 做坐标换算。→ **一个 backdrop 实例 = 一份每帧录制的离屏层**。
- **`effects/Blur.kt`**：`BlurEffect`（`RenderEffect`，双半径）叠进 `BackdropEffectScope.renderEffect`，并自动扩 `padding` 防边缘裁切。
- **`effects/Lens.kt`**：AGSL 着色器 `RoundedRectRefractionShaderString` / `...WithDispersionShaderString`，按容器圆角做边缘折射偏移（`refractionHeight`/`refractionAmount`/`depthEffect`/`chromaticAberration` = 色散）。
- **`effects/ColorFilter.kt` 里的 `vibrancy()`**：提升采样内容的饱和度，让玻璃底色不发灰（这是 iOS 观感的关键一笔）。
- **`highlight/`**：沿形状边缘画 0.5dp 亮边的 `Highlight`（`Default` / `Ambient` / `Plain`）三个预设。
- **`shadow/`**：外阴影 + `InnerShadow`（玻璃的「厚度感」来自内阴影）。

### 3.3 调用姿势（摘自官方 `LiquidButton.kt`）

```kotlin
Row(
    Modifier.drawBackdrop(
        backdrop = backdrop,                 // Backdrop 实例
        shape = { Capsule() },
        effects = {
            vibrancy()
            blur(2f.dp.toPx())
            lens(12f.dp.toPx(), 24f.dp.toPx())
        },
        // layerBlock = 按下时的「液态形变」：translation/scale 用 tanh 做软限幅
    )
) { /* 文案 */ }
```

要点：`blur` 的半径用的是 **dp 级小值**（2dp），不是 20dp —— 因为它采样的是**内容层**，过大半径会把内容糊成一团。视觉上的「磨砂」主要来自 `vibrancy()` + `padding` + 半透明 tint 的组合。

### 3.4 性能模型（决定架构）

| 因素 | 成本 | 结论 |
|---|---|---|
| 每个 `LayerBackdrop` | 每帧一次离屏录制（相当于多画一遍内容） | **一屏只允许 1 份 backdrop 源**，所有玻璃元素共享 |
| 每个玻璃元素的 `blur` | 对元素尺寸区域做 RenderEffect，O(面积) | 小面积多次 ≫ 大面积一次；避免在 LazyColumn item 里用 |
| AGSL `lens` | 顶点/片元着色器，面积线性 | 与 blur 同区域叠加，控制元素数量 |
| `padding` 扩展 | 模糊会外扩，采样区变大 | 圆角容器上限制 padding ≤ 2×radius |
| 滚动中 | 每帧重录 + 重算 | iOS 也做不到全局实时；滚动时降级为静态 tint，停稳 120ms 后恢复 |

### 3.5 可复用性判断（关键决策）

- **许可证**：Apache-2.0 ✅，可移植进 GPLv3 项目（需 NOTICE 归属）。
- **API 兼容**：参考工程用 **AGP 9.3.2 / Kotlin 2.4.10 / CMP 1.12.0**，minis 用 **Kotlin 2.1.0 / BOM 2025.09.00**。
  - Maven Central 上确实有 `io.github.kyant0:backdrop`（最新 2.0.1）与 `io.github.kyant0:shapes:1.2.1`。
  - **但直接依赖 Maven 包不可行 —— 已实测确认**：`backdrop-android-2.0.1.aar` 里的 `Backdrop.class` 带 `kotlin.Metadata(mv=[2,4,0])`，即用 Kotlin 2.4 编译；minis 的 Kotlin 2.1 编译器会直接拒绝（`class file was compiled with a newer version of Kotlin`）。要吃得下这个包，得把 Kotlin/AGP/Compose 一起升到 2.4/9.x/1.12，等于给上游做一次大版本升级 —— 与「改动面最小、好维护」的目标冲突。
  - 附带证据：`backdrop-2.0.1.module` 由 Gradle 9.7.1 生成，库内 `compileSdk = 37`，同样高于 minis 的 36。
  - 因此方案是 **源码移植**：把 `backdrop` 的 Android 相关部分（约 1.2k 行）搬进本仓库的独立模块 `:glass`，去掉 skiko/desktop/js/ios 目标与 CMP 专属 API。
- **依赖 `com.kyant.shapes`**：库只用到 `RoundedRectangularShape`（逐角半径 + 连续曲率）与 `Capsule`/`RoundedRectangle`。我们自己实现一个 `ContinuousRoundedRectShape`（iOS 风格超椭圆圆角）即可，顺带解决全 App 圆角「方角感」问题 —— 这是 iOS 与 Android 观感差异里最容易被忽视的一条。

### 3.6 我们要加、参考库没有的东西

参考库只到「能画出玻璃」。产品级还差：

1. **一份 backdrop 多元素共享**的页面级容器（`GlassBackdropHost`）。
2. **滚动降级**与帧时间自适应。
3. **能力分级**三档（AGSL / 仅模糊 / 纯半透明）+ 设置项 + 无障碍联动。
4. **令牌体系**（材质层级、圆角、间距、动效曲线）与 iOS 对标。
5. **与 Material3 组件（Sheet/Dialog/Menu/Scaffold bar）的替换层** —— 这是让 131 处 `ModalBottomSheet`、104 处 `AlertDialog` 一次性变玻璃的唯一现实路径。

---

## 4. 视觉与交互规格（对齐 iOS 26）

> 全部数值来自 `design-ios-extract.md`（对 `src/ios` 的实扫，含 `rg` 原始计数与 `文件:行号`）。这一节的每一条都能在 iOS 源码里找到出处，不是"看着像"。

### 4.1 材质：iOS 只有 3 档，安卓映射成 3 档 + 1 特例

| iOS 材质 | 出现次数 | 语义 | 安卓对应（本项目） |
|---|---|---|---|
| `.glassEffect(.regular, in:)` | **9 处**（2 处带 `.interactive()`） | 唯一「高级」表面：输入框、用户气泡、FAB、工具条、弹窗、搜索胶囊 | `GlassLevel.Glass`（唯一带折射的一档） |
| `.ultraThinMaterial` | 36 | 叠在照片/视频上的浮层、HUD、圆形翻页钮（15 处） | `GlassLevel.Overlay`（只模糊 + 极淡 tint） |
| `.regularMaterial` | 12 | 要"压得住文字"的中档：文件夹展开段、顶部 banner、Helper 卡 | `GlassLevel.Panel`（模糊 + 无折射） |
| `Material.bar` | 8 | 系统工具栏：设置底栏、分享底栏、聊天内搜索条 | `GlassLevel.Bar`（模糊 + 细高光） |
| `.thinMaterial` / `.thickMaterial` | 0 | iOS 自己也没用 | 我们也不做 |

**9 处玻璃的完整清单（安卓端就是要复刻这 9 个位置）**：

| # | 元素 | 形状 | 参数 |
|---|---|---|---|
| 1 | 新建会话 FAB 56×56 | 圆 | `Glass.regular.tint(沙色 0.30)` |
| 2 | 展开后的搜索胶囊（高 56） | 胶囊 | `Glass.regular` |
| 3 | 消息列表浮动圆钮（回顶/回底/下载） | 圆 | `Glass.regular.interactive()` |
| 4 | **输入框主体** | 圆角 **20** 连续 | `Glass.regular` |
| 5 | **用户消息气泡**（已发送态） | 圆角 **18**（非连续） | `Glass.regular` |
| 6 | 长按预览浮板 | 圆角 18 | 先铺不透明底再叠玻璃 |
| 7 | `/` 斜杠、`@` 文件弹窗 | 圆角 **20** 连续 | `Glass.regular` |
| 8 | 工具状态条（输入框上方） | 圆角 **10** 连续 | `Glass.regular` |
| 9 | 多选分享「已选 N 条」胶囊 | 胶囊 | `Glass.regular.interactive()` |

> 注意 iOS **没有**用 `GlassEffectContainer`（会吞掉 FAB 长按菜单）、没有 `buttonStyle(.glass)`（"glass-on-glass 读成一个凸起的竞争控件"）。安卓端同理：**不要做玻璃套玻璃**。

### 4.2 颜色

| 令牌 | 浅色 | 深色 |
|---|---|---|
| Accent | `#3686EE` | `#5490E4` |
| 页面背景 | `UI systemBackground`（安卓用上游现成的 `#F2F2F7`） | `#000000` |
| 输入框（玻璃回退底色） | `.white` | `white @0.12` |
| 用户气泡（玻璃回退） | `tertiarySystemFill` | 自适应 |
| 工具卡 | `tertiarySystemGroupedBackground` | 自适应 |
| **品牌沙色**（FAB 玻璃 tint） | `#B7AF96 @0.30` | `#C4B078 @0.30` |
| 品牌沙色（不透明回退/列表选中） | `#B7AF96` | `#504C42` |

**这是本次改造里唯一需要新引入的颜色**：`#B7AF96`（品牌沙）。上游安卓侧目前完全没有这个色 —— iOS 的 FAB 之所以不像"一个蓝色按钮"，就是因为它 tint 成了沙色玻璃。已列入令牌表。

### 4.3 圆角：7 档，前三档占 68%，**全部 continuous**

```
8 → 67 次   10 → 46   12 → 20   14 → 14   16 → 11   18 → 8   20 → 5    （22/7/6/5.5/5/4/3/2 为长尾）
胶囊：155 处 Capsule()
```

语义映射（安卓端照抄）：

| 半径 | 用在哪 | 安卓现状 |
|---|---|---|
| **8** | 代码块、行内图片、分类图标底、小色块 | 已有 8dp（`MarkdownText` 等） |
| **10** | 列表选中行、工具状态条、聊天内搜索框、设置行背景 | 半数用 10，半数用 12 → 需统一 |
| **12** | 设置卡、UserAsk 卡、Helper 卡、BackupHUD | 上游用 14/16 |
| **14** | 顶部 banner、日程卡、分享预览块 | 无 |
| **16** | **文件夹容器**（唯一） | 上游用 16 |
| **18** | **消息气泡**（唯一） | 上游用 16/18 混用 |
| **20** | **输入框 / 弹窗**（最大） | 上游 composer 是 20 ✅ |

两条硬规则：
1. **连续曲率**：iOS 196 处圆角全是 `.continuous`（超椭圆）。安卓的 `RoundedCornerShape` 是圆弧圆角，同半径下看起来更"方"。**这是观感差异的第一来源**，必须实现 `ContinuousRoundedRectShape`。
2. **同心圆角**：内层 = 外层 − 内边距（20 − 6 = 14）。玻璃条里嵌按钮时照此算。

### 4.4 描边与阴影

- **发丝描边 0.5pt（≈0.5dp）出现 63 次**，是绝对主力；玻璃兼容层用 0.75pt。安卓端：玻璃默认由 `Highlight` 画 0.5dp 边，**不要再叠手写 border**。
- 阴影全仓 44 处，**半径 ≤ 8pt、不透明度 ≤ 0.2**。安卓端现有 `setShadowLayer(20dp)`（composer）明显偏重，要降到 ~8dp。
- **铁律：上了玻璃就删掉手写阴影和描边**（iOS 原注释：叠在一起会读成"暗色光晕"而不是"深度"）。

### 4.5 字体与字号（type scale）

| 层级 | 字号/字重 |
|---|---|
| App 标题 | 18.5 / semibold |
| 聊天页标题 | 16 / semibold |
| **消息正文（基准）** | **16.5 / regular** |
| 会话行标题 | 16 / semibold |
| 会话行副标题 | 14 / regular |
| 日期 | 13 / regular |
| 分组头 | subheadline / semibold（不转大写） |
| 工具卡标题 | 13 |
| 用量胶囊、时间戳 | 10 / **monospaced** |
| 状态徽标 | 9 / semibold |
| 角标 | 9 / bold（16×16 圆） |

字重只用 **semibold + medium**，不用 bold/heavy。安卓现状 `fontSize=` 出现 349 次，散落严重 —— 令牌化时按上表归一。

### 4.6 间距（4pt 基准，实际 7 个数：2/4/6/8/10/12/16）

| 位置 | 值 |
|---|---|
| 页面级水平边距 | **16** |
| 卡片 / 输入框水平边距 | **12** |
| 列表行垂直内距 | **12** |
| 列表行卡片 inset | horizontal 6 / vertical 2 |
| 助手消息行 | horizontal 16 / vertical 6 |
| 用户气泡内 | horizontal 14 / vertical 10 |
| 图标与文字 | `spacing: 4`（103 次，最高频） |
| 行内块之间 | 6 / 8 |
| 块之间 | 12 |

### 4.7 动效

| token | iOS | Compose |
|---|---|---|
| fast | `.easeInOut(0.15)` | `tween(150, FastOutSlowIn)` |
| **base** | `.easeInOut(0.2)`（28 处，最常用） | `tween(200, FastOutSlowIn)` |
| medium | `.easeInOut(0.25)` | `tween(250)` |
| slow | `.easeInOut(0.35)` | `tween(350)` |
| 弹簧（FAB 吸附） | `.spring(response: 0.35, dampingFraction: 0.75)` | `spring(dampingRatio = 0.75f, stiffness = 300f)` |
| 弹簧（通用） | `response 0.3 / damping 0.8` | `spring(0.8f, 400f)` |
| 按压 | `scaleEffect(0.9)` | `graphicsLayer` scale 0.9 + 上述弹簧 |
| 呼吸（上下文超限） | `1.4s repeatForever`（`reduceMotion` 时关） | `rememberInfiniteTransition`，跟随 `ANIMATOR_DURATION_SCALE` |

### 4.8 iOS 的五条工程铁律 → 安卓端对照

| # | iOS 铁律 | 安卓端做法 |
|---|---|---|
| 1 | 内容必须画在玻璃**里面**，`.overlay` 会被盖住 | Compose 里 `drawBackdrop` 只画背景采样，内容照常绘制 → 天然满足；但**高光/内阴影要指定 `onDrawFront`** |
| 2 | 玻璃不提供命中区域 | Compose 的 `Modifier` 不影响 hit test，天然满足；但用 `Box` 包玻璃时注意 `clickable` 要挂在**外层** |
| 3 | 上玻璃后删掉手写阴影/描边 | 逐个删（见 §5.8 触点表） |
| 4 | **玻璃背后要有东西可采样**，否则退化为常量（iOS 文件夹卡最终改成实测常量色） | **安卓端必须先加背景层**（见 §5.3.1），否则纯色背景上模糊零收益 |
| 5 | 玻璃不能跨 List 行合并（会在每段之间露出接缝） | 安卓端同理：**列表不做逐行玻璃**，用一块整体玻璃条 + 局部高亮 |

### 4.9 逐界面规格

| 界面 | 玻璃位置 | 关键数值 |
|---|---|---|
| 会话列表 | 顶部标题栏（滚动时）、搜索胶囊、新建 FAB | FAB 56×56，边距 16，底部留白 20；搜索胶囊高 56，宽 = 屏宽−16×2−56−10 |
| 聊天页 | 顶栏、输入框、工具状态条、浮动圆钮、用户气泡 | composer r20、气泡 r18、工具条 r10、圆钮直径 44（估，取 iOS 三档之一） |
| `/`、`@` 弹窗 | 整个弹窗 | r20 连续 |
| 图片/视频全屏 | 关闭/翻页圆钮、底部条 | ultraThin 档，15 处圆钮 |
| 设置页 | 固定底栏、分组卡 | `Material.bar` 档 + 卡片 r12 |
| 长按预览浮板 | 整块 | r18，先铺不透明底再玻璃 |
| 多选分享 | 底部「已选 N 条」胶囊 | 胶囊 + interactive |
| 终端 / 文件预览 | 仅顶栏 | 内容区不透明（原生 View 采样不到） |

### 4.10 iOS 有、安卓做不到 / 不该做的

1. iOS 26 导航栏**自己不画背景**，背景来自下方 scroll view 的 top edge effect（`CABackdropLayer`）。安卓没有这层系统联动，只能自己在滚动位置变化时插值顶栏透明度。
2. iOS 的玻璃随系统「减少透明度」一键退化；安卓要靠我们手动接 `Settings.Global` + 自己的设置项。
3. iOS 有两处 glass **叠在玻璃上**的场景（长按预览浮板、sheet 内搜索条），安卓端性能上不划算，改用「不透明底 + 单层玻璃」。
4. iOS 的 FAB 可以 morph 成搜索条（`glassEffectID` 死代码，实际用 `matchedGeometryEffect`）；安卓的 `SharedTransitionLayout` 在 1.9 上可做但成本高 → **首版不做 morph**，只做淡入缩放。

---
## 5. 技术架构

### 5.1 新增模块 `:glass`

```
src/android/
├── settings.gradle.kts              # include(":glass")
└── glass/
    ├── build.gradle.kts             # com.android.library + kotlin.android + compose
    ├── LICENSE-APACHE-2.0           # 参考库许可（合规要求）
    ├── NOTICE                       # 归属声明
    └── src/main/kotlin/app/minis/glass/
        ├── core/                    # 移植自 Backdrop（1.2k 行）
        │   ├── Backdrop.kt
        │   ├── DrawBackdropModifier.kt
        │   ├── RuntimeShader.kt / RuntimeShaderCache.kt
        │   ├── LayerRecorder.kt / InverseLayerScope.kt
        │   ├── ShapeProvider.kt
        │   ├── backdrops/{LayerBackdrop, CanvasBackdrop, CombinedBackdrop}.kt
        │   ├── effects/{Blur, Lens, Vibrancy, ColorFilter}.kt
        │   ├── highlight/{Highlight, HighlightModifier}.kt
        │   └── shadow/{Shadow, InnerShadow, *Modifier}.kt
        ├── shape/ContinuousRoundedRectShape.kt   # 自研，替代 com.kyant.shapes
        ├── token/GlassTokens.kt                  # §4 的数值
        ├── token/GlassMotion.kt                  # spring 映射
        ├── capability/GlassCapability.kt         # 三档能力 + 设备探测
        ├── host/GlassBackdropHost.kt             # 页面级共享 backdrop
        ├── components/{GlassSurface, GlassBar, GlassSheet, GlassDialog,
        │               GlassMenu, GlassFab, GlassCard, GlassSegmented, GlassToggle}.kt
        └── internal/{GlassDebug, GlassFrameBudget}.kt
```

独立的 library module 而不是塞进 `:app` 的理由：① 强制「玻璃层不依赖业务代码」的单向依赖；② 可以单独跑 Compose preview / 单元测试；③ 未来上游升级 Kotlin 后，可以整体切回 Maven 依赖（删模块即可）。

### 5.2 移植改造点（源码级）

| 参考库 | minis 环境 | 处理 |
|---|---|---|
| `expect/actual isRuntimeShaderSupported()` | Android-only | 直接写成 `Build.VERSION.SDK_INT >= 33` |
| `skikoMain` / `desktopMain` / `iosMain` | 不需要 | 删除 |
| `org.jetbrains.compose.*` 依赖 | androidx Compose | 换 import，无 API 差异 |
| `com.kyant.shapes.RoundedRectangularShape` | 无此库 | 自研 `ContinuousRoundedRectShape : Shape`，实现 `createOutline`（超椭圆）+ `cornerRadii` 数组供 `Lens.kt` 使用 |
| `com.kyant.shapes.Capsule` | — | `RoundedCornerShape(50%)` |
| Compose 1.12 API（若有） | Compose 1.9 | 逐项核对：`GraphicsLayer.record/drawLayer`、`rememberGraphicsLayer`、`Modifier.Node`、`GlobalPositionAwareModifierNode` 在 1.9 均可用；出现缺失 API 时用等价实现填补（预计 ≤3 处） |

**验收**：`:glass` 模块能编译、能在 demo 页画出与参考 demo 一致的玻璃（对比截图），APK 体积增量 < 60KB。

### 5.3 Backdrop 供给模型（本方案最关键的架构决策）

三种供给方式，按「采样成本 / 正确性」取舍：

| 模型 | 做法 | 用在哪 | 成本 |
|---|---|---|---|
| **A. 页面级共享** | 页面根节点一份 `LayerBackdrop`，所有玻璃元素采样同一层 | 会话列表、聊天页、设置页 | 1 次录制/帧 |
| B. 局部供给 | 只把「滚动内容区」录成一层，chrome 采样它 | 聊天页（消息列表 + 输入条） | 1 次录制/帧 + 少量裁剪 |
| C. 静态背景 | 用固定渐变/图片当 backdrop（`CanvasBackdrop`） | 全屏预览页、终端 | 0 |

**决策：主用 A+B，禁止每元素独立录制 C 之外的第三种做法。**

z-order 与 WebView：
- 玻璃元素必须与 backdrop 源在同一 Compose 树内、且在源码顺序上位于其后绘制。
- 压在 `AndroidView(WebView)` 之上的玻璃（浏览器页顶栏、文件预览顶栏）：**改用 `CanvasBackdrop`（静态渐变）**，否则采样到黑块。
- 终端（自绘 Canvas）同理走 C。

### 5.3.1 「玻璃要有东西可采样」—— 必须先做背景层（P0）

**这是改造的第一块砖，不是可选项。**

现状：`Theme.kt` 的 `background = #F2F2F7`（深色 `#000000`），`AppNavigation.kt:565` 给 NavHost 铺同一色，各屏 `Scaffold(containerColor=…)` 再铺一层。所有"背后"都是纯色 → **模糊纯色 = 纯色**，玻璃会退化成"半透明灰卡片"，用户根本看不出改了。

iOS 之所以成立，是因为它有真实的滚动内容（消息、列表、照片）在玻璃后面走。安卓端要有等价物，两条路并用：

1. **让内容当背景**（首选）：聊天页/会话列表把滚动内容录成 backdrop 源（模型 B），玻璃条采样真实内容 —— 滚动时能看见文字在玻璃后流动，这就是"液态"的来源。
2. **给静态页加一层"墙纸"**（兜底）：新增 `GlassWallpaper` —— 极低饱和的层叠径向渐变（用主题色 + 品牌沙，明暗两套，alpha 3~6%），铺在 `AppNavigation` 的 NavHost 背景上。作用有二：给玻璃提供可采样的层次；避免纯色页上的玻璃变成死灰。**不是炫技的彩色背景**，肉眼几乎看不出是渐变，但玻璃采样后会有真实的色温。

反例（iOS 自己踩过）：文件夹卡背后就是纯色列表背景，玻璃逐帧闪烁，最后改成**实测常量色**（暗 `rgb(18,18,18)` / 亮 `rgb(252,252,252)`）。安卓端同理：**背后没有内容的玻璃，直接给常量色，别开 blur**。

### 5.4 组件 API 设计

```kotlin
// 统一入口：所有玻璃表面都从这里出
@Composable
fun GlassSurface(
    level: GlassLevel,                     // Bar / Panel / Popover
    shape: Shape = ContinuousRoundedRectShape(GlassTokens.Radius.L),
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,       // 默认按 level + 明暗模式取令牌
    interactive: Boolean = false,           // 按下形变（LiquidButton 效果）
    content: @Composable BoxScope.() -> Unit,
)

// 页面壳：提供共享 backdrop
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable (Backdrop) -> Unit, // 把 backdrop 传给子树
)
```

约束（写在 KDoc 里、并由 lint 检查）：
- `GlassSurface` 在 `LazyColumn` 的 `item {}` 内**禁止使用**（会每帧录制多份）。
- 同一屏 `GlassSurface` 数量 > 8 时自动降级为 `Panel` 级（无 lens）。

### 5.5 主题与令牌注入

- `MinisTheme()` 里新增 `CompositionLocalProvider(LocalGlassStyle)`（`Off / Standard / High`）+ `LocalGlassCapability`。
- `GlassTokens` 是纯数据 object，深/浅色两套；不引用 `MaterialTheme`，避免循环依赖（由调用方传入 `isDark`）。
- 现有 `MaterialTheme.shapes` **不动**，玻璃组件自己用 `ContinuousRoundedRectShape`。

### 5.6 能力分级与降级

| 档 | 条件 | 表现 |
|---|---|---|
| `High` | SDK≥33 且设备 RAM≥6G 且用户选「高」 | blur + lens（含色散）+ vibrancy + 高光 + 内阴影 |
| `Standard`（默认） | SDK≥33 | blur + lens（无色散）+ vibrancy + 高光 |
| `Lite` | SDK 31~32 | RenderEffect blur + 半透明 tint + 静态高光（无 lens） |
| `Off` | SDK<31 / 用户关闭 / 系统「移除透明度」 | 纯半透明表面（观感等价于上游） |

额外规则：
- 滚动中（`LazyListState.isScrollInProgress`）→ 降一档，停稳 120ms 恢复。
- 连续 3 帧超 16ms（`GlassFrameBudget` 监测）→ 本页降一档，直到下次进入。
- `Settings.Global.ANIMATOR_DURATION_SCALE == 0` → 关闭 `interactive` 形变。

### 5.7 与现有组件的对接策略：「先收敛，再玻璃化」

上游的外壳是**三套并行实现**（§2.3）。直接糊玻璃要改 200+ 个调用点，且以后每次跟上游都重新冲突。所以分两步：

**第一步：收敛（纯重构，零视觉变化，可单独提交/单独回退）**

| 收敛对象 | 现状 | 目标 |
|---|---|---|
| 顶栏 | `SettingsScaffold`(36 处) + 裸 `TopAppBar`(29 处) | 一个 `MinisTopBar`，`SettingsScaffold` 内部改调它 |
| 分区卡 | 3 套同名 `SettingsSection`（圆角 12/14/16） | 一套 `SettingsSection`，圆角取 12 |
| 弹窗 | `MinisAlertDialog`(21) + 裸 `AlertDialog`(83) | 全部转 `MinisAlertDialog`；后者内部换 `GlassDialog` |
| Sheet | 29 处 `ModalBottomSheet` 各写各的 containerColor | 抽 `MinisSheet` 壳，统一容器/拖柄/scrim |
| 菜单 | `MinisMenu` 24 处 + `Popup` 10 处 | `MinisMenu` 内部换 `GlassMenu`；`Popup` 保持不动 |

收敛这一步的收益独立于玻璃：**报告里点名的「3 套圆角」「2 套顶栏」不解决，后面所有对齐 iOS 圆角/间距的工作都会反复返工。**

**第二步：换实现（不动调用点）**

1. `MinisAlertDialog` / `MinisMenu` / `MinisSheet` / `SettingsSection` 的**函数签名不变**，内部换成 `GlassSurface`。
2. 界面级重排只做 4 处：会话列表顶栏 + 双 FAB、聊天页顶栏、聊天输入条（自绘 `drawBehind`，必须重写）、设置首页卡片。
3. **禁止改动**：`ChatViewModel`、`data/`、`provider/`、`sandbox/`、`agent/` 下任何文件。业务逻辑零触碰是验收硬指标。

### 5.8 上游文件触点表（改动面预算）

**A. 新增（不冲突）**

| 路径 | 内容 |
|---|---|
| `src/android/glass/**` | 独立 `:glass` 模块（§5.1） |
| `src/android/app/src/main/java/com/openminis/app/ui/glass/**` | 业务侧玻璃封装（`GlassWallpaper`、`MinisTopBar`、`MinisSheet`、页面级 host） |
| `scripts/glass/fetch-prebuilt-native.sh` | 预编译产物拉取 |
| `docs/glass/**`、`MODIFICATIONS.md` | 文档与合规 |
| `.github/workflows/{android-build,upstream-watch}.yml` | CI |

**B. 修改上游文件（预算 ≤ 20 个）**

| 文件 | 改动 | 必要性 |
|---|---|---|
| `settings.gradle.kts` | `include(":glass")` | 必须 |
| `app/build.gradle.kts` | ① `implementation(project(":glass"))` ② `-Pminis.prebuiltNative` 开关（已加） ③ `RELEASE_REPO`→fork ④ 可选 rclone 开关 | 必须 |
| `MainActivity.kt` | 套 `GlassBackdropHost` + 玻璃风格初始化 | 必须 |
| `ui/theme/Theme.kt` | 注入 `LocalGlassStyle` / `LocalGlassCapability`；背景改 `GlassWallpaper` | 必须 |
| `ui/navigation/AppNavigation.kt` | NavHost 背景层（`:565`）；转场 spec 不动 | 必须 |
| `ui/navigation/ChatSplitScaffold.kt` | 双栏分隔线 | 需 |
| `ui/chat/ChatScreen.kt` | 顶栏 + 输入区外壳（`:2973` Scaffold、`:6349` 自绘输入卡） | 必须 |
| `ui/chat/ChatComposerWidgets.kt` | 输入条内部组件（按钮行/附件菜单） | 必须 |
| `ui/sessions/SessionListScreen.kt` | 顶栏滚动渐显（`:1809` DualFabRow） | 必须 |
| `ui/components/MinisAlertDialog.kt` | 内部换 `GlassDialog` | 必须 |
| `ui/components/MinisMenu.kt` | 内部换 `GlassMenu` | 必须 |
| `ui/components/SettingsSection.kt` | 收敛 + 玻璃卡 | 必须 |
| `ui/components/SectionDesign.kt` | 同上 | 需 |
| `ui/settings/AppearanceScreen.kt` | 新增「玻璃风格」三项设置 | 必须 |
| `res/values/themes.xml` / `values-night/themes.xml` | windowBackground 对齐首帧 | 需 |
| `res/xml/shortcuts.xml` | `targetPackage` 改 `${applicationId}` | 必须（共存版） |
| `data/repository/AppIconRepository.kt` | 硬编码包名改 `BuildConfig.APPLICATION_ID` | 必须（共存版） |
| `AndroidManifest.xml` | 追加 `minisliquid://` 深链别名 | 需 |

> 加上改包名必须碰的 `res/xml/shortcuts.xml` 与 `data/repository/AppIconRepository.kt`（各一行），触点总数约 **22 个**。**任何超过 25 个文件的方案都要先停下来重估。**

---

## 6. 构建、签名与发布

### 6.1 三条构建路径（按用途分工）

| 路径 | 主机 | 用途 | 耗时（实测/估算） | 原生库 |
|---|---|---|---|---|
| **A. 本机预编译** | 手机内 PRoot Alpine aarch64（本机） | 日常 UI 迭代、编译验证、装到手机上看效果 | 首次 25~40 分钟（下载依赖），增量 3~8 分钟（估） | 复用官方 APK 抽出的 .so |
| **B. 桌面完整** | x86_64 Linux/macOS + NDK 27 | 正式回归、出可发布包 | 40~60 分钟（含 native 编译） | 源码编译 |
| **C. GitHub Actions** | ubuntu-latest | 发布、给别人下载 | 45~70 分钟 | 源码编译 |

**A 的前提**（已验证并落地）：
1. `ANDROID_HOME=/opt/android-sdk`（含 platform-36、build-tools 36、cmdline-tools）、JDK 17、Gradle 8.11.1（wrapper 会自取）。
2. 复制 `app/provider-customization.properties.example` → `.properties`（留空可编译）。
3. `-Pminis.prebuiltNative=true`：跳过 CMake，直接用 `app/src/main/jniLibs/arm64-v8a/*.so`。
4. `app/src/main/assets/` 补齐 `proot-aarch64` 与 `alpine-minirootfs.tar`（从官方 APK 抽出）。
5. 变体名是 `githubDebug`，命令为 `:app:compileGithubDebugKotlin` / `:app:assembleGithubDebug`。

**A 的限制**：
- **不能编 native**：NDK 无 aarch64 Linux 主机工具链 → 改 C/C++ 必须去 CI 或桌面。
- APK 只能自签，且与官方签名不同（不能覆盖安装）。
- 打包 45MB 的 `libgojni.so` + 18MB `libonnxruntime.so` 时 I/O 压力大，`assemble` 比 `compile` 慢很多；日常用 `compile` 验证，出包才 `assemble`。

### 6.1.1 已验证的第一个真实阻塞：`rclone.aar` 不存在

**现象**（本机实测）：
```
Execution failed for task ':app:checkGithubDebugAarMetadata'.
> Could not resolve all files for configuration ':app:githubDebugRuntimeClasspath'.
   > Could not find :rclone:.
     Searched in the following locations:
       - file:.../app/libs/rclone.aar
```

**原因**：`app/build.gradle.kts:448` 依赖 `flatDir("app/libs")` 里的 `rclone.aar`；该文件是 `deps/build_rclone_android.sh` 的产物（Go + gomobile + NDK 交叉编译），**上游不提交**。也就是说：**任何干净 clone 都编不出 APK，必须先把 Go 原生依赖编出来**。这跟「手机内能不能编 Kotlin」是两件事 —— Kotlin 编译本身还没开始就被这个依赖卡住了。

**三条解法**（按推荐顺序）：

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **S1 反编译重建**（推荐用于本机构建） | 从官方 APK 的 `classes.dex` 里用 jadx 反编译 `com.openminis.rclone.gomobile.**` + `go.**`（实测只有 4 个类：`Gomobile`、`LogSink`、`Gomobile$proxyLogSink`、`RcloneRPCResult`，外加 gomobile 运行时的 `go.Seq`/`go.Universe`），编译成 jar，与 `libgojni.so` 一起打成 AAR | 产物**功能完整**（JNI 符号完全对上），本机也能出可发布包 | 一次性工程；反编译代码需核对 |
| S2 CI 源码编译 | 在 `ubuntu-latest` 跑 `deps/build_rclone_android.sh` | 最干净 | 只有 CI 能出包，本机永远只能编一半 |
| S3 空壳 stub（仅开发用） | 同包名同签名的 Java 空实现，调用即抛异常 | 10 分钟能编过 | 备份远程 / Tailscale 功能失效，**绝不能进发布包** |

**已确认的 API 面**（从 `classes.dex` 提取，S1/S3 都按这个实现）：

```java
package com.openminis.rclone.gomobile;

interface LogSink { void line(String s); }

// RcloneRPCResult: long status / String output（Kotlin 侧只用这两个）
final class Gomobile {
  static native void rcloneInitialize();
  static native void rcloneFinalize();
  static native String rcloneSetSocks(String socks);
  static native RcloneRPCResult rcloneRPC(String method, String input);
  static native String tsStart(String authKey, String hostname, String dir);
  static native String tsSetInterfaces(String json);
  static native String tsStatus();  /* tsStop/tsLogout/tsRoutes/tsSetAcceptRoutes/tsSetExitNode/... 同上，返回 String */
  static native void   tsSetLogger(LogSink sink);
  static native String netConfigure(String json);   /* net* 共 21 个，签名形如 (String[,String[,long]]) -> String */
}
```

Kotlin 侧消费点仅 4 个文件：`backup/remote/RcloneBridge.kt`、`network/TailscaleNode.kt`、`network/TailscalePathMonitor.kt`、`network/TailscaleNode.kt`。


**A 的仓库副作用**（必须 gitignore）：
```
src/android/app/src/main/jniLibs/arm64-v8a/libgojni.so
src/android/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so
src/android/app/src/main/jniLibs/arm64-v8a/libpty_bridge.so
src/android/app/src/main/jniLibs/arm64-v8a/libjieba_jni.so
src/android/app/src/main/jniLibs/arm64-v8a/libminis_crash_handler.so
src/android/app/src/main/jniLibs/arm64-v8a/libc++_shared.so
src/android/app/src/main/jniLibs/arm64-v8a/libRealtimeCutVadLibrary*.so
src/android/app/src/main/jniLibs/arm64-v8a/libwebrtc-audio-processing-1.so
src/android/app/src/main/assets/proot-aarch64
src/android/app/src/main/assets/alpine-minirootfs.tar
src/android/local.properties
```
用 `scripts/glass/fetch-prebuilt-native.sh` 一键从官方 release APK 拉取（脚本内 pin 上游版本号）。

### 6.2 CI 工作流

`.github/workflows/android-build.yml`：

```
on: [push to glass-ui branches, workflow_dispatch, release published]
jobs:
  build:
    runs-on: ubuntu-latest (8 core / 32GB RAM / 14GB 磁盘 → 需要先清理)
    steps:
      1. checkout (with submodules: recursive)   # deps/ish、deps/proot
      2. setup-java 17 + gradle cache
      3. sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
      4. apt install 依赖（构建 ffmpeg/lame/proot 需要：make, autoconf, automake, libtool, pkg-config, ninja）
      5. bash deps/build_proot.sh && ./scripts/prepare_android_sandbox.sh
      6. (可选) deps/build_ffmpeg.sh / build_lame.sh / build_rclone_android.sh  # 视上游是否已预置
      7. ./gradlew :app:assembleGithubRelease  -Pminis.prebuiltNative=false
      8. 签名（secrets: KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD）
      9. upload-artifact + 发 Release 附件
```

注意点（都来自上游踩坑记录）：
- **磁盘**：runner 只有 14GB 空闲，清 `android` SDK 无用包 + `docker` + `ghc` 能腾 ~20GB；native 编译中间产物很吃盘。
- **rclone.aar**：`app/libs/rclone.aar` 由 `deps/build_rclone_android.sh` 生成，上游不提交。CI 必须先跑该脚本，否则 `:app` 配置期就会因为 flatDir 找不到 aar 而失败（推断，配置期不会立刻失败，但打包会缺类）。
- **submodule**：`deps/proot` 必须有，否则 `build_proot.sh` 报错。
- **缓存**：`~/.gradle/caches` + `src/android/app/.cxx` 命中率高，能把二次构建压到 ~15 分钟。

### 6.3 签名与应用标识（已决策：共存版）

**先说死一件事：不可能做出「覆盖安装官方版」的包。** 覆盖安装要求签名一致，而签名私钥只在上游手里 —— 这是 Android 签名机制的全部意义。任何声称能做到的做法（改 applicationId 装进去但共用数据、用 debug key 冒充）都不成立。

**已决策：共存版**

| 项 | 值 |
|---|---|
| applicationId | `com.openminis.app.glass` |
| 应用名 / 图标 | **保持与官方一致**（主人要求） |
| 效果 | 两个 App 可并存；官方版数据完全不受影响；我们的首次安装是全新状态（可用官方的备份导出/导入迁移） |

**改包名的真实风险点（全仓仅 2 处硬编码，其余都用 `${applicationId}`）**：

| 文件 | 内容 | 处理 |
|---|---|---|
| `app/src/main/res/xml/shortcuts.xml:17,30,43` | `android:targetPackage="com.openminis.app"` | 改成 `${applicationId}` |
| `app/src/main/java/com/openminis/app/data/repository/AppIconRepository.kt:32` | `PACKAGE_NAME = "com.openminis.app"` | 改成 `BuildConfig.APPLICATION_ID` |

其余（FileProvider authority `${applicationId}.fileprovider`、Shizuku `${applicationId}.shizuku`、通知、权限）都由 AGP 占位符自动隔离。

**已知副作用**：
- 深链 `minis://` 两边都声明 → 系统会弹选择器。缓解：我们的构建额外声明 `minisliquid://`（不删 `minis://`）。
- 桌面上会有两个同名同图标的 Minis → 靠位置/长按信息区分（主人已确认不改图标，此处仅记录）。

> 上一版文档我推荐过「保持原 applicationId」，**那是错的**：它既换不来覆盖安装（签名不同），又会让首次安装必须卸载官方版、连带丢掉全部会话与沙箱数据。已更正。

### 6.4 签名方案

- 生成专用 keystore：`keytool -genkeypair -v -keystore minisglass.jks -keyalg RSA -keysize 4096 -validity 10000 -alias minisglass`（JDK 自带 keytool；本机 JVM 可用）。
- keystore **不进仓库**；CI 用 `KEYSTORE_BASE64` secret 还原。
- 仓库内提供 `keystore.properties.example`，`app/build.gradle.kts` 读取（不存在则回退 debug 签名，保证 fork 者开箱能编译）。

### 6.5 版本号与自更新

- `versionName = "<上游版本>-glass.<构建号>"`，例如 `1.15-glass.1`。
- `versionCode = 上游 versionCode * 100 + build`（37*100+1=3701），保证单调递增且能看出上游基线。
- `RELEASE_REPO` 改成 `<主人用户名>/<fork 仓库名>`；`UPDATE_CHANNEL` 保持 `stable`。**不改的话**：App 内「检查更新」会去拉上游官方 APK，签名不同安装失败，用户看到的是「下载完装不上」的坏体验。

### 6.6 许可证合规（GPLv3）

| 要求 | 做法 |
|---|---|
| 保留版权声明 | 保留上游 `LICENSE`（GPLv3）与所有文件头 |
| 标注修改（§5a） | 每个改动文件加 `// [GLASS] 修改说明`；根目录 `MODIFICATIONS.md` 汇总「fork 自 OpenMinis @ <commit/tag>，修改了哪些文件、改了什么」 |
| 提供源码 | 仓库公开，且 release 附件里附 `source` tag 链接 |
| 传染性 | 本 fork 整体仍为 GPLv3；新增 `:glass` 模块也声明 GPLv3（虽然其内核源自 Apache-2.0 的 Backdrop） |
| 第三方归属 | `:glass/LICENSE-APACHE-2.0` + `:glass/NOTICE`（"Contains code derived from Kyant0/AndroidLiquidGlass, Apache-2.0"）；根 `THIRD_PARTY_LICENSES.md` 增加一节 |
| 上游商标 | README 里写明「非官方 fork，与 OpenMinis 团队无关」，不使用其 logo 作为应用图标（建议自绘图标） |

---

## 7. 质量保障

### 7.1 测试矩阵

| 维度 | 覆盖 |
|---|---|
| API 级别 | 26（Lite/Off 下限）、31、33、34、35、36 |
| 设备形态 | 手机、折叠屏（双栏 `ChatSplitScaffold`）、平板 |
| 明暗 | 浅色 / 深色 / 跟随系统 |
| 桌面模式 | 分屏、悬浮窗（多窗口下 backdrop 坐标换算易错） |
| 语言 | zh-Hans / en / ar（RTL 下玻璃形状要跟 `LayoutDirection`） |
| 性能 | 中端机（目标）、旗舰（上限） |

### 7.2 性能预算

| 指标 | 预算 | 测法 |
|---|---|---|
| 聊天页滚动 | 帧时间 P95 ≤ 16ms，无 > 32ms 帧 | `perf` 变体 + `simpleperf`/`dumpsys gfxinfo` |
| 会话列表滚动 | 同上 | 同上 |
| Sheet 打开 | ≤ 250ms 到完全展开 | `FrameMetrics` |
| 冷启动 | 相比上游增量 ≤ 150ms | `am start -W` |
| 内存 | backdrop 额外占用 ≤ 24MB（1080p 页面层 2 份） | `dumpsys meminfo` |
| 电量 | 10 分钟聊天，相比上游增量 ≤ 8% | 粗略 `dumpsys batterystats` |

### 7.3 稳定性风险点

1. **RenderEffect 崩溃**：Android 12~13 上部分 GPU（Mali-G52/G57 老驱动）对大尺寸 `BlurEffect` 会硬崩（native crash）。防护：限制 backdrop layer 尺寸 ≤ 屏幕像素；`try/catch` 包不住 native crash，只能靠「尺寸上限 + 低端机默认 Lite」。
2. **`GraphicsLayer.record` 在 `draw()` 内重入**：参考库的 `recordLayer` 在绘制期间录制，若 backdrop 源与玻璃元素互相包含会死循环 → 架构上强制 backdrop 源必须是玻璃元素的「兄弟前置节点」。
3. **WebView 合成**：见 §5.3。
4. **Compose 编译器插桩**：玻璃组件大量 lambda + `Modifier.Node`，注意别引入每帧重组（用 `remember` + `derivedStateOf`）。

### 7.4 功能回归清单（每次出包跑）

- [ ] 冷启动 → 首屏渲染正常，无白屏/闪屏
- [ ] 新建会话 → 发消息 → 流式输出 → 工具调用卡片展开 → 图片渲染
- [ ] 沙箱：打开终端 → 执行 `apk add` 装包 → 跑 python 脚本
- [ ] 文件：浏览 workspace → 预览 md/png/pdf → 分享出去
- [ ] 设置：切主题、改字体大小、切语言（含 RTL）、导出/导入备份
- [ ] provider：新增 API key、模型列表拉取、切换模型
- [ ] MCP / 技能 / 记忆：各开一次
- [ ] 通知与前台服务：跑长任务时切后台再回来
- [ ] 深链：`minis://` 打开会话
- [ ] 分屏/旋转：玻璃不错位
- [ ] 无障碍：TalkBack 能读出玻璃按钮；「移除动画」开启后无动画

---

## 8. 维护与上游同步

### 8.1 远端与分支

```
origin    git@github.com:<主人>/<fork>.git
upstream  https://github.com/OpenMinis/OpenMinis.git

main      = 上游 main 的镜像（只快进，不带我们的改动）
glass-ui  = main + 玻璃改造（我们的长期分支，默认分支）
```

日常：
```sh
git fetch upstream --tags
git checkout main && git merge --ff-only upstream/main
git checkout glass-ui && git rebase main     # 冲突只可能出现在 §5.8 的 16 个文件
```
发布：`glass-ui` 打 tag `v1.15-glass.1`。

### 8.2 `[GLASS]` 标记规范

每个改动点：
```kotlin
// [GLASS] <一句话说明>  (upstream: <上游该处原始行为>)
```
目的：rebase 时 `rg -n '\[GLASS\]'` 一秒定位全部冲突候选；也方便未来上游吸收我们的改动。

### 8.3 冲突处理原则

| 情况 | 处理 |
|---|---|
| 上游改了同文件的无关区域 | 直接 rebase |
| 上游重写了我们改的组件 | 以**上游逻辑**为准，把我们那层玻璃包在新实现外面 |
| 上游自己升级到 Material3 Expressive / 自带模糊 | **丢掉我们的对应实现**，改用上游，玻璃层收窄到上游没做的部分 |
| 上游升级 Kotlin ≥ 2.4 | 评估切回 Maven 依赖 `io.github.kyant0:backdrop`，删掉移植代码（维护成本大降） |

### 8.4 上游漂移检测

`.github/workflows/upstream-watch.yml`：每天 09:00 检查 `OpenMinis/OpenMinis` 是否有新 tag / `main` 新提交，有则开 Issue（或直接尝试 rebase 并跑 CI，绿了开 PR）。这样主人不用手动盯。

### 8.5 长期演进路线

- **P1**（本项目完成时）：玻璃 + 令牌 + 开关。
- **P2**：跟随 iOS 逐屏对齐（每个上游版本挑 1~2 个界面做像素级对照）。
- **P3**：把 `:glass` 抽成独立可复用库开源（Apache-2.0 回馈社区，与 GPLv3 主仓分离）—— 可选。

---

## 9. 风险登记表

| # | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | 参考库代码用了 Compose 1.12 API，移植到 1.9 需补实现 | 中 | 工期 +1~2 天 | 先做「最小玻璃 demo」验证（M1），失败则降级为「blur + 高光 + 半透明」（无折射） |
| R2 | AGSL 在低端机渲染异常/崩溃 | 中 | 稳定性 | 能力分级 + 尺寸上限 + 默认 Lite 白名单 |
| R3 | 每帧录制导致掉帧（尤其列表） | 高 | 体验 | §5.3 单一 backdrop + 滚动降级 + 帧预算自适应 |
| R4 | 上游 UI 大改导致 rebase 冲突变多 | 中 | 维护成本 | 触点表 ≤16 文件 + `[GLASS]` 标记 + 冲突优先让位上游 |
| R5 | 手机内构建太慢，迭代效率低 | 高 | 工期 | compile 验证 + 只在必要时 assemble；必要时用 CI 出包（等 60 分钟） |
| R6 | rclone.aar / native 依赖在 CI 上编不出来 | 中 | 发版阻塞 | 提前跑通 CI（M0 验收项之一），实在不行改为「本机 prebuilt + 本地出包」 |
| R7 | GPLv3 合规疏漏 | 低 | 法律 | §6.6 清单 + 首次发布前逐项核对 |
| R8 | `applicationId` 决策失误导致用户数据丢失 | 低 | 用户 | 首发前明确告知；提供备份导入路径 |
| **R9** | **纯色背景让玻璃不可见**（采到 `#F2F2F7` → 模糊后还是 `#F2F2F7`） | **高（已确认）** | 整个项目白做 | §5.3.1 背景层 P0；静态页禁用 blur 改常量色 |
| **R10** | `rclone.aar` 缺失导致任何干净 clone 都编不过 | **已发生** | M0 阻塞 | §6.1.1 三方案，优先 S1 反编译重建 |
| R11 | 收敛 3 套 SettingsSection / 2 套顶栏时误改行为 | 中 | 功能回退 | 收敛单独提交、单独回退；每步跑回归清单 |
| R12 | 输入卡是自绘（`drawBehind`+`setShadowLayer`），不能靠换色玻璃化 | 已确认 | 主界面观感 | §5.8 单列为必须重写项，M3 单独排期 |

---

## 附录 A：本机（手机内）工作流命令

```sh
# 一次性准备
cd src/android
cp app/provider-customization.properties.example app/provider-customization.properties
echo "sdk.dir=/opt/android-sdk" > local.properties
export ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk
bash scripts/glass/fetch-prebuilt-native.sh 1.15        # 从官方 APK 抽 .so/assets

# 日常：只验证 Kotlin（3~8 分钟）
./gradlew :app:compileGithubDebugKotlin -Pminis.prebuiltNative=true --console=plain

# 出包并安装（慢，I/O 重）
./gradlew :app:assembleGithubDebug -Pminis.prebuiltNative=true
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk
```

## 附录 B：度量数据（2026-10-30 实测）

| 项 | 值 |
|---|---|
| 上游 Android 源码 | 1,497 文件 / 289,987 行 |
| UI 层 | 281 文件 / 140,402 行 |
| 上游 iOS 源码 | 1,040 文件 / 389,460 行（参照物） |
| 参考库 backdrop | 2,550 行（Apache-2.0） |
| 硬编码 `Color(0x…)` | 523 处（ui/ 内） |
| `MaterialTheme.colorScheme` 引用 | 1,221 处 |
| `ModalBottomSheet` / `AlertDialog(` | 131 / 104 次 |
| 官方 APK | 42.7MB（1.15 / build 37） |
| 官方 APK 内原生库 | 14 个 .so，共 64.8MB（libgojni 46MB + onnxruntime 17MB） |
| 本机沙箱 | 15GB RAM / 45GB 可用盘 / JDK17 / Gradle 8.11.1 / SDK 34+36 |
