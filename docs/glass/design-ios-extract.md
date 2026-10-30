# iOS 设计系统提取报告（Minis iOS · SwiftUI · iOS 26 Liquid Glass）

> **用途**：安卓端重写时 1:1 对标依据。
> **源码**：`/var/minis/workspace/minisglass/minis/src/ios/`（SwiftUI，GPLv3）
> **扫描规模**：1040 个 `.swift` 文件，约 20.0 MB 源码。
> **统计口径**：
> - 「令牌统计」（圆角 / 描边 / 阴影 / 弹簧）扫全仓 `*.swift`；
> - 「界面布局统计」（padding / spacing / font / 材质）限定 `Views/**/*.swift`，避免把 Agent、Backup、Provider 的网络层噪声算进来。
> - 所有 `rg` 原始输出均原样贴出，行号对应当前工作副本。
>
> **一句话结论**：这是一个**「系统材质优先」**的 iOS 26 应用——它不自己画毛玻璃，而是把 9 处关键表面交给 `glassEffect(.regular, in:)`，其余表面用 `Material` 三档（`.ultraThinMaterial` 36 次 / `.regularMaterial` 12 次 / `.bar` 8 次）+ 0.5pt 发丝描边 + 8~20pt 圆角补齐。品牌识别只靠**一个颜色**（沙色 `rgb(183,175,150)`）和**一个动作**（56pt 可拖拽玻璃 FAB）。

---

## 0. 令牌速查表（安卓端移植时先落这一张）

| 令牌 | 浅色 | 深色 | 出处 |
|---|---|---|---|
| AccentColor（强调色） | `#3686EE`（sRGB 0.212 / 0.525 / 0.933） | `#5490E4`（0.329 / 0.565 / 0.894） | `Assets.xcassets/AccentColor.colorset/Contents.json:6-11（浅）, 24-29（深）` |
| 页面背景 | `UIColor.systemBackground` | 自适应 | `Views/Chat/AIChatView/Support/ChatColors.swift:27` |
| 次级背景 | `UIColor.secondarySystemBackground` | 自适应 | 同上 `:28-29` |
| 输入框（非玻璃回退）底色 | `.white` | `white: 0.12` | 同上 `:31` |
| 用户气泡（非玻璃回退） | `UIColor.tertiarySystemFill` | 自适应 | 同上 `:35` |
| 工具卡底色 | `UIColor.tertiarySystemGroupedBackground` | 自适应 | 同上 `:36` |
| 品牌沙色（FAB 不透明回退 / 列表选中态） | `rgb(183,175,150)` | `rgb(80,76,66)` | `Views/ContentView/ContentView+FAB.swift:23-25`；`Views/ContentView/Sidebar/SessionListRows.swift:118` |
| 品牌沙色（玻璃 tint，alpha 0.30） | `rgb(183,175,150) @0.30` | `rgb(196,176,120) @0.30` | `Views/ContentView/ContentView+FAB.swift:44-46` |
| 主容器圆角 | 20（composer / `/`·`@` 弹窗） | — | `ComposerSurface.swift:103`；`AIChatView+InputPopups.swift:465` |
| 气泡圆角 | 18 | — | `ChatMessageViews.swift:182` |
| 卡片圆角 | 16（文件夹）/ 12（设置卡、Helper 卡）/ 10（工具条、列表高亮） | — | `SidebarSurfaces.swift:211`；`ContentView+EmptyState.swift:203`；`ToolLiveSheet.swift:3332` |
| 小色块圆角 | 8 | — | `MarkdownTheme.swift:54` |
| 发丝描边 | 0.5pt（63 次，绝对主力）；玻璃兼容层 0.75pt | — | 见 §5 |
| FAB | 56×56，屏幕边距 16，底部留白 20 | — | `DraggableFAB.swift:27-28`；`ContentView+FAB.swift:367-368` |
| 输入框最大高度 | 120（一行 + 1pt） | — | `ChatInputBar.swift:1078, 1085` |
| 正文基准字号 | 16.5 | — | `MarkdownTheme.swift:58` |
| 会话行 | 44pt 头像 + 16/12 padding（≈68pt 行高） | — | `SessionRow.swift:226, 336-337` |
| 弹簧 | `.spring(response: 0.35, dampingFraction: 0.75)`（FAB 吸附） | — | `DraggableFAB.swift:52` |

---

## 1. 主壳层结构

### 1.1 结论（层级 + 每层材质）

```
App
└─ ContentView                                   ← 根视图，唯一壳层
   │  body: GeometryReader → if wide → splitLayout / else stackLayout
   │  ContentView.swift:564-573
   │
   ├─ [宽屏 iPad/Mac] NavigationSplitView          ContentView+Layout.swift:32-39
   │  ├─ sidebar  : sessionList(useNavigationLinks: false)  → splitList
   │  │     List(selection:) .listStyle(.plain)
   │  │     .navigationSplitViewColumnWidth(min: 340, ideal: 380, max: 500)
   │  │     背景：系统 List 背景（无自定义色）；顶部导航栏玻璃由系统 edge effect 提供，
   │  │           macOS 27 上被 MacOS27OpaqueNavigationBar 打补丁成 `Material.bar`
   │  └─ detail   : detailView → AIChatView / "No Conversation Selected" 占位
   │
   ├─ [窄屏 iPhone] NavigationStack(path:)          ContentView+Layout.swift:43-91
   │  └─ sessionList(useNavigationLinks: true) → stackList   ← 同一套 UI，只是包 NavLink
   │
   ├─ sessionList 的两个变体共用同一套「底部浮层」：
   │  ├─ .safeAreaInset(edge: .bottom) { fabRow }      ContentView+SessionList.swift:290-293
   │  ├─ .overlay(alignment: .top) { folderMiniBarOverlay }   同上 :289
   │  ├─ .overlay { emptyState }                      同上 :288
   │  └─ .ignoresSafeArea(.keyboard, edges: showSearchBar ? [] : .bottom)  同上 :301
   │
   └─ 模态层：.sheet(item: activeToolSheet){ Settings / Rootfs / Browser / ... }
              .fullScreenCover(showTerminal){ ISHTerminalView }
              ContentView.swift:774-880

AIChatView（聊天页，ContentView 的 detail / destination）
└─ ZStack                                    AIChatView.swift:547
   ├─ messagesArea                           AIChatView+MessagesArea.swift:35
   │  └─ CollectionViewMessageListV3（UIKit UICollectionView 承载，虚拟化）
   │     + .overlay(alignment:.bottom) { 工具预览 + VStack{toolbar; inputBar} }
   ├─ 底部浮层 VStack{toolbar; inputBar}      AIChatView.swift:616-690
   │  ├─ ToolStatusBar（玻璃，圆角 10）        ToolLiveSheet.swift:3330-3343
   │  └─ inputBar → ComposerSurface（玻璃，圆角 20）  ComposerSurface.swift:101-113
   ├─ 顶部：.safeAreaInset(edge:.top) { topBanners }   AIChatView.swift:779
   └─ .background(ChatColors.background)      AIChatView.swift:755
```

**关键结构判断（安卓端必须照抄的骨架）**：

| 层 | 是否浮在内容上 | 材质 | 证据 |
|---|---|---|---|
| 会话列表 | 否，普通 List | 无自定义背景（系统 `systemBackground`） | `ContentView+SessionList.swift:279, 373` |
| 会话行选中态 | 否，行内填充 | `rgb(183,175,150) @0.3`，圆角 10，inset 6/2 | `SessionListRows.swift:118, 173-175` |
| 文件夹卡 | 否，List 行（不是 section header） | 折叠：采样常量 `rgb(18,18,18)` 暗 / `rgb(252,252,252)` 亮；展开：`.regularMaterial`；描边 0.75pt | `SidebarSurfaces.swift:202-207, 232-250, 262-270` |
| FAB 行 | **是**，`safeAreaInset(.bottom)` | 玻璃圆 56 + 玻璃胶囊搜索条 | `ContentView+SessionList.swift:290`；`ContentView+FAB.swift:163-166, 296` |
| 文件夹迷你条 | **是**，`.overlay(.top)` | `.ultraThinMaterial` 胶囊 48 高、maxWidth 320 | `ContentView+SidebarGrouping.swift:557-564` |
| 聊天输入区 | **是**，overlay 在消息列表底部 | 玻璃，圆角 20 + 半透明底部渐变遮罩 | `ComposerSurface.swift:109`；`AIChatView+InputBar.swift:500-509` |
| 工具状态条 | **是**，与输入框同栈 | 玻璃，圆角 10 | `ToolLiveSheet.swift:3337` |
| 导航栏 | 系统玻璃 | 内容 `ignoresSafeArea(.container, edges:.top)` 让玻璃有东西可采样 | `NavBarModifiers.swift:80-86` |

> **注意（安卓端容易做错）**：iOS 版**没有底部 TabBar**。全仓 `TabView` 只出现在图片浏览器（`Media/MessageImageGallery.swift:251`、`Media/ImagePreview.swift:103`）做左右翻页，没有任何 `.toolbar(placement: .bottomBar)`。会话列表切换、设置、终端、浏览器全部走**顶部导航栏按钮 + sheet**。

### 1.2 证据

```swift
// Views/ContentView/ContentView.swift:564-573
var body: some View {
    GeometryReader { geo in
        let wide = isIPad && geo.size.width >= compactThreshold
        Group {
            if wide { splitLayout } else { stackLayout }
        }
```

```swift
// Views/ContentView/ContentView+Layout.swift:32-39
NavigationSplitView(columnVisibility: $columnVisibility) {
    sessionList(useNavigationLinks: false)
        .appFontScale()
} detail: {
    detailView
        .appFontScale()
}
```

```swift
// Views/ContentView/ContentView+SessionList.swift:373-390  （splitList 尾部）
.listStyle(.plain)
.navigationSplitViewColumnWidth(min: 340, ideal: 380, max: 500)
.modifier(MacOS27OpaqueNavigationBar(background: Material.bar))
.opacity(didInitialLoad ? 1 : 0)
.overlay { if didInitialLoad, displaySessions.isEmpty, !isSearching { emptyState } }
.overlay(alignment: .top) { folderMiniBarOverlay(scrollProxy) }
.safeAreaInset(edge: .bottom) {
    if isSelecting { selectionToolbar }
    else if !hidesFABForOnboarding(listIsEmpty: displaySessions.isEmpty) { fabRow }
}
```

```swift
// Views/Chat/AIChatView/AIChatView.swift:554-560
messagesArea
    .overlay(alignment: .bottom) {
        // Tool preview + input bar stacked at the bottom.
        // Both overlay on top of the message list for immersive scrolling.
```

```swift
// Views/Chat/AIChatView/AIChatView+InputBar.swift:500-509
.background(
    // Fade from transparent (top) to solid background (bottom half)
    LinearGradient(
        stops: [
            .init(color: ChatColors.background.opacity(0), location: 0),
            .init(color: ChatColors.background, location: 0.8),
        ],
        startPoint: .top, endPoint: .bottom
    )
)
```

---

## 2. 材质与玻璃（Liquid Glass）

### 2.1 结论：材质只有 3 档 + 1 个新 API

| 材质 / API | 出现次数 | 语义 | 典型用途 |
|---|---|---|---|
| `.glassEffect(.regular, in:)` | **9 处真实调用**（其中 2 处带 `.interactive()`） | iOS 26 液态玻璃，唯一「高级」表面 | 输入框、气泡、FAB、工具条、弹窗、搜索条 |
| `.ultraThinMaterial` | **36**（`rg` 命中 37，含 1 行注释） | 最轻一档，叠在照片/视频上做浮层 | 图片/视频全屏浏览的关闭·翻页圆钮（15 处 Circle）、HUD 卡、胶囊条 |
| `.regularMaterial` | **12 次命中 = 8 处真实调用 + 1 行注释 + 3 行测试断言** | 中档，要「厚一点」才压得住文字 | 文件夹展开段、顶部 banner、Helper 卡、锁屏遮罩、浏览器 sheet |
| `Material.bar` / `.background(.bar)` | **8** | 系统工具栏材质 | 设置表单固定底栏、分享页底栏、聊天内搜索条、Mac 侧边栏补丁 |
| `.thinMaterial` / `.thickMaterial` / `.ultraThickMaterial` | **0 / 0 / 0** | 完全不用 | — |
| `visualEffect` / `MaterialBlur` | **0 / 0** | 不用 UIKit 手搓 UIVisualEffectView | — |
| `GlassEffectContainer` | **0 处真实使用**（3 处只在注释里） | 曾用 `GlassEffectContainer(spacing: 10)` 让 FAB 与搜索条 morph，因**它会吞掉 FAB 的长按菜单**被移除 | `ContentView+FAB.swift:210-231` |
| `glassEffectID("fabSearch", in:)` | 1 处，**当前是死代码** | 只在 container 内生效 | `SidebarSurfaces.swift:152-167` |
| `buttonStyle(.glass)` | **0 处**（注释里被否决：「glass-on-glass 会读成一个凸起的竞争控件」） | — | `ContentView+FAB.swift:49-55` |

**玻璃参数只有 4 种组合，没有第五种**：

| 组合 | 处数 | 参数 |
|---|---|---|
| `Glass.regular` | 6 | 无 tint、无 interactive |
| `Glass.regular.interactive()` | 2 | 可点击玻璃（按钮） |
| `Glass.regular.tint(Color)` | 1 | FAB 品牌瓷色，alpha 已内置在 tint 里（0.30） |
| shape 变体 | — | 只有 `.circle` / `.capsule` / `RoundedRectangle(20 / 18 / 10, .continuous)` |

### 2.2 逐条清单（文件:行号 + 元素 + 参数）

| # | 文件:行号 | 元素 | 代码 | 形状 |
|---|---|---|---|---|
| 1 | `Views/ContentView/ContentView+FAB.swift:163-166` | 新建会话 FAB（56×56） | `.glassEffect(tint.map { Glass.regular.tint($0) } ?? Glass.regular, in: .circle)` | 圆 |
| 2 | `Views/ContentView/Sidebar/SidebarSurfaces.swift:141` | 展开后的搜索胶囊（56 高，宽 = 屏宽 −16×2 −56 −10） | `content.glassEffect(.regular, in: .capsule)` | 胶囊 |
| 3 | `Views/Chat/AIChatView/Composer/ComposerSurface.swift:64` | 消息列表上的浮动圆钮（回顶/回底/下载） | `.glassEffect(.regular.interactive(), in: .circle)` | 圆 |
| 4 | `Views/Chat/AIChatView/Composer/ComposerSurface.swift:109` | **输入框主体** ComposerSurface | `.glassEffect(.regular, in: shape)`，`shape = RoundedRectangle(cornerRadius: 20, style: .continuous)` | 圆角 20 连续 |
| 5 | `Views/Chat/ChatMessageViews.swift:196` | **用户消息气泡**（已发送态） | `content.glassEffect(.regular, in: shape)`，`shape = RoundedRectangle(cornerRadius: 18)`（**非** continuous） | 圆角 18 |
| 6 | `Views/Chat/ChatMessageViews.swift:132` | 长按预览浮板 ContextMenuPreviewSurface | `.background(shape.fill(ChatColors.background)).glassEffect(.regular, in: shape)`（先铺不透明底，再叠玻璃） | 圆角 18 |
| 7 | `Views/Chat/AIChatView/AIChatView+InputPopups.swift:330` | `/` 斜杠命令与 `@` 文件提及弹窗 | `.glassEffect(.regular, in: shape)`，`RoundedRectangle(cornerRadius: 20, .continuous)` | 圆角 20 连续 |
| 8 | `Views/Chat/ToolLiveSheet.swift:3337` | 工具状态条（列表底部、输入框上方） | `content.glassEffect(.regular, in: shape)`，`RoundedRectangle(cornerRadius: 10, style: .continuous)` | 圆角 10 连续 |
| 9 | `Views/Chat/ChatShareSelectionView.swift:848` | 多选分享时底部「已选 N 条」胶囊 | `.glassEffect(.regular.interactive(), in: .capsule)` | 胶囊 |

### 2.3 玻璃的 5 条工程铁律（决定安卓端能不能做对）

1. **内容必须在玻璃里面，不能 overlay 上去。**
   `.glassEffect` 会把材质**画在被修饰视图之上**，`.overlay` 的图标会被玻璃盖住消失。
   > `// The icon has to be passed IN rather than .overlay-ed on afterwards: .glassEffect draws the material over the view it modifies, so an overlay applied to the surface ends up UNDER the glass and disappears (verified on device — ... pixel variance ~1.2 across its centre).` —— `ContentView+FAB.swift:120-126`

2. **玻璃不提供命中区域，必须显式 `contentShape`。**
   > `.glassEffect` draws a material but contributes no hit-testable shape —— `ComposerSurface.swift:34-35`
   FAB 用 `.contentShape(.circle)` + `.contentShape(.contextMenuPreview, Circle())` 两份（`ContentView+FAB.swift:192-199`）；搜索条用 `.contentShape(.capsule)`（`ContentView+FAB.swift:333`）。

3. **上了玻璃就删掉手写阴影和描边。**
   > Liquid Glass renders its own shadow/edge, and stacking the hand-rolled one on top reads as a dark halo rather than depth. —— `ContentView+FAB.swift:128-131`；`ComposerSurface.swift:93-95`；`ToolLiveSheet.swift:3321-3323`

4. **玻璃要有「东西可采样」，否则退化为采样常量。**
   文件夹卡背后是纯色列表背景，玻璃会逐帧闪烁，最终改成**实测常量**：暗 `rgb(18,18,18)`（24 点采样零方差），亮 `rgb(252,252,252)`。注释明确记录「早先的 57/57/60 是被彩色内容污染的读数」。
   —— `SidebarSurfaces.swift:184-207`

5. **玻璃不能跨 List 行合并。**
   > glass pieces cannot merge across List rows (edge lines between every segment — twice rejected) —— `SidebarSurfaces.swift:194-196`
   所以展开的文件夹容器不是「每行一块玻璃」，而是**每行一块 `.regularMaterial` + 拼接的外框描边**（`FolderSegmentBorder`，`SidebarSurfaces.swift:86-121`）。

### 2.4 兼容层 `MacOS27GlassWorkaround.swift`

位置：`Views/ContentView/Support/MacOS27GlassWorkaround.swift`（106 行，**全量 shipping 代码，不在 `#if DEBUG` 内**）。

- **作用域判定**：`isActive` = `#available(iOS 27.0, *)` **且** `ProcessInfo.processInfo.isiOSAppOnMac`（`:75-84`）。注意：iOS app on Mac 的 `UIDevice.systemVersion` 报的是**宿主 macOS 版本**，所以 `#available(iOS 27.0, *)` 恰好排除 macOS 26。
- **做的事**（`MacOS27OpaqueNavigationBar`，`:96-106`）：关掉系统 top scroll-edge effect，并给导航栏钉显式背景：
  ```swift
  content
      .scrollEdgeEffectHidden(true, for: .top)
      .toolbarBackground(background, for: .navigationBar)
      .toolbarBackground(.visible, for: .navigationBar)
  ```
- **两个调用点的背景选择不同（这是设计令牌，不是随手写的）**：

  | 调用点 | 背景 | 原文理由 |
  |---|---|---|
  | 聊天详情列 `NavBarModifiers.swift:96` | `ChatColors.background`（**不透明色**） | 详情列的活动窗口里 backdrop 根本不渲染，任何 Material 都会同样失败 |
  | 侧边栏列表 `ContentView+SessionList.swift:378` | `Material.bar` | 侧边栏 backdrop **是**渲染的（跑飞的高斯模糊本身就是证据），`bar` 能与 Mac 侧边栏系统灰无缝衔接 |

- **对 iOS 26 手机端零影响**（`isActive == false` 时直接 `return content`），安卓端**不需要移植**。但要知道其背后的原理：**iOS 26 导航栏自己不画背景**，背景来自下方 scroll view 的 top edge effect（`CABackdropLayer` 采样的液态玻璃）—— 这就是 `NavBarModifiers.swift:80-86` 里 `ignoresSafeArea(.container, edges: .top)` 的原因。

### 2.5 `Material` 全量位置（可直接当 token dump）

```
$ rg -n '\.regularMaterial' -g 'Views/**/*.swift' .
Views/Settings/BackupHUD.swift:30:        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
Views/Chat/ChatShareSelectionView.swift:584:  .background(RoundedRectangle(cornerRadius: 14).fill(.regularMaterial))
Views/Chat/ScheduledTaskDetailView.swift:1129:  Capsule().fill(.regularMaterial)
Views/Chat/AIChatView/AIChatView+InputPopups.swift:334:  .background(.regularMaterial, in: shape)
Views/Chat/AIChatView/AIChatView+Banners.swift:161:  RoundedRectangle(cornerRadius: 14, style: .continuous).fill(.regularMaterial)
Views/Chat/AIChatView/Overlays/SessionLockGateOverlay.swift:87:  .fill(.regularMaterial)
Views/ContentView/Sidebar/SidebarSurfaces.swift:247:  content.background(shape.fill(.regularMaterial))
```

```
$ rg -n '\.background\(\.bar\)|Material\.bar|toolbarBackground\(\.bar' -g '*.swift' .
Views/Settings/StorageManagementView.swift:881:  .background(.bar)
Views/Settings/BackupRestoreView.swift:1332:  .background(.bar)
Views/Chat/ChatShareSelectionView.swift:172:  .background(.bar)
Views/Chat/ChatShareSelectionView.swift:537:  .background(.bar)
Views/Providers/AddProviderView.swift:106:  .background(.bar)
Views/Chat/AIChatView/Overlays/ChatSearchBar.swift:87:  .background(.bar)
Views/MCP/MCPFormSheet.swift:162:  .background(.bar)
Views/ContentView/ContentView+SessionList.swift:378:  .modifier(MacOS27OpaqueNavigationBar(background: Material.bar))
```

```
$ rg -o '\.ultraThinMaterial' | sort | uniq -c   → 命中 37 次，其中 1 次是注释，实际调用 36 处：
 15  .background(.ultraThinMaterial, in: Circle())
  6  .background(.ultraThinMaterial, in: Capsule())
  4  .fill(.ultraThinMaterial)
  4  .background(.ultraThinMaterial)
  2  .background(Capsule().fill(.ultraThinMaterial))
  1  .toolbarBackground(.ultraThinMaterial, for: .navigationBar)   ← WebPreviewSheet.swift:887
  1  .background(..., in: RoundedRectangle(cornerRadius: 16))      ← ContentView.swift:974（导出 HUD）
  1  .background(..., in: RoundedRectangle(cornerRadius: 12))      ← SkillsManagementView.swift:336
  1  .background(..., in: RoundedRectangle(cornerRadius: 10))      ← Debug/MessageListTestView.swift:619
  1  .background(..., in: RoundedRectangle(cornerRadius: 8))       ← SkillsManagementView.swift:935
```

### 2.6 玻璃下方的手工高光（无 iOS 26 时的替代方案，安卓端可直接抄）

```swift
// Views/Chat/AIChatView/AIChatView+InputPopups.swift:342-355
private static let edgeHighlight = Color(UIColor { traits in
    traits.userInterfaceStyle == .dark
        ? UIColor(white: 1, alpha: 0.14)   // 深色：白色 14% 发丝高光
        : UIColor(white: 0, alpha: 0.08)   // 浅色：黑色 8% 细线
})
private static let ambientShadow = Color(UIColor { traits in
    traits.userInterfaceStyle == .dark
        ? UIColor(white: 0, alpha: 0.45)
        : UIColor(white: 0, alpha: 0.12)
})
```

```swift
// Views/ContentView/Sidebar/SidebarSurfaces.swift:122-127  —— 文件夹卡边缘高光
private let folderEdgeHighlight = Color(UIColor { traits in
    traits.userInterfaceStyle == .dark
        ? UIColor(white: 1, alpha: 0.30)
        : UIColor(white: 0, alpha: 0.08)
})
```

> 这种 `Color(UIColor { traits in ... })` 是 iOS 端**唯一**做深浅色分支的写法；全仓 `Color(hex:)` 出现 **0** 次。`Color(red:…)` / `UIColor(red:…)` 虽共 138 处，但 116 处集中在终端 ANSI 调色板（`iSH/Terminal/TerminalTypes.swift:26-43`）、工具输出成功/错误色、测试夹具，**不参与 UI 主题**。安卓端对应 `Color(light=…, dark=…)` 或 `MaterialTheme` 扩展属性。

---

## 3. 颜色

### 3.1 结论：整套调色板只有 1 个自定义色 + 3 个品牌色 + 系统语义色

| 类别 | 数量 | 说明 |
|---|---|---|
| `Assets.xcassets` 里的 colorset | **1 个**（只有 `AccentColor`） | 无第二套品牌色、无渐变资产、无背景图资产 |
| 代码里 `Color(red:green:blue:)` 字面量 | **82 处**（`UIColor(red:…)` 另有 **56 处**） | 绝大多数不是主题色：终端 ANSI 16 色调色板、工具成功/错误色、Helper 紫、头像渐变；真正参与 UI 主题的只有 6 处（见 §3.4 / §3.8） |
| `Color(hex:)` / `init(hex:` | **0 处** | 不存 hex 工具 |
| 系统语义色（`UIColor.label` 等） | 贯穿全仓 | 深浅色完全交给系统 |
| 分类图标色 | 16 个系统色名 | 见 §3.4 |

### 3.2 AccentColor（唯一的资产色）

`Assets.xcassets/AccentColor.colorset/Contents.json` 原文：

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : { "red" : "0.212", "green" : "0.525", "blue" : "0.933", "alpha" : "1.000" }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ],
      "color" : {
        "color-space" : "srgb",
        "components" : { "red" : "0.329", "green" : "0.565", "blue" : "0.894", "alpha" : "1.000" }
      },
      "idiom" : "universal"
    }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
```

换算：

| 模式 | sRGB 分量 | HEX | 用途 |
|---|---|---|---|
| Light | 0.212 / 0.525 / 0.933 | **#3686EE** | 链接、开关、选中高亮、`Color.accentColor` 的所有引用 |
| Dark | 0.329 / 0.565 / 0.894 | **#5490E4** | 同上（深色下**更亮、更偏灰**，不是简单提亮） |

> 注意这不是系统的 `#007AFF`：R 分量高出 0.21，整体更浅更「天蓝」。安卓端 `colorPrimary` 直接取 `#3686EE` / `#5490E4`。

### 3.3 代码里的语义调色板（`ChatColors`，全仓唯一调色板文件）

```swift
// Views/Chat/AIChatView/Support/ChatColors.swift:26-39
enum ChatColors {
    static let background        = Color(UIColor.systemBackground)
    static let secondaryBg       = Color(UIColor.secondarySystemBackground)
    static let inputIconBg       = Color(UIColor.secondarySystemBackground)
    static let inputIconBorder   = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.35, alpha: 1) : UIColor(white: 0, alpha: 0) })
    static let inputBg           = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.12, alpha: 1) : .white })
    static let inputBorder       = Color(UIColor.separator)
    static let primaryText       = Color(UIColor.label)
    static let secondaryText     = Color(UIColor.secondaryLabel)
    static let tertiaryText      = Color(UIColor.tertiaryLabel)
    static let userBubble        = Color(UIColor.tertiarySystemFill)
    static let toolBg            = Color(UIColor.tertiarySystemGroupedBackground)
    static let toolBorder        = Color(UIColor.separator).opacity(0.5)
    static let accent            = Color(UIColor.label)     // ← 注意：不是 AccentColor！
    static let sendButton        = Color(UIColor.label)     // ← 发送键 = 纯 label 色（黑/白）
    static let sendButtonDisabled = Color(UIColor.quaternaryLabel)
}
```

**两个反直觉但必须照抄的点**：
- `ChatColors.accent` 是 `UIColor.label`（浅色=近黑，深色=白），**不是**品牌蓝。聊天内的"强调"用的是**墨色**而不是彩色。
- 发送键 `sendButton` 也是 `UIColor.label`：浅色模式是**黑圆 + 白箭头**，深色模式是**白圆 + 黑箭头**。图标 `arrow.up.circle.fill` 34pt（`ChatSendButton.swift:119,129,163,209`）。

### 3.4 品牌沙色（唯一「有性格」的颜色）

```swift
// Views/ContentView/ContentView+FAB.swift:21-25   —— 不透明回退 / 非 26 系统
private static let newChatBrandColor = Color(UIColor { $0.userInterfaceStyle == .dark
    ? UIColor(red: 80/255, green: 76/255, blue: 66/255, alpha: 1)
    : UIColor(red: 183/255, green: 175/255, blue: 150/255, alpha: 1) })

// Views/ContentView/ContentView+FAB.swift:44-46   —— 玻璃 tint（alpha 内置 0.30）
private static let newChatGlassTint = Color(UIColor { $0.userInterfaceStyle == .dark
    ? UIColor(red: 196/255, green: 176/255, blue: 120/255, alpha: 0.30)
    : UIColor(red: 183/255, green: 175/255, blue: 150/255, alpha: 0.30) })
```

| 名称 | Light | Dark | 用途 |
|---|---|---|---|
| 品牌沙 | `rgb(183,175,150)` = `#B7AF96` | `rgb(80,76,66)` = `#504C42` | FAB 不透明底座（<iOS 26） |
| 玻璃 tint | `#B7AF96 @30%` | `rgb(196,176,120)` = `#C4B078` @30% | iOS 26 FAB 着色 |
| 列表选中填充 | `#B7AF96 @30%`（两模式同一值） | 同 | `SessionListRows.swift:118` |

> tint 用 0.30 而不是 1.0 是**实测结论**：满强度会变成不透明圆盘（环向像素标准差 1.1，没有折射），0.30 时暗色模式暖度 22.6 / 环差 9.3（真玻璃），浅色 9.9。这段推导写在 `ContentView+FAB.swift:27-43`，安卓端若用真实 blur，务必把 tint alpha 控制在 0.3 附近。

### 3.5 分类图标色（16 个，全部系统色名，无 hex）

```swift
// Views/ContentView/Sidebar/SessionRow.swift:23-42
case "code":         ("terminal.fill", .orange)
case "writing":      ("doc.text.fill", .blue)
case "research":     ("globe.americas.fill", .teal)
case "analysis":     ("chart.pie.fill", .indigo)
case "creative":     ("paintbrush.pointed.fill", .pink)
case "chat":         ("bubble.left.fill", .green)
case "math":         ("number.circle.fill", .purple)
case "translation":  ("character.bubble", .cyan)
case "health":       ("heart.fill", .red)
case "finance":      ("banknote.fill", .mint)
case "travel":       ("map.fill", .orange)
case "education":    ("book.closed.fill", .blue)
case "design":       ("paintpalette.fill", .pink)
case "productivity": ("calendar.badge.checkmark", .yellow)
case "support":      ("gearshape.fill", .brown)
case "other":        ("square.grid.2x2.fill", .gray)
```
图标底：`iconBackgroundColor.opacity(isHighlighted ? 0.35 : 0.18)`（`SessionRow.swift:227`）——**18% 常规 / 35% 选中**。

### 3.6 Markdown 颜色（`SelectableMarkdownTheme`）

```swift
// Views/Chat/Markdown/Support/MarkdownTheme.swift:53-77
let baseFontSize: CGFloat                       // init 默认 16.5（:58）
let codeBlockCornerRadius: CGFloat = 8          // :54
let inlineCodeCornerRadius: CGFloat = 5         // :55
var accentColor: UIColor { .systemOrange }      // ← 强调 = 橙，不是蓝
var linkColor: UIColor { .systemBlue }
var codeBlockBackground: UIColor {
    UIColor { $0.userInterfaceStyle == .dark ? UIColor(white: 0.15, alpha: 1) : .black }
}
var codeBlockTextColor: UIColor {
    UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.55, green: 0.95, blue: 0.55, alpha: 1) : .systemGreen }
}
var inlineCodeBackground: UIColor { minisInlineCodeBackgroundColor }   // 亮 #F2F2F7 / 暗 #3A3A3C
var inlineCodeColor: UIColor { .systemOrange }
var blockquoteBarColor: UIColor { UIColor.systemOrange.withAlphaComponent(0.5) }
var tableBorderColor: UIColor { UIColor.label.withAlphaComponent(0.25) }
```

> **代码块在浅色模式下是黑底**（`.black`），深色模式反而是 `white: 0.15` 的浅灰底 —— 这是全项目最反常规的一处，安卓端照抄时不要"顺手修正"。
> 行内代码胶囊：亮 `#F2F2F7`(systemGray6) / 暗 `#3A3A3C`（`MarkdownTheme.swift:43-48`），圆角 5（`:56`）。

### 3.7 深浅色模式

- 全仓**没有**自定义主题系统：`appearanceMode: Int`（0=跟随系统 / 1=浅 / 2=深），写入 `window.overrideUserInterfaceStyle`（`Views/Settings/AppearanceSettingsView.swift:255-269`）。
- 3 处 `preferredColorScheme` 强制：`SettingsSheet.swift`、`WebPreviewSheet.swift`（2 处）、`MinisMediaViews.swift` —— 都是为了让**sheet / 全屏预览**跟随 App 内主题，而不是系统主题。
- 深色模式**不是纯黑**：页面背景是 `systemBackground`（= `#000`），卡片是 `white: 0.12`~`0.15`，文件夹卡采样值是 `rgb(18,18,18)` = `#121212`。所以灰阶梯度是 `#000 → #121212 → #1F1F1F(0.12) → #262626(0.15)`。

### 3.8 其余参与 UI 的硬编码色（共 6 组，安卓端需要记账）

| 用途 | Light | Dark | 出处 |
|---|---|---|---|
| 工具输出·错误文字 | `rgb(0.8, 0.1, 0.1)` | `rgb(1, 0.4, 0.4)` | `ToolLiveSheet.swift:1921` |
| 工具输出·错误底 | `rgb(1, 0.9, 0.9)` | `rgb(0.3, 0.08, 0.08)` | `ToolLiveSheet.swift:1925` |
| 工具输出·成功文字 | `rgb(0.1, 0.6, 0.1)` | `rgb(0.4, 1, 0.4)` | `ToolLiveSheet.swift:1934` |
| 工具输出·成功底 | `rgb(0.9, 1, 0.9)` | `rgb(0.08, 0.2, 0.08)` | `ToolLiveSheet.swift:1938` |
| 代码/日志深底（**不随模式变化**） | `rgb(0.12, 0.12, 0.14)` ≈ `#1F1F24` | 同 | `ToolLiveSheet.swift:3088, 3142, 3186, 3199`；`UserAskCardView.swift:1874, 1927` |
| Helper 紫（助手替身标识） | `#734DED` `rgb(0.45,0.30,0.93)` | `#A385FF` `rgb(0.64,0.52,1.00)` | `HelperBlockView.swift:346-347` |
| 头像/图标渐变（沙色双色） | `rgb(0.72,0.69,0.59)` → `rgb(0.6,0.6,0.55)` | 同 | `ChatMessageViews.swift:560-561`；`BridgedAssistantHeaderV3.swift:54-55` |
| 终端 ANSI 16 色 | 标准 xterm 值（见文件） | 同 | `iSH/Terminal/TerminalTypes.swift:26-43` |
| 终端光标 | `rgb(0.4, 0.6, 1.0)` | 同 | `iSH/Terminal/TerminalCanvasView.swift:313` |

---

## 4. 圆角与形状

### 4.1 结论：8 / 10 / 12 / 14 / 16 / 18 / 20 七档，前三档占 68%，全部 `.continuous`（共 196 处）

```
$ rg -o 'cornerRadius: [0-9.]+' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
     67 cornerRadius: 8
     46 cornerRadius: 10
     20 cornerRadius: 12
     14 cornerRadius: 14
     11 cornerRadius: 16
      8 cornerRadius: 18
      6 cornerRadius: 7
      5 cornerRadius: 3
      5 cornerRadius: 20
      5 cornerRadius: 2
      3 cornerRadius: 6
      2 cornerRadius: 5.5
      2 cornerRadius: 5
      1 cornerRadius: 4
      1 cornerRadius: 22
```

```
$ rg -o 'RoundedRectangle\(cornerRadius: [0-9.]+' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
     64 RoundedRectangle(cornerRadius: 8     ← 8 的 64/67 都是 RoundedRectangle
     46 RoundedRectangle(cornerRadius: 10
     20 RoundedRectangle(cornerRadius: 12
     14 RoundedRectangle(cornerRadius: 14
     11 RoundedRectangle(cornerRadius: 16
      8 RoundedRectangle(cornerRadius: 18
      6 RoundedRectangle(cornerRadius: 7
      5 RoundedRectangle(cornerRadius: 20
      4 RoundedRectangle(cornerRadius: 3
      3 RoundedRectangle(cornerRadius: 6
      3 RoundedRectangle(cornerRadius: 2
      2 RoundedRectangle(cornerRadius: 5.5
      1 RoundedRectangle(cornerRadius: 4
      1 RoundedRectangle(cornerRadius: 22
```

### 4.2 语义映射（哪个值用在什么上）

| 半径 | 语义 | 代表元素（文件:行号） |
|---|---|---|
| **8**（67） | 微型容器 / 内联代码 / 代码块 / 小色块 / 表格 | 代码块（`MarkdownTheme.swift:54`）、分类图标底（`SessionRow.swift:532`）、行内图片（`MinisMediaViews.swift:424-425`）、日期头拖放高亮（`ContentView+SectionHeaders.swift:85`） |
| **10**（46） | 中等卡片 / 列表选中行 / 工具条 / 输入内嵌块 | 列表选中态（`SessionListRows.swift:173`）、工具状态条（`ToolLiveSheet.swift:3332`）、聊天内搜索框（`ChatSearchBar.swift:64`）、设置行背景（`SoulSettingsView.swift:464`） |
| **12**（20） | 标准卡片 | 设置卡片（`ContentView+EmptyState.swift:203`）、UserAsk 卡（`UserAskCardView.swift:498-499`）、Helper 卡（`HelperBlockView.swift:549-552`）、BackupHUD（`BackupHUD.swift:30`） |
| **14**（14） | 顶部 banner / 日程卡 / 分享预览块 | `AIChatView+Banners.swift:161`、`ScheduledTaskDetailView.swift:582, 788`、`ChatShareSelectionView.swift:584` |
| **16**（11） | **文件夹容器**（唯一） | `SidebarSurfaces.swift:211, 264-267, 293`；导出 HUD（`ContentView.swift:974`） |
| **18**（8） | **消息气泡**（唯一） | `ChatMessageViews.swift:124, 182, 472` |
| **20**（5） | **输入框 / 弹窗**（最大档） | `ComposerSurface.swift:103, 156`；`AIChatView+InputPopups.swift:465` |
| 22（1） | 单点例外 | 见 `rg` 定位，非主路径 |

### 4.3 胶囊：155 处 `Capsule()` + 15 处 `.capsule`

```
$ rg -o 'Capsule\(\)' -g '*.swift' . | wc -l   → 155
$ rg -o '\.capsule'   -g '*.swift' . | wc -l   → 15
```
胶囊是第二大类形状，全部用在「短状态」上：搜索条、文件夹迷你条、用量胶囊、语言徽章、语音状态条、"已选 N 条"、加载 HUD。

### 4.4 iOS 26 新形状 API 的使用情况

| API | 使用 | 说明 |
|---|---|---|
| `ConcentricRectangle` | **0** | 未使用 |
| `.rect(corners:)` / `ConcentricRectangle(corners:)` | **0** | 未使用 |
| `concentricRadius` | **0** | 未使用 |
| `UnevenRoundedRectangle` | **6**（4 文件） | 用于「文件夹展开顶部 / 中间 / 底部」的三段拼接，以及选中条的底角 |
| `.capsule`（作为 Shape） | 15 | 玻璃 API 需要 `Shape` 而非 `View`，`:141`、`:848`、`ComposerSurface.swift:64` |

**但同心圆角的思想是用手工公式实现的**（这是安卓端真正该抄的）：

```swift
// Views/Chat/AIChatView/AIChatView+InputPopups.swift:444-449
RoundedRectangle(
    cornerRadius: AIChatView.popupCornerRadius - AIChatView.popupRowInset,   // 20 - 6 = 14
    style: .continuous
)
```
```swift
// 同上 :463-465
fileprivate static let popupRowInset: CGFloat = 6
fileprivate static let popupCornerRadius: CGFloat = 20
```
> 规则：**内层圆角 = 外层圆角 − 内边距**。弹窗 20，行内缩进 6，行高亮 14。安卓端 `Modifier.concentric()` 等价实现。

### 4.5 `.continuous` 是默认，`.circular` 是例外

- 绝大多数 `RoundedRectangle(cornerRadius: X, style: .continuous)`。
- **唯一刻意用非 continuous 的是消息气泡 + 长按预览板**（18），原因写在 `ChatMessageViews.swift:177-181`：必须和 `.contentShape(.contextMenuPreview, RoundedRectangle(cornerRadius: 18))` 完全一致，否则长按抬起时圆角会「变一下形」。
> 安卓端：聊天气泡用 **quintic 连续圆角**（Compose 的 `RoundedCornerShape` 是圆弧，需自定义 `ContinuousRoundedCornerShape`）。

---

## 5. 阴影与描边

### 5.1 结论：0.5pt 发丝描边 63 次；阴影 ≤ 8pt 半径、≤ 0.2 不透明度

```
$ rg -o 'lineWidth: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
     63 lineWidth: 0.5      ← 主力（发丝线）
      7 lineWidth: 1
      6 lineWidth: 1.5
      6 lineWidth: 0.75     ← 玻璃兼容层 / 文件夹卡
      4 lineWidth: 2
      4 lineWidth: 0.8      ← 卡片描边（Helper / UserAsk）
      2 lineWidth: 2.5
      2 lineWidth: 1.6
      1 lineWidth: 6
      1 lineWidth: 13       ← ComposerContextGlow 内发光
      1 lineWidth: 1.8
```

### 5.2 阴影台账（全仓 44 处 `.shadow(`，`Views/**` 39 处、21 个文件）

| 文件:行号 | 元素 | 参数 |
|---|---|---|
| `ContentView+FAB.swift:204` | FAB 不透明回退 | `.black @0.2, r8, x0, y4` |
| `SidebarSurfaces.swift:146` | 搜索胶囊回退 | `.black @0.1, r6, x0, y2` |
| `ComposerSurface.swift:115` | 输入框回退（上） | `.black @0.12, r8, x0, y2` |
| `ComposerSurface.swift:116` | 输入框回退（下，深色专用） | `white:0 @0.5（浅色时 0）, r8, x0, y-4` |
| `ToolLiveSheet.swift:3346` | 工具条回退 | `暗 white:0.08@0.75 / 亮 black@0.12, r8, x0, y4` |
| `AIChatView+InputPopups.swift:339-340` | 弹窗回退（双层） | `.black@0.06 r2 y1` + `ambient（暗0.45/亮0.12）r18 y8` |
| `ContentView+SidebarGrouping.swift:563` | 文件夹迷你条 | `.black@0.15, r8, y2` |
| `AIChatView+Banners.swift:168` | 顶部 banner | `.black@0.08, r10, x0, y3` |
| `ChatShareSelectionView.swift:854` | 分享胶囊 | `.black @（暗0.35/亮0.10）, r6, y2` |
| `SyncStatusIndicators.swift:85` | 同步徽章 | `.black@0.18, r8, y3` |
| `ChatInputBar.swift:45 / :54` | 附件 chip | `.black@0.18 r6 y2` / `.black@0.15 r5 y2` |
| `MinisMediaViews.swift:458 / :699` | 图片缩略图 | `.black@0.08 r2 y1` / `.black@0.15 r4 y2` |
| `Media/AudioPreview.swift:146` | 音频播放卡 | `.black@0.4, r24, x0, y10`（全仓最大） |
| `BackupHUD.swift:31` | HUD | `.shadow(radius: 8, y: 2)`（默认黑） |

**阴影规律（可直接提炼成 token）**：

| token | 值 | 用途 |
|---|---|---|
| elevation-1 | `0 1 2 rgba(0,0,0,.08)` | 小 chip、缩略图 |
| elevation-2 | `0 2 6 rgba(0,0,0,.12)` | 输入框、搜索条 |
| elevation-3 | `0 3 8 rgba(0,0,0,.15~.18)` | FAB、迷你条、banner |
| elevation-4 | `0 8 18 rgba(0,0,0,.12~.45)` | 弹窗（带 ambient 层） |

> **上了玻璃的路径一律不加阴影**（`ContentView+FAB.swift:128-131`、`ComposerSurface.swift:93-95`、`ToolLiveSheet.swift:3321-3323`、`ChatMessageViews.swift:105-107`）。安卓端：`glassEffect` 分支返回 `Modifier` 时不叠 `shadow`，由材质自带的 edge + ambient 承担。

### 5.3 描边的三种写法

```swift
// ① 内描边（最常用，不占布局）
.overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(UIColor { ... }), lineWidth: 0.5))

// ② strokeBorder（描边在形状内侧，用于需要精确尺寸的卡）
.overlay(shape.strokeBorder(Self.edgeHighlight, lineWidth: 0.5))   // AIChatView+InputPopups.swift:337

// ③ 虚线段（未发送的排队消息 —— 全项目唯一的虚线）
.strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))    // ChatMessageViews.swift:194-196
```

常用描边色：
| 场景 | 色值 |
|---|---|
| 分隔线 / 常规描边 | `Color(UIColor.separator)`，常再 `.opacity(0.5)`，0.5pt |
| 玻璃高光（暗） | `white:1 @0.30`（文件夹卡）/ `@0.14`（弹窗） |
| 玻璃高光（亮） | `white:0 @0.08` |
| 输入图标圈 | `dark: white:0.35 / light: clear`，0.5pt（`ChatColors.swift:30`） |
| 工具卡 | `ChatColors.toolBorder` = `separator @0.5`，0.5pt |
| 拖放目标 | `Color.accentColor` 1.5pt + `accentColor@0.15` 填充（`SidebarSurfaces.swift:298-303`） |---

## 6. 字体与字号

### 6.1 结论：语义字体为主（caption 家族压倒性），显式 size 集中在 13/11/12/9

```
$ rg -o '\.font\(\.(largeTitle|title|title2|title3|headline|subheadline|body|callout|footnote|caption|caption2)\b' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
    306 .font(.caption        ← 第一大类（元信息、徽章、日期）
    152 .font(.caption2       ← 第二大类
    114 .font(.subheadline    ← 副标题、分组头
     69 .font(.body          ← 正文（Markdown 之外的普通文本）
     62 .font(.footnote
     31 .font(.headline      ← 导航标题、弹窗标题
     13 .font(.title3
     11 .font(.title2
     11 .font(.callout
      1 .font(.title
      1 .font(.largeTitle
```

```
$ rg -o '\.system\(size: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn | head -14
     88 .system(size: 13      ← 显式 size 的绝对主力（元信息行）
     64 .system(size: 9       ← 图标徽章
     59 .system(size: 11      ← 状态徽标文字
     52 .system(size: 12      ← 小标签
     32 .system(size: 14      ← 会话行副标题
     30 .system(size: 10      ← 时间戳、用量
     20 .system(size: 15
     14 .system(size: 20
     14 .system(size: 18
     13 .system(size: 8
     13 .system(size: 16       ← 会话行标题
     12 .system(size: 6
     10 .system(size: 48       ← 空状态大图标
      7 .system(size: 22       ← FAB 图标
```

```
$ rg -o '\.weight\(\.(ultraLight|thin|light|regular|medium|semibold|bold|heavy|black)\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
     85 .weight(.semibold)
     65 .weight(.medium)
      3 .weight(.bold)
```
> 只用 3 个字重：**semibold（标题类）/ medium（可点类）/ bold（仅 3 处图标徽章）**。**从不使用** `.light` / `.thin` / `.heavy` / `.black`。

### 6.2 内容层级字号表（可直接当 type scale）

| 层级 | 字号 / 字重 | 颜色 | 出处 |
|---|---|---|---|
| App 标题（侧边栏导航） | `18.5 / semibold` | `.primary` | `ContentView+SidebarToolbar.swift:270` |
| 聊天页标题 | `16 / semibold`（+ 副标题 `caption2`） | `ChatColors.primaryText` | `AIChatView+NavigationTitle.swift:278` |
| 消息正文（Markdown 基准） | `16.5 / regular` | `.label` | `MarkdownTheme.swift:58` |
| 用户气泡正文 | `16.5` | `ChatColors.primaryText` | `ChatMessageViews.swift:445` |
| 会话行标题 | `16 / semibold` | `.label` | `SessionRow.swift:277` |
| 会话行副标题 | `14 / regular` | `.secondaryLabel` | `SessionRow.swift:290, 297` |
| 会话行日期 | `13 / regular` | `.tertiaryLabel` | `SessionRow.swift:323` |
| 分组头（Today / Pinned / Groups） | `.subheadline.weight(.semibold)` | `.secondaryLabel`，`.textCase(nil)` | `ContentView+SessionList.swift:271-277` |
| 设置行标题 | `.body` | `.primary` | 系统 List 默认 |
| 工具卡标题 | `13` | — | `ToolLiveSheet.swift` 各段 |
| 用量胶囊 / 时间戳 | `10 / monospaced` | `.tertiaryLabel` | `ChatMessageViews.swift:768` |
| 状态徽标 | `9 / semibold` | — | `ContentView+SidebarToolbar.swift:146` |
| 图标徽章（角标） | `9 / bold`，16×16 圆 | 白字 | `SessionRow.swift:354-357` |
| 未读点 | `8×8` 圆，`Color.red` | — | `SessionRow.swift:264-267` |

### 6.3 Markdown 排版比例（相对 16.5 的倍数）

```swift
// Views/Chat/Markdown/Support/MarkdownTheme.swift:77-92
H1 = base × 1.5   bold
H2 = base × 1.3   bold
H3 = base × 1.15  semibold
H4 = base         semibold
H5 = base × 0.875 semibold
H6 = base × 0.85  semibold
行内代码 = 上下文字号 × 0.845   （:99）
代码块   = base × 0.85          （:110）
```

### 6.4 等宽字体

```
$ rg -o '\.monospaced'      → 120 次
$ rg -o '\.monospacedDigit' → 42 次
$ rg -o '\.monospacedSystemFont' → 6 次
```
- Markdown 代码块 / 行内代码用 **Menlo + PingFang SC cascading**（`MarkdownTheme.swift:105-118`，注释说明「PingFang SC 的 CJK 字形恰好是 Menlo ASCII 的 2 倍宽，能让代码块里的表格对齐」）。
- 数字（token 计数、计时）用 `.monospacedDigit()` 防跳动（42 次）。

### 6.5 字号缩放体系（3 条独立轴）

```swift
// Shared/FontSettings.swift:17-32
case xSmall → ×0.88   // ~15pt body
case small  → ×0.94   // ~16pt body
case default→ ×1.00   // 17pt body
case medium → ×1.06
case large  → ×1.12
case extraLarge → ×1.21
```
三条轴：`chatInputScale`（输入框）、`messageBaseScale`（消息正文）、`appBaseScale`（通用 UI）。
`scaledApp(_:)` / `scaledMessage(_:)` 只是 `size × multiplier`（`:129-136`）；`appBaseScale` 额外把 `DynamicTypeSize` 灌进环境（`appFontScale()`，`:213-215`），且**必须注入到 NavigationSplitView 每一列的内部**（`ContentView+Layout.swift:22-36` 有详细说明：SwiftUI 环境不跨列边界）。

---

## 7. 间距体系

### 7.1 结论：4pt 基准，实际是 2 / 4 / 6 / 8 / 10 / 12 / 16 七个数

```
$ rg -o '\.padding\(\.[a-z]+, [0-9.]+\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn | head -18
     59 .padding(.horizontal, 12)   ← 最常用水平内距
     40 .padding(.horizontal, 16)   ← 页面级水平内距
     34 .padding(.vertical, 10)     ← 卡片内上下
     32 .padding(.vertical, 2)      ← 行内微调
     31 .padding(.vertical, 12)     ← 列表行上下
     31 .padding(.horizontal, 14)   ← 气泡内距
     29 .padding(.vertical, 8)
     26 .padding(.vertical, 1)
     23 .padding(.vertical, 6)
     23 .padding(.vertical, 4)
     22 .padding(.horizontal, 4)
     19 .padding(.horizontal, 6)    ← 列表行卡片左右 inset
     19 .padding(.horizontal, 10)
     18 .padding(.top, 8)
     15 .padding(.horizontal, 5)
     14 .padding(.bottom, 16)
     13 .padding(.top, 12)
     13 .padding(.horizontal, 8)
```

```
$ rg -o '\.padding\([0-9]+\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn | head
      6 .padding(16)
      6 .padding(14)
      5 .padding(4)
      4 .padding(24)
      4 .padding(10)
      2 .padding(8)
      2 .padding(20)
      2 .padding(12)
```

```
$ rg -o 'spacing: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn | head -14
    103 spacing: 4     ← 图标与文字之间
    101 spacing: 6
     93 spacing: 8     ← 行内块之间
     93 spacing: 2
     79 spacing: 0     ← 刻意贴边
     72 spacing: 12
     67 spacing: 10
     21 spacing: 1
     15 spacing: 3
     13 spacing: 5
     10 spacing: 16
      7 spacing: 14
      5 spacing: 20
      3 spacing: 24
```

### 7.2 间距语义表

| 位置 | 值 | 出处 |
|---|---|---|
| 页面级水平边距 | **16** | `SessionRow.swift:336`；`ChatMessageViews.swift:650`；`ContentView+SessionList.swift` 各 row |
| 卡片/输入框水平边距 | **12** | `AIChatView+InputBar.swift:181`（composer 外层）；`ChatMessageViews.swift:845` |
| 列表行垂直内距 | **12** | `SessionRow.swift:337` |
| 列表行之间的卡片 inset | `horizontal 6 / vertical 2` | `SessionListRows.swift:175` |
| 文件夹成员行底部 | `horizontal 6 / bottom 4（最后一行）` | `SidebarSurfaces.swift:318-321` |
| 消息行（助手） | `horizontal 16 / vertical 6` | `ChatMessageViews.swift:649-651` |
| 消息行（用户气泡内） | `horizontal 14 / vertical 10` | `ChatMessageViews.swift:446-447` |
| 系统分隔行 | `horizontal 24 / vertical 16（或 6）` | `ChatMessageViews.swift:375-376, 413-414` |
| 输入框内文本上留白 | **16**（无附件）/ **11**（有附件） | `AIChatView+ComposerControls.swift` `topPadding` |
| 输入框按钮行 | `horizontal 12 / bottom 10` | `AIChatView+InputBar.swift:136-137` |
| 输入框外层 | `horizontal 12 / vertical 8` | `AIChatView+InputBar.swift:181-182` |
| FAB 底部留白 | `20` | `ContentView+FAB.swift:273` |
| 文件夹迷你条 | `top 8 / horizontal 24`，高 48，maxWidth 320 | `ContentView+SidebarGrouping.swift:558-564` |
| 弹窗卡片 | 内缩 `6`（四周），水平外距 `12` | `AIChatView+InputPopups.swift:463, 319` |
| 设置卡（首启） | `padding(14)`，卡间距 `spacing: 12` | `ContentView+EmptyState.swift:202`；`ContentView+Layout.swift:118` |
| HUD | `padding(24)` | `ContentView.swift:973` |

### 7.3 行高

| 元素 | 高度 | 计算 |
|---|---|---|
| 会话行 | **≈68pt** | 44 头像 + 12×2 padding = 68（`SessionRow.swift:226, 337`） |
| 文件夹卡 | 48（迷你条）/ 由内容撑开（行内卡，同会话行） | — |
| 输入框（空） | 一行文本 + 16 上留白 + 34 按钮 + 10 底 | `ChatInputBar.swift:1085-1090`（`(font.lineHeight ?? 20) + 1`） |
| 输入框（最大） | 120 文本区 | `ChatInputBar.swift:1078` |
| FAB | 56 | `DraggableFAB.swift:27` |
| 导航栏标题框 | 46（iOS 26 玻璃条带对齐） | `NavBarModifiers.swift:33` |
| 聊天内搜索框 | 36 | `ChatSearchBar.swift:63` |
| 工具栏图标按钮 | 34 | `MicButton.swift:42`；`AIChatView+ComposerControls.swift:226` |

---

## 8. 动效

### 8.1 结论：时长 0.2s 为主，弹簧 0.3/0.35，按下缩到 0.9

```
$ rg -o '\.animation\(\.(easeInOut|easeIn|easeOut|linear|spring|smooth|snappy|bouncy|interactiveSpring)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
     28 .animation(.easeInOut
     13 .animation(.spring
      3 .animation(.easeOut
      1 .animation(.interactiveSpring
```

```
$ rg -o 'duration: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn | head
     45 duration: 0.2    ← 绝对主力（hover/展开/淡入）
     17 duration: 0.25
     10 duration: 0.28
      8 duration: 0.35
      7 duration: 0.15
      3 duration: 0.3
```

```
$ rg -o '\.spring\([^)]*\)' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
     10 .spring(response: 0.3)                             ← 默认弹簧
      2 .spring(response: 0.18, dampingFraction: 0.82)     ← 弹窗/浮层出现（快而稳）
      1 .spring(response: 0.38, dampingFraction: 0.86)
      1 .spring(response: 0.35, dampingFraction: 0.88)
      1 .spring(response: 0.35, dampingFraction: 0.75)     ← FAB 左右吸附
      1 .spring(response: 0.35)
      1 .spring(response: 0.3, dampingFraction: 0.8)
      1 .spring(response: 0.18, dampingFraction: 0.6)
      1 .spring(response: 0.15, dampingFraction: 0.6)
```

```
$ rg -o 'scaleEffect\([^)]*\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
      4 scaleEffect(0.6)      ← ProgressView 缩小
      3 scaleEffect(0.7)
      1 scaleEffect(pressing ? 0.9 : 1.0)     ← 按压反馈 = 0.9
      1 scaleEffect(micPressed ? 0.9 : 1.0)   ← 同上
      1 scaleEffect(animating ? 1.0 : 0.55)
      1 scaleEffect(0.9 + 0.18 * progress, anchor: .leading)
      1 scaleEffect(0.8)
```

### 8.2 动效 token 表

| token | 值 | 用途 | 出处 |
|---|---|---|---|
| `motion.fast` | `.easeInOut(duration: 0.15)` | 高亮、hover | 全仓 |
| `motion.base` | `.easeInOut(duration: 0.2)` | 展开/收起、toast、遮罩 | `ContentView.swift:987`（导出遮罩 `.transition(.opacity)` + 0.2） |
| `motion.medium` | `.easeInOut(duration: 0.25)` | 顶栏出现 | `ContentView.swift:583`（forceSyncToast） |
| `motion.slow` | `.easeInOut(duration: 0.35)` | 上下文用量光晕 | `ComposerSurface.swift:203` |
| `motion.spring` | `.spring(response: 0.3)` | 默认弹性 | 10 处 |
| `motion.springSnap` | `.spring(response: 0.35, dampingFraction: 0.75)` | FAB 吸附屏幕边 | `DraggableFAB.swift:52` |
| `motion.springPop` | `.spring(response: 0.18, dampingFraction: 0.82)` | 弹出层出现 | 2 处 |
| `motion.press` | `scaleEffect(0.9)` | 按钮按下 | `MicButton.swift:59` 等 |
| `motion.breath` | `.easeInOut(duration: 1.4).repeatForever(autoreverses: true)` | 上下文超限呼吸（`reduceMotion` 时关闭） | `ComposerSurface.swift:210-213` |

### 8.3 玻璃交互与出现方式

**FAB ↔ 搜索条的 morph**（`ContentView+FAB.swift:290-300`）：
```swift
.transition(.asymmetric(
    insertion: .scale(scale: 0.85, anchor: fabOnLeft ? .leading : .trailing).combined(with: .opacity),
    removal:   .scale(scale: 0.85, anchor: fabOnLeft ? .leading : .trailing).combined(with: .opacity)
))
```
> 原本用 `GlassEffectContainer` + `glassEffectID` 做真正的 shape morph，因为**它会让 FAB 长按菜单彻底失效**（SwiftUI 不创建 `UIContextMenuInteraction`），最终降级为 **scale 0.85 + opacity** 的对称过渡。安卓端可以直接做真 morph（Compose 没有这个 bug），但**过渡参数请沿用 0.85 + fade**，手感已调过。

**玻璃按钮的交互反馈**：`.glassEffect(.regular.interactive(), in:)` 只用在 2 个地方 —— 浮动圆钮（`ComposerSurface.swift:64`）与分享胶囊（`ChatShareSelectionView.swift:848`）。输入框、气泡、工具条**不加 `.interactive()`**（它们不是按钮，加了会有"可按"的错觉）。

**sheet 呈现**：
```
$ rg -o 'presentationDetents\([^)]*\)' | sort | uniq -c | sort -rn
     11 presentationDetents([.medium, .large])
      8 presentationDetents([.medium])
      7 presentationDetents([.large])
      1 presentationDetents([.height(380)])
      1 presentationDetents([.height(360)])
      1 presentationDetents([.height(240)])
      1 presentationDetents([.fraction(0.8)])
      1 presentationDetents([.fraction(0.75)])
      1 presentationDetents(Self.detents, selection: $detent)
```
- `presentationCornerRadius` **0 次** —— 圆角完全交给系统（iOS 26 sheet 默认更圆）。
- `presentationDragIndicator(.visible)` 只在两个配额/测试 sheet 上显式设置（`UnifiedModelPicker.swift:755, 767`）。
- 大列表型模态（Settings、Model picker、File browser）统一 `.large`；选择型（语言、配额、快速测试）`.medium, .large`；单动作确认 `.height(240~380)`。

---

## 9. 15 个关键界面清单（视觉构成一句话）

| # | 界面 | 一句话视觉构成 | 关键数值 |
|---|---|---|---|
| 1 | **会话列表（首页）** | 系统 `List(.plain)` 铺满、无自定义底色；日期分组头用 `.subheadline.semibold` + `.secondaryLabel` + `.textCase(nil)`；顶部 inline 导航标题 = SOUL 名 `18.5/semibold`，左侧浮一个同步小圆点 | 列表无内距（`listRowInsets(EdgeInsets())`）、行卡片 inset `h6/v2`（`ContentView+SessionList.swift:165-166, 279`；`SessionListRows.swift:175`） |
| 2 | **会话行 + 文件夹卡** | 左 44pt 圆形头像（分类色 18% 底）+ 右两行文字（16/semibold 标题、14/secondary 摘要）+ 最右 13/tertiary 相对时间；选中时整行套一个 `#B7AF96@30%`、圆角 10 的填充条；文件夹是同一行的变体，用 16 圆角卡 + 0.75pt 高光描边把 N 行"焊"成一个容器 | 44 头像、16/12 padding、radius 10、`folderEdgeHighlight` 0.75pt（`SessionRow.swift:226, 277, 290-297, 323, 336-337`；`SidebarSurfaces.swift:122-127, 211`） |
| 3 | **FAB 行（列表底部）** | 左右各一枚 56pt 玻璃圆（左=新建，品牌沙 tint；右=搜索，无色玻璃），可横向拖拽吸附到边距 16；点搜索时右圆被一条 56 高的玻璃胶囊"顶开"（scale 0.85 + fade）；上方永远留 20pt 让内容透出 | 56×56、edge 16、bottom 20、tint `#B7AF96@30%`（`DraggableFAB.swift:27-28`；`ContentView+FAB.swift:163-166, 367-368`） |
| 4 | **聊天页（消息流）** | 整页 `systemBackground`；消息区是虚拟化 UICollectionView，**内容一直滚到导航栏玻璃底下**；底部一条从透明到实色的 0~80% 线性渐变遮罩，把消息"沉"到输入框后面 | `ignoresSafeArea(.container, edges:.top)`；渐变 stops `0 → 0.8`（`NavBarModifiers.swift:82`；`AIChatView+InputBar.swift:502-509`） |
| 5 | **用户消息气泡** | 右对齐、最多占 60% 宽（`Spacer(minLength: 60)`），玻璃圆角 18，内距 `14×10`，字号 16.5；排队未发送时**故意不用玻璃**，改成空填充 + 1.5pt 虚线（dash 6/4）——"看起来还没落地" | radius 18（非 continuous）、`Spacer(minLength: 60)`、dash `[6,4]`（`ChatMessageViews.swift:430, 446-447, 182, 194-196`） |
| 6 | **输入框（composer）** | 单个玻璃圆角 20 容器浮在消息上，内部三行：附件网格 → 文本区（上留白 16）→ 按钮行（34pt 圆形图标 + 34pt 发送箭头）；外层再包 `h12/v8`；上下文将满时沿内壁渗出一圈 13pt 模糊 7pt 的琥珀/红色内发光（呼吸 1.4s） | radius 20、按钮 34、内发光 `lineWidth 13 / blur 7`（`:183-184`）、opacity `0.16~0.26`（`ComposerSurface.swift:103, 109`；`MicButton.swift:42`；`ChatSendButton.swift:119`） |
| 7 | **工具调用状态条** | 贴在输入框正上方，圆角 10 玻璃条，内容 = 状态图标 + 标题 + "第 i/n 个工具"翻页器；点开变成整屏 sheet 显示实时输出 | radius 10、`.glassEffect(.regular)`（`ToolLiveSheet.swift:3330-3343`） |
| 8 | **`/` 与 `@` 弹窗** | 圆角 20 玻璃卡、四周内缩 6、行高亮是"同心圆角" 14 的胶囊；选中行用 `Color.accentColor` 实心填充（不是浅色底）；卡片底部贴合输入框顶边（用 `alignmentGuide(.top){$0[.bottom]}` 精确对齐） | 20 / 6 / 14、`Color.accentColor` 选中（`AIChatView+InputPopups.swift:312, 448-458, 463-465`） |
| 9 | **设置页** | 标准 iOS `List`（insetGrouped 默认），分段标题 `Section("Appearance")`；每行 = 21×21 圆底 + 9pt 白色 SF Symbol + 文字 + chevron，图标底色是纯系统色（indigo/blue/pink/teal/green/orange/cyan/red/gray） | 图标 `21×21`、字号 `9`、`.background(.indigo, in: Circle())`（`SettingsSheet.swift:80-95, 200-215`） |
| 10 | **模型选择器** | 大 List（`.large` sheet）；分组行可展开，右侧 chevron 是 28×28 圆 + `tertiarySystemFill` 底；"默认"标签是 12pt 蓝字 + `blue@10%` 胶囊（`h5/v1`）；配额/快速测试走 `.medium, .large` 双档 sheet | 28×28、`blue@0.1`、capsule（`UnifiedModelPicker.swift:924-951, 754`） |
| 11 | **文件浏览器** | `List(.plain)`；行 = 12 间距 + `title2` 图标（目录蓝、文件 secondary）+ 文件名 + 大小/日期；顶部路径栏是 `secondarySystemBackground` 实色；预览不支持时显示 48pt 灰图标 + `.subheadline` 说明 | `HStack(spacing: 12)`、`font(.title2)`、48pt（`FileBrowserView.swift:826-834, 94, 705`） |
| 12 | **终端（Minis Shell）** | **纯黑**满屏（`Color.black`），inline 导航标题 "Minis Shell"，底部 `safeAreaInset` 挂一条键盘配件栏（QuickCommand 按钮：未激活 `white 0.25` 底 + 绿字、激活 `blue` 底 + 白字、圆角 6） | `Color.black`、radius 6、`caption`（`ISHTerminalView.swift:95, 497-499`） |
| 13 | **语音输入（内联）** | 输入框原地换成语音面板：波形条 + 识别文本（居中、`.subheadline`）；所有控件都是 `Circle().fill(ChatColors.inputIconBg)` + 0.5pt `inputIconBorder` 描边；语言的徽章是 `Capsule` | 圆 + 0.5 描边（`InlineVoiceInputView.swift:766, 954, 1149-1151`） |
| 14 | **Markdown / 代码块** | 正文 16.5；代码块 **圆角 8、浅色模式黑底 / 深色模式 `white:0.15` 灰底**，等宽 Menlo + PingFang SC 级联；行内代码 5 圆角小胶囊（亮 `#F2F2F7` / 暗 `#3A3A3C`）+ 橙字；引用条是 `systemOrange@50%` | 8 / 5 / 16.5 / 0.85×（`MarkdownTheme.swift:54-55, 58, 66-77, 43-48`） |
| 15 | **图片 / 视频全屏预览** | 纯黑满屏 + 左右 `TabView` 翻页；所有浮控件（关闭、保存、翻页、播放）都是 **44×44** 的 `.ultraThinMaterial` 圆钮叠在画面上——这是全项目**唯一**大量使用 `ultraThinMaterial` 的场景（15 处 Circle） | `Color.black.ignoresSafeArea()` + `.frame(44×44)` + `.ultraThinMaterial, in: Circle()`（`ImagePreview.swift:414, 430-434`；`MessageImageGallery.swift:288, 354-358`） |

> 补充：**多选工具栏**是 `safeAreaInset(.bottom)` 的一条 `.ultraThinMaterial` 通栏（无圆角、无描边），按钮竖排 图标 20pt + `.caption2` 文字，`padding(.vertical, 10)`（`ContentView+SelectionToolbar.swift:87-99`）。删除态用 `Color.red`，禁用态 `Color.gray`。

---

## 10. 安卓端对标要点（10 条硬特征，缺一条就不像）

| # | 硬特征 | iOS 原始值 | 安卓落地建议 |
|---|---|---|---|
| 1 | **输入条必须浮在内容上，不是一个底部条** | 消息列表 `overlay(alignment:.bottom)` + 输入框玻璃圆角 20 + 外层 `h12/v8` + 底部 0→80% 渐隐遮罩（`AIChatView.swift:554-560`；`AIChatView+InputBar.swift:500-509`） | `Box` 叠 `LazyColumn`，composer 用 `Alignment.BottomCenter` + `WindowInsets.ime`；底部铺一层 `Brush.verticalGradient(transparent → surface, 0f to 0.8f)` |
| 2 | **玻璃用「材质 + 圆角 20 + 发丝高光」三件套，不手画模糊** | `glassEffect(.regular, in: RoundedRectangle(20, .continuous))`；高光 = 暗 `white@0.14` / 亮 `black@0.08`，0.5pt（`ComposerSurface.swift:109`；`AIChatView+InputPopups.swift:342-346`） | API 31+ `RenderEffect.createBlurEffect` 或 blur+`graphicsLayer`；叠 1dp（≈0.5pt）`border` 渐变白→透明 |
| 3 | **上了玻璃就删阴影、删描边**；只有非玻璃回退才加 `0 3 8 @15%` | `ContentView+FAB.swift:128-131`；`ComposerSurface.swift:93-95` | 玻璃分支 `Modifier` 里不 `shadow()`；`tonalElevation = 0` |
| 4 | **FAB 是 56pt 玻璃圆、距边 16、距底 20，并且可拖拽吸附左右** | `DraggableFAB.swift:27-28, 52`；`ContentView+FAB.swift:367-368` | `Modifier.offset` + `draggable`；吸附用 `animateFloatAsState` + `spring(dampingRatio = 0.75, stiffness 对应 response 0.35)` |
| 5 | **品牌色只有一个：沙色 `#B7AF96`，且玻璃里必须降到 30% alpha** | `ContentView+FAB.swift:23-25, 44-46` | `Color(0xFFB7AF96)`；叠加时 `copy(alpha = 0.30f)`，**不要**用满强度（会变成不透明圆盘） |
| 6 | **强调色 `#3686EE`，但聊天内的"强调"是墨色 `label`（黑/白），不是蓝** | `ChatColors.swift:37-39`；`AccentColor` 见 §3.2 | 两套：`colorPrimary` 用于链接/开关；`sendButton` 用 `onSurface`（浅=近黑，深=白） |
| 7 | **圆角只用 5 档主力 8 / 10 / 12 / 16 / 20（辅助 14 / 18），且必须是连续圆角（squircle）** | 统计见 §4.1（196 处，前三档占 68%）；气泡 18 是唯一非 continuous | 自定义 `ContinuousRoundedCornerShape`（贝塞尔近似 squircle），不要用默认 `RoundedCornerShape` |
| 8 | **内层圆角 = 外层圆角 − 内边距**（弹窗 20/6→14） | `AIChatView+InputPopups.swift:444-449, 463-465` | `Modifier.concentric(outerRadius, padding)`；Compose 的 `RoundedCornerShape` 不会自动同心 |
| 9 | **列表是 `.plain` 无卡片、无分隔线**：`listRowInsets(0)` + `listRowSeparator(hidden)`，靠 6pt 横向 inset 的圆角高亮条区分选中 | `ContentView+SessionList.swift:165-166`；`SessionListRows.swift:173-175` | `LazyColumn` + `Modifier.padding(horizontal = 6.dp, vertical = 2.dp)` 的 `Surface(shape = 10.dp, color = sand30)` |
| 10 | **字号梯度只有 5 档：9 / 11-13 / 14 / 16 / 18.5；字重只有 semibold / medium** | 统计见 §6.1；`SessionRow.swift:277, 290, 323`；`ContentView+SidebarToolbar.swift:270` | `Typography`: label=18.5/semibold, title=16/semibold, body=14, caption=13, micro=11, badge=9 |

**额外 5 条容易漏的细节**（做到这 5 条才算"1:1"）：

11. **导航栏不画背景**，让内容滚到它下面（`ignoresSafeArea(edges = Top)`），玻璃由系统/自绘材质采样下方内容生成。
12. **每个界面只有一个"最高级"表面**：同一屏里不要出现两块同级玻璃（`buttonStyle(.glass)` 在搜索胶囊里被明确否决，理由写在 `ContentView+FAB.swift:49-55`）。
13. **等待/未确认状态不用玻璃**：排队消息用虚线（`ChatMessageViews.swift:194-196`），因为"玻璃读起来是已经落地的实体"。
14. **胶囊是状态专属**（155 处 `Capsule()`），卡片是圆角矩形专属——两者不混用。
15. **深色不是纯黑叠纯黑**：`#000`（页面）→ `#121212`（文件夹卡）→ `#1F1F1F`(0.12) → `#262626`(0.15，代码块)。每层至少差 7~12 个灰阶，否则玻璃失去层次。

---

## 附录 A：本次统计的命令与原始输出（可复现）

```bash
cd /var/minis/workspace/minisglass/minis/src/ios

# 圆角
rg -o 'cornerRadius: [0-9.]+' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
# 描边
rg -o 'lineWidth: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 内距
rg -o '\.padding\(\.[a-z]+, [0-9.]+\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 栈间距
rg -o 'spacing: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 语义字体
rg -o '\.font\(\.(largeTitle|title|title2|title3|headline|subheadline|body|callout|footnote|caption|caption2)\b' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 显式字号
rg -o '\.system\(size: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 字重
rg -o '\.weight\(\.(ultraLight|thin|light|regular|medium|semibold|bold|heavy|black)\)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
# 材质
rg -o '\.ultraThinMaterial|\.regularMaterial|\.bar\b|\.thinMaterial' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
# 玻璃
rg -n 'glassEffect|GlassEffectContainer|glassEffectID' -g '*.swift' .
# 动效
rg -o '\.animation\(\.(easeInOut|easeIn|easeOut|linear|spring|smooth|snappy|bouncy|interactiveSpring)' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
rg -o 'duration: [0-9.]+' -N --no-filename -g 'Views/**/*.swift' . | sort | uniq -c | sort -rn
rg -o '(response|dampingFraction): [0-9.]+' -N --no-filename -g '*.swift' . | sort | uniq -c | sort -rn
```

**统计基线**：`rg` 覆盖 1040 个 `.swift` 文件 / 20.0 MB；`glassEffect` 命中 42 行（含注释）→ 9 处真实调用；`Capsule()` 155 处；`.shadow(` 44 处。

## 附录 B：文件地图（改安卓端时按此顺序读 iOS 源码）

```
Views/ContentView/
  ContentView.swift              根视图 body、sheet 群、状态（1483 行）
  ContentView+Layout.swift       split / stack 两套布局
  ContentView+SessionList.swift  List 本体 + safeAreaInset(fabRow)
  ContentView+SidebarToolbar.swift  顶部栏（principal 标题 18.5/semibold）
  ContentView+FAB.swift          玻璃 FAB + 玻璃搜索胶囊（品牌色定义在此）
  ContentView+SectionHeaders.swift 日期/文件夹分组头
  Support/MacOS27GlassWorkaround.swift  导航栏玻璃兼容层（Mac only）
  Sidebar/SidebarSurfaces.swift  文件夹卡三态表面 + 搜索条表面
  Sidebar/SessionRow.swift       会话行
  Sidebar/SessionListRows.swift  行背景 / 选中填充
  Sidebar/DraggableFAB.swift     56pt 可拖拽 FAB

Views/Chat/
  AIChatView/AIChatView.swift              聊天页 body（ZStack + 底部叠层）
  AIChatView/AIChatView+InputBar.swift     输入条 + 底部渐隐
  AIChatView/Composer/ComposerSurface.swift  ComposerSurface / 浮动圆钮 / 内发光
  AIChatView/Composer/MicButton.swift      34pt 圆钮
  AIChatView/Composer/ChatSendButton.swift 34pt 发送箭头
  AIChatView/AIChatView+InputPopups.swift  玻璃弹窗（20/6/14 同心圆角）
  AIChatView/Support/ChatColors.swift      全项目唯一调色板
  AIChatView/Toolbar/NavBarModifiers.swift 导航栏玻璃策略
  ChatMessageViews.swift                   气泡 18 + 长按浮板
  ToolLiveSheet.swift                      工具状态条 10 + 工具详情
  Markdown/Support/MarkdownTheme.swift     排版比例 / 代码块配色

Views/Chat/Media/                          全屏预览（ultraThinMaterial 圆钮）
Views/Rootfs/ISHTerminalView.swift          终端（纯黑）
Views/Settings/SettingsSheet.swift          设置首页（21pt 圆底图标）
Views/Providers/UnifiedModelPicker.swift    模型选择
Shared/FontSettings.swift                   三条字号缩放轴
Assets.xcassets/AccentColor.colorset/Contents.json   唯一资产色
```