#!/bin/sh
# Makes the release files, and with --publish the tag and the GitHub release.
#
#   tools/release.sh             dist/release/ only (a check build)
#   tools/release.sh --publish   on main: tag v<version>, push it, GitHub release
#
# Files (<v> = mod/5w-screencap/mod.json "version"; tools/package.sh makes them)
#   5w-screencap-<v>-usb.zip              unzip onto a USB stick: NEMESIS installs it (most owners)
#   5w-screencap-<v>.zip                  the signed HMG-API mod (HMG-API app's USB update, adb)
#   5w-screencap-<v>-nemesis-package.zip  the NEMESIS package alone
#   SHA256SUMS.txt
#
# The whole procedure: docs/RELEASING.md.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PUBLISH=0
[ "${1:-}" = "--publish" ] && PUBLISH=1

V="$(python3 -c 'import json; print(json.load(open("mod/5w-screencap/mod.json"))["version"])')"
[ -n "$V" ] || { echo "can't read the version from mod.json"; exit 1; }
TAG="v$V"
OUT="$ROOT/dist/release"

if [ "$PUBLISH" = 1 ]; then
    branch="$(git rev-parse --abbrev-ref HEAD)"
    [ "$branch" = main ] || { echo "releases are made on main (now: $branch)"; exit 1; }
    [ -z "$(git status --porcelain)" ] || { echo "there are uncommitted changes"; exit 1; }
    git fetch -q origin main --tags
    [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "not the same as origin/main: push or pull first"; exit 1; }
    if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
        echo "tag $TAG already exists: raise the version in mod.json"; exit 1
    fi
fi

# tests, then the files
if [ "${SC_SKIP_TESTS:-0}" != 1 ]; then
    sh tests/run.sh | tail -1 | grep -q ", 0 failed" || { echo "tests failed: release stopped"; exit 1; }
fi
sh tools/package.sh

[ "$PUBLISH" = 1 ] || { echo "check build done. To publish: tools/release.sh --publish on main"; exit 0; }

NOTES="$ROOT/dist/release-notes.md"
{
    echo "## 설치"
    echo
    echo "**HMG-API가 먼저 설치되어 있어야 합니다** (HMG-API 1.0 이상, NEMESIS 위에서 동작)."
    echo
    echo "1. 아래 **\`5w-screencap-$V-usb.zip\`**을 받아 압축을 풀고, 안에 있는 \`NEMESIS.data\`와 \`packages\` 폴더를 USB **맨 위**에 복사합니다."
    echo "2. 차에 꽂고 NEMESIS가 물으면 **예**를 누릅니다."
    echo "3. 앱 메뉴의 **\"화면 캡처\"**에서 버튼과 녹화를 설정합니다."
    echo
    echo "| 파일 | 용도 |"
    echo "|---|---|"
    echo "| \`5w-screencap-$V-usb.zip\` | **대부분은 이것.** USB에 풀면 NEMESIS로 설치 |"
    echo "| \`5w-screencap-$V.zip\` | HMG-API mod 파일만 (HMG-API의 USB 업데이트 \`HMGAPI/updates/\`, adb) |"
    echo "| \`5w-screencap-$V-nemesis-package.zip\` | NEMESIS 패키지만 (직접 설치하는 경우) |"
    echo "| \`SHA256SUMS.txt\` | 파일 검증용 |"
    echo
    if [ -f "docs/changes/$TAG.md" ]; then
        echo "## 바뀐 점"
        echo
        cat "docs/changes/$TAG.md"
    fi
} > "$NOTES"

git tag -a "$TAG" -m "5w-screencap $V"
git push -q origin "$TAG"
gh release create "$TAG" "$OUT"/*.zip "$OUT/SHA256SUMS.txt" --title "5w-screencap $V" --notes-file "$NOTES"
echo "released $TAG"
