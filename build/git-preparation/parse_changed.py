"""仅进行语法解析和交付元数据核对，不编译、不执行单元测试。"""
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root.parent / "YuDanLauncher/build/plugin-background-validation/deps"))
sys.path.insert(0, str(root / "build/git-preparation/deps"))
from tree_sitter import Language, Parser
import tree_sitter_groovy
import tree_sitter_kotlin

errors = []
notes = []
for relative, language in [
    ("gradle/yd-plugin-library.gradle", tree_sitter_groovy),
    ("kugoumusic/build.gradle.kts", tree_sitter_kotlin),
    ("kugoumusic/src/main/java/com/kugoumusic/car/api/KuGouLoginApi.kt", tree_sitter_kotlin),
]:
    parser = Parser(Language(language.language()))

    def diagnostics(source):
        result, pending = [], [parser.parse(source).root_node]
        while pending:
            node = pending.pop()
            if node.is_error or node.is_missing:
                result.append((node.type, node.start_point.row + 1, node.start_point.column))
            elif node.has_error:
                pending.extend(node.children)
        return sorted(result)

    source = (root / relative).read_bytes()
    current = diagnostics(source)
    if relative.endswith(".gradle"):
        original = subprocess.run(["svn", "cat", relative], cwd=root, check=True, capture_output=True).stdout
        declaration = next(line.strip() for line in source.splitlines() if b"licenses {" in line)
        if current == diagnostics(original) and not diagnostics(declaration):
            notes.append("Groovy 许可声明单独解析通过；完整 Gradle DSL 的三处既有解析限制与原文件相同。")
            current = []
    for kind, line, column in current:
        errors.append({"path": relative, "line": line, "category": "语法解析异常"})

lgpl = (root / "docs/licenses/LGPL-3.0.txt").read_text(encoding="utf-8").strip()
for relative in ["LICENSE", *[f"yd-plugin-{name}/LICENSE" for name in ["api", "runtime", "ui", "music-api", "vehicle-model-api"]], "example-plugin/LICENSE"]:
    if (root / relative).read_text(encoding="utf-8").strip() != lgpl:
        errors.append({"path": relative, "category": "许可正文不一致"})
if (root / "COPYING.GPL").read_bytes() != (root / "docs/licenses/GPL-3.0.txt").read_bytes():
    errors.append({"path": "COPYING.GPL", "category": "GPL 正文不一致"})

baseline = json.loads((root / "docs/runtime/host-abi.json").read_text(encoding="utf-8"))
evidence = (root / baseline["resourceOwnership"]["evidence"]).read_text(encoding="utf-8")
tokens = re.findall(r"dimen/(hmi_music_plugin_text_\d+)", evidence)
if len(tokens) != 89 or set(tokens) != set(baseline["resourceOwnership"]["publicDimensionTokens"]):
    errors.append({"category": "公开资源摘录与 ABI 令牌不一致"})
original = root / "docs/runtime/host-resources-20261102.txt"
if hashlib.sha256(original.read_bytes()).hexdigest() not in evidence:
    errors.append({"category": "原始资源转储哈希不一致"})

print(json.dumps({"语法及交付元数据问题": errors, "公共资源令牌": len(tokens), "解析边界": notes}, ensure_ascii=False, indent=2))
raise SystemExit(bool(errors))
