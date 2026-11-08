"""核对当前模拟器宿主资源，再从实际 APK 导出本次打包基线。"""
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
work = Path(__file__).resolve().parent
apk = Path("E:/felix/work/YuDanLauncher/build/plugin-download-diagnosis/installed-host.apk")
baseline = json.loads((root / "docs/runtime/host-abi.json").read_text(encoding="utf-8"))
dump = subprocess.run([
    "D:/tools/android-sdk/build-tools/34.0.0/aapt2.exe", "dump", "resources", str(apk)
], check=True, capture_output=True).stdout
(work / "host-resources.txt").write_bytes(dump)
resources = dump.decode("utf-8", errors="strict")
tokens = [f"hmi_music_plugin_text_{size}" for size in range(8, 97)]
for token in tokens:
    if not re.search(r"\bdimen/" + token + r"\s", resources):
        raise ValueError(f"实际宿主缺少公开字号：{token}")
with zipfile.ZipFile(apk) as archive:
    if archive.namelist().count("resources.arsc") != 1:
        raise ValueError("宿主资源表不唯一")
    resource_hash = hashlib.sha256(archive.read("resources.arsc")).hexdigest()
    if not any(b"1.6.1-host-icons-v1" in archive.read(name)
               for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)):
        raise ValueError("实际宿主未提供新版运行库标识")
apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
runtimes = dict(baseline["runtimeAbis"])
runtimes["androidx.compose"] = "1.6.1-host-icons-v1"
metadata = {
    "releaseBuildId": f"mumu-installed-normal-debug-2.0.23-{apk_hash[:16]}",
    "buildType": "debug",
    "verificationScope": "来自用户当前 MuMu 已安装桌面的实际 APK；不表示其他 Release 制品已经验收。",
    "runtimeAbis": runtimes,
    "resourceOwnership": {
        "owner": "host", "verified": True,
        "evidence": "当前宿主 aapt2 资源表核对；原始输出在 build/plugin-refresh-20261102/host-resources.txt",
        "resourceTableSha256": resource_hash,
        "publicDimensionTokens": tokens,
        "verification": "实际 APK 中资源表唯一，89 个公开字号全部存在；插件独立 R 类由打包检查器拒绝。"
    }
}
(work / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
subprocess.run([sys.executable, str(root / "tools/packaging/export_host_abi.py"),
                "--metadata", str(work / "metadata.json"), "--output", str(work / "full.json"), str(apk)], check=True)
subprocess.run([sys.executable, str(root / "tools/packaging/prepare_public_abi.py"),
                "--input", str(work / "full.json"), "--output", str(work / "host-abi.json")], check=True)
print("实际宿主基线已导出：" + apk_hash)
