#!/usr/bin/env python3
"""检查公开文件中的凭据与宿主私有依赖，只记录位置和类别，不输出凭据值。"""
import argparse
import hashlib
import json
import re
from pathlib import Path

from public_source import public_files

PROTOCOL_CONSTANTS = {
    ("kugoumusic/src/main/java/com/kugoumusic/car/api/KuGouLoginApi.kt", "WX_SECRET"),
    ("app/src/main/java/com/cloudmusic/car/api/NeteaseCrypto.kt", "WEAPI_SECRET_KEY"),
    ("app/src/main/java/com/cloudmusic/car/api/NeteaseCrypto.kt", "EAPI_KEY"),
    ("kugoumusic/src/main/java/com/kugoumusic/car/api/KuGouMusicClient.kt", "TOKEN_P3_KEY"),
    ("kugoumusic/src/main/java/com/kugoumusic/car/api/KuGouMusicClient.kt", "TOKEN_P3_IV"),
}
CREDENTIAL = re.compile(r'''(?i)\b([\w.]*(?:password|passwd|secret|token|cookie|api[_-]?key|access[_-]?key|music[_-]?key)[\w.]*)\s*(?:=|:|to)\s*["']([^"'\n]{4,})["']''')
RULES = {
    "私钥正文": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "访问令牌": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16}|sk-(?:proj-)?[A-Za-z0-9_-]{24,})\b"),
    "个人绝对路径": re.compile(r"(?i)(?:[a-z]:[/\\](?:users|felix)[/\\]|[/]Users[/]|[/]home[/])[^\s\"']+"),
    "宿主私有实现引用": re.compile(r"\bcom[./]egogame[./](?:egolauncher|yudansoundbrick)[./]"),
    "实际局域网地址": re.compile(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"),
}


def local_secrets(root):
    values = set()
    for path in (root / ".local").rglob("*"):
        if not path.is_file() or path.is_symlink():
            continue
        if path.name == "password.txt":
            value = path.read_text(encoding="utf-8").strip()
            if len(value) >= 16:
                values.add(value.encode("utf-8"))
    return values


def audit(root, private_root=None):
    errors, reviewed, inventory = [], [], []
    secrets = local_secrets(private_root or root)
    for path in sorted(public_files(root)):
        name = path.relative_to(root).as_posix()
        data = path.read_bytes()
        inventory.append({"path": name, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)})
        if any(value in data for value in secrets):
            errors.append({"path": name, "category": "包含本地真实凭据"})
        try:
            text = data.decode("utf-8")
        except UnicodeError:
            if name not in {"gradle/wrapper/gradle-wrapper.jar", "docs/official-plugin-certificate.der"} and path.suffix != ".png":
                errors.append({"path": name, "category": "未经核对的二进制文件"})
            continue
        for number, line in enumerate(text.splitlines(), 1):
            for match in CREDENTIAL.finditer(line):
                symbol = match.group(1)
                item = {"path": name, "line": number, "symbol": symbol}
                if (name, symbol) in PROTOCOL_CONSTANTS:
                    reviewed.append({**item, "category": "已核对的上游协议常量"})
                else:
                    errors.append({**item, "category": "待核对的凭据字面量"})
            for category, rule in RULES.items():
                if not rule.search(line):
                    continue
                item = {"path": name, "line": number, "category": category}
                if category == "实际局域网地址" and "/src/test/" in name and all(
                    match.group() in {".".join(("10", "0", "0", last)) for last in ("1", "2")}
                    for match in rule.finditer(line)
                ):
                    reviewed.append({**item, "category": "已核对的通用测试地址"})
                else:
                    errors.append(item)
    baseline = json.loads((root / "docs/runtime/host-abi.json").read_text(encoding="utf-8"))
    public = tuple(baseline["publicContract"]["includedPrefixes"])
    excluded = tuple(baseline["publicContract"]["excludedPrefixes"])
    if any(not name.startswith(public) or name.startswith(excluded) for name in baseline["classes"]):
        errors.append({"path": "docs/runtime/host-abi.json", "category": "ABI 含公开范围以外的类"})
    evidence = baseline["resourceOwnership"]["evidence"]
    if evidence.startswith("/") or ".." in Path(evidence).parts or not (root / evidence).is_file():
        errors.append({"path": "docs/runtime/host-abi.json", "category": "公开资源证据不可达"})
    return {"files": inventory, "errors": errors, "reviewed": reviewed, "localSecretCount": len(secrets),
            "publicAbiClassCount": len(baseline["classes"])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--private-root", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    report = audit(root, args.private_root)
    output = root / "build/verification/public-source-audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"公开文件 {len(report['files'])} 个，阻断项 {len(report['errors'])} 个，已核对项 {len(report['reviewed'])} 个。")
    if report["errors"]:
        raise SystemExit(json.dumps(report["errors"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
