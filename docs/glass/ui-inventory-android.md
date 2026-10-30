# 安卓 UI 界面与外壳组件盘点（Liquid Glass 改造前置）

> 范围：`/var/minis/workspace/minisglass/minis/src/android/app/src/main/java/com/openminis/app/ui/`
> 方法：只读源码 + ripgrep 统计，未修改任何文件。
> 结论全部带 `文件:行号`。行号基于当前工作区快照。

---

## 0. 盘面基线

### 0.1 规模

```
$ find . -name '*.kt' | wc -l
281
$ find . -name '*.kt' -exec cat {} + | wc -l
140402
```

按目录：

| 目录 | 文件数 | 行数 | 说明 |
|---|---:|---:|---|
| `chat/` | 126 | 75,087 | 会话页，占全量 53%；`ChatScreen.kt` 单文件 8,760 行 |
| `settings/` | 59 | 30,265 | 设置体系（含 `backup/` 11 文件） |
| `components/` | 28 | 6,239 | 共享视觉组件 |
| `sessions/` | 9 | 6,073 | 会话列表 |
| `sandbox/` | 10 | 4,689 | 文件浏览 / 预览 / rootfs / 镜像 |
| `terminal/` | 11 | 2,900 | 终端（Canvas + AndroidView 原生视图） |
| `markdown/` | 5 | 2,864 | Markdown + KaTeX |
| `navigation/` | 3 | 2,725 | 路由 + 分栏骨架 |
| `browser/` | 6 | 2,180 | 内置浏览器（WebView） |
| `scheduled/` | 4 | 1,764 | 定时任务 |
| `preview/` | 4 | 1,562 | HTML 预览（WebView） |
| `quota/` | 3 | 1,476 | 配额 |
| `onboarding/` | 3 | 811 | 首次配置 |
| `media/` | 1 | 659 | 内联播放器 |
| `theme/` | 2 | 393 | 主题 |
| `util/` | 3 | 198 | 小工具 |
| `webview/` | 1 | 79 | WebView 渲染进程守护 |

最大 10 个文件（改造时无法回避的"巨石"）：

```
  20341 ./chat/ChatViewModel.kt
   8760 ./chat/ChatScreen.kt
   5033 ./chat/StreamingMarkdownText.kt
   4019 ./sessions/SessionListScreen.kt
   2123 ./settings/backup/BackupAndRestoreScreen.kt
   1730 ./chat/MinisTextKitGesture.kt
   1681 ./chat/ChatToolDetailUI.kt
   1650 ./navigation/AppNavigation.kt
   1628 ./chat/HelperUi.kt
   1589 ./chat/voice/InlineVoiceInputPanel.kt
```

> `ChatScreen.kt` 里 **只有一个 `@Composable fun ChatScreen`（:515–8584）**，全长 8,069 行，顶栏 / 消息列表 / 输入卡 / 全部 Popup 都在这一个函数体内。这是玻璃改造最大的单点风险。

### 0.2 平台约束（先记下，决定方案可行性）

```
$ rg -n 'minSdk|targetSdk|compileSdk|composeBom' app/build.gradle.kts
33:    compileSdk = 36
37:        minSdk = 26
38:        targetSdk = 35
369:    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
```

- **minSdk 26**，而 `Modifier.blur()` / `RenderEffect` 需要 **API 31**。
- 全工程 **零 `Modifier.blur` / 零 `RenderEffect` / 零 haze 依赖**：

```
$ rg -n 'Modifier\.blur|\.blur\(|RenderEffect|Haze|haze' --glob '*.kt'
chat/ChatComposerWidgets.kt:616:                // smeared square. Hand-drawn because Modifier.blur needs API
chat/ContextUsageHintUi.kt:223:        // minSdk 26 — `Modifier.blur()` needs API 31, so it is not an option
```
只有两条注释在解释"为什么不能用 blur"。也就是说：**当前没有任何模糊/材质实现，玻璃要么新写、要么引三方库，且必须自带 API<31 降级路径。**

- `build.gradle.kts` 中无任何 shader / 图形库依赖（`rg 'blur|shader|RenderEffect|graphics'` 无命中）。

### 0.3 三条先行判断

1. **背景是纯色**（`#F2F2F7` / `#000000`，`theme/Theme.kt:77-85`）。玻璃的模糊在没有纹理的背景上**没有视觉收益**。改造第一步不是加 blur，而是**先有背景层**（渐变 / 噪点 / 轻网格），否则所有玻璃都会退化成"半透明灰卡片"。
2. **外壳没有统一抽象**：顶栏有 2 套（`SettingsScaffold` 36 处 / 裸 `TopAppBar` 29 处），分区卡有 **3 套同名 `SettingsSection`**，弹窗有 2 类（`MinisAlertDialog` 21 处 / 裸 `AlertDialog` 83 处）。玻璃化必须先把这些收敛成 1 套，否则要改 200+ 处。
3. **输入卡是自绘的**（`ChatScreen.kt:6349` `drawBehind` + `Paint.setShadowLayer`，圆角 20dp，填充色由阴影画笔的 color 一并绘制）。它不是 `Surface`，不能靠换 `containerColor` 玻璃化——这是**必须单独重写的组件**。

---

## 1. 导航目的地清单

### 1.1 统计

```
$ rg -c '^\s{8}composable\(' AppNavigation.kt
64
$ rg -c '^\s{8}dialog\(' AppNavigation.kt
1
$ rg -c 'const val [A-Z_]+ =' AppNavigation.kt
66
```

- **65 个目的地**：64 个 `composable(...)` + 1 个 `dialog(...)`。
- `Routes` 有 66 个 `const val`，其中 `BACKUP_INITIAL_TAB_KEY`（`AppNavigation.kt:133`）不是路由，故 **65 条路由常量 ↔ 65 个目的地一一对应**。
- **没有 NavigationBar / NavigationRail / BottomNavigation**：全工程 `rg 'NavigationBar\(|NavigationRail\('` = **0**。App 是"两个根 + 一路 push"的结构。
- 两个根目的地都渲染同一个 `ChatSplitScaffoldRoute`（`AppNavigation.kt:618`、`:630`），靠 `initialSessionId` 区分；启动分发在 `AppNavigation.kt:539-560`（`startDestination`）与 `:374-517`（冷启动模式 0/1/2/3 判定）。
- 页面转场统一在 `AppNavigation.kt:563-616`（shared-axis X，300ms enter / 200ms exit，`EmphasizedDecelerate` / `EmphasizedAccelerate`）。**玻璃化若改用自定义转场，会与这套全局 spec 冲突。**

### 1.2 全表（65 条）

| # | 路由 | 目标 Composable | 源文件:行 | 职责 | 全屏 |
|---:|---|---|---|---|---|
| 1 | `sessions` | `ChatSplitScaffoldRoute` | `navigation/AppNavigation.kt:618` → `navigation/ChatSplitScaffold.kt:903` | App 首页：会话列表；宽屏为左栏 | 是 |
| 2 | `chat/{sessionId}` | `ChatSplitScaffoldRoute` | `AppNavigation.kt:630` → `ChatSplitScaffold.kt:903` | 同上，初始打开指定会话 | 是 |
| 3 | `settings` | `SettingsScreen` | `AppNavigation.kt:646` → `settings/SettingsScreen.kt:82` | 设置总入口 | 是 |
| 4 | `backup` | `BackupAndRestoreScreen` | `AppNavigation.kt:676` → `settings/backup/BackupAndRestoreScreen.kt:215` | 备份 / 恢复双 Tab | 是 |
| 5 | `backup_history_detail/{recordId}` | `BackupHistoryDetailScreen` | `AppNavigation.kt:699` → `settings/backup/BackupHistoryDetailScreen.kt:66` | 单条备份记录详情 | 是 |
| 6 | `backup_destination_browse/{remoteName}` | `BackupDestinationBrowseScreen` | `AppNavigation.kt:745` → `settings/backup/BackupDestinationBrowseScreen.kt:55` | 浏览备份目标远端目录 | 是 |
| 7 | `restore_servers` | `RestoreServersScreen` | `AppNavigation.kt:771` → `settings/backup/RestoreServersScreen.kt:52` | 选择恢复来源服务器 | 是 |
| 8 | `restore_browse/{remoteName}` | `RestoreBrowseScreen` | `AppNavigation.kt:782` → `settings/backup/RestoreBrowseScreen.kt:75` | 浏览远端备份包（`scrollable=false`） | 是 |
| 9 | `backup_destinations` | `RcloneDestinationsScreen` | `AppNavigation.kt:831` → `settings/backup/RcloneDestinationsScreen.kt:71` | rclone 目标管理 | 是 |
| 10 | `shared_folders` | `SharedFoldersScreen` | `AppNavigation.kt:837` → `settings/SharedFoldersScreen.kt:52` | 共享目录（shared/skills/memory） | 是 |
| 11 | `shared_folders_detail/{folderId}` | `SharedFolderDetailScreen` | `AppNavigation.kt:846` → `settings/SharedFolderDetailScreen.kt:53` | 单个共享目录详情 | 是 |
| 12 | `mounted_folders` | `MountedFoldersScreen` | `AppNavigation.kt:878` → `settings/MountedFoldersScreen.kt:89` | 外部挂载目录列表 | 是 |
| 13 | `mounted_folders_detail/{mountId}` | `MountDetailScreen` | `AppNavigation.kt:888` → `settings/MountDetailScreen.kt:67` | 单个挂载详情 | 是 |
| 14 | `providers` | `ProviderListScreen` | `AppNavigation.kt:921` → `settings/ProviderListScreen.kt:66` | 服务商列表 | 是 |
| 15 | `voice_service/{instanceId}` | `ShadowVoiceDetailScreen` | `AppNavigation.kt:935` → `settings/ShadowVoiceDetailScreen.kt:42` | 语音服务只读详情 | 是 |
| 16 | `add_provider` | `AddProviderScreen` | `AppNavigation.kt:947` → `settings/AddProviderScreen.kt:91` | 新增服务商 | 是 |
| 17 | `provider/{instanceId}` | `ProviderDetailScreen` | `AppNavigation.kt:955` → `settings/ProviderDetailScreen.kt:88` | 服务商详情 / 模型列表 | 是 |
| 18 | `model_groups` | `ModelGroupsScreen` | `AppNavigation.kt:976` → `settings/ModelGroupsScreen.kt:89` | 模型分组 + Agent Loop 配置 | 是 |
| 19 | `model_group/{groupId}` | `ModelGroupDetailScreen` | `AppNavigation.kt:992` → `settings/ModelGroupDetailScreen.kt:90` | 单个分组详情 | 是 |
| 20 | `add_models_to_group/{groupId}` | `AddModelsToGroupScreen` | `AppNavigation.kt:1007` → `settings/AddModelsToGroupScreen.kt:36` | 往分组加模型（全屏选择器） | 是 |
| 21 | `add_models_to_agent_loop` | `AddAgentLoopModelsScreen` | `AppNavigation.kt:1023` → `settings/AgentLoopAddSheets.kt:63` | 往 Agent Loop 加模型条目 | 是 |
| 22 | `add_groups_to_agent_loop` | `AddAgentLoopGroupsScreen` | `AppNavigation.kt:1033` → `settings/AgentLoopAddSheets.kt:172` | 往 Agent Loop 加模型组 | 是 |
| 23 | `agent_loop_models` | （无 UI，`LaunchedEffect` 直接 pop） | `AppNavigation.kt:1047` | 旧深链兼容空壳 | 否（立即返回） |
| 24 | `model_entry/{instanceId}/{entryId}` | `ModelEntryDetailScreen` | `AppNavigation.kt:1053` → `settings/ModelEntryDetailScreen.kt:48` | 单个模型条目参数 | 是 |
| 25 | `add_custom_model/{instanceId}` | `AddCustomModelScreen` | `AppNavigation.kt:1076` → `settings/AddCustomModelScreen.kt:34` | 手填自定义模型 | 是 |
| 26 | `storage` | `StorageManagementScreen` | `AppNavigation.kt:1088` → `settings/StorageManagementScreen.kt:104` | 存储占用总览 | 是 |
| 27 | `session_storage/{sessionId}` | `SessionStorageDetailScreen` | `AppNavigation.kt:1107` → `settings/StorageManagementScreen.kt:596` | 单会话存储明细 | 是 |
| 28 | `rootfs_management` | `RootfsManagementScreen` | `AppNavigation.kt:1150` → `sandbox/RootfsManagementScreen.kt:61` | Linux rootfs 管理 | 是 |
| 29 | `mirror_category/{categoryKey}` | `MirrorCategoryDetailScreen` | `AppNavigation.kt:1177` → `sandbox/MirrorSettingsScreen.kt:612` | 镜像源分类手选 | 是 |
| 30 | `file_browser` | `FileBrowserScreen` | `AppNavigation.kt:1190` → `sandbox/FileBrowserScreen.kt:93` | 文件浏览器（宿主根） | 是 |
| 31 | `chat_files/{sessionId}?path=` | `FileBrowserScreen`（就地建 VM） | `AppNavigation.kt:1208` | 会话文件浏览（rootfs + /var/minis） | 是 |
| 32 | `file_preview` | `FilePreviewScreen` | `AppNavigation.kt:1278`（`dialog(`）→ `sandbox/FilePreviewScreen.kt:101` | 文件预览（图/文/HTML/视频） | 是（全屏 Dialog） |
| 33 | `network` | `NetworkSettingsScreen` | `AppNavigation.kt:1293` → `settings/NetworkSettingsScreen.kt:98` | 网络总览 | 是 |
| 34 | `network_tailscale` | `NetworkSettingsScreen(page=TAILSCALE)` | `AppNavigation.kt:1299` | Tailscale | 是 |
| 35 | `network_proxies` | `NetworkSettingsScreen(page=PROXIES)` | `AppNavigation.kt:1305` | 代理 | 是 |
| 36 | `container_dns` | `ContainerDnsScreen` | `AppNavigation.kt:1311` → `sandbox/ContainerDnsScreen.kt:16` | 容器 DNS | 是 |
| 37 | `env_vars` | `EnvironmentVariablesScreen` | `AppNavigation.kt:1315` → `settings/EnvironmentVariablesScreen.kt:60` | 环境变量 | 是 |
| 38 | `skills` | `SkillsManagementScreen` | `AppNavigation.kt:1324` → `settings/SkillsManagementScreen.kt:118` | 技能管理 | 是 |
| 39 | `skill/{skillId}` | `SkillDetailScreen` | `AppNavigation.kt:1335` → `settings/SkillsManagementScreen.kt:680` | 技能详情 | 是 |
| 40 | `skill_file/{skillId}/{relativePath}` | `SkillFileViewerScreen` | `AppNavigation.kt:1352` → `settings/SkillsManagementScreen.kt:1213` | 技能文件查看 | 是 |
| 41 | `minis_skills_browser` | `MinisSkillsBrowserScreen` | `AppNavigation.kt:1372` → `settings/MinisSkillsBrowserScreen.kt:69` | 内置技能浏览器（WebView） | 是 |
| 42 | `terminal?initCommand=&sessionId=` | `TerminalScreen` | `AppNavigation.kt:1381` → `terminal/TerminalScreen.kt:87` | 终端 | 是 |
| 43 | `memory` | `MemoryManagementScreen` | `AppNavigation.kt:1408` → `settings/MemoryManagementScreen.kt:60` | 记忆管理 | 是 |
| 44 | `mcp` | `MCPIntegrationsScreen` | `AppNavigation.kt:1421` → `settings/MCPIntegrationsScreen.kt` | MCP 集成 | 是 |
| 45 | `soul` | `SoulSettingsScreen` | `AppNavigation.kt:1432` → `settings/SoulSettingsScreen.kt:96` | SOUL.md 编辑 | 是 |
| 46 | `memory_file/{fileName}/{isGlobal}` | `MemoryFileEditScreen` | `AppNavigation.kt:1438` → `settings/MemoryManagementScreen.kt:287` | 记忆文件编辑 | 是 |
| 47 | `permissions` | `OffloadPermissionScreen` | `AppNavigation.kt:1457` → `settings/OffloadPermissionScreen.kt:43` | 权限总览 | 是 |
| 48 | `shizuku` | `ShizukuPermissionScreen` | `AppNavigation.kt:1469` → `settings/ShizukuPermissionScreen.kt:54` | Shizuku/AXManager 授权引导 | 是 |
| 49 | `system_permissions` | `SystemPermissionsScreen` | `AppNavigation.kt:1475` → `settings/SystemPermissionsScreen.kt:110` | 系统权限状态 | 是 |
| 50 | `usage_stats` | `UsageStatsScreen` | `AppNavigation.kt:1481` → `settings/UsageStatsScreen.kt:184` | Token 用量统计 | 是 |
| 51 | `appearance` | `AppearanceScreen` | `AppNavigation.kt:1489` → `settings/AppearanceScreen.kt:280` | 外观设置 | 是 |
| 52 | `background` | `BackgroundSettingsScreen` | `AppNavigation.kt:1495` → `settings/BackgroundSettingsScreen.kt:87` | 后台 / 通知 / 灵动岛 | 是 |
| 53 | `agents` | `SubAgentsScreen` | `AppNavigation.kt:1512` → `settings/SubAgentsScreen.kt:69` | 子代理名册（深链兼容入口） | 是 |
| 54 | `sub_agents` | `SubAgentsScreen` | `AppNavigation.kt:1519` → `settings/SubAgentsScreen.kt:69` | 同上（重复注册） | 是 |
| 55 | `sub_agent/{agentId}` | `SubAgentDetailScreen` | `AppNavigation.kt:1526` → `settings/SubAgentsScreen.kt:229` | 子代理详情 | 是 |
| 56 | `agent_tools` | `AgentToolsSettingsScreen` | `AppNavigation.kt:1536` → `settings/AgentToolsSettingsScreen.kt:27` | 工具开关 | 是 |
| 57 | `agent_transcript/{sessionId}` | `AgentTranscriptScreen` | `AppNavigation.kt:1542` → `chat/HelperUi.kt:1379` | 子代理只读转录 | 是 |
| 58 | `about` | `AboutScreen` | `AppNavigation.kt:1553` → `settings/AboutScreen.kt:40` | 关于 | 是 |
| 59 | `logs` | `LogManagementScreen` | `AppNavigation.kt:1557` → `settings/LogManagementScreen.kt:70` | 日志管理 | 是 |
| 60 | `config_audit_detail/{auditId}` | `ConfigAuditDetailScreen` | `AppNavigation.kt:1569` → `settings/ConfigAuditDetailScreen.kt:58` | 配置变更记录 | 是 |
| 61 | `log_detail/{fileName}` | `LogDetailScreen` | `AppNavigation.kt:1580` → `settings/LogManagementScreen.kt:445` | 日志文件内容 | 是 |
| 62 | `onboarding_models` | `OnboardingModelSelectionScreen` | `AppNavigation.kt:1591` → `onboarding/OnboardingModelSelectionScreen.kt:68` | 首次配置选模型 | 是 |
| 63 | `scheduled_tasks` | `ScheduledTasksScreen` | `AppNavigation.kt:1602` → `scheduled/ScheduledTasksScreen.kt:75` | 定时任务列表 | 是 |
| 64 | `scheduled_tasks/runs/{taskId}` | `ScheduledTaskRunsScreen` | `AppNavigation.kt:1617` → `scheduled/ScheduledTaskRunsScreen.kt:56` | 任务执行记录 | 是 |
| 65 | `scheduled_tasks/edit?taskId=` | `ScheduledTaskEditScreen` | `AppNavigation.kt:1630` → `scheduled/ScheduledTaskEditScreen.kt:91` | 任务编辑 | 是 |

### 1.3 对改造的含义

- **只有 1 条路由是"对话框式"（`file_preview`）**，其余 64 条都会把上一个页面从 composition 卸掉。玻璃的"透出下层内容"在 NavHost 里**天然拿不到下层像素**——要么改成 `dialog()` 语义，要么接受"透出背景层"（而非"透出上一页"）。
- `AppNavigation.kt:565` 给 NavHost 铺了 `Modifier.background(MaterialTheme.colorScheme.background)`，这层**不透明底色必须替换**成玻璃背景层，否则所有页面的 blur 采样到的都是同一个纯色。
- 三级/四级深链（如 `skill_file`、`config_audit_detail`）没有任何 tab/分段控件，返回全靠 `safePopBackStack()`（`navigation/SafeNavigate.kt`，63 行）。

---

## 2. 外壳（chrome）组件定位

> 「贴边浮层 / 栏」类实现共分 5 个族：顶栏、输入区、悬浮按钮、弹层、分栏。下面逐一给位置与**当前实现方式**。

### 2.1 顶部导航栏 / 标题栏

| 场景 | 文件:行号 | 当前实现 | 现状细节 |
|---|---|---|---|
| 会话页顶栏 | `chat/ChatScreen.kt:2981` | `Scaffold(topBar=…)` + `Column` + M3 `TopAppBar` | `expandedHeight = 68.dp`（`:3758`），`windowInsets = WindowInsets.statusBars`（`:3740`），`containerColor = ChatColors.background.copy(alpha = 0.92f)`（`:3742`，**已是 92% 不透明纯色，没有模糊**） |
| 会话页顶栏下挂的定时任务胶囊 | `chat/ChatScreen.kt:3764` → `chat/ScheduledTaskPill.kt`（446 行） | 作为 `topBar` 内 `Column` 的第二个孩子 | **不是浮层**，占布局高度，把消息列表整体下推 |
| 会话页 ⋮ 菜单 | `chat/ChatScreen.kt:3387` 起的 `actions` 块：⋮ 菜单 `:3393`、速度子菜单 `:3712` | 两个 `MinisMenu`（`Popup` 独立窗口） | 见 2.4 |
| 会话列表顶栏 | `sessions/SessionListScreen.kt:972` | `Scaffold` + M3 `TopAppBar`（默认 64dp） | 标题区是自绘 `Column`（`Text` 20sp + `NetworkStatusBadges`），`navigationIcon` 用 `BadgedIconButton` |
| 设置总入口顶栏 | `settings/SettingsScreen.kt:131` | `Scaffold` + 裸 `TopAppBar` | **没有走 `SettingsScaffold`**，containerColor 未指定（默认 `surface`） |
| 二级页顶栏（统一） | `settings/SettingsComponents.kt:68` `SettingsScaffold` → `:110` `CenterAlignedTopAppBar` / `:119` `TopAppBar` | 共享封装 | `containerColor = MaterialTheme.colorScheme.background`（**完全不透明**），支持 `actions` / `navigation` / `floatingActionButton` / `bottomBar` / `scrollable` |
| 二级页顶栏（绕过） | 见下 | 裸 `Scaffold` + `TopAppBar` | 29 处 |

`SettingsScaffold` 覆盖率：**36 处调用，分布在 30 个文件**

```
$ rg -c 'SettingsScaffold\(' --glob '*.kt' | awk -F: '{s+=$2} END {print s}'
36
settings/SubAgentsScreen.kt:3        settings/AddProviderScreen.kt:3
settings/StorageManagementScreen.kt:2  （其余 27 个文件各 1）
```

**没有走 `SettingsScaffold`、直接拼 `Scaffold + TopAppBar` 的 29 处**（玻璃化必须逐处改）：

```
chat/HelperUi.kt:1421            chat/ChatScreen.kt:2981
sessions/SessionListScreen.kt:972 scheduled/ScheduledTasksScreen.kt:92
scheduled/ScheduledTaskRunsScreen.kt:71   scheduled/ScheduledTaskEditScreen.kt:245
settings/MountedFoldersScreen.kt:163      settings/MountDetailScreen.kt:96
settings/ModelGroupsScreen.kt:162         settings/ModelGroupDetailScreen.kt:147
settings/SettingsScreen.kt:131            settings/SkillsManagementScreen.kt:682
settings/SkillsManagementScreen.kt:1213   settings/SystemPermissionsScreen.kt:112
settings/MemoryManagementScreen.kt:310    settings/LogManagementScreen.kt:480
settings/BackgroundSettingsScreen.kt:146  settings/SharedFoldersScreen.kt:58
settings/SharedFolderDetailScreen.kt:67   settings/MinisSkillsBrowserScreen.kt:100
settings/AgentLoopAddSheets.kt:89         settings/AgentLoopAddSheets.kt:185
settings/AddModelsToGroupScreen.kt:84     sandbox/RootfsManagementScreen.kt:83
sandbox/FileBrowserScreen.kt:120          sandbox/FilePreviewScreen.kt:262
sandbox/MirrorSettingsScreen.kt:626       onboarding/OnboardingModelSelectionScreen.kt:163
settings/SettingsComponents.kt:110 / :119（封装本身）
```

**关键结论：**

- **`TopAppBarScrollBehavior` 全工程 0 处**（`rg 'TopAppBarScrollBehavior|enterAlwaysScrollBehavior|exitUntilCollapsedScrollBehavior'` = 0）。顶栏是死高度，滚动时不会收起。
- `topAppBarColors()` 只覆盖了 10 处（其余用默认）。`containerColor = ` 全工程 46 处。
- 玻璃顶栏的正确形态应是"滚动到顶部时透明、滚动后变玻璃"，**当前代码没有任何滚动监听可挂**——`ChatScreen` 里唯一相关的是 `listState` 的 `isNearBottom`，但它不暴露给顶栏。

### 2.2 底部输入区（chat composer）

调用链（全部在 `ChatScreen.kt` 的同一个 8,069 行函数体内）：

| 层 | 文件:行号 | 实现 |
|---|---|---|
| 输入区外框 | `chat/ChatScreen.kt:5756` | `Column` + `wrapContentWidth(Center)` + `widthIn(max = CHAT_MAX_CONTENT_WIDTH)`（`= 900.dp`，`:413`）+ `navigationBarsPadding()` + `padding(horizontal=12.dp)` + `padding(top=2.dp, bottom=8.dp)` |
| **输入卡本体** | `chat/ChatScreen.kt:6331`（Column）→ `:6349-6376`（`drawBehind`） | **自绘**：`Paint.setShadowLayer` 两遍（6dp 环境光 / 10dp 下投影），`drawRoundRect` 半径 **20dp**。填充色 = `shadowPaint.color = ChatColors.inputBg.toArgb()`（`:6214`）——**卡片的底色是由阴影画笔顺手画出来的**，不是一个 `Modifier.background` |
| 上下文压力内发光 | `chat/ChatScreen.kt:6378` `contextUsageGlow(...)` | 只用 paint pass，不改布局 |
| 顶部 Move-to 胶囊 | `chat/ChatScreen.kt:6404` 起 `Popup` + `Surface(CircleShape)` | 独立窗口浮层（`focusable=false`） |
| 附件行 | `chat/ChatScreen.kt` 约 `:6060` 区（`RoundedCornerShape(10.dp)` + `ChatColors.inputBg`） | 自绘 `Box.background` |
| **工具栏行**（+ / `/` / mic / send） | `chat/ChatScreen.kt:7323` `Row` | 普通 `Row`，`padding(horizontal=12.dp, vertical=10.dp)` |
| + 按钮 | `chat/ChatScreen.kt:7338` `InputCircleButton` | `Box` 38dp + `background(inputIconBg, CircleShape)` + `border(0.5dp, inputIconBorder, CircleShape)`（`chat/ChatComposerWidgets.kt:379-398`） |
| **附件菜单** | `chat/ChatScreen.kt:7347` `MinisMenu` | **`Popup` 窗口里的自定义菜单，不是 BottomSheet**。三项：拍照 / 选照片视频 / 选文件 |
| 麦克风按钮 | `chat/ChatComposerWidgets.kt:405` `MicButton` | 两态自绘 |
| 发送 / 停止按钮 | `chat/SendReturnModeMenu.kt:203` `ComposerSendButton` / `:512` `ComposerStopButton` | 自带长按菜单 |
| **语音面板** | `chat/ChatScreen.kt:6698` → `chat/voice/InlineVoiceInputPanel.kt`（1589 行） | 大量 `RoundedCornerShape(50)` 胶囊 + `Color(0xFFFF9500).copy(alpha=0.12f)` 等硬编码色 |
| 语音播报胶囊 | `chat/ChatScreen.kt:5540` → `chat/voice/SpeechPlayerCapsule.kt`（727 行） | 自绘波形 `Canvas` |
| 会话内搜索条 | `chat/ChatScreen.kt:8026` → `chat/search/ChatSearchBar.kt:102-105` | `Row` + `background(ChatColors.inputBg, RoundedCornerShape(20.dp))`，**覆盖在 composer 同一位置**（composer 被 `alpha(0f)` 隐藏但仍占位，见 `:6337-6351`） |
| 上滚 / 下滚浮动按钮 | `chat/ChatScreen.kt:5638`（上）、`:5680`（下） | `FilledIconButton` 36dp，`shadow(4.dp, CircleShape)`，`containerColor = ChatColors.inputBg`，`align(BottomEnd)` + `padding(end=12.dp+fabEndInset)` |
| 浮动工具状态条 | `chat/ChatScreen.kt:5495` → `chat/ChatComposerWidgets.kt:1034` `FloatingToolStatusBar` | 贴消息列表底部，`padding(horizontal=12.dp)` |
| 上滑发送提示 | `chat/ChatScreen.kt:8033` `SwipeToSendHint` | 跟手浮层 |

**改造要点：** composer 是本 App 视觉权重最高的外壳（用户 90% 时间盯着它），而它**完全没有走 M3 组件**：底色靠 native canvas 画、圆角硬编码 20dp、阴影是两遍 `setShadowLayer`。玻璃化需要**整块重写**为 `Modifier.graphicsLayer` + 玻璃层，且要保留 `contextUsageGlow` 这一层内发光（它是 `drawBehind` 追加的第三个 paint pass）。

### 2.3 悬浮按钮 / 搜索框 / 分组 tabs

| 元素 | 文件:行号 | 实现 |
|---|---|---|
| **会话列表双 FAB 容器** | `sessions/SessionListScreen.kt:1809` `DualFabRow` | 自绘 `Row`，`imePadding()` + `padding(horizontal=16.dp, vertical=20.dp)`，`SpaceBetween` |
| 新建聊天 FAB | `sessions/SessionListScreen.kt:1876` | `FloatingActionButton` 56dp `CircleShape`，`containerColor = minisFabColor()`（`theme/Theme.kt:143`，米色 `#B7AF96`），`shadow(6.dp)` + `combinedClickable`（长按弹分组菜单） |
| 搜索 FAB | `sessions/SessionListScreen.kt:1933` | `FloatingActionButton` 56dp，`containerColor = ChatColors.secondaryBg`，带 `AnimatedVisibility` 缩放进出 |
| **FAB 左右可拖动互换** | `sessions/SessionListScreen.kt:1857-1874`、`:1962-1980` | `pointerInput` + `detectHorizontalDragGestures`，阈值 100dp，持久化到 `PREF_FAB_SWAPPED` |
| 新建聊天长按分组菜单 | `sessions/SessionListScreen.kt:1944` | 裸 `DropdownMenu`（**不是 `MinisMenu`**） |
| **搜索框** | `sessions/SessionListScreen.kt:1984` | `OutlinedTextField`，`shape = RoundedCornerShape(percent = 50)`（胶囊），`heightIn(min = 48.dp)`，`focusedContainerColor/unfocusedContainerColor = surfaceContainerHigh`（`:2030-2036`） |
| 搜索高亮命中 | `sessions/SearchHighlight.kt`（65 行）、`sessions/SearchFolders.kt`（41 行） | — |
| **分组 tabs** | — | **本 App 没有 tab 条**。`rg 'TabRow\|ScrollableTabRow\|LazyRow' sessions/` = 0 |
| 替代方案：分组文件夹卡 | `sessions/SessionListScreen.kt:2738` `FolderCard` | 自绘：`groupGlyphPath`（`:2605`，`Canvas` 画 Path）、`SpinningRing`（`:3427`）、`FolderMemberTile`（`:2648`）、`folderSurface`（`:2499`） |
| 日期分区头 | `sessions/SessionListScreen.kt:2149` `SectionHeader` | 7 个日期桶（`:244` `datePeriod`） |
| 分组选择 Sheet | `sessions/GroupPickerSheet.kt:137` | `ModalBottomSheet` |
| 其他 FAB | `scheduled/ScheduledTasksScreen.kt:140`（`CircleShape`）、`chat/ChatModelPickerSheet.kt:1355`（`SmallFloatingActionButton`） | — |

> 结论：会话列表的"分组"是**卡片式文件夹**，不是 tabs。玻璃化时这批卡片（含自绘 Path 图标 + 会员头像九宫格）是最容易"糊成一团"的地方。

### 2.4 Sheet / ModalBottomSheet / Dialog 调用点

**总量（精确统计，已排除 `MinisAlertDialog(` 的干扰）：**

```
$ python3 统计
TOTALS: {'ModalBottomSheet': 29, 'AlertDialog': 83, 'MinisAlertDialog': 21,
         'Dialog': 11, 'MinisMenu': 24, 'DropdownMenu': 15, 'Popup': 10,
         'StandardChatSheet': 7}
```

| 类型 | 处数 | 说明 |
|---|---:|---|
| `ModalBottomSheet(...)` | **29** | 全部是 M3 原生 sheet，`containerColor` 各写各的 |
| 裸 `AlertDialog(...)` | **83** | 分布在 **44 个文件**，完全绕过 `MinisAlertDialog` |
| `MinisAlertDialog(...)` | **21** | 只有 7 个文件在用（`ChatScreen` 8、`SessionListScreen` 4、`StorageManagementScreen` 3、`ProviderDetailScreen` 3…） |
| `Dialog(...)`（自绘） | **11** | 其中 `MinisAlertDialog.kt:61` 是封装本身 |
| `MinisMenu(...)` | **24** | 自定义 `Popup` 菜单 |
| 裸 `DropdownMenu(...)` | **15** | 未统一 |
| 裸 `Popup(...)` | **10** | 位置计算全手写 |
| `StandardChatSheet(...)` | **7** | 聊天 ⋯ 菜单的统一样式 sheet 壳 |

> **玻璃化的最大一致性缺口：83 : 21。** 裸 `AlertDialog` 数量是统一封装（`MinisAlertDialog`）的 4 倍，且散在 44 个文件里。玻璃 Dialog 要么批量替换为 `MinisAlertDialog`，要么改 M3 主题的 `AlertDialogDefaults`（后者只能改颜色，改不了圆角/描边/blur）。

**Top 15 浮层调用点（按用户可见度排序）：**

| # | 文件:行号 | 宿主函数 | 类型 | 职责 |
|---:|---|---|---|---|
| 1 | `chat/ChatModelPickerSheet.kt:396` | `ModelPickerSheet`（:287，1512 行） | `ModalBottomSheet` | 会话页模型选择器（最高频浮层） |
| 2 | `components/UnifiedModelPickerSheet.kt:168` | `UnifiedModelPickerSheet`（:93，887 行） | `ModalBottomSheet` | 统一模型选择器（语音场景用，与 #1 重复） |
| 3 | `chat/ChatScreen.kt:8644` | `ThinkingLevelSheet`（:8631） | `ModalBottomSheet` | 思考等级选择 |
| 4 | `chat/ChatToolDetailUI.kt:299` | `ToolDetailSheet`（1681 行文件） | `ModalBottomSheet` | 工具调用详情 |
| 5 | `chat/ChatScreen.kt:8050` 附近 → `chat/MoveToSessionSheet.kt:159` | `MoveToSessionSheet` | `ModalBottomSheet` | 消息移动到其他会话 |
| 6 | `chat/ScheduledTaskDetailSheet.kt:158` | `ScheduledTaskDetailSheet`（695 行） | `ModalBottomSheet` | 定时任务详情 |
| 7 | `sessions/SessionListScreen.kt:3764` | `SessionEditSheet`（:3738） | `ModalBottomSheet` | 会话改名 / 分类（列表与聊天页共用） |
| 8 | `components/QuickTestSheet.kt:165` | `QuickTestSheet`（625 行） | `ModalBottomSheet` | 模型连通性快速测试 |
| 9 | `preview/WebPreviewBottomSheet.kt:158` | `WebPreviewBottomSheet`（503 行） | `ModalBottomSheet` | HTML 预览（内含 WebView） |
| 10 | `browser/BrowserSettingsSheet.kt:136` | `BrowserSettingsSheet`（737 行） | `ModalBottomSheet` | 浏览器设置 |
| 11 | `chat/RetryBadge.kt:128` | `RetryLogSheet` | `ModalBottomSheet` | 重试日志 |
| 12 | `quota/ProviderQuotaSection.kt:340` | `QuotaDetailSheet` | `ModalBottomSheet` | 配额详情 |
| 13 | `sessions/GroupPickerSheet.kt:137` | `GroupPickerSheet` | `ModalBottomSheet` | 分组选择 |
| 14 | `settings/MCPIntegrationsScreen.kt:236` / `:1112` | `MCPAddSheet` | `ModalBottomSheet` ×2 | MCP 添加 / 编辑 |
| 15 | `settings/SkillsManagementScreen.kt:350` / `:475` | `SkillImportSheet` | `ModalBottomSheet` ×2 | 技能导入 |

其余 `ModalBottomSheet`：`browser/BrowserDownloadsSheet.kt:71`、`browser/BrowserHistorySheet.kt:64`、`chat/SpeechLanguagePickerSheet.kt:67`、`chat/StandardChatSheet.kt:76`、`settings/EnvironmentVariablesScreen.kt:315`、`settings/MountedFoldersScreen.kt:622`、`settings/NetworkSettingsScreen.kt:703`（`AddProviderRoutesSheet`）、`settings/ProviderListScreen.kt:336`、`settings/SettingsScreen.kt:403`、`settings/SoulSettingsScreen.kt:610`、`settings/backup/RestoreBrowseScreen.kt:403`、`scheduled/ScheduledTaskEditScreen.kt:655`。

**共享 sheet 壳**：`chat/StandardChatSheet.kt:63`（`StandardChatSheet`）——90% 屏高、自定义 slim 拖拽把手（`:139` `StandardChatSheetHeader`）、`containerColor = ChatColors.background`。**这是玻璃 BottomSheet 最该改的一个点**：7 个调用方（`TokenUsageSheet` / `SessionSkillsSheet` / `SessionMemorySheet` / `SessionMcpsSheet` / `ChatMiscViews` / `BrowserSheet` 等）会一次性全部受益。

### 2.5 侧边抽屉 / 分栏

`navigation/ChatSplitScaffold.kt`（1012 行）是唯一的分栏骨架。

| 元素 | 文件:行号 | 实现 |
|---|---|---|
| 骨架入口 | `navigation/ChatSplitScaffold.kt:272` `ChatSplitScaffold` | 参数含 `twoPane` / `listPane` / `detailPane` / `onToggleSidebar` |
| 官方分栏容器 | `:543` `NavigableListDetailPaneScaffold`（M3 adaptive 1.2.0） | 不是自绘 Row，是 `AnimatedPane` + `PaneScaffoldDirective`（`:223` `chatPaneScaffoldDirective`） |
| 左栏宽度 | `:122` `LIST_PANE_MIN_WIDTH = 340.dp` / `:123` `LIST_PANE_MAX_WIDTH = 500.dp` | 可拖动并持久化（`KEY_LIST_PANE_WIDTH`） |
| 分栏缝 | `:612` `VerticalDivider(thickness = 1.dp, color = outlineVariant)` | 1dp 实线，**没有渐变/阴影** |
| 拖动把手 | `:704-746` | 覆盖在 seam 上的独立 `Box`（`:136` `DIVIDER_TOUCH_WIDTH = 24.dp`），`draggable` + `pointerHoverIcon` |
| 空态页 | `:762` `NoConversationSelected` | `Surface(fillMaxSize)`，右上角折叠按钮 `Menu` 图标对齐 64dp 基线（`:783`） |
| 折叠态 | `isTwoPane` 判定 + `listCollapsed` | 单栏时该槽位变返回箭头（`ChatScreen.kt:3315-3320`） |

**没有 `ModalNavigationDrawer`**（全工程 0 处）。所谓"侧边抽屉"实际是"可折叠的列表栏"。

> 玻璃化注意：`AnimatedPane` 会在切栏时对整栏做 `graphicsLayer` 变换。
> 在 `AnimatedPane` **内部**加 `blur` 会被离屏层（`GraphicsLayer`）反复重绘，平板上左右拖动 seam 时会明显掉帧。建议玻璃层放在 `AnimatedPane` **外面**或使用静态预渲染的玻璃底片。

---

## 3. 共享视觉组件清单（`ui/components/`）

`components/` 只有 **28 个文件 / 6,239 行**，却要服务 281 个文件——覆盖率非常低。下面是全量清单。

| 文件 | 行数 | 作用 | 关键 API |
|---|---:|---|---|
| `MinisButton.kt` | 203 | 全 App 按钮封装（最小高度 `MinisButtonHeight`） | `MinisButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)` :35<br>`MinisOutlinedButton(...)` :62<br>`MinisTextButton(...)` :89<br>`MinisSmallButton(...)` :119<br>`MinisSmallOutlinedButton(...)` :148<br>`MinisSmallTextButton(...)` :177 |
| `MinisAlertDialog.kt` | 130 | 统一确认弹窗（**内部仍是 `Dialog` + `Surface(RoundedCornerShape(16.dp), surface, tonalElevation=6.dp)`**） | `MinisAlertDialog(onDismissRequest, title, confirmText, onConfirm, text, dismissText, isDestructive, onDismiss, neutralText, onNeutral, neutralColor)` :34 |
| `MinisMenu.kt` | 306 | 统一 `Popup` 菜单（替代 `DropdownMenu`），iOS 26 风格 200ms scale-from-anchor | `MinisMenu(expanded, onDismissRequest, modifier, offset, scrollState, properties, shape=RoundedCornerShape(20.dp), containerColor, tonalElevation=3.dp, shadowElevation=6.dp, border, minWidth=180.dp, alignEnd, content)` :56<br>`MinisMenuDivider(modifier)` :272 |
| `SectionDesign.kt` | 220 | **设置类卡片的设计 token 对象**（唯一成体系的 spacing/shape token） | `object SectionDesign` :29（16 个尺寸常量 + `cardColor()` / `screenBackgroundColor()` / `dividerColor()` / `footerColor()`）<br>`SectionHeader(text, modifier)` :116<br>`SectionFooter(text, modifier)` :137<br>`SectionCard(modifier, content)` :161<br>`SectionDivider()` :181<br>`RowLabel(...)` :210 |
| `SettingsSection.kt` | 66 | 旧版设置分区（`Surface` 16dp 圆角 + primary 色标题） | `SettingsSection(title, modifier, content)` :30<br>`SettingsRowDivider(modifier)` :60 |
| `SettingsComponents.kt`（在 `settings/`，不在 `components/`） | 522 | **实际主力**：`SettingsScaffold` + 行/开关/值行 | `SettingsScaffold(title, onBack, actions, navigation, centerTitle, floatingActionButton, scrollable, bottomBar, content)` `settings/SettingsComponents.kt:68`<br>`SettingsSwitch(...)` :185、`SettingsSection(header, footer, modifier, content)` :212、`SettingsRow(...)` :283、`SettingsSwitchRow(...)` :395、`SettingsValueRow(...)` :426 |
| `SwipeRowActions.kt` | 187 | 行左滑露出操作按钮（iOS 风格） | `data class SwipeRowAction(label, icon, containerColor, contentColor, onClick)` :45<br>`SwipeRowActions(actions, modifier, actionWidth=88.dp, rowColor=SectionDesign.cardColor(), content)` :88 |
| `SectionTextField.kt` | 191 | 设置行内输入框（透明描边 + 可注入底色） | `SectionTextField(value, onValueChange, modifier, placeholder, isError, singleLine, readOnly, enabled, maxLines, keyboardOptions, keyboardActions, visualTransformation, trailingIcon, textStyle, fieldModifier, containerColor, contentHorizontalPadding)` :49 |
| `SectionDropdown.kt` | 139 | 设置行内下拉 | `fun <T> SectionDropdown(selected, items, onSelect, modifier, enabled, itemLabel, textStyle)` :47 |
| `UnifiedModelPickerSheet.kt` | 887 | 统一模型选择器（885 行，自带搜索/分组/供应商分区/语音目录） | `UnifiedModelPickerSheet(providerRepository, title, modalityFilter, boundGroup, boundGroupName, selectedId, onSelect, onDismiss, onEditGroups, onEditProvider)` :93 |
| `ModelEntryPicker.kt` | 605 | 模型条目列表的行渲染（供全屏选择器与 sheet 复用） | `LazyListScope.modelEntryPickerItems(...)` :115、`modalityBadges(model)` :438、`ModalityBadge(badge)` :465、`ModelActiveBadge()` :502、`providerDotColor(type)` :522、`rememberModelEntryPickerSections(...)` :545 |
| `ModelSearch.kt` | 189 | 模型搜索（防抖） | `rememberDebouncedQuery(raw, delayMs)` :181 |
| `QuickTestSheet.kt` | 625 | 模型连通性测试 sheet | `QuickTestSheet(...)` :146、`applicableKinds(entry)` :129 |
| `FullscreenImageViewer.kt` | 658 | 全屏图片查看（沉浸式、隐藏系统栏） | `FullscreenImageViewer(model: Any, onDismiss)` :92、`ImageActionButton(...)` :341、`copyBitmapToClipboard(...)` :409 |
| `ImageGalleryViewer.kt` | 401 | 多图画廊（Pager） | `ImageGalleryViewer(...)` :89 |
| `DocumentEditorField.kt` | 219 | 长文本编辑器（自管滚动） | `DocumentEditorField(value, onValueChange, modifier, textStyle, placeholder, contentPadding, outlined, ownScroll, minHeight, ...)` :110 |
| `DialogTextField.kt` | 99 | 弹窗内输入框 | `DialogTextField(value, onValueChange, ...)` :37 |
| `BadgedIconButton.kt` | 52 | 带角标图标按钮（角标画在裁剪外） | `BadgedIconButton(...)` :32 |
| `MenuItemIcon.kt` | 77 | 菜单项图标 | `MenuItemIcon(icon, tint)` :27 |
| `SpeedTierIcons.kt` | 78 | 速度档位图标 | — |
| `DecorativeAnimation.kt` | 233 | 装饰动画时钟（`Canvas` 转圈 / 彗星拖尾） | `Modifier.animationLayer()` :67、`rememberDecorativeTick()` :128、`decorativePhase(...)` :168、`decorativePingPong(phase)` :179、`DecorativeSpinner(color, modifier, strokeWidth, sweepFraction, periodMs)` :207 |
| `IosBounceOverscrollEffect.kt` | 140 | iOS 回弹 overscroll | `rememberIosBounceOverscrollEffect()` :137 |
| `FitOrWrapLine.kt` | 134 | 一行放得下就一行、否则换行（标签组） | `FitOrWrapLine(modifier, spacing, lineSpacing, separator, lead, tags)` :38 |
| `SheetScrollGuard.kt` | 53 | 阻止列表惯性把 sheet 拖下去 | `rememberSheetScrollGuard(): NestedScrollConnection` :38 |
| `UrlPreviewSheet.kt` | 31 | 链接预览（`InAppBrowserLauncher` 的弹层） | `UrlPreviewSheet(...)` :19 |
| `InAppBrowserLauncher.kt` | 83 | 应用内浏览器启动器 | `openExternalUrl(context, url)` :35、`InAppBrowserHost(context, content)` :54、`LocalInAppBrowserLauncher` :25 |
| `HardwareKeyboard.kt` | 120 | 硬件键盘探测（决定是否自动聚焦输入框） | `rememberHasHardwareKeyboard()` :101 |
| `ReopenOnResume.kt` | 55 | 回前台重开某页 | `rememberReopenOnResume(onReopen)` :28 |
| `SafePickerLaunch.kt` | 58 | 系统选择器防崩启动 | `launchPickerSafely(...)` :45 |

### 3.1 重点组件逐个说明（改造影响面）

**① `MinisButton`（`components/MinisButton.kt:35`）**
6 个变体全部**直接转发给 M3 的 `Button` / `OutlinedButton` / `TextButton`**，只加了 `heightIn(min = MinisButtonHeight)`（定义在 `components/MinisButton.kt:23`，48dp）。也就是说它现在**没有任何绘制逻辑**：改 `shape`/`colors` 只能改颜色与圆角，改不了材质。
调用量：`MinisButton(` 37 处、`MinisTextButton(` **209 处**、`MinisOutlinedButton(` 少量。
→ 但也正因为它只是薄封装：**把这 6 个函数体换成自绘玻璃容器（保留原参数表），就一次覆盖 ~250 个调用点**。这是全工程 ROI 最高的改动点。

**② `MinisAlertDialog`（`components/MinisAlertDialog.kt:34`）**
内部是 `Dialog { Surface(RoundedCornerShape(16.dp), color = surface, tonalElevation = 6.dp) { ... } }`（`:61-70`）。标题 24dp padding、按钮行/竖排二选一（有 `neutralText` 时改竖排，`:43-51` 注释说明）。
调用量 **21 处**，而裸 `AlertDialog` **83 处** → 覆盖率仅 20%。

**③ `MinisMenu`（`components/MinisMenu.kt:56`）**
不是 M3 `DropdownMenu`，是**自建 `Popup` + `AnimatedVisibility`**（`:100` 之后的注释说明：为了统一 200ms 缩放动画）。参数里硬编码了深浅色两套 `containerColor`/`border`（`:70-95`）：
- 深色：`surfaceContainerHigh` + `BorderStroke(0.5.dp, outlineVariant)`
- 浅色：`Color.White` + `BorderStroke(0.5.dp, Black alpha 0.05)`
`shadowElevation` 被刻意限制在 6dp（注释解释 `AndroidPopup` 的 `maxSupportedElevation = 8.dp` 会裁掉更大的阴影，`:78-95`）。
→ **Popup 是独立系统窗口，无法采样它背后的像素**。玻璃菜单只能做"半透明 + 内描边 + 高光"假玻璃，或者改成同窗口内的 `Box` 浮层（要改 24 处调用 + 位置计算）。

**④ `SectionDesign`（`components/SectionDesign.kt:29`）**
**全工程唯一成体系的尺寸 token**，但只覆盖设置类页面：
```
ScreenHorizontalPadding = 16.dp     CardShape = RoundedCornerShape(12.dp)
FirstSectionTopGap = 16.dp          SectionTopGap = 24.dp
HeaderToCardGap = 8.dp              CardToFooterGap = 8.dp
InterRowGap = 8.dp                  RowMinHeight = 44.dp
TextFieldMinHeight = 56.dp          RowHorizontalPadding = 16.dp
RowVerticalPadding = 10.dp          CardInnerVerticalPadding = 6.dp
DividerThickness = 0.5.dp           DividerStartInset = 16.dp
```
语义色：`cardColor() = surfaceContainerLow`、`screenBackgroundColor() = background`、`dividerColor() = outlineVariant.copy(alpha=0.4f)`、`footerColor() = onSurfaceVariant`。
调用量：`SectionCard(` 5、`SectionHeader(` 14、`SectionTextField(` 23。**覆盖率同样很低**，大量设置页仍在手写 `Column + clip + background`。

**⑤ `SettingsSection` —— 三份同名实现（严重分裂）**

| 实现 | 文件:行号 | 签名 | 圆角 / 底色 | 标题色 |
|---|---|---|---|---|
| A | `components/SettingsSection.kt:30` | `(title, modifier, content)` | `Surface(16.dp)` / `colorScheme.surface` | `colorScheme.primary`（蓝） |
| B | `settings/SettingsScreen.kt:564`（private） | `(title, footer, content)` | `clip(12.dp)` + `background(surfaceContainerLow)` | `onSurfaceVariant` |
| C | `settings/SettingsComponents.kt:212` | `(header, footer, modifier, content)` | `clip(14.dp)` + `background(surfaceContainerLow)` | `onSurfaceVariant` |

同一视觉语义，**三种圆角（16 / 12 / 14dp）、两种容器（`Surface` / `Box.background`）**。玻璃化前必须先合并，否则玻璃卡片会以三种圆角出现在相邻页面。

**⑥ `SwipeRowActions`（`components/SwipeRowActions.kt:88`）**
自绘滑动：`rowColor` 参数注释明确要求"必须与所在 surface 完全一致，否则静止时后面的按钮会透出来"（`:92-94`）。3 处调用：`ProviderListScreen.kt:265`、`ModelGroupsScreen.kt:258`、`backup/RestoreBrowseScreen.kt:150`。
→ **玻璃化的直接冲突点**：一旦行的底色半透明，滑出的红色删除按钮会在静止时透视出来。必须改成"玻璃行 + 不透明动作层"的分层方案。

**⑦ `SectionTextField`（`components/SectionTextField.kt:49`）**
`OutlinedTextField` + **全部边框色设为 `Transparent`**（`:96-103`），底色也 `Transparent`，靠外层卡片提供背景；`containerColor` 参数供 sheet 场景覆盖（注释 `:65-73` 解释了为什么 sheet 里默认色会"看不见"）。23 处调用。
→ 玻璃输入框需要保留"无边框"哲学，只换外层填充。

**⑧ `UnifiedModelPickerSheet`（`components/UnifiedModelPickerSheet.kt:93`）**
887 行，内部 `ModalBottomSheet`（:168）+ 自建搜索框 + 供应商分区折叠 + 语音目录。**与 `chat/ChatModelPickerSheet.kt:287 ModelPickerSheet`（1512 行）功能重复**：聊天页用后者（`ChatScreen.kt:8337`、`ScheduledTaskEditScreen.kt:717`），语音输入用前者（`chat/voice/VoiceInputPickerSheet.kt:34`、`:63`）。两个 picker 的 `ModalBottomSheet` 参数与头部样式各不相同（一个自定义 slim 把手 `:171`，一个用 `StandardChatSheet` 口径）。

### 3.2 组件层结论

- `components/` 只承担了"设置类 + 通用按钮 + 图片查看"三块，**聊天页的外壳（顶栏/输入卡/消息气泡/tool 卡）完全不在共享层**，全部内联在 `ChatScreen.kt` 的 8,069 行里。
- 有 3 套分区卡、2 套模型选择器、2 套 prompt 输入框（`SectionTextField` / `DialogTextField`）、2 套菜单（`MinisMenu` / 裸 `DropdownMenu`）。
- **玻璃 token 的落地点建议就是 `SectionDesign` 旁边新建 `GlassDesign`（同目录同风格），并在 `MinisButton` / `MinisAlertDialog` / `MinisMenu` / `SettingsScaffold` / `StandardChatSheet` 这 5 个文件里实现；其余靠替换调用点。**

---

## 4. 主题落地方式

### 4.1 文件与入口

`theme/` 只有 2 个文件 393 行：

- `theme/Theme.kt`（231 行）
- `theme/ChatColors.kt`（162 行）

挂载点：`MainActivity.kt:566` `MinisTheme(darkTheme, fontScale) { … AppNavigation(...) }`，`enableEdgeToEdge` 在 `MainActivity.kt:563`。

### 4.2 `Theme.kt` 暴露了什么

```
$ rg -n 'val Local|CompositionLocal|fun MinisTheme|shapes|typography' theme/Theme.kt
142:@Composable
147:private val MinisShapes = Shapes(
156:fun MinisTheme(
167:        shapes = MinisShapes,
168:        typography = typography,
170:        CompositionLocalProvider(LocalChatPalette provides chatPalette, content = content)
181:fun MinisAppearance(darkTheme: Boolean, content: @Composable () -> Unit)
```

| 暴露物 | 位置 | 内容 |
|---|---|---|
| `MinisTheme(darkTheme, fontScale, content)` | `Theme.kt:156` | 唯一主题入口。**没有 spacing 参数、没有 glass 参数、没有 `LocalGlassTokens`** |
| `MinisAppearance(darkTheme, content)` | `Theme.kt:181` | 子树强制反色（导出/分享用） |
| `MinisShapes` | `Theme.kt:147`（private） | `extraSmall=12 / small=12 / medium=20 / large=24 / extraLarge=28 dp`。**没有暴露成 public token，外部拿不到** |
| `scaledTypography(fontScale)` | `Theme.kt:212`（private） | 只是把 M3 默认 `Typography()` 每一项 `fontSize * factor` + `.bidi()`（`Theme.kt:209`，每条目 `textDirection = Content, textAlign = Left`） |
| `minisFabColor()` | `Theme.kt:143` | 读 `LocalChatPalette.current.fabAccent` |
| **`LocalChatPalette`** | `ChatColors.kt:156` | **全工程唯一一个"品牌级"自定义 CompositionLocal**（`compositionLocalOf { LightChatPalette }`） |

配色结构：
- `LightColorScheme` / `DarkColorScheme`（`Theme.kt:87` / `:115`）：`primary = #528AD2`，`background/surface = #F2F2F7`（浅）/ `#000000`（深），`surfaceContainerLow = #FFFFFF` / `#1C1C1E`。
- 语义 token 数量：`surfaceContainerLowest/Low/(plain)/High/Highest` 全部被覆写成"卡片色 / 页面色"两档，**实际只有 2 级层次**（`Theme.kt:106-110`）——这对玻璃分层是不够的。

### 4.3 `ChatColors.kt` 提供什么

`ChatPalette` 是一个 **34 字段的 `@Immutable data class`**（`ChatColors.kt:24-60`），镜像 iOS `ChatColors`：

```
isDark, background, secondaryBg, inputBg, inputIconBg, inputIconBorder, inputBorder,
primaryText, secondaryText, tertiaryText, disabledText, userBubble, toolBg, toolBorder,
toolCapsuleBg, separator, sendButton, sendButtonDisabled, codeBlockBg, codeBlockText,
inlineCodeBg, inlineCodeText, link, blockquoteBar, thinking, warningBg, warningText,
tableBorder, inputShadow, toastBg, thumbnailBorder, sheetHeaderBg, sheetHeaderBorder, fabAccent
```

两套实例：`LightChatPalette`（`ChatColors.kt:61`）、`DarkChatPalette`（`ChatColors.kt:107`）。访问器 `val ChatColors: ChatPalette @Composable @ReadOnlyComposable get() = LocalChatPalette.current`（`:159-162`）。

**两套配色系统并存，且值不一致：**

| 语义 | `MaterialTheme.colorScheme.*` | `ChatColors.*` |
|---|---|---|
| 页面底色（浅） | `background = #F2F2F7` | `background = #FFFFFF` |
| 卡片（浅） | `surfaceContainerLow = #FFFFFF` | `secondaryBg = #F2F2F7` |
| 输入卡（浅） | — | `inputBg = #FFFFFF`（**与页面同色，全靠阴影分离**） |
| 页面底色（深） | `#000000` | `#000000` |
| 卡片（深） | `#1C1C1E` | `toolBg = #3A3A3F` |

引用量：`MaterialTheme.colorScheme.*` **1,220 次**，`ChatColors.*` **353 次**。
→ **玻璃化必须两套一起改**，只改一套会出现"设置页玻璃、聊天页不玻璃"的割裂。

### 4.4 其余 CompositionLocal（20 个，全部零散定义）

```
theme/ChatColors.kt:156            LocalChatPalette
chat/ChatMiscViews.kt:968          LocalBrowserTabPool
chat/ChatMiscViews.kt:969          LocalToolPreviewEnabled
chat/ToggleAnchor.kt:162           LocalChatToggleAnchor
chat/StreamingMarkdownText.kt:131  LocalShardId
chat/StreamingMarkdownText.kt:160  LocalShardSubIndexAllocator
chat/StreamingMarkdownText.kt:224  LocalMarkdownFontScale
chat/StreamingMarkdownText.kt:227  LocalMarkdownUrlClickHandler
chat/StreamingMarkdownText.kt:244  LocalMarkdownBlankTapHandler
chat/StreamingMarkdownText.kt:271  LocalMarkdownSessionId
chat/search/ChatSearchHighlights.kt:21  LocalChatSearchHighlight
chat/search/ChatSearchHighlights.kt:24  LocalChatSearchRow
chat/StreamingFade.kt:55            LocalAppendOnlyFade
chat/StreamingFade.kt:150           LocalLiveIncremental
chat/UsageTapBand.kt:82            LocalUsageTapBandRow
chat/UsageTapBand.kt:85            LocalUsageTapBandRegistry
chat/MessageBoundsRegistry.kt:64   LocalMessageBoundsRegistry
chat/MinisTextKitSelection.kt:1117 LocalMinisSelectionController
components/InAppBrowserLauncher.kt:25  LocalInAppBrowserLauncher
```
全部是**功能态**（渲染分片、搜索高亮、选区），**没有一个是视觉 token**。

### 4.5 是否有统一的 spacing / shape / typography token？

**没有。**

```
$ rg -n 'object (Spacing|Dimens|Tokens|MinisDimens|Sizes|Metrics)|val Spacing|LocalSpacing' --glob '*.kt'
（无输出）
```

- **spacing**：只有 `SectionDesign`（`components/SectionDesign.kt:29`）里 16 个常量，且只服务设置类页面。全工程 `.dp` 直接写在 modifier 里。
- **shape**：`MinisShapes` 是 `private`（`Theme.kt:147`），外部只能用 `MaterialTheme.shapes.*`；而实际代码里 **289 处 `RoundedCornerShape(...)`** 直接写死（见 §5）。
- **typography**：有 `Typography()` 基础表，但代码里 **349 处 `fontSize = X.sp`** 覆盖（详见 §5.4）。

> **玻璃改造的前置动作**：新建一个 `GlassTokens`（`components/` 或 `theme/`），把 `blurRadius / tintAlpha / highlightAlpha / strokeWidth / cornerRadius / shadowRadius` 定义成唯一来源，并用 CompositionLocal 下发；否则玻璃参数会像今天的圆角一样散成 289 份。

---

## 5. 硬编码样式普查（含原始 rg 输出）

> 工作目录：`.../com/openminis/app/ui/`，ripgrep 14.1.1，`--glob '*.kt'`。

### 5.1 硬编码颜色 `Color(0x`

```
$ rg -o 'Color\(0x' --glob '*.kt' | wc -l
523

$ rg -c 'Color\(0x' --glob '*.kt' | sort -t: -k2 -rn | head -15
theme/ChatColors.kt:62
theme/Theme.kt:42
settings/backup/BackupAndRestoreScreen.kt:24
settings/SettingsScreen.kt:24
chat/ChatToolDetailUI.kt:22
sessions/SessionListScreen.kt:20
chat/ChatModelPickerSheet.kt:20
terminal/AnsiParser.kt:17
chat/MoveToSessionSheet.kt:17
components/ModelEntryPicker.kt:13
chat/HelperUi.kt:13
settings/AddProviderScreen.kt:10
settings/NetworkSettingsScreen.kt:9
sandbox/FileBrowserScreen.kt:9
chat/ChatToolFormatting.kt:9
```

补充口径：

```
$ rg -l 'Color\(0x' --glob '*.kt' | wc -l
78          ← 78 个文件里出现
$ rg -o 'Color\(0x' --glob '*.kt' --glob '!theme/*' | wc -l
419         ← 其中 419 处在 theme/ 之外（占 80%）
```

| 指标 | 数值 |
|---|---:|
| 出现次数 | **523** |
| 涉及文件 | **78 / 281** |
| `theme/` 内（合理，是调色板） | 104（19.9%） |
| `theme/` 外（需治理） | **419（80.1%）** |
| Top 15 之外的文件合计 | 523 − 311 = 212 |

**判读**：Top 15 里除了 `theme/` 两个文件（104 处，属正常定义）之外，前 5 名都是**具体页面**：`BackupAndRestoreScreen`(24)、`SettingsScreen`(24)、`ChatToolDetailUI`(22)、`SessionListScreen`(20)、`ChatModelPickerSheet`(20)。这 5 个页面合计 110 处硬编码色，是玻璃化（需要统一 tint / 高光 / 描边 alpha）时最先爆炸的地方。

### 5.2 圆角 `RoundedCornerShape(`

```
$ rg -o 'RoundedCornerShape\(' --glob '*.kt' | wc -l
289

$ rg -o 'RoundedCornerShape\([0-9]+\.?[0-9]*\.dp\)' --glob '*.kt' \
    | sed 's/.*RoundedCornerShape(//; s/\.dp)//' | sort -n | uniq -c | sort -rn | head -8
     46 12
     43 8
     42 10
     18 6
     17 14
     10 2
     10 16
      6 0

$ rg -o 'RoundedCornerShape\([A-Za-z%][^)]*\)' --glob '*.kt' \
    | sed 's/.*RoundedCornerShape(//' | sort | uniq -c | sort -rn | head -10
      5 bottomStart = 14.dp, bottomEnd = 14.dp)
      4 topStart = 12.dp, topEnd = 12.dp)
      4 bottomStart = 12.dp, bottomEnd = 12.dp)
      1 topStart = 16.dp, topEnd = 16.dp)
      1 topStart = 14.dp, topEnd = 14.dp)
      1 sizeDp * SoulIcon.CORNER_RADIUS_FRACTION)
      1 percent = 50)
      1 bottomStart = 8.dp, bottomEnd = 8.dp)
      1 bottomStart = 16.dp, bottomEnd = 16.dp)
      1 TaskCardMetrics.corner)
```

| 圆角值 | 次数 | 典型用途 |
|---:|---:|---|
| 12dp | **46** | 卡片 / 设置行 / 输入框（最常见） |
| 8dp | **43** | 小胶囊 / chip |
| 10dp | **42** | 消息气泡 / 斜杠菜单（`ChatScreen.kt:5878`、`:6069`） |
| 6dp | 18 | 小 chip |
| 14dp | 17 | 设置卡（`SettingsComponents.kt:255`） |
| 2dp | 10 | 进度条 |
| 16dp | 10 | 旧设置卡（`components/SettingsSection.kt:43`）、弹窗 |
| 0dp | 6 | 平角 |

**结论：289 处圆角，实测落在 18 个取值上（0 / 1.5 / 2 / 3 / 4 / 5 / 5.5 / 6 / 8 / 10 / 12 / 14 / 16 / 18 / 20 / 24 / 28 / 32 dp），主要档位是 8/10/12/14；但没有任何 token 约束，且同一个语义有多个值**（设置卡同时存在 12/14/16 三种，见 §3.1⑤）。玻璃的"边缘高光"必须跟圆角严格一致，圆角不统一等于高光画歪。

### 5.3 `.background(Color...)` 与 `Surface(color = ...)`

```
$ rg -o '\.background\(' --glob '*.kt' | wc -l
339

$ rg -o '\.background\(Color' --glob '*.kt' | wc -l
33
$ rg -c '\.background\(Color' --glob '*.kt' | sort -t: -k2 -rn | head -10
chat/ChatToolDetailUI.kt:5
settings/backup/BackupAndRestoreScreen.kt:3
media/InlineMediaPlayer.kt:3
chat/voice/InlineVoiceInputPanel.kt:3
chat/ChatAssistantMessageUI.kt:3
browser/BrowserSheet.kt:3
chat/ChatUserMessageUI.kt:2
chat/ChatComposerWidgets.kt:2
settings/ProviderDetailScreen.kt:1
settings/MountDetailScreen.kt:1

$ rg -o '\.background\(MaterialTheme' --glob '*.kt' | wc -l
69
$ rg -o '\.background\(ChatColors' --glob '*.kt' | wc -l
33
```

`Surface(` 调用点（用 lookbehind 排除 `ComposerSurface(` 之类）：

```
$ (python) Surface( 调用点: 41   其中显式传 color= 的: 33
按文件：settings/MountedFoldersScreen.kt:4  chat/MinisTextKitGesture.kt:3
        settings/MountDetailScreen.kt:3    settings/SharedFolderDetailScreen.kt:3
        settings/SharedFoldersScreen.kt:3  components/SectionDesign.kt:2
        media/InlineMediaPlayer.kt:2       （其余各 1）
```

| 着色方式 | 次数 | 占比 |
|---|---:|---:|
| `Modifier.background(...)` 总计 | **339** | 100% |
| ↳ `background(MaterialTheme...)` | 69 | 20.4% |
| ↳ `background(ChatColors...)` | 33 | 9.7% |
| ↳ `background(Color...)`（字面量） | 33 | 9.7% |
| ↳ 其余（`Brush` / 变量 / `copy()` 链） | 204 | 60.2% |
| `Surface(color = ...)` 显式着色 | 33 / 41 | 80.5% |

**判读**：**60% 的背景着色连字面量都不是**（`Brush.verticalGradient(...)`、`someColor.copy(alpha=…)`、局部 `val`）。这意味着 `rg` 能定位的只有 4 成；玻璃化时必须用**编译期拦截**（把 `Modifier.background` 包一层 `Modifier.glassBackground(...)`）而不是靠文本替换。

### 5.4 直接写的 `.dp`（padding 场景）

```
$ rg -o 'padding\([^)]*[0-9]+\.dp[^)]*\)' --glob '*.kt' \
    | rg -o '[0-9]+(\.0)?\.dp' | sort | uniq -c | sort -rn | head -8
    265 16.dp
    203 8.dp
    183 12.dp
    149 4.dp
    108 14.dp
     88 6.dp
     79 10.dp
     57 32.dp
```

| 值 | 次数 | 备注 |
|---:|---:|---|
| 16dp | 265 | 屏幕边距（= `SectionDesign.ScreenHorizontalPadding`，但绝大多数没引用它） |
| 8dp | 203 | 元素间距 |
| 12dp | 183 | 卡片内边距 / composer 边距 |
| 4dp | 149 | 微距 |
| 14dp | 108 | 行内间距 |
| 6dp | 88 | — |
| 10dp | 79 | — |
| 32dp | 57 | 页脚 / 大间距 |

> 注：`14.dp` 出现 108 次却**不在 4dp 栅格上**（4/8/12/16/24/32 才是栅格）。这是历史遗留（iOS 的 14pt 圆角/间距被照抄）。

### 5.5 排版与透明度（补充口径，影响玻璃对比度）

```
$ rg -o 'fontSize = [0-9]+(\.[0-9]+)?\.sp' --glob '*.kt' | wc -l
349
$ 分布
     72 12.sp
     70 11.sp
     69 13.sp
     29 14.sp
     20 10.sp
     18 16.sp
     16 15.sp
     15 9.sp

$ rg -o 'Color\.White'     → 95
$ rg -o 'Color\.Black'     → 47
$ rg -o '\.copy\(alpha = [0-9.]+f' → 363
$ alpha 取值 top6: 0.5f×68 / 0.6f×31 / 0.3f×29 / 0.15f×29 / 0.4f×24 / 0.12f×20
$ rg -o '\.shadow\('       → 15
$ rg -o '\.border\('       → 65
$ rg -o 'HorizontalDivider\(' → 75
```

**玻璃化的关键风险数**：363 处 `copy(alpha=...)` + 95 处 `Color.White`。玻璃层本身是半透明白/黑，叠在这些已有的 alpha 着色上会**指数级降低对比度**（白玻璃 + 22% 白卡片 + 12% 白高光 ≈ 文字不可读）。改造时必须先做一次"alpha 用途分类"（描边 / 底片 / 禁用态 / 阴影），不能一律保留。

---

## 6. 改造难点预警

按"会不会直接做不出来"排序。

### 6.1 ⛔ minSdk 26 与 blur API 31 的硬冲突

- `build.gradle.kts:37` `minSdk = 26`；`Modifier.blur` / `RenderEffect.createBlurEffect` 需 API 31（Android 12）。
- 工程里 **0 处** blur 实现，且有两处注释明确记录"因为 minSdk 26 所以不能用"：
  - `chat/ChatComposerWidgets.kt:616` — "Hand-drawn because Modifier.blur needs API…"
  - `chat/ContextUsageHintUi.kt:223` — "minSdk 26 — `Modifier.blur()` needs API 31, so it is not an option"
- **影响**：`minSdk 26 ~ 30`（Android 8~11，存量设备占比不低）必须走降级路径：半透明色 + 顶部/底部内阴影 + 1px 内描边 + 极细噪点图，**不能有真模糊**。两套实现意味着两套视觉验收标准。

### 6.2 ⛔ 背景是纯色，模糊无信息量

- `theme/Theme.kt:77-85`：`background = NeutralGroupedBg (#F2F2F7)` / `NeutralDarkGroupedBg (#000000)`。
- `navigation/AppNavigation.kt:565`：NavHost 整体铺 `Modifier.background(MaterialTheme.colorScheme.background)`。
- 各屏 `Scaffold(containerColor = ...)` 再铺一层同色。所有"背后"都是同一个纯色。
- **影响**：玻璃采样到的是 `#F2F2F7`，blur 之后还是 `#F2F2F7`。**必须先有背景层**（渐变 / 网格 / 噪点 / 层叠色块），否则整个玻璃改造肉眼不可见。

### 6.3 ⚠️ WebView 之上的浮层（z-order / 合成）

6 处 `AndroidView { WebView }`：

```
browser/BrowserWebView.kt:36                （BrowserSheet 内）
preview/WebPreviewFullscreenScreen.kt:194
preview/WebPreviewBottomSheet.kt:252        ← WebView 在 ModalBottomSheet 里
sandbox/FilePreviewScreen.kt:569 / :1367
settings/MinisSkillsBrowserScreen.kt:140
chat/KatexWebViewPool.kt:167                （离屏渲染成 Bitmap，无 z-order 问题）
```

- WebView 有独立的合成层（`WebView` 内部走 `AwContents` 的 `SurfaceTexture`/`TextureView` 路径），Compose 的 `graphicsLayer`+blur **不会**作用到 WebView 内容上，只会模糊 WebView 这张"图"的外框，看起来像"玻璃窗后面贴了一张清晰的网页"。
- `preview/WebPreviewBottomSheet.kt:158` 是"玻璃 sheet 里嵌 WebView"的直接冲突场景；再加一层玻璃顶栏就变成三层叠加。
- 另外，`ModalBottomSheet` 在 WebView 上滑出时，部分设备会出现 1 帧黑闪（Compose Dialog/Popup 与 WebView 的合成顺序问题）。

### 6.4 ⚠️ 终端是原生 View，不受 Compose blur 影响

- `terminal/canvas/TerminalNativeView.kt:58` `AndroidView(...)` 包 `TerminalCanvasView`（312 行，自绘 Canvas 终端）
- `terminal/canvas/TerminalInputView.kt:54` 第二个 `AndroidView`
- `terminal/TerminalScreen.kt:87` `TerminalScreen`
- **影响**：终端页只能做"玻璃边框 + 玻璃顶栏"，中间那块永远是黑底 text 视图。玻璃外壳与黑底的接缝需要专门处理（内阴影 / 发光边框）。

### 6.5 ⚠️ 大列表逐项 blur 必然卡

```
$ rg -o 'LazyColumn\(' --glob '*.kt' | wc -l          → 41
$ rg -o 'items\(|itemsIndexed\(' --glob '*.kt' | wc -l → 49
```

- `chat/ChatScreen.kt` 的消息列表是**单个巨型 LazyColumn**，每项由 `StreamingMarkdownText.kt`（5,033 行）分片渲染（`LocalShardId` / `LocalShardSubIndexAllocator`）。
- `sessions/SessionListScreen.kt`（4,019 行）也是 LazyColumn，行内含 `FolderCard`（自绘 `Path`、`SpinningRing`、九宫格头像）。
- **影响**：每个 list item 加一层 `RenderEffect` 等于每帧 N 次离屏纹理采样。手机上 60fps 大约只能扛住 3~5 层全屏 blur；逐项玻璃必须改成"**整块背板一次模糊 + 各项仅做半透明填充**"。

### 6.6 ⚠️ 自绘 Canvas 丛生

`Canvas(` 出现在 15 个文件 / 18 处，其中几个是"每帧重绘"：

- `components/DecorativeAnimation.kt:128 rememberDecorativeTick()`（按 vsync 走）、`:207 DecorativeSpinner`
- `sessions/SessionListScreen.kt:2605 groupGlyphPath` / `:3427 SpinningRing` / `:2648 FolderMemberTile`
- `chat/voice/SpeechPlayerCapsule.kt`（音频波形）
- `chat/ScheduledTaskPill.kt`、`chat/ToolGroupRow.kt`
- `settings/UsageStatsScreen.kt`（图表）

**影响**：这些画笔一旦被包进 `graphicsLayer` 做模糊，每帧都要重新栅格化整层。装饰动画（转圈）尤其危险——它会让整块玻璃每帧失效。

### 6.7 ⚠️ 自绘文本选择系统（1,314 + 1,730 + 533 行）

- `chat/MinisTextKitSelection.kt:1117 LocalMinisSelectionController`、`:1155 drawSelectionForShard`
- `chat/MinisTextKitGesture.kt`（1,730 行）
- `chat/MinisMarkdownTextToolbar.kt`（533 行）
- 选择气泡是 `Popup`（`chat/MinisTextKitGesture.kt` 内 4 处 `Popup(`），独立窗口 → 采样不到背后文字。
- **影响**：iOS 26 的玻璃选择工具栏在本工程里只能做"半透明深色胶囊"，不能真模糊。

### 6.8 ⚠️ 全屏预览 / 图片查看器直接操作 Window

- `components/FullscreenImageViewer.kt:92`（`:637 findWindowViaDialog`、`:100-112` 隐藏系统栏并改 `decorFitsSystemWindows`）
- `components/ImageGalleryViewer.kt:89`（`:391 findDialogWindowForGallery`）
- `preview/WebPreviewFullscreenScreen.kt:70`
- `sandbox/FilePreviewScreen.kt:101`（由 `dialog()` 路由进入，`usePlatformDefaultWidth=false` + `decorFitsSystemWindows=false`，`AppNavigation.kt:1278-1285`）
- **影响**：这些页面自己改 Window 属性。玻璃层依赖 `WindowInsets`，而这里 `decorFitsSystemWindows=false` 已经绕开了 Scaffold 的 inset 体系，需要单独适配。

### 6.9 ⚠️ 83 处裸 AlertDialog 绕过统一封装

- 裸 `AlertDialog(` **83 处 / 44 个文件**，而 `MinisAlertDialog` 只有 21 处。
- 分布最密（括号内为处数）：`settings/NetworkSettingsScreen.kt`（7）、`scheduled/ScheduledTaskEditScreen.kt`（5）、`settings/backup/BackupHistoryDetailScreen.kt`（4）、`settings/StorageManagementScreen.kt`（4）、`settings/SoulSettingsScreen.kt`（4）。
- **影响**：即使把 `MinisAlertDialog` 改成玻璃，也还有 80% 的弹窗是"不透明 M3 白卡"。而且 M3 `AlertDialog` 的颜色只能通过 `AlertDialogDefaults`/主题改，**改不了模糊与描边**。

### 6.10 ⚠️ 平板双栏的 `AnimatedPane` 与拖拽

- `navigation/ChatSplitScaffold.kt:546` 与 `:589` 两个 `AnimatedPane`：切栏时对整栏做 `graphicsLayer` 动画。
- `:704-746` 拖 seam 时每帧更新 `listPaneWidth`（`:729`），整栏重排 + 重绘。
- **影响**：玻璃层如果放在 pane 内部，拖 seam 的每一帧都要重新模糊整个 340~500dp 宽的栏 → 明显掉帧。玻璃层必须放在 pane 外部（或改用预渲染的静态底片）。

### 6.11 ⚠️ `ChatScreen.kt` 单函数 8,069 行

- `@Composable fun ChatScreen(...)` 定义在 `chat/ChatScreen.kt:515`，函数体到 `:8584`。
- 里面同时住着：顶栏（`:2981`）、消息列表、composer（`:6331`）、9 个弹层、3 个 `Popup`、4 个 `MinisMenu`、8 个 `MinisAlertDialog`、1 个 `ModalBottomSheet`。
- **影响**：任何外壳改动都在这一个函数里做，无法并行、无法局部回归；编译一次就要等整文件。

### 6.12 ⚠️ 全局转场与 inset 是手算的

- 转场：`AppNavigation.kt:563-616`（共享轴，300/200ms，四条 easing 曲线硬编码）。
- inset：64 处 `statusBarsPadding|navigationBarsPadding|WindowInsets.*`；`ChatScreen.kt:2975` 用 `contentWindowInsets = WindowInsets(0)` 后手工加 `WindowInsets.statusBars`（`:3740`）。
- **影响**：玻璃栏若改用 `Scaffold` 默认 inset 策略，会整体偏移几个 dp，直接破坏已验证过的"68dp / 64dp 基线对齐"（`ChatSplitScaffold.kt:783` 的注释专门记录了这类 2dp 偏移的历史 bug）。

---

## 7. 优先级建议

排序原则：**用户第一眼看到 → 影响观感面积最大 → 改动风险最小**。
每条都给出可直接开工的落点。

### P0 —— 第一批（占观感 80%，改动集中在少数文件）

| # | 项目 | 落点 | 为什么是 P0 |
|---:|---|---|---|
| 1 | 新建 `GlassTokens` + `LocalGlassTokens` | 新文件 `ui/theme/GlassTokens.kt`（对齐 `SectionDesign` 的写法） | 没有 token 就会重演"289 个圆角"的结局。必须先于一切 UI 改动 |
| 2 | **背景层**（渐变 + 噪点 + 层叠色块） | `navigation/AppNavigation.kt:565` 的 `Modifier.background(colorScheme.background)`；`theme/Theme.kt:77-85` 增加 backdrop token | 纯色背景下玻璃不可见。这是"能不能看出来"的前提 |
| 3 | **`MinisButton` 6 个变体** | `components/MinisButton.kt:35 / :62 / :89 / :119 / :148 / :177` | 一次覆盖 ~250 个按钮调用点，单文件改动 |
| 4 | **`MinisMenu` 玻璃菜单** | `components/MinisMenu.kt:56`（+ `:272 MinisMenuDivider`） | 24 处菜单，全 App 高频（顶栏 ⋮、附件 +、长按菜单） |
| 5 | **`SettingsScaffold` 玻璃顶栏** | `settings/SettingsComponents.kt:68`（顶栏 `:110`/`:119`；`containerColor` 在 `:115`/`:124`） | 一次覆盖 36 处二级页顶栏 / 30 个文件 |
| 6 | **`StandardChatSheet` 玻璃底片** | `chat/StandardChatSheet.kt:63`（`containerColor` 在 `:85`） | 7 处聊天 ⋯ 菜单一次到位，是"弹出即高级"的样板 |
| 7 | **聊天页顶栏** | `chat/ChatScreen.kt:2981` + `:3740`（inset）+ `:3742`（`containerColor` 0.92 alpha）+ `:3758`（68dp） | 用户 90% 时间停留在这一屏；现状已是"准玻璃"（92% alpha），改成玻璃成本最低 |
| 8 | **composer 输入卡** | `chat/ChatScreen.kt:6331`（Column）+ `:6349-6376`（`drawBehind` 自绘阴影，20dp）+ `:6378`（`contextUsageGlow`） | 全 App 视觉焦点。必须整块重写（不是换颜色）——但只有 1 处 |
| 9 | **会话列表顶栏 + 双 FAB + 搜索框** | `sessions/SessionListScreen.kt:972`（顶栏）、`:1809`（`DualFabRow`）、`:1876`（新建 FAB）、`:1933`（搜索 FAB）、`:1984`（搜索框胶囊） | App 首屏，一眼定生死；5 个位置都在同一文件，可一次改完 |
| 10 | **`MinisAlertDialog` 玻璃化** | `components/MinisAlertDialog.kt:61`（`Dialog`）→ `:64`（`Surface(16.dp, tonalElevation=6.dp)`） | 21 处直接受益，且为 P2 的 83 处迁移提供目标形态 |

### P1 —— 第二批（补齐一致性，中等风险）

| # | 项目 | 落点 |
|---:|---|---|
| 1 | 合并 3 套 `SettingsSection` | `components/SettingsSection.kt:30` / `settings/SettingsScreen.kt:564` / `settings/SettingsComponents.kt:212` → 保留 C，删 A/B（圆角统一 14dp） |
| 2 | `SectionDesign` 卡片玻璃化 | `components/SectionDesign.kt:161 SectionCard` / `:116 SectionHeader` / `:181 SectionDivider`（影响 5 + 14 处） |
| 3 | 设置总入口接入 `SettingsScaffold` | `settings/SettingsScreen.kt:131`（当前是裸 `Scaffold`，与其它 36 个设置页不一致） |
| 4 | 会话列表卡片与行 | `sessions/SessionListScreen.kt:2738 FolderCard`、`:3065 SessionRow`、`:2184 SessionItemContent`、`:2149 SectionHeader` |
| 5 | 消息气泡与工具卡 | `chat/ChatUserMessageUI.kt`（807 行）、`chat/ChatAssistantMessageUI.kt`（1377 行）、`chat/ChatToolDetailUI.kt:299 ToolDetailSheet` |
| 6 | 主模型选择器 | `chat/ChatModelPickerSheet.kt:396`（Main）→ 与 `components/UnifiedModelPickerSheet.kt:168` 合并 |
| 7 | 语音面板 | `chat/voice/InlineVoiceInputPanel.kt`（1589 行，`RoundedCornerShape(50)` 胶囊群 + 硬编码 `#FF9500`） |
| 8 | 会话内搜索条 | `chat/search/ChatSearchBar.kt:102-105`（`inputBg` + 20dp 圆角，直接覆盖在 composer 位置） |
| 9 | 设置行原语 | `settings/SettingsComponents.kt:283 SettingsRow` / `:185 SettingsSwitch` / `:426 SettingsValueRow` |
| 10 | 列表滑动操作 | `components/SwipeRowActions.kt:88`（`rowColor` 必须与玻璃行分层，见 §3.1⑥；3 处调用） |

### P2 —— 第三批（长尾 / 高风险，建议独立排期）

| # | 项目 | 落点 | 风险 |
|---:|---|---|---|
| 1 | 83 处裸 `AlertDialog` 迁移 | 44 个文件（最密：`settings/NetworkSettingsScreen.kt:479/499/526/566/656/884/976`、`scheduled/ScheduledTaskEditScreen.kt`、`settings/StorageManagementScreen.kt`） | 纯体力活，但每处都要验按钮语义 |
| 2 | WebView 相关页 | `browser/BrowserWebView.kt:36`、`preview/WebPreviewBottomSheet.kt:158/252`、`preview/WebPreviewFullscreenScreen.kt:194`、`sandbox/FilePreviewScreen.kt:569`、`settings/MinisSkillsBrowserScreen.kt:140` | z-order / 合成不可控 |
| 3 | 终端 | `terminal/TerminalScreen.kt:87`、`terminal/canvas/TerminalNativeView.kt:58`、`TerminalInputView.kt:54` | 原生 View 不受 Compose blur 影响 |
| 4 | 全屏预览三件套 | `components/FullscreenImageViewer.kt:92`、`components/ImageGalleryViewer.kt:89`、`sandbox/FilePreviewScreen.kt:101` | 自管 Window / inset |
| 5 | 平板双栏 | `navigation/ChatSplitScaffold.kt:543`（pane）、`:612`（分隔线）、`:704`（拖拽把手）、`:762`（空态） | `AnimatedPane` 重绘 |
| 6 | 自绘文本选择 | `chat/MinisTextKitSelection.kt:1117/1155`、`chat/MinisTextKitGesture.kt`(1,730 行)、`chat/MinisMarkdownTextToolbar.kt` | Popup 无法采样背景 |
| 7 | 装饰动画 | `components/DecorativeAnimation.kt:128/207`、`sessions/SessionListScreen.kt:2605/3427` | 每帧重绘 + 模糊 = 掉帧 |
| 8 | 分隔线统一 | 75 处 `HorizontalDivider(`（`SettingsComponents.kt:60 SettingsRowDivider`、`SectionDesign.kt:181 SectionDivider` 为主） | 玻璃的"发丝线"应统一为 0.5dp + 白 alpha |
| 9 | 排版收敛 | 349 处 `fontSize = X.sp`（11/12/13sp 占 211 处） | 玻璃上小字号对比度不足 |
| 10 | 二级页其余 29 处裸 `TopAppBar` | 见 §2.1 清单 | 逐个验 inset 对齐 |

### 7.1 建议的实施顺序

```
第 1 步  GlassTokens + 背景层        （无 UI 变化，但决定后面能不能看出来）
第 2 步  MinisButton / MinisMenu / MinisAlertDialog / StandardChatSheet / SettingsScaffold
         （5 个文件，覆盖 ~300 个调用点）
第 3 步  聊天顶栏 + 输入卡 + 会话列表顶栏/双 FAB/搜索框
         （3 个文件，覆盖 App 的 2 个首屏）
第 4 步  设置卡片体系收敛（3 套 SettingsSection → 1 套）+ SectionDesign
第 5 步  长尾：AlertDialog 83 处、WebView、终端、全屏页、双栏
```

### 7.2 一句话风险提示

> 这次改造真正的成本**不在"做玻璃"**，而在：
> ① 先把 3 套分区卡 / 2 套 picker / 2 套菜单 / 83 : 21 的弹窗比例收敛成 1 套；
> ② 给 minSdk 26 准备一套"没有模糊的玻璃"；
> ③ 把 `ChatScreen.kt` 那 8,069 行里的输入卡从 `drawBehind` 手绘阴影里拆出来。
> 这三件事做完，剩下的才是"调 tint 和 blur radius"。

---

## 附录 A：统计命令汇总（可复现）

```bash
cd .../com/openminis/app/ui

# 规模
find . -name '*.kt' | wc -l
find . -name '*.kt' -exec cat {} + | wc -l
find . -name '*.kt' -exec wc -l {} + | sort -rn | head -50

# 路由
rg -c '^\s{8}composable\(' navigation/AppNavigation.kt      # 64
rg -c '^\s{8}dialog\('     navigation/AppNavigation.kt      # 1
rg -c 'const val [A-Z_]+ =' navigation/AppNavigation.kt     # 66

# 颜色
rg -o 'Color\(0x' --glob '*.kt' | wc -l                     # 523
rg -c 'Color\(0x' --glob '*.kt' | sort -t: -k2 -rn | head -15
rg -o 'Color\(0x' --glob '*.kt' --glob '!theme/*' | wc -l   # 419
rg -o 'MaterialTheme\.colorScheme\.[a-zA-Z]+' --glob '*.kt' | wc -l   # 1220
rg -o 'ChatColors\.[a-zA-Z]+' --glob '*.kt' | wc -l                   # 353
rg -o 'Color\.White' --glob '*.kt' | wc -l                  # 95
rg -o 'Color\.Black' --glob '*.kt' | wc -l                  # 47
rg -o '\.copy\(alpha = [0-9.]+f' --glob '*.kt' | wc -l      # 363

# 形状 / 间距
rg -o 'RoundedCornerShape\(' --glob '*.kt' | wc -l          # 289
rg -o 'RoundedCornerShape\([0-9]+\.?[0-9]*\.dp\)' --glob '*.kt' \
  | sed 's/.*RoundedCornerShape(//; s/\.dp)//' | sort -n | uniq -c | sort -rn | head -8
rg -o 'padding\([^)]*[0-9]+\.dp[^)]*\)' --glob '*.kt' \
  | rg -o '[0-9]+(\.0)?\.dp' | sort | uniq -c | sort -rn | head -8

# 背景 / 表面
rg -o '\.background\(' --glob '*.kt' | wc -l                # 339
rg -o '\.background\(Color' --glob '*.kt' | wc -l           # 33
rg -o '\.background\(MaterialTheme' --glob '*.kt' | wc -l   # 69
rg -o '\.background\(ChatColors' --glob '*.kt' | wc -l      # 33
rg -o '(?<![A-Za-z])Surface\(' --glob '*.kt' -P | wc -l     # 41

# 组件 / 容器
rg -o 'Scaffold\(' --glob '*.kt' | wc -l                    # 29
rg -o 'TopAppBar\(' --glob '*.kt' | wc -l                   # 29
rg -o 'ModalBottomSheet\(' --glob '*.kt' | wc -l            # 29
rg -o '(?<![A-Za-z])AlertDialog\(' --glob '*.kt' -P | wc -l # 83
rg -o 'MinisAlertDialog\(' --glob '*.kt' | wc -l            # 21
rg -o 'MinisMenu\(' --glob '*.kt' | wc -l                   # 24
rg -o '(?<![A-Za-z])DropdownMenu\(' --glob '*.kt' -P | wc -l# 15
rg -o '(?<![A-Za-z])Popup\(' --glob '*.kt' -P | wc -l       # 10
rg -o 'StandardChatSheet\(' --glob '*.kt' | wc -l           # 7
rg -o 'LazyColumn\(' --glob '*.kt' | wc -l                  # 41
rg -o 'Canvas\(' --glob '*.kt' | wc -l                      # 18
rg -o 'HorizontalDivider\(' --glob '*.kt' | wc -l           # 75
rg -o 'NavigationBar\(|NavigationRail\(' --glob '*.kt' | wc -l  # 0
rg -o 'TopAppBarScrollBehavior|enterAlwaysScrollBehavior' --glob '*.kt' | wc -l  # 0
rg -o 'Modifier\.blur|RenderEffect' --glob '*.kt' | wc -l   # 0（仅注释提及）
```

