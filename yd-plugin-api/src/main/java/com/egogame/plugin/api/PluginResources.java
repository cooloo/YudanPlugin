package com.egogame.plugin.api;

import java.io.IOException;
import java.io.InputStream;

/** 只访问已验证版本内的资源，路径为包内相对路径。 */
public interface PluginResources {
    InputStream open(String path) throws IOException;
    long size(String path) throws IOException;
    String sha256(String path) throws IOException;
}
