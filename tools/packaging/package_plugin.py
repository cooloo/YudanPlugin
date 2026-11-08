#!/usr/bin/env python3
"""统一鱼蛋音乐插件打包入口；只在显式 Gradle 打包任务中执行。"""
import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

sys.dont_write_bytecode = True
from class_abi import load_classes
from dex_abi import check_dex
from runtime_contract import is_host_private_class


def digest(data):
    return hashlib.sha256(data).hexdigest()


def run(arguments, **kwargs):
    subprocess.run([str(value) for value in arguments], check=True, **kwargs)


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def properties(path):
    # 此文件是受限 UTF-8 键值格式，保留 Windows 路径中的反斜杠。
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            key, separator, value = line.partition("=")
            if not separator or key.strip() in result:
                raise ValueError("签名属性格式错误或键重复")
            result[key.strip()] = value.strip()
    for key in ("storeFile", "keyAlias", "storePasswordEnv", "keyPasswordEnv"):
        if not result.get(key):
            raise ValueError(f"缺少插件签名配置：{key}")
    for key in ("storePasswordEnv", "keyPasswordEnv"):
        password_file = result.get(key.replace("Env", "File"))
        if password_file:
            password_path = Path(password_file)
            if not password_path.is_absolute():
                password_path = path.parent / password_path
            os.environ[result[key]] = password_path.read_text(encoding="utf-8").strip()
        if not os.environ.get(result[key]):
            raise ValueError(f"缺少签名环境变量：{result[key]}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", required=True)
    args = parser.parse_args()
    request = json.loads(Path(args.request).read_text(encoding="utf-8"))
    root, work, output = (Path(request[key]).resolve() for key in ("root", "work", "output"))
    if not work.is_relative_to(root / "build") or not output.is_relative_to(root / "build"):
        raise ValueError("临时与产物目录必须位于仓库根 build 下")
    manifest = json.loads(Path(request["manifest"]).read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1 or manifest.get("packageKind") != "code" or manifest.get("minAndroidApi") != 28:
        raise ValueError("清单必须声明代码模式、容器版本 1 与最低 API 28")
    if not re.fullmatch(r"[a-z][a-z0-9.-]*", manifest.get("id", "")):
        raise ValueError("无效插件标识")
    if not re.fullmatch(r"[0-9A-Za-z._-]+", manifest.get("versionName", "")):
        raise ValueError("无效插件版本")
    signing = properties(Path(request["signing"]))
    store = Path(signing["storeFile"])
    if not store.is_absolute():
        store = root / store
    if not store.is_file():
        raise ValueError("插件签名密钥不存在")

    paths = [*request["jars"], *request["directories"]]
    spec = importlib.util.spec_from_file_location("check_host_abi", root / "tools/check-host-abi.py")
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    sdk = Path(request["sdk"])
    android = sdk / "platforms/android-34/android.jar"
    abi = checker.check(request["baseline"], paths, manifest, request["classpath"], [str(android)])
    own, _, _ = load_classes(paths)
    entry = manifest["entryClass"].replace(".", "/")
    if entry not in own:
        raise ValueError("清单入口不在本模块类中")
    music = any(item["id"] == "yudan.music" for item in manifest["provides"])
    if music and any(name not in own for name in ("com/egogame/music/plugin/common/StatefulMusicPlugin", "com/paopao/music/nowplaying/NowPlayingScreenKt")):
        raise ValueError("音乐包必须包含自己的公共媒体实现和播放页")
    host = {name: value for name, value in json.loads(Path(request["baseline"]).read_text(encoding="utf-8"))["classes"].items()
            if not name.startswith(("com/egogame/music/", "com/paopao/music/nowplaying/"))
            and not is_host_private_class(name)}

    def implements_contract(name, contract, visited=None):
        visited = set() if visited is None else visited
        if name == contract:
            return True
        if name in visited:
            return False
        visited.add(name)
        definition = own.get(name, host.get(name, {}))
        return any(implements_contract(base, contract, visited) for base in [definition.get("super"), *definition.get("interfaces", [])] if base)

    contract = "com/egogame/plugin/music/api/MusicPlugin" if music else "com/egogame/plugin/api/YdPlugin"
    if not implements_contract(entry, contract):
        raise ValueError("清单入口没有实现公开插件接口")
    if not (own[entry]["access"] & 1) or (own[entry]["access"] & 0x0600) or not (own[entry]["methods"].get("<init>()V", 0) & 1):
        raise ValueError("入口必须是公开可实例化类，并提供公开无参构造")

    # 每次使用独立的 DEX 目录，防止旧的 classesN.dex 混入新包。
    dex = work / "dex"
    if dex.exists():
        shutil.rmtree(dex)
    dex.mkdir(parents=True)
    classes_jar = work / "plugin-classes.jar"
    entries = {}
    for raw in request["jars"]:
        with zipfile.ZipFile(raw) as jar:
            for name in jar.namelist():
                if name.endswith(".class"):
                    data = jar.read(name)
                    if name in entries:
                        raise ValueError(f"本模块类重复：{name}")
                    entries[name] = data
    for raw in request["directories"]:
        directory = Path(raw)
        for path in directory.rglob("*.class"):
            name = path.relative_to(directory).as_posix()
            if name in entries:
                raise ValueError(f"本模块类重复：{name}")
            entries[name] = path.read_bytes()
    with zipfile.ZipFile(classes_jar, "w", zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(entries.items()):
            jar.writestr(name, data)

    d8 = sdk / "build-tools" / request["buildTools"] / "lib/d8.jar"
    command = ["java", "-cp", d8, "com.android.tools.r8.D8", "--release", "--min-api", "28", "--lib", android, "--output", dex]
    classpath = work / "classpath"
    classpath.mkdir(exist_ok=True)
    for index, raw in enumerate(request["classpath"]):
        path = Path(raw)
        if path.suffix == ".aar":
            with zipfile.ZipFile(path) as aar:
                for name in aar.namelist():
                    if name == "classes.jar" or (name.startswith("libs/") and name.endswith(".jar")):
                        target = classpath / f"{index}-{digest(name.encode())[:12]}.jar"
                        target.write_bytes(aar.read(name))
                        command.extend(["--classpath", target])
        else:
            command.extend(["--classpath", path])
    command.append(classes_jar)
    run(command)
    dex_files = sorted(dex.glob("classes*.dex"), key=lambda path: 1 if path.name == "classes.dex" else int(path.stem[7:]))
    expected = ["classes.dex", *[f"classes{i}.dex" for i in range(2, len(dex_files) + 1)]]
    if not dex_files or [path.name for path in dex_files] != expected:
        raise ValueError("DEX 编号必须从 classes.dex 开始且连续")
    boot, _, _ = load_classes([str(android)])
    final_abi = check_dex(dex_files, {**boot, **host})
    payload = {"plugin.json": Path(request["manifest"]).read_bytes(), "icon.png": Path(request["icon"]).read_bytes()}
    payload.update({path.name: path.read_bytes() for path in dex_files})
    license_names = ["LGPL-3.0", "GPL-3.0"]
    for name in license_names:
        payload[f"assets/LICENSE-{name}.txt"] = (root / "docs/licenses" / f"{name}.txt").read_bytes()
    payload["assets/NOTICE.txt"] = (root / "NOTICE").read_bytes()
    payload["assets/source-info.json"] = (json.dumps({"sourceArchive": "YudanPlugin-source.zip",
        "license": "LGPL-3.0",
        "modules": [Path(request["work"]).name, "music-plugin-common", "shared/nowplaying"] if music else ["example-plugin"],
        "note": "分发时一并提供对应源码包；允许修改、使用自有密钥构建并经用户信任导入替换。"}, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    assets = Path(request["assets"])
    if assets.exists():
        for path in sorted(assets.rglob("*")):
            if path.is_symlink():
                raise ValueError("插件资源不允许符号链接")
            if path.is_file():
                name = "assets/" + path.relative_to(assets).as_posix()
                if "\\" in name or any(part in ("", ".", "..") for part in name.split("/")):
                    raise ValueError("插件资源路径无效")
                if name in payload:
                    raise ValueError("插件资源不能覆盖构建保留条目")
                payload[name] = path.read_bytes()
    source = {"schemaVersion": 1, "sdkVersion": "1.0.0", "abi": abi, "dexAbi": final_abi,
              "inputs": {path.name: digest(path.read_bytes()) for path in [classes_jar, Path(request["manifest"])]},
              "classpathArtifacts": [{"name": Path(path).name, "sha256": digest(Path(path).read_bytes())} for path in request["classpath"] if Path(path).is_file()],
              "runtimeRequirements": manifest["runtimeRequirements"], "minAndroidApi": 28, "compileSdk": 34}
    if "assets/yd-build-source.json" in payload:
        raise ValueError("assets/yd-build-source.json 是构建来源保留路径")
    payload["assets/yd-build-source.json"] = (json.dumps(source, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")
    checksums = {"schemaVersion": 1, "files": {name: digest(data) for name, data in sorted(payload.items())}}
    payload["checksums.json"] = (json.dumps(checksums, sort_keys=True, indent=2) + "\n").encode("utf-8")
    unsigned = work / "unsigned.ydplugin"
    with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as package:
        package.writestr("assets/", b"")
        for name, data in sorted(payload.items()):
            package.writestr(name, data)
    signed = work / "signed.ydplugin"
    shutil.copyfile(unsigned, signed)
    run(["jarsigner", "-keystore", store, "-storepass:env", signing["storePasswordEnv"],
         "-keypass:env", signing["keyPasswordEnv"], "-digestalg", "SHA-256", "-sigalg", signing.get("signatureAlgorithm", "SHA256withRSA"),
         signed, signing["keyAlias"]])
    # Java 的验证读取全部有效载荷，检查每项完整签名且只有同一个签名者。
    result = subprocess.run(["java", str(root / "tools/packaging/VerifySignedJar.java"), str(signed)], check=True, capture_output=True, text=True, encoding="utf-8")
    certificate = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ValueError("签名验证没有返回证书 SHA-256")
    output.mkdir(parents=True, exist_ok=True)
    target = output / f"{manifest['id']}-{manifest['versionName']}.ydplugin"
    staging = output / (target.name + ".tmp")
    shutil.copyfile(signed, staging)
    staging.replace(target)
    write_json(output / (target.name + ".build.json"), {**source, "packageSha256": digest(target.read_bytes()), "signerCertificateSha256": certificate})
    write_json(output / (manifest["id"] + ".latest.json"), {
        "file": target.name, "versionName": manifest["versionName"], "versionCode": manifest["versionCode"],
        "packageSha256": digest(target.read_bytes()), "signerCertificateSha256": certificate})
    print(str(target))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        sys.exit(f"插件打包失败：{error}")
