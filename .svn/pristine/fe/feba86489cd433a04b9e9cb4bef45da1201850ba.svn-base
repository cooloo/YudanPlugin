#!/usr/bin/env python3
"""从已核对的最终宿主基线提取公共运行库，保留制品身份和原始成员描述符。"""
import argparse
import hashlib
import json
from pathlib import Path

from runtime_contract import COMPOSE_ABI, HOST_PRIVATE_PREFIXES, is_host_private_class

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
    baseline["classes"] = {name: value for name, value in baseline["classes"].items()
                           if name.startswith(PUBLIC) and not is_host_private_class(name)}
    for name in ("yudan.music.common", "yudan.music.nowplaying"):
        baseline["runtimeAbis"].pop(name, None)
    baseline["publicContract"] = {"sourceBaselineSha256": hashlib.sha256(source).hexdigest(),
                                  "includedPrefixes": list(PUBLIC),
                                  "excludedPrefixes": list(HOST_PRIVATE_PREFIXES),
                                  "requiredComposeAbi": COMPOSE_ABI,
                                  "pluginOwnedPrefixes": ["com/egogame/music/", "com/paopao/music/nowplaying/"],
                                  "note": "排除宿主私有 Material 图标；插件使用图标服务或自有资源。保留来源制品身份与运行库声明，未将旧基线改写为新宿主证明；运行库契约不符时须从实际新 Release 重新导出。"}
    target = Path(args.output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(baseline, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
