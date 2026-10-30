# Minis Liquid

**Minis 的非官方 Android 分支：功能一行不改，只把界面做成 iOS 26 的液态玻璃。**

> 上游：[OpenMinis/OpenMinis](https://github.com/OpenMinis/OpenMinis)（GPLv3）。
> 本项目是与官方无关的第三方分支，仅改动 Android 端视觉层。
> 上游原始 README 保留在 [`README.upstream.md`](README.upstream.md)。

---

## 它是什么

Minis 是一个「把 Claude / GPT / Gemini 装进手机」的本地 AI Agent，带完整的 Alpine Linux 沙箱、浏览器自动化、技能与记忆。官方有 iOS 和 Android 两个版本 —— **iOS 版用的是 iOS 26 的液态玻璃材质，Android 版还是 Material 3 的静态配色**。

这个分支做的事只有一件：**把 Android 端的视觉层换成液态玻璃**，让两个平台看起来是同一个产品。业务逻辑、数据格式、沙箱、provider、技能系统全部保持上游原样。

## 现在是什么状态

| 阶段 | 状态 |
|---|---|
| 上游源码剖析 / 参考库评估 / iOS 设计令牌提取 / 安卓 UI 解剖 | ✅ 完成 |
| 开发文档 + 开发规划 | ✅ 完成（`docs/glass/`） |
| M0 基建（本机构建链路打通） | 🚧 进行中 |
| M1 玻璃渲染内核 | ⬜ |
| M2 设计令牌 + 外壳收敛 | ⬜ |
| M3~M6 界面实施 / 性能 / 发布 | ⬜ |

## 与官方版的区别

| | 官方版 | 本分支 |
|---|---|---|
| 包名 | `com.openminis.app` | `com.openminis.app.glass`（**可与官方版共存**） |
| 功能 | — | 完全一致 |
| 界面 | Material 3 | 液态玻璃（可在设置里关回原样） |
| 更新源 | 官方 GitHub Releases | 本仓库 Releases |

> 签名不同，**不能覆盖安装官方版**（这是 Android 签名机制决定的，任何人都做不到）。

## 许可

GPLv3，与上游一致。修改声明见 [`MODIFICATIONS.md`](MODIFICATIONS.md)，第三方归属见 [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)。

玻璃渲染内核移植自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0），归属声明见 `src/android/glass/NOTICE`。

## 从源码构建

见 [`docs/glass/BUILDING-FORK.md`](docs/glass/BUILDING-FORK.md)。简述：

```sh
git clone --recurse-submodules https://github.com/<you>/minis-liquid.git
cd minis-liquid/src/android
cp app/provider-customization.properties.example app/provider-customization.properties
bash ../scripts/glass/fetch-prebuilt-native.sh      # 拉预编译 .so + 沙箱资产
bash ../scripts/glass/build-rclone-aar.sh           # 重建 rclone.aar
./gradlew :app:assembleGithubDebug
```
