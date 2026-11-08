"""源码归档、静态检查和 Git 上传准备共用的公开文件边界。"""
import os
from pathlib import Path

EXCLUDED_DIRECTORIES = {"build", ".gradle", ".kotlin", ".git", ".svn", ".local", ".idea", "__pycache__"}
PRIVATE_SUFFIXES = {".pyc", ".jks", ".p12", ".pfx", ".keystore", ".key", ".pem", ".iml",
                    ".apk", ".aab", ".ydplugin", ".hprof", ".trace", ".log"}
PRIVATE_NAMES = {"local.properties", ".env", "password.txt", "credentials.json", "google-services.json"}
INTERNAL_DOCUMENTS = {"docs/公开发布检查.md", "docs/构建记录.md", "docs/音乐插件后台任务调整.md"}


def exclusion_reason(relative):
    if relative.as_posix() in INTERNAL_DOCUMENTS:
        return "内部发布检查与历史调试记录"
    if any(part in EXCLUDED_DIRECTORIES for part in relative.parts):
        return "本地材料、缓存、构建产物或版本历史"
    name = relative.name.lower()
    if relative.suffix.lower() in PRIVATE_SUFFIXES or name in PRIVATE_NAMES:
        return "凭据、本机配置或二进制产物"
    if name.startswith(".env.") or name.endswith(("signing.properties", "secrets.properties")):
        return "本地凭据配置"
    if relative.parent.as_posix() == "docs/runtime" and name.startswith("host-resources-"):
        return "完整宿主资源转储"
    return None


def public_files(root):
    root = Path(root).resolve()
    for directory, directories, filenames in os.walk(root, followlinks=False):
        parent = Path(directory)
        for name in list(directories):
            path = parent / name
            if exclusion_reason(path.relative_to(root)):
                directories.remove(name)
            elif path.is_symlink() or getattr(path, "is_junction", lambda: False)():
                raise ValueError("公开源码不允许链接目录：" + path.relative_to(root).as_posix())
        directories.sort()
        for name in sorted(filenames):
            path = parent / name
            relative = path.relative_to(root)
            if exclusion_reason(relative):
                continue
            if path.is_symlink():
                raise ValueError("公开源码不允许符号链接：" + relative.as_posix())
            yield path
