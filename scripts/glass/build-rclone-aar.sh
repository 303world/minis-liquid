#!/usr/bin/env bash
# [GLASS] 重建 app/libs/rclone.aar。
#
# 为什么需要它：上游的 app/build.gradle.kts 用 flatDir 依赖 app/libs/rclone.aar，
# 该文件是 deps/build_rclone_android.sh 的产物（Go + gomobile 交叉编译，需要 NDK），
# 上游不提交。任何干净 clone 都会在配置期直接失败：
#     Could not find :rclone:.
# 在没有 NDK / 没有 Go 工具链的机器（例如手机内的 aarch64 PRoot 沙箱）上，
# 只能用官方 release APK 里已经编好的那一份。
#
# 做法：官方 APK 的 classes.dex 含 gomobile 生成的 Java 层（纯 Java，无 Android 依赖），
# 用 dex2jar 还原成 class 文件（保留原始字节码，不反编译不重写），只取
#   com/openminis/rclone/**  (4 个业务类)
#   go/**                    (gomobile 运行时：Seq / Universe / error …)
# 重新打成 AAR。native 侧不动：libgojni.so 由 fetch-prebuilt-native.sh 放进 jniLibs。
#
# 用法：bash scripts/glass/build-rclone-aar.sh [上游版本]
set -euo pipefail

VERSION="${1:-1.15}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT/src/android"
CACHE="$ROOT/.cache/prebuilt"
TOOLS="$ROOT/.cache/tools"
BUILD_N="$(rg -o 'versionCode = ([0-9]+)' "$ANDROID_DIR/app/build.gradle.kts" -r '$1' | head -1)"
APK="$CACHE/upstream-$VERSION.apk"
D2J_DIR="$TOOLS/dex-tools-v2.4"
D2J_LIB="$D2J_DIR/lib/*"

mkdir -p "$CACHE" "$TOOLS" "$ANDROID_DIR/app/libs"

# 1. 官方 APK
if [ ! -f "$APK" ]; then
  echo "→ 下载上游 APK $VERSION (build $BUILD_N)"
  curl -fSL --noproxy '*' -o "$APK" \
    "https://github.com/OpenMinis/OpenMinis/releases/download/$VERSION/MinisApp-$VERSION-build$BUILD_N-arm64-v8a.apk"
fi

# 2. dex2jar（只在需要时下）
if [ ! -d "$D2J_DIR" ]; then
  echo "→ 下载 dex2jar 2.4"
  curl -fSL --noproxy '*' -o "$TOOLS/dex-tools.zip" \
    "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip"
  ( cd "$TOOLS" && unzip -o -q dex-tools.zip )
fi

# 3. classes.dex → jar（6g 堆：整包 9k+ 类，默认堆会 OOM）
echo "→ 解 classes.dex"
rm -rf "$CACHE/dex" && mkdir -p "$CACHE/dex"
unzip -o -q "$APK" 'classes*.dex' -d "$CACHE/dex"
[ -f "$CACHE/dex/classes2.dex" ] && echo "  ⚠ 存在 classes2.dex，需一并处理（本脚本当前只处理 classes.dex）"

echo "→ dex2jar（约 1~2 分钟）"
rm -f "$CACHE/all.jar"
_JV=""
[ -n "${JAVA_HOME:-}" ] && _JV="env JAVA_HOME=$JAVA_HOME"
env JAVA_HOME="${JAVA_HOME:-}" _JAVA_OPTIONS="-Xmx6g" java -cp "$D2J_LIB" \
  com.googlecode.dex2jar.tools.Dex2jarCmd \
  -o "$CACHE/all.jar" "$CACHE/dex/classes.dex" >/dev/null 2>&1

# 4. 只留需要的类
echo "→ 筛选 com/openminis/rclone/** 与 go/**"
rm -rf "$CACHE/aar" && mkdir -p "$CACHE/aar/cls"
( cd "$CACHE/aar/cls" && unzip -o -q "$CACHE/all.jar" 'com/openminis/rclone/*' 'go/*' )
( cd "$CACHE/aar/cls" && zip -qr ../classes.jar com go )

cat > "$CACHE/aar/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.openminis.rclone.gomobile">
    <uses-sdk android:minSdkVersion="21" />
</manifest>
XML
touch "$CACHE/aar/R.txt"

( cd "$CACHE/aar" && rm -f rclone.aar && zip -q rclone.aar AndroidManifest.xml classes.jar R.txt )
cp -f "$CACHE/aar/rclone.aar" "$ANDROID_DIR/app/libs/rclone.aar"

echo "✓ 完成：$ANDROID_DIR/app/libs/rclone.aar"
unzip -l "$ANDROID_DIR/app/libs/rclone.aar"
