"""核对源码归档、实际 Git 文件范围、忽略规则及未发布状态。"""
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
delivery = root / "build/git-preparation/distribution"
checkout = root / "build/git-preparation/upload/YudanPlugin"
report = json.loads((delivery / "source-manifest.json").read_text(encoding="utf-8"))
archive = delivery / report["archive"]
assert hashlib.sha256(archive.read_bytes()).hexdigest() == report["sha256"], "归档哈希不符"
expected = {item["path"]: item["sha256"] for item in report["files"]}
with zipfile.ZipFile(archive) as source:
    assert set(source.namelist()) == {"YudanPlugin/" + name for name in expected}, "归档文件范围不符"
    for name, digest in expected.items():
        data = source.read("YudanPlugin/" + name)
        assert hashlib.sha256(data).hexdigest() == digest, "归档文件哈希不符"
        assert (checkout / name).read_bytes() == data, "Git 导出与归档不一致"
        assert (root / name).read_bytes() == data, "维护目录已变化，需要重新导出"


def git(*args, **kwargs):
    return subprocess.run(["git", "-C", str(checkout), *args], capture_output=True, check=True, **kwargs).stdout


actual = set(git("ls-files", "--others", "--exclude-standard", "-z").decode("utf-8").rstrip("\0").split("\0"))
assert actual == set(expected), "Git 实际待加入文件与归档不一致"
private_paths = [".local/signing/plugin-signing.p12", ".local/signing/password.txt", "local.properties", ".svn/wc.db",
                 "build/cache.txt", "example.keystore", ".env", "docs/runtime/host-resources-20261102.txt",
                 "docs/公开发布检查.md", "docs/构建记录.md", "docs/音乐插件后台任务调整.md"]
ignored = git("check-ignore", "--stdin", "-z", input=("\0".join(private_paths) + "\0").encode("utf-8")).decode("utf-8").rstrip("\0").split("\0")
assert set(ignored) == set(private_paths), "Git 忽略规则不完整"
assert all(not (checkout / name).exists() for name in private_paths), "导出目录不应保留内部文件"
assert not git("ls-files", "-z"), "准备目录不应自动暂存"
assert not git("remote"), "准备目录不应自动配置远程"
assert not list((checkout / ".git/refs").rglob("main")), "准备目录不应自动创建提交"
result = {"公开文件数": len(expected), "源码归档SHA256": report["sha256"], "归档与Git目录逐文件一致": True,
          "Git忽略规则已核对": len(private_paths), "提交数": 0, "已配置远程": False}
(root / "build/git-preparation/export-verification.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
