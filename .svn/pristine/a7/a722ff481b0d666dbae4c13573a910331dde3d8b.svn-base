#!/usr/bin/env python3
"""从已核对的最终宿主基线提取公共运行库，保留制品身份和原始成员描述符。"""
import argparse
import hashlib
import json
from pathlib import Path

PUBLIC = ("androidx/", "kotlin/", "kotlinx/", "coil/", "okhttp3/", "okio/", "com/google/", "com/egogame/plugin/")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    source = Path(args.input).read_bytes()
    baseline = json.loads(source)
    if baseline.get("schemaVersion") != 1 or not baseline.get("releaseArtifacts"):
        raise ValueError("缺少最终宿主制品身份")
    baseline["classes"] = {name: value for name, value in baseline["classes"].items() if name.startswith(PUBLIC)}
    for name in ("yudan.music.common", "yudan.music.nowplaying"):
        baseline["runtimeAbis"].pop(name, None)
    baseline["publicContract"] = {"sourceBaselineSha256": hashlib.sha256(source).hexdigest(),
                                  "includedPrefixes": list(PUBLIC),
                                  "pluginOwnedPrefixes": ["com/egogame/music/", "com/paopao/music/nowplaying/"],
                                  "note": "只保留宿主共享运行库；音乐实现由插件自带；来源制品哈希保留，换宿主后须重新导出核对。"}
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(baseline, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
