"""直接解析 JVM 字节码中的类、继承、成员和链接引用，不执行插件代码。"""
import io
import re
import struct
import zipfile
from pathlib import Path


class Reader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def read(self, size):
        result = self.stream.read(size)
        if len(result) != size:
            raise ValueError("截断的 class 文件")
        return result

    def u1(self):
        return self.read(1)[0]

    def u2(self):
        return struct.unpack(">H", self.read(2))[0]

    def u4(self):
        return struct.unpack(">I", self.read(4))[0]


def parse_class(data):
    r = Reader(data)
    if r.u4() != 0xCAFEBABE:
        raise ValueError("无效 class 文件")
    r.u2()
    major = r.u2()
    cp = [None] * r.u2()
    index = 1
    while index < len(cp):
        tag = r.u1()
        if tag == 1:
            cp[index] = (tag, r.read(r.u2()).replace(b"\xc0\x80", b"\x00").decode("utf-8", errors="surrogatepass"))
        elif tag in (3, 4):
            cp[index] = (tag, r.read(4))
        elif tag in (5, 6):
            cp[index] = (tag, r.read(8))
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[index] = (tag, r.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[index] = (tag, r.u2(), r.u2())
        elif tag == 15:
            cp[index] = (tag, r.u1(), r.u2())
        else:
            raise ValueError(f"未知常量池类型：{tag}")
        index += 1

    def text(index):
        return cp[index][1]

    def classname(index):
        return text(cp[index][1]) if index else None

    access, own, parent = r.u2(), classname(r.u2()), classname(r.u2())
    interfaces = [classname(r.u2()) for _ in range(r.u2())]

    def attributes():
        for _ in range(r.u2()):
            r.u2()
            r.read(r.u4())

    classes, refs = set(), []

    def members():
        result = {}
        for _ in range(r.u2()):
            flags, name, descriptor = r.u2(), text(r.u2()), text(r.u2())
            classes.update(re.findall(r"L([^;]+);", descriptor))
            result[name + descriptor] = flags
            attributes()
        return result

    fields, methods = members(), members()
    attributes()
    for entry in cp:
        if not entry:
            continue
        if entry[0] == 7:
            name = text(entry[1])
            classes.update(re.findall(r"L([^;]+);", name) if name.startswith("[") else [name])
        elif entry[0] in (9, 10, 11):
            name_type = cp[entry[2]]
            name, descriptor = text(name_type[1]), text(name_type[2])
            classes.update(re.findall(r"L([^;]+);", descriptor))
            refs.append({"owner": classname(entry[1]), "name": name, "descriptor": descriptor,
                         "kind": "fields" if entry[0] == 9 else "methods", "interface": entry[0] == 11})
        elif entry[0] in (12, 16):
            descriptor = text(entry[2] if entry[0] == 12 else entry[1])
            classes.update(re.findall(r"L([^;]+);", descriptor))
    return own, {"access": access, "super": parent, "interfaces": interfaces,
                 "fields": fields, "methods": methods, "major": major}, classes, refs


def class_bytes(paths):
    for raw in paths:
        path = Path(raw)
        if path.is_dir():
            for item in sorted(path.rglob("*.class")):
                yield item.read_bytes()
        elif path.suffix in (".jar", ".zip"):
            with zipfile.ZipFile(path) as jar:
                for name in jar.namelist():
                    if name.endswith(".class") and not name.startswith("META-INF/versions/"):
                        yield jar.read(name)
        elif path.suffix == ".aar":
            with zipfile.ZipFile(path) as aar:
                for name in aar.namelist():
                    if name == "classes.jar" or (name.startswith("libs/") and name.endswith(".jar")):
                        with zipfile.ZipFile(io.BytesIO(aar.read(name))) as jar:
                            for item in jar.namelist():
                                if item.endswith(".class"):
                                    yield jar.read(item)
        else:
            raise ValueError(f"不支持的类制品：{path}")


def load_classes(paths):
    classes, references, class_refs = {}, [], set()
    for data in class_bytes(paths):
        name, definition, used, refs = parse_class(data)
        if name in classes and classes[name] != definition:
            raise ValueError(f"冲突类：{name}")
        classes[name] = definition
        class_refs.update(used)
        references.extend(refs)
    return classes, class_refs, references


def resolve(classes, owner, kind, signature, visited=None):
    visited = set() if visited is None else visited
    if owner in visited or owner not in classes:
        return None
    visited.add(owner)
    cls = classes[owner]
    if signature in cls[kind]:
        return cls[kind][signature]
    if signature.startswith("<init>"):
        return None
    for base in [cls.get("super"), *cls.get("interfaces", [])]:
        result = resolve(classes, base, kind, signature, visited)
        if result is not None:
            return result
    return None
