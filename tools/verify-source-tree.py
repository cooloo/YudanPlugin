#!/usr/bin/env python3
"""核对公开源文件的 UTF-8、替换字符及可解析脚本，并记录无私钥文件清单。"""
import ast
import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

from public_source import public_files

root = Path(__file__).resolve().parents[1]
text_extensions = {".java", ".kt", ".kts", ".groovy", ".gradle", ".xml", ".json", ".md", ".properties", ".py", ".txt", ".sh"}
errors, inventory = [], []
for path in sorted(public_files(root)):
    relative = path.relative_to(root)
    data = path.read_bytes()
    inventory.append({"path": relative.as_posix(), "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)})
    if path.suffix not in text_extensions and path.name not in {"LICENSE", "COPYING.GPL", "NOTICE", "gradlew", ".gitignore", ".gitattributes"}:
        continue
    try:
        text = data.decode("utf-8", errors="strict")
        if text.startswith("\ufeff"):
            raise ValueError("公开文本必须使用无 BOM 的 UTF-8")
        if any(marker in text for marker in ("\ufffd", "\u951f\u65a4\u62f7", "\u70eb\u70eb\u70eb")):
            raise ValueError("发现替换字符或常见乱码")
        if path.suffix == ".py":
            ast.parse(text)
        elif path.suffix == ".json":
            json.loads(text)
        elif path.suffix == ".xml":
            ET.fromstring(text)
    except (UnicodeError, SyntaxError, ValueError, ET.ParseError) as error:
        errors.append({"path": relative.as_posix(), "error": str(error)})
output = root / "build/verification/source-inventory.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({"files": inventory, "errors": errors}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"核对公开文件 {len(inventory)} 个，编码或解析错误 {len(errors)} 个。")
if errors:
    raise SystemExit(json.dumps(errors, ensure_ascii=False))
