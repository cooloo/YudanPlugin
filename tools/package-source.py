#!/usr/bin/env python3
"""生成可与插件一起交付的完整公开源码归档，排除密钥、私有配置和临时产物。"""
import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from public_source import public_files

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, default=root / "build/distribution")
args = parser.parse_args()
output = args.output.resolve()
if not output.is_relative_to(root / "build"):
    raise ValueError("源码归档输出必须位于仓库根 build 下")
output.mkdir(parents=True, exist_ok=True)
archive = output / "YudanPlugin-source.zip"
files = []
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as target:
    for path in sorted(public_files(root)):
        relative = path.relative_to(root)
        data = path.read_bytes()
        name = relative.as_posix()
        entry = zipfile.ZipInfo("YudanPlugin/" + name, (2026, 10, 31, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = (0o100755 if name == "gradlew" or name.endswith(".sh") else 0o100644) << 16
        target.writestr(entry, data)
        files.append({"path": name, "sha256": hashlib.sha256(data).hexdigest()})
report = {"archive": archive.name, "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(), "files": files}
(output / "source-manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
delivery = output / "music-plugins"
delivery.mkdir(parents=True, exist_ok=True)
for name in (archive.name, "source-manifest.json"):
    shutil.copyfile(output / name, delivery / name)
print(f"公开源码 {len(files)} 个文件；SHA-256：{report['sha256']}")
