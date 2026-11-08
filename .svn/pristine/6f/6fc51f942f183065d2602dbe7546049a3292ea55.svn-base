"""核对最终 DEX 的共享成员引用及编译器生成类的接口实现。"""
from pathlib import Path
import zipfile

from class_abi import load_classes, resolve
from export_host_abi import dex_classes
from runtime_contract import host_private_dex_types


def check_dex(paths, host):
    from androguard.core.dex import DEX
    from loguru import logger
    logger.disable("androguard")
    own = dex_classes(paths)
    classes = {**host, **own}
    errors = []
    # D8 会把 SAM 转成合成具体类，编译前 class 检查无法覆盖这些实现。
    import importlib.util
    spec = importlib.util.spec_from_file_location("checker", Path(__file__).parents[1] / "check-host-abi.py")
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    for name, definition in own.items():
        if name.startswith(checker.SHARED):
            errors.append(f"最终 DEX 重复包含宿主共享类：{name}")
        if not definition["access"] & 0x600:
            for signature in checker.missing_implementations(classes, name):
                errors.append(f"最终 DEX 具体类缺少宿主接口实现：{name}.{signature}")
    for path in paths:
        dex = DEX(Path(path).read_bytes())
        for name in host_private_dex_types(dex):
            errors.append(f"最终 DEX 禁止引用宿主私有 Material 图标，请使用图标服务或自有资源：{name}")
        for method in dex.get_methods():
            owner = method.get_class_name()[1:-1]
            if owner not in host:
                continue
            signature = method.get_name() + method.get_descriptor().replace(" ", "")
            if resolve(classes, owner, "methods", signature) is None:
                errors.append(f"最终 DEX 缺少宿主成员：{owner}.{signature}")
    if errors:
        raise ValueError("\n".join(sorted(set(errors))))
    return {"classes": len(own)}


def check_hosted_calls(apk, prefixes, bootclasspath=()):
    from androguard.core.dex import DEX
    from loguru import logger
    logger.disable("androguard")
    boot, _, _ = load_classes(bootclasspath)
    host = {**boot, **dex_classes([apk])}
    errors, count = [], 0
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if "/" in name or not name.startswith("classes") or not name.endswith(".dex"):
                continue
            dex = DEX(archive.read(name))
            for cls in dex.get_classes():
                if not cls.get_name()[1:-1].startswith(prefixes):
                    continue
                for method in cls.get_methods():
                    for instruction in method.get_instructions():
                        if instruction.get_op_value() not in (*range(0x6e, 0x73), *range(0x74, 0x79)):
                            continue
                        ref = dex.get_class_manager().get_method_ref(instruction.get_ref_kind())
                        owner = ref.get_class_name()[1:-1]
                        if owner not in host:
                            continue
                        count += 1
                        signature = ref.get_name() + ref.get_descriptor().replace(" ", "")
                        if resolve(host, owner, "methods", signature) is None:
                            errors.append(f"共享运行层最终 DEX 缺少宿主成员：{owner}.{signature}")
    if errors:
        raise ValueError("\n".join(sorted(set(errors))))
    return host, count
