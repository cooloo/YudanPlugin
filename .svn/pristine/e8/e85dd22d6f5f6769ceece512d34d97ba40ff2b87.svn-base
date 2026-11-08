#!/usr/bin/env python3
"""核对宿主最终成员 ABI、运行库声明与资源归属；缺少基线立即失败。"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "packaging"))
from class_abi import load_classes, resolve

FRAMEWORK = ("android/", "java/", "javax/", "dalvik/", "org/json/", "org/w3c/", "org/xml/", "com/android/")
ANNOTATIONS = ("androidx/annotation/", "org/jetbrains/annotations/")
SHARED = ("androidx/", "kotlin/", "kotlinx/", "coil/", "okhttp3/", "okio/", "com/google/", "com/egogame/plugin/")


def access_compatible(actual, contract):
    if (actual & 0x08) != (contract & 0x08):
        return False
    # R8 可将受保护成员扩大为公开，扩大访问范围不破坏原调用。
    return (actual & 0x07) == (contract & 0x07) or bool(actual & 0x01)


def missing_implementations(classes, name):
    required, visited = set(), set()

    def collect(owner):
        if owner not in classes or owner in visited:
            return
        visited.add(owner)
        definition = classes[owner]
        required.update(signature for signature, flags in definition['methods'].items()
                        if flags & 0x400 and not flags & 0x08)
        for parent in [definition.get('super'), *definition.get('interfaces', [])]:
            collect(parent)

    collect(name)
    missing = []
    for signature in required:
        flags = resolve(classes, name, 'methods', signature)
        if flags is None or flags & 0x400:
            missing.append(signature)
    return sorted(missing)


def check(baseline_path, paths, manifest, classpath, bootclasspath=()):
    baseline = json.loads(Path(baseline_path).read_text(encoding="utf-8"))
    if baseline.get("schemaVersion") != 1 or not baseline.get("classes"):
        raise ValueError("宿主成员 ABI 基线为空或格式不支持")
    if not baseline.get("releaseBuildId") or not baseline.get("releaseArtifacts"):
        raise ValueError("ABI 基线缺少宿主 Release 制品身份")
    if baseline.get("resourceOwnership", {}).get("owner") != "host" or not baseline["resourceOwnership"].get("verified"):
        raise ValueError("宿主资源归属未核对")
    for item in manifest["runtimeRequirements"]:
        if baseline.get("runtimeAbis", {}).get(item["id"]) != item["abi"]:
            raise ValueError(f"宿主运行库 ABI 不匹配：{item['id']}")
    own, classes, refs = load_classes(paths)
    expected, _, _ = load_classes([*classpath, *bootclasspath])
    boot, _, _ = load_classes(bootclasspath)
    host = {**boot, **{name: value for name, value in baseline["classes"].items() if not name.startswith(("com/egogame/music/", "com/paopao/music/nowplaying/"))}}
    host_and_own = {**host, **own}
    expected_and_own = {**expected, **own}
    errors = []
    if not own:
        errors.append("本模块类为空")
    for name in own:
        if own[name]["major"] > 52:
            errors.append(f"插件字节码超过 JVM 1.8：{name}")
        if name.startswith(SHARED):
            errors.append(f"插件重复包含共享类：{name}")
        if name.endswith("/R") or "/R$" in name:
            errors.append(f"插件不能携带独立 Android R 类：{name}")
        if not own[name]['access'] & 0x600:
            for signature in missing_implementations(host_and_own, name):
                errors.append(f"插件具体类缺少宿主接口实现：{name}.{signature}")
    for name in sorted(classes - own.keys()):
        if not name.startswith(FRAMEWORK + ANNOTATIONS) and name not in host:
            errors.append(f"缺少宿主类：{name}")
    for ref in refs:
        owner = ref["owner"]
        if owner.startswith(FRAMEWORK + ANNOTATIONS) or owner.startswith("["):
            continue
        if owner in own and ref["name"] + ref["descriptor"] in own[owner][ref["kind"]]:
            continue
        signature = ref["name"] + ref["descriptor"]
        actual = resolve(host_and_own, owner, ref["kind"], signature)
        contract = resolve(expected_and_own, owner, ref["kind"], signature)
        if actual is None or contract is None:
            errors.append(f"缺少宿主或编译契约成员：{owner}.{signature}")
        elif not access_compatible(actual, contract):
            errors.append(f"宿主成员可见性或 static 属性变化：{owner}.{signature}")
        if owner in host and owner in expected and (host[owner]["access"] & 0x0201) != (expected[owner]["access"] & 0x0201):
            errors.append(f"宿主类可见性或接口类型变化：{owner}")
    if errors:
        raise ValueError("\n".join(sorted(set(errors))))
    return {"classes": len(own), "memberReferences": len(refs), "baselineSha256": hashlib.sha256(Path(baseline_path).read_bytes()).hexdigest(), "releaseBuildId": baseline["releaseBuildId"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--classpath", action="append", default=[])
    parser.add_argument("--bootclasspath", action="append", default=[])
    parser.add_argument("jars", nargs="+")
    args = parser.parse_args()
    result = check(args.baseline, args.jars, json.loads(Path(args.manifest).read_text(encoding="utf-8")), args.classpath, args.bootclasspath)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        sys.exit(str(error))
