# Minis Glass 开发规划

> 配套：`01-development-doc.md`（技术方案）、`design-ios-extract.md`（iOS 令牌实扫）、`ui-inventory-android.md`（安卓 UI 解剖）
> 编制日期：2026-10-30 ｜ 基于上游 `1.15` / build 37

---

## 0. 先说结论

- **7 个里程碑，15~20 个有效工作日**。真正的时间黑洞不是写代码，是两条反馈回路：手机内编译（5~10 分钟/次）和 CI 出包（~60 分钟/次）。
- **风险最高的三件事**（按概率×影响排序）：
  1. `rclone.aar` 不存在 —— **已经发生**，干净 clone 编不过（§01 文档 6.1.1）。不解决，一步都走不了。
  2. 背景是纯色 —— 玻璃采样纯色 = 只见"半透明灰卡"。所以 M3 的第一件事不是玻璃，是背景层。
  3. 参考库基于 Compose 1.12，我们只有 1.9 —— 需要移植，最坏情况砍掉折射。
- **需要主人拍板 5 件事**（§4），其中 3 件现在就卡着 M0。

---

## 1. 里程碑总览

| # | 里程碑 | 目标 | 产出 | 验收 | 估时 |
|---|---|---|---|---|---|
| **M0** | 基建 | 能编、能出包、能装 | fork 仓库、rclone.aar 重建、prebuilt 脚本、CI 跑通 | 手机上装出**功能与官方完全一致**的自签包 | 2~3 天 |
| **M1** | 玻璃内核 | 证明 AGSL 玻璃在 minis 里成立 | `:glass` 模块 + 内部 demo 页（6 种表面） | demo 与参考库 catalog 观感一致；P95 ≤16ms | 2~3 天 |
| **M2** | 令牌 + 收敛 | 把 iOS 数值变成代码，把 3 套外壳合成 1 套 | `GlassTokens`、`ContinuousRoundedRectShape`、10 个组件、`MinisTopBar`/`MinisSheet`/统一 `SettingsSection` | 组件 gallery 对表通过；**收敛前后功能零变化** | 3 天 |
| **M3** | 背景层 + 主壳 | 第一眼看到的就是液态玻璃 | `GlassWallpaper`、会话列表、聊天页、输入条、FAB | 三张主截图与 iOS 盲测相似 | 4 天 |
| **M4** | 浮层 | Sheet / 弹窗 / 菜单 / 设置全玻璃 | `MinisSheet`(29)、`MinisAlertDialog`(21+83)、`MinisMenu`(24)、设置卡 | 任意浮层都是玻璃且不破相 | 3 天 |
| **M5** | 性能与降级 | 中端机稳、老机不崩 | 能力三档、滚动降级、帧预算、设置项、无障碍 | 中端机 ≥55fps；API26/31 设备正常 | 2 天 |
| **M6** | 打磨发布 | 对外可发 | 回归、图标、README、`MODIFICATIONS.md`、Release | 陌生人照 README 能装能用 | 2 天 |
| **M7** | 长期维护 | 跟得上上游 | `upstream-watch.yml`、`TOUCHPOINTS.md` | 上游发版后 ≤3 天出新包 | 持续 |

---

## 2. 逐里程碑拆解

### M0 基建（2~3 天）— 先解决"编不过"

**已踩到的坑**：`:app` 依赖 `app/libs/rclone.aar`（Go + gomobile 产物，上游不提交），干净 clone 直接 `Could not find :rclone:`。`-Pminis.prebuiltNative`（已加）只解决 CMake 那 3 个 .so，解决不了它。

**要做**
1. **rclone.aar 重建（S1 方案）**：从官方 APK 的 `classes.dex` 反编译 `com.openminis.rclone.gomobile.**` + `go.**`（实测 4 个业务类 + gomobile 运行时），产出 jar，配 `libgojni.so` 打成 AAR。产物放 `app/libs/`（gitignore），脚本 `scripts/glass/build-rclone-aar.sh` 固化。
   - 备选 S3：空壳 stub（备份/Tailscale 失效），只用作"先跑起来"的临时手段。
2. 建 GitHub 仓库、`main` 镜像上游 1.15、`glass-ui` 开发分支。
3. `-Pminis.prebuiltNative` + `fetch-prebuilt-native.sh` 落地（已完成初版）。
4. keystore 生成 + 本地 `keystore.properties`。
5. **第一次完整出包** `assembleGithubDebug` → 装手机 → 跑回归清单前 5 项。
6. CI：先只做 build（不发布），验证 `ubuntu-latest` 能跑通 native 依赖。

**验收**：手机上有两个图标，我们的能正常聊天 + 跑沙箱命令；CI 绿。

**风险**：反编译的 `go.Seq` 可能有细节问题（JNI 回调）。回退：CI 编真 AAR，本机用 S3 stub 只做 UI 迭代。

---

### M1 玻璃内核（2~3 天）

**要做**
1. 新建 `:glass` 模块，移植 backdrop 核心（`Backdrop`/`DrawBackdropModifier`/`LayerBackdrop`/`CanvasBackdrop`/`effects`/`highlight`/`shadow`/`RuntimeShader`，约 1.2k 行）。
2. 自研 `ContinuousRoundedRectShape`（超椭圆）替掉 `com.kyant.shapes`。
3. 内部 demo 页（临时路由）：6 种表面（Bar/Panel/Overlay/Glass/Popover/Button）叠在一段可滚动内容上。
4. 真机截图 + `dumpsys gfxinfo` 帧数据。

**验收**：与参考库 catalog APK 并排观感一致；滚动时玻璃实时跟随；P95 ≤16ms。

**风险**：Compose 1.9 缺 API（已核对全部 import 均在 1.9 可用，实际编译才是真考验）。回退：砍 `lens`（折射），保留 blur + vibrancy + 高光 —— 观感收益仍有 ~80%。

---

### M2 令牌 + 收敛（3 天）

**要做**
1. `GlassTokens`（§4 的全部数值）+ `GlassMotion`（spring 表）。
2. 10 个组件：`GlassSurface`/`GlassBar`/`GlassSheet`/`GlassDialog`/`GlassMenu`/`GlassFab`/`GlassCard`/`GlassSegmented`/`GlassToggle`/`GlassButton`。
3. **外壳收敛**（零视觉变化，单独提交）：
   - 2 套顶栏 → `MinisTopBar`
   - 3 套 `SettingsSection` → 1 套（圆角 12）
   - 83 处裸 `AlertDialog` → `MinisAlertDialog`
   - 29 处各写各的 `ModalBottomSheet` → `MinisSheet`
4. 组件 gallery 页，明/暗各一张截图。

**验收**：gallery 与 iOS 令牌逐项对表；收敛提交跑回归清单，功能零变化。

**为什么单列**：报告明确指出 3 套 SettingsSection、2 套顶栏、2 类弹窗。不收敛，后面每改一次圆角都要改 200 处，且跟上游 rebase 时全是冲突。

---

### M3 背景层 + 主壳（4 天）

**要做**（每完成一项出一次包）
1. **`GlassWallpaper`**（P0）：极低饱和层叠渐变，铺在 NavHost 背景；肉眼几乎看不出，但给玻璃提供色温。同时给"背后无内容"的玻璃准备常量色回退。
2. 会话列表顶栏：滚动 0 位置透明 → 滚出 40dp 过渡为玻璃条（对齐 iOS 的 scroll edge 行为；安卓没有系统联动，自己插值）。
3. 会话列表双 FAB → 玻璃胶囊/圆钮（56×56，边距 16，底部留白 20，品牌沙 tint 0.30）。
4. 聊天页顶栏玻璃化。
5. **输入条重写**：上游是 `drawBehind` + `setShadowLayer` 自绘（`ChatScreen.kt:6349`，圆角 20dp，20dp 阴影），必须改成 `GlassSurface(r=20)` + 阴影降到 ~8dp。
6. 用户气泡（r18）+ 工具状态条（r10）。

**验收**：会话列表 / 聊天页两张主截图与 iOS 并排，盲测能认出是同一套设计。

**风险**：输入条内部有附件菜单、模型选择、语音面板，牵一发动全身 —— 只在最外层包玻璃，内部布局不动。

---

### M4 浮层（3 天）

1. `MinisSheet` 内部换玻璃（覆盖 29 处），拖柄/scrim/圆角统一。
2. `MinisAlertDialog` → `GlassDialog`（覆盖 21 + 83 处）。
3. `MinisMenu` → `GlassMenu`（24 处）。
4. 设置分组卡玻璃化（r12）+ 设置页固定底栏走 `Material.bar` 档。
5. `AppearanceScreen` 新增「玻璃风格」三项 + 「性能模式」。

**验收**：任意弹窗/sheet/菜单都是玻璃；长列表里不掉帧。

---

### M5 性能与降级（2 天）

1. `GlassCapability` 三档（High / Standard / Lite / Off）+ 老 Mali GPU 名单。
2. 滚动降级（`isScrollInProgress` → 降档，停稳 120ms 恢复）。
3. `GlassFrameBudget` 帧预算守卫（连续超预算自动降档）。
4. 无障碍：`ANIMATOR_DURATION_SCALE=0`、系统「移除透明度」。
5. 与上游 `perf` 变体一起跑性能对比，产出数据表（帧时间 P95 / 冷启动 / 内存 / 电量）。

**验收**：中端机 ≥55fps；API 26 设备 Off 档观感与上游一致；无 native crash。

---

### M6 打磨发布（2 天）

1. 10~15 屏逐屏对表（`design-ios-extract.md` 的 §9 界面清单）。
2. 全量回归 + 上游 `androidTest`。
3. 自绘玻璃风格应用图标（避开上游商标）。
4. README（截图/GIF/安装/与官方差异）、`MODIFICATIONS.md`、`THIRD_PARTY_LICENSES.md`。
5. tag `v1.15-glass.1` → CI 出 release APK。

---

### M7 长期维护（持续）

1. `upstream-watch.yml` 每日检查上游 tag/提交，漂移即开 Issue。
2. 上游发版 → `main` 快进 → `glass-ui` rebase → CI → 新 tag。
3. 维护 `TOUCHPOINTS.md`（≤20 个文件）。
4. 若上游升级 Kotlin ≥2.4：评估切回 Maven 依赖，删掉移植代码（维护成本大降）。

---

## 3. 依赖关系

```
M0 ──▶ M1 ──▶ M2 ──▶ M3 ──▶ M4 ──▶ M5 ──▶ M6 ──▶ M7
              │       │
              │       └─ 背景层是 M3 的前置（否则玻璃不可见）
              └─ 收敛是 M3/M4 的前置（否则改 200+ 处）
M1 失败 → 降级版（无折射）仍可走完 M2~M6
```

---

## 4. 需要主人拍板 / 提供（阻塞项）

| # | 事项 | 为什么卡 | 我的建议 |
|---|---|---|---|
| **D1** | **GitHub 用户名**（仓库名已定 `minis-liquid`） | 没有用户名就没法 push | 只差这个，其余都已就绪 |
| ~~D2~~ | ~~applicationId~~ | **已定（2026-10-30）**：签名一致不可能（私钥在上游），走**共存版** `com.openminis.app.glass` | 见 01 文档 §6.3 |
| ~~D3~~ | ~~测试设备~~ | **已定**：Android 13+ → 走完整玻璃档（AGSL 可用） | — |
| D4 | 签名 | CI 需要 secrets | 我生成 keystore，密码写本地 `keystore.properties`（不进库），你自己备份（已按此执行） |
| D5 | 玻璃浓度 | 审美主观 | 默认按 iOS 的 3 档材质；M1 出 demo 后你一句话我调数值 |

> 只剩 D1（GitHub 用户名）。给到就能 push，其余全部已定、M0 已在推进。

---

## 5. 执行节奏（"一步步完成"具体怎么走）

每一轮交付给你的是 **一个可安装的包 + 3~5 张真机截图 + 一句结论**，你只需要：

1. 装上、看、说「这条不对」或「继续」；
2. 在岔路口（审美/取舍）拍板。

| 轮次 | 我做 | 你验收什么 |
|---|---|---|
| R0 | M0：仓库 + rclone.aar + 首个可安装包 | 官方功能是否完全正常 |
| R1 | M1：玻璃内核 demo | 玻璃「像不像」，浓度是否要调 |
| R2 | M2：gallery + 收敛 | 圆角/间距/描边对不对；收敛后有无功能异常 |
| R3 | M3a：`GlassWallpaper` + 会话列表 | 第一眼观感（最关键的一次反馈） |
| R4 | M3b：聊天页 + 输入条 | 主界面观感 |
| R5 | M4：Sheet/弹窗/菜单/设置 | 细节一致性 |
| R6 | M5：性能档位 + 设置项 | 中端机体验 |
| R7 | M6：发布包 + README | 可以发出去 |

---

## 6. 说实话的部分

- **15~20 天是"顺利"估算**。真实情况大概率还有 1~2 个没预料的坑（Mali 驱动、某个 Sheet 的嵌套、CI 磁盘），每个 0.5~2 天。
- **反馈延迟很真实**：手机内改一次 5~10 分钟，出包 15~25 分钟，CI 1 小时。视觉微调会很慢 —— 缓解办法是把可调参数全塞进 `GlassTokens`，一次改多个一起验。
- **我赌不了"看起来正合适"**：没在真机上看到的效果我不下结论，得靠你的截图/描述。
- **维护是长期成本**：上游 2~3 周一个版本。只要改动面守在 20 个文件内，每次合并是"改十几次若干行"；但如果上游大改 UI（比如自己上了 Material3 Expressive 玻璃），我会先停下来跟你重新评估，而不是硬扛。
- **一件事我要提前说清**：GPLv3 的 fork **必须继续开源**，且不能改个名就当自己的作品卖。这个项目的定位是"给 Android 端做一个更好看的开源版本"，署名上游是底线。
