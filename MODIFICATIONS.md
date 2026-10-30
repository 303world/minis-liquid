# MODIFICATIONS — Minis Liquid

> GPLv3 §5(a) 要求：分发修改版本时须显著标注修改内容与日期。
> 本文件即该声明；每个改动点在源码里另有 `// [GLASS]` 标记便于定位。

**上游**：https://github.com/OpenMinis/OpenMinis
**基线**：tag `1.15`（versionCode 37），同步快照 2026-10-30
**本 fork**：Minis Liquid —— 只改 Android 端的视觉层（液态玻璃），不改业务逻辑
**许可**：GPLv3（与上游一致），随附 `LICENSE`

---

## 修改原则

1. **业务代码零改动**：`data/`、`provider/`、`agent/`、`sandbox/`、`service/` 不碰。
2. **新增优先于修改**：玻璃实现全部放在新增模块 `src/android/glass/` 与新增包 `ui/glass/`。
3. **修改的上游文件必须加 `// [GLASS]` 标记**，并在下表登记。

---

## 修改清单

| 文件 | 日期 | 改动 |
|---|---|---|
| `src/android/app/build.gradle.kts` | 2026-10-30 | ① 新增 `-Pminis.prebuiltNative=true` 开关：跳过 CMake 原生构建，复用 `jniLibs` 里的预编译 `.so`（aarch64 主机无 NDK 工具链）② 预编译模式下 `packaging.jniLibs.keepDebugSymbols` 避免调用 NDK 的 `llvm-strip` |
| `.gitignore` | 2026-10-30 | 追加 `[GLASS]` 段：忽略 `alpine-minirootfs.tar`、`provider-customization.properties`、`.cache/`、`.kotlin/` |
| `README.md` | 2026-10-30 | 换成 fork 说明；上游原文完整保留为 `README.upstream.md` |
| `src/android/app/res/xml/shortcuts.xml` | 待改 | `android:targetPackage` 改 `${applicationId}`（共存版包名变更） |
| `src/android/app/src/main/java/com/openminis/app/data/repository/AppIconRepository.kt` | 待改 | 硬编码 `com.openminis.app` 改 `BuildConfig.APPLICATION_ID` |
| （后续里程碑持续更新） | | |

## 新增文件（非修改，不受 §5(a) 约束，仅作索引）

| 路径 | 作用 |
|---|---|
| `src/android/glass/**` | `:glass` 模块：液态玻璃渲染内核（移植自 Apache-2.0 的 Kyant0/AndroidLiquidGlass，归属见 `THIRD_PARTY_LICENSES.md`） |
| `src/android/app/src/main/java/com/openminis/app/ui/glass/**` | 业务侧玻璃封装与页面外壳 |
| `scripts/glass/fetch-prebuilt-native.sh` | 从官方 release APK 抽取预编译 `.so` 与沙箱资产 |
| `scripts/glass/build-rclone-aar.sh` | 从官方 APK 重建 `app/libs/rclone.aar`（上游该文件是构建产物，未提交） |
| `scripts/glass/setup-aapt2-qemu.sh` | aarch64 主机上通过 qemu 运行 x86_64 版 AAPT2 |
| `.github/workflows/` | CI：构建发布包 / 检查上游漂移 |
| `docs/glass/**` | 设计与流程文档 |

## 与上游的差异（用户可感知）

| 差异 | 说明 |
|---|---|
| applicationId | `com.openminis.app.glass`（可与官方版共存） |
| 版本号 | `<上游版本>-liquid.<构建号>`，versionCode = 上游 ×100 + 构建号 |
| App 内更新源 | 指向本 fork 的 GitHub Releases |
| 界面 | 液态玻璃材质（可用设置项关回上游观感） |

## 未修改但需知会

- `src/ios/**` 原样跟随上游，不参与本 fork 的构建。
- 应用名与图标沿用上游（主人决定）；本 fork 与 OpenMinis 官方无关，属非官方分支。
