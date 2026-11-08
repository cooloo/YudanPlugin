#!/usr/bin/env python3
"""从宿主最终保留的 class 制品导出成员清单，不用 keep 类名冒充成员核对。"""
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True
from class_abi import load_classes


def dex_classes(paths):
    try:
        from androguard.core.dex import DEX
        from loguru import logger
        logger.disable("androguard")
    except ImportError as error:
        raise ValueError("解析最终 APK 需先安装 tools/packaging/requirements-abi.txt 中的依赖") from error

    def class_name(value):
        return value[1:-1] if value and value.startswith("L") and value.endswith(";") else value

    result = {}
    for raw in paths:
        path = Path(raw)
        if path.suffix == ".dex":
            entries = [path.read_bytes()]
        elif path.suffix == ".apk":
            with zipfile.ZipFile(path) as apk:
                names = sorted(name for name in apk.namelist() if name.startswith("classes") and name.endswith(".dex") and "/" not in name)
                if not names:
                    raise ValueError("最终宿主 APK 不含 DEX")
                entries = [apk.read(name) for name in names]
        else:
            raise ValueError("最终 DEX 基线输入不能混用 APK/DEX 与编译前类文件")
        for data in entries:
            for cls in DEX(data).get_classes():
                name = class_name(cls.get_name())
                if name in result:
                    raise ValueError(f"最终宿主含重复类：{name}")
                result[name] = {
                    "access": cls.get_access_flags(), "super": class_name(cls.get_superclassname()),
                    "interfaces": [class_name(item) for item in cls.get_interfaces()],
                    "fields": {item.get_name() + item.get_descriptor().replace(" ", ""): item.get_access_flags() for item in cls.get_fields()},
                    "methods": {item.get_name() + item.get_descriptor().replace(" ", ""): item.get_access_flags() for item in cls.get_methods()},
                    "major": 52,
                }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", required=True, help="人工核对的 Release 身份、运行库和资源归属 JSON")
    parser.add_argument("--output", required=True)
    parser.add_argument("artifacts", nargs="+")
    args = parser.parse_args()
    metadata = json.loads(Path(args.metadata).read_text(encoding="utf-8"))
    if not metadata.get("releaseBuildId") or not metadata.get("runtimeAbis"):
        raise ValueError("缺少 Release 身份或运行库版本")
    if metadata.get("resourceOwnership", {}).get("owner") != "host" or not metadata["resourceOwnership"].get("verified"):
        raise ValueError("须先核对共享资源在宿主 Release 中唯一存在")
    if any(Path(raw).suffix in (".apk", ".dex") for raw in args.artifacts):
        classes = dex_classes(args.artifacts)
    else:
        classes, _, _ = load_classes(args.artifacts)
    artifacts = []
    for raw in args.artifacts:
        path = Path(raw)
        if not path.is_file():
            raise ValueError("基线只接受可计算完整哈希的最终 APK、DEX 或类制品")
        artifacts.append({"name": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    metadata.update(schemaVersion=1, releaseArtifacts=artifacts, classes=classes)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(metadata, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile) as error:
        sys.exit(str(error))
