"""校验三个新包，整理带源码及商店元数据的本地交付目录。"""
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
work = Path(__file__).resolve().parent
delivery = root / "build/distribution/music-plugins-20261102"
delivery.mkdir(parents=True, exist_ok=True)
expected_certificate = hashlib.sha256((root / "docs/official-plugin-certificate.der").read_bytes()).hexdigest()
baseline = json.loads((work / "host-abi.json").read_text(encoding="utf-8"))
baseline["resourceOwnership"]["evidence"] = "docs/runtime/host-resources-20261102.txt"
(root / "docs/runtime/host-abi.json").write_text(json.dumps(baseline, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
shutil.copyfile(work / "host-resources.txt", root / "docs/runtime/host-resources-20261102.txt")
items = []
for plugin_id, version, code in [("netease", "1.0.20", 21), ("qqmusic", "1.0.22", 23), ("kugoumusic", "1.0.19", 20)]:
    package = root / "build/music-plugins" / f"{plugin_id}-{version}.ydplugin"
    with zipfile.ZipFile(package) as archive:
        manifest = json.loads(archive.read("plugin.json"))
        assert (manifest["id"], manifest["versionName"], manifest["versionCode"]) == (plugin_id, version, code)
        runtimes = {entry["id"]: entry["abi"] for entry in manifest["runtimeRequirements"]}
        assert runtimes["androidx.compose"] == "1.6.1-host-icons-v1"
        checksums = json.loads(archive.read("checksums.json"))["files"]
        for name, checksum in checksums.items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == checksum, name
    verification = subprocess.run(["java", str(root / "tools/packaging/VerifySignedJar.java"), str(package)],
                                  check=True, capture_output=True, text=True, encoding="utf-8")
    assert verification.stdout.strip() == expected_certificate
    report = json.loads(package.with_name(package.name + ".build.json").read_text(encoding="utf-8"))
    checksum = hashlib.sha256(package.read_bytes()).hexdigest()
    assert checksum == report["packageSha256"]
    assert report["signerCertificateSha256"] == expected_certificate
    assert report["abi"]["releaseBuildId"] == baseline["releaseBuildId"]
    shutil.copyfile(package, delivery / package.name)
    items.append({"id": plugin_id, "name": manifest["name"], "fileName": package.name,
                  "versionName": version, "versionCode": code, "sizeBytes": package.stat().st_size,
                  "sha256": checksum, "signerCertificateSha256": expected_certificate})
subprocess.run([sys.executable, str(root / "tools/package-source.py")], cwd=root, check=True)
source = root / "build/distribution/YudanPlugin-source.zip"
with zipfile.ZipFile(source) as archive:
    for name in archive.namelist():
        path = Path(name)
        assert not {".svn", ".git", ".local", "build", ".gradle"}.intersection(path.parts), name
        assert path.suffix not in {".p12", ".jks", ".keystore"}, name
        assert path.name not in {"password.txt", "local.properties"}, name
shutil.copyfile(source, delivery / source.name)
summary = {"hostBuildId": baseline["releaseBuildId"], "hostBuildType": "debug",
           "composeAbi": "1.6.1-host-icons-v1", "plugins": items,
           "sourceArchive": {"fileName": source.name, "sha256": hashlib.sha256(source.read_bytes()).hexdigest()}}
(delivery / "上传信息.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(delivery / "说明.txt").write_text(
    "本目录三个 ydplugin 为本次新包，沿用 egogame 插件签名。\n"
    "将三个包上传到 Alist，在商店同步下载地址、版本、文件大小和 SHA-256，具体值见上传信息.json。\n"
    "音乐插件分发时一并提供对应 YudanPlugin-source.zip。\n"
    "本次完成编译、宿主成员 ABI、最终 DEX、签名和条目哈希校验；未安装插件或验证登录播放。\n"
    "基线来自当前 MuMu 的鱼蛋桌面 2.0.23 Debug APK，不表示其他 Release 制品已经验收。\n", encoding="utf-8")
print(json.dumps(summary, ensure_ascii=False, indent=2))
print("交付目录：" + str(delivery))
