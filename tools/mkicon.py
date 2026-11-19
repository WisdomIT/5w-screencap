#!/usr/bin/env python3
"""App-menu tile pictures for the mod, in the car's stock style, with no image tools needed.

Same rules as the HMG-API mod SDK's bin/mkicon.sh (which needs ImageMagick): the glyph is
centred on a 99x99 transparent tile in a box of about 58 px, filled with the stock gradient
across the glyph's own box (cyan top left to purple bottom right, darker at night), plus the
flat grey "disabled" looks. Colours are the SDK's (measured from the launcher's own icons).

The glyph: a wide screen (the head unit's 8:3) with a record dot in it.

  tools/mkicon.py <out dir>            icon.png, icon_night.png, icon_disabled.png, icon_disabled_night.png
  tools/mkicon.py <out dir> --app N    also app_icon.png, N x N, the day tile scaled (the APK's own icon)
"""
import struct
import sys
import zlib
from pathlib import Path

TILE = 99
SS = 4  # supersampling per axis


def glyph(x, y):
    """1 inside the glyph, 0 outside; coordinates in tile pixels (0..99)."""
    # the screen: a rounded-rectangle outline, 60 x 40, centred
    l, t, r, b, rad, stroke = 19.5, 29.5, 79.5, 69.5, 7.0, 5.0
    def in_rrect(x, y, l, t, r, b, rad):
        if x < l or x > r or y < t or y > b:
            return False
        cx = min(max(x, l + rad), r - rad)
        cy = min(max(y, t + rad), b - rad)
        return (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad
    outline = in_rrect(x, y, l, t, r, b, rad) and not in_rrect(x, y, l + stroke, t + stroke, r - stroke, b - stroke, rad - stroke)
    # the record dot
    dot = (x - 49.5) ** 2 + (y - 49.5) ** 2 <= 9.0 ** 2
    return 1.0 if outline or dot else 0.0


def mask():
    m = [[0.0] * TILE for _ in range(TILE)]
    for py in range(TILE):
        for px in range(TILE):
            s = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    s += glyph(px + (sx + 0.5) / SS, py + (sy + 0.5) / SS)
            m[py][px] = s / (SS * SS)
    return m


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def box(m):
    xs = [x for y in range(TILE) for x in range(TILE) if m[y][x] >= 0.5]
    ys = [y for y in range(TILE) for x in range(TILE) if m[y][x] >= 0.5]
    return min(xs), min(ys), max(xs), max(ys)


def gradient(m, tl, tr, bl, br):
    x0, y0, x1, y1 = box(m)
    tl, tr, bl, br = map(hexrgb, (tl, tr, bl, br))
    rows = []
    for y in range(TILE):
        row = []
        v = min(max((y - y0) / max(1, y1 - y0), 0.0), 1.0)
        for x in range(TILE):
            u = min(max((x - x0) / max(1, x1 - x0), 0.0), 1.0)
            c = [round(tl[i] * (1 - u) * (1 - v) + tr[i] * u * (1 - v) + bl[i] * (1 - u) * v + br[i] * u * v) for i in range(3)]
            row.append((*c, round(255 * m[y][x])))
        rows.append(row)
    return rows


def flat(m, opacity):
    c = hexrgb("#62797f")
    return [[(*c, round(255 * m[y][x] * opacity)) for x in range(TILE)] for y in range(TILE)]


def scale(rows, n):
    """box-filter resample of an RGBA picture to n x n (premultiplied, so edges stay clean)"""
    w = len(rows)
    out = []
    for oy in range(n):
        row = []
        for ox in range(n):
            ys, ye = oy * w / n, (oy + 1) * w / n
            xs, xe = ox * w / n, (ox + 1) * w / n
            acc = [0.0, 0.0, 0.0, 0.0]
            area = 0.0
            for y in range(int(ys), min(w, int(ye + 0.999))):
                wy = min(ye, y + 1) - max(ys, y)
                for x in range(int(xs), min(w, int(xe + 0.999))):
                    wx = min(xe, x + 1) - max(xs, x)
                    a = wx * wy
                    r, g, b, al = rows[y][x]
                    acc[0] += r * al * a
                    acc[1] += g * al * a
                    acc[2] += b * al * a
                    acc[3] += al * a
                    area += a
            al = acc[3] / area if area else 0
            row.append(tuple(round(acc[i] / acc[3]) if acc[3] else 0 for i in range(3)) + (round(al),))
        out.append(row)
    return out


def png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    m = mask()
    day = gradient(m, "#23e4c5", "#32b6ba", "#758cd7", "#8763cc")
    png(out / "icon.png", day)
    png(out / "icon_night.png", gradient(m, "#2ab8aa", "#2d9aa3", "#4c61b4", "#4d3fae"))
    png(out / "icon_disabled.png", flat(m, 0.2))
    png(out / "icon_disabled_night.png", flat(m, 0.4))
    if "--app" in sys.argv:
        n = int(sys.argv[sys.argv.index("--app") + 1])
        png(out / "app_icon.png", scale(day, n))


if __name__ == "__main__":
    main()
