#!/bin/sh
# Prints the path of the unpacked HMG-API mod SDK, fetching it first if needed
# (pinned release and sha256; AGPL-3.0, used as a build tool, nothing of it ships in the mod).
# The SDK's version only has to be at or above the mod's "hmgapi" floor.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
V=1.1.0-rc.4
SHA=5e7a0e71d6e001ed494c63719f43756f5eef6d740379d1666852ecf851433f7f
D="$ROOT/build/sdk"
Z="$D/hmgapi-mod-sdk-$V.zip"
if [ ! -f "$D/sdk/VERSION" ] || [ "$(cat "$D/sdk/VERSION")" != "$V" ]; then
    mkdir -p "$D"
    if [ ! -f "$Z" ]; then
        echo "download HMG-API mod SDK $V" >&2
        curl -fsSL -o "$Z.part" "https://github.com/Open-HMG/HMG-API/releases/download/v$V/hmgapi-mod-sdk-$V.zip"
        mv "$Z.part" "$Z"
    fi
    got="$(shasum -a 256 "$Z" | cut -d' ' -f1)"
    [ "$got" = "$SHA" ] || { echo "sha256 mismatch: $Z ($got)" >&2; rm -f "$Z"; exit 1; }
    rm -rf "$D/sdk" && mkdir -p "$D/sdk" && unzip -q "$Z" -d "$D/sdk"
fi
echo "$D/sdk"
