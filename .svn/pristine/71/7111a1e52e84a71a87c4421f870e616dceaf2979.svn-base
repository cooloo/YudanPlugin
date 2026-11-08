package com.egogame.plugin.runtime;

import java.io.File;
import java.io.IOException;

final class PluginPaths {
    private PluginPaths() { }
    static String validate(String path) throws PluginException {
        if (path == null || path.isEmpty() || path.length() > 512 || path.startsWith("/")
                || path.contains("\\") || path.contains(":") || path.indexOf('\0') >= 0) {
            throw new PluginException("INVALID_PATH", "插件资源路径无效");
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")
                    || !part.equals(part.trim())) {
                throw new PluginException("INVALID_PATH", "插件资源路径包含非法段");
            }
        }
        return path;
    }
    static File resolve(File root, String path) throws IOException {
        validate(path);
        File child = new File(root, path).getCanonicalFile();
        if (!child.getPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new PluginException("INVALID_PATH", "插件资源超出版本目录");
        }
        return child;
    }
    static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法建立插件目录");
    }
    static void delete(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("无法读取插件目录");
            File canonicalParent = file.getCanonicalFile();
            for (File child : children) {
                // 只检查子项本身的跳转，父目录别名不代表子项是符号链接。
                if (!child.getCanonicalFile().equals(new File(canonicalParent, child.getName()))) {
                    if (!child.delete()) throw new IOException("无法删除插件链接");
                } else delete(child);
            }
        }
        if (!file.delete()) throw new IOException("无法删除插件文件");
    }
}
