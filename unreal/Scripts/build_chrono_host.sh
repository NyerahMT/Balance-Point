#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${CHRONO_TAG:-10.0.0}"
WORK="${CHRONO_WORK:-$ROOT/.chrono-build}"

case "$(uname -s)" in
  Darwin) PLATFORM=Mac ;;
  Linux) PLATFORM=Linux ;;
  *) echo "Unsupported host; build Chrono manually and set CHRONO_ROOT." >&2; exit 2 ;;
esac

SRC="$WORK/src"
BUILD="$WORK/build-$PLATFORM"
INSTALL="$ROOT/Plugins/BalancePointChrono/ThirdParty/Chrono/$PLATFORM"

rm -rf "$WORK" "$INSTALL"
git clone --depth 1 --branch "$TAG" https://github.com/projectchrono/chrono.git "$SRC"
git -C "$SRC" submodule update --init --recursive --depth 1

cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
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
cmake --build "$BUILD" --target install --parallel

echo "Chrono $TAG installed to $INSTALL"
