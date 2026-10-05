#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "iOS Chrono cross-build requires macOS/Xcode." >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${CHRONO_TAG:-10.0.0}"
WORK="${CHRONO_WORK:-$ROOT/.chrono-ios-build}"
SRC="$WORK/src"
BUILD="$WORK/build"
INSTALL="$ROOT/Plugins/BalancePointChrono/ThirdParty/Chrono/IOS"

rm -rf "$WORK" "$INSTALL"
git clone --depth 1 --branch "$TAG" https://github.com/projectchrono/chrono.git "$SRC"
git -C "$SRC" submodule update --init --recursive --depth 1

# Chrono does not advertise iOS as a first-class tested target. Keep this build isolated
# and fail loudly rather than silently substituting a different physics implementation.
cmake -S "$SRC" -B "$BUILD" -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS \
  -DCMAKE_OSX_SYSROOT=iphoneos \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=17.0 \
  -DCMAKE_INSTALL_PREFIX="$INSTALL" \
  -DBUILD_SHARED_LIBS=OFF \
  -DBUILD_DEMOS=OFF \
  -DBUILD_TESTING=OFF \
  -DBUILD_BENCHMARKING=OFF \
  -DCH_ENABLE_MODULE_VEHICLE=OFF \
  -DCH_ENABLE_MODULE_IRRLICHT=OFF \
  -DCH_ENABLE_MODULE_VSG=OFF \
  -DCH_ENABLE_MODULE_SENSOR=OFF \
  -DCH_ENABLE_MODULE_MULTICORE=OFF
cmake --build "$BUILD" --config Release --target install --parallel

echo "Chrono $TAG iOS arm64 install: $INSTALL"
