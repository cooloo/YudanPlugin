"""只输出位置和分类，避免在检查日志中泄露凭据原文。"""
import collections
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
excluded = {"build", ".gradle", ".kotlin", ".git", ".svn", ".local", ".idea", "__pycache__"}
rules = {
    "凭据字面量": re.compile(r'''(?i)(?:password|passwd|secret|token|cookie|api[_-]?key|access[_-]?key|music[_-]?key|app[_-]?secret)[\w.]*\s*(?:=|:|to)\s*["']([^"'\n]{4,})["']'''),
    "私钥标记": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "令牌格式": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16}|sk-(?:proj-)?[A-Za-z0-9_-]{24,})\b"),
    "个人路径": re.compile(r"(?i)(?:[a-z]:[/\\](?:users|felix)[/\\]|/Users/|/home/)[^\s\"']+"),
    "局域网地址": re.compile(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"),
}
findings = []
hosts = collections.Counter()
for path in root.rglob("*"):
    relative = path.relative_to(root)
    if not path.is_file() or any(part in excluded for part in relative.parts) or path.name == "local.properties":
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeError:
        continue
    if relative.as_posix() == "docs/runtime/host-abi.json":
        value = json.loads(text)
        print("ABI 顶层字段：", list(value))
        print("ABI 非类元数据：", json.dumps({key: item for key, item in value.items() if key != "classes"}, ensure_ascii=False)[:6000])
        counts = collections.Counter(name.split("/")[0:2][0] for name in value["classes"])
        print("ABI 类数：", len(value["classes"]), dict(counts))
        continue
    for number, line in enumerate(text.splitlines(), 1):
        for label, rule in rules.items():
            if rule.search(line):
                findings.append({"path": relative.as_posix(), "line": number, "category": label})
    for host in re.findall(r"https?://([^/\s\"'<>]+)", text):
        hosts[host] += 1
report = {"findings": findings, "hosts": dict(hosts)}
target = root / "build/git-preparation/inspection.json"
target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False, indent=2))
