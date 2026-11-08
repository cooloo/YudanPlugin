#!/usr/bin/env python3
"""生成独立示例的原创位图图标，不依赖音乐素材或第三方图形库。"""
from pathlib import Path
import struct
import zlib


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


size = 64
pixels = bytearray()
for y in range(size):
    pixels.append(0)
    for x in range(size):
        foreground = 17 <= x < 24 and 16 <= y < 48 or 17 <= x < 47 and any(a <= y < a + 7 for a in (16, 29, 41))
        pixels.extend((250, 252, 255, 255) if foreground else (30, 112, 97, 255))
data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b"")
path = Path(__file__).resolve().parents[1] / "example-plugin/icon.png"
path.write_bytes(data)
