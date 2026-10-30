#!/usr/bin/env bash
# [GLASS] 从上游官方 release APK 里抽出「本机（aarch64 Linux）编不出来」的产物：
#   - app/src/main/jniLibs/arm64-v8a/*.so   （CMake 现编的三个 + Go/ONNX 预置的）
#   - app/src/main/assets/proot-aarch64      （由 deps/build_proot.sh 产出）
#   - app/src/main/assets/alpine-minirootfs.tar（由 scripts/prepare_android_sandbox.sh 下载）
#
# 为什么需要它：NDK 只提供 x86_64 Linux / macOS / Windows 主机工具链，手机内的
# PRoot aarch64 沙箱跑不了 NDK，因此本机构建必须复用官方包里的预编译产物。
# 桌面/CI 构建不要用本脚本，走 deps/ 下的源码编译。
#
# 用法：
#   bash scripts/glass/fetch-prebuilt-native.sh [上游版本] [--force]
#   默认版本 1.15（对应 versionCode 37）
set -euo pipefail

VERSION="${1:-1.15}"
FORCE=0
[ "${2:-}" = "--force" ] && FORCE=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_DIR="$ROOT/src/android"
JNI_DIR="$ANDROID_DIR/app/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="$ANDROID_DIR/app/src/main/assets"
CACHE="$ROOT/.cache/prebuilt"
APK="$CACHE/upstream-$VERSION.apk"

# 与上游 release 附件命名一致：MinisApp-<ver>-build<N>-arm64-v8a.apk
# build 号从 build.gradle.kts 的 versionCode 取，避免手改两处
BUILD_N="$(rg -o 'versionCode = ([0-9]+)' "$ANDROID_DIR/app/build.gradle.kts" -r '$1' | head -1)"

URL="https://github.com/OpenMinis/OpenMinis/releases/download/$VERSION/MinisApp-$VERSION-build$BUILD_N-arm64-v8a.apk"

mkdir -p "$CACHE" "$JNI_DIR" "$ASSETS_DIR"

if [ ! -f "$APK" ] || [ "$FORCE" = "1" ]; then
  echo "→ 下载 $URL"
  curl -fSL --noproxy '*' -o "$APK" "$URL"
else
  echo "✓ 已缓存 $APK"
fi

echo "→ 解包原生库"
unzip -o -q "$APK" 'lib/arm64-v8a/*' -d "$CACHE/x" 
cp -f "$CACHE/x/lib/arm64-v8a/"*.so "$JNI_DIR/"

echo "→ 解包 proot 与 rootfs"
unzip -o -q "$APK" 'assets/proot-aarch64' 'assets/alpine-minirootfs.tar' 'assets/alpine-minirootfs.tar.gz' -d "$CACHE/x" 2>/dev/null || true
for f in proot-aarch64 alpine-minirootfs.tar alpine-minirootfs.tar.gz; do
  [ -f "$CACHE/x/assets/$f" ] && cp -f "$CACHE/x/assets/$f" "$ASSETS_DIR/"
done

echo "✓ 完成："
ls -la "$JNI_DIR" | tail -n +2
ls -la "$ASSETS_DIR" | rg 'proot|alpine' || true
