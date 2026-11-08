package com.egogame.plugin.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 文件与图片走公共下载器；音频流支持范围读取，不要求整首预取。 */
public interface PluginTransfers {
    interface Cancellation { void cancel(); }
    interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String code, String message);
    }
    interface Progress { void onProgress(long received, long total); }
    interface MediaStream extends AutoCloseable {
        InputStream body();
        long length();
        @Override void close() throws IOException;
    }

    final class Request {
        public final String url;
        public final Map<String, String> headers;
        public final long maxBytes;
        public final int timeoutMillis;

        public Request(String url, Map<String, String> headers, long maxBytes, int timeoutMillis) {
            if (url == null || maxBytes <= 0 || timeoutMillis <= 0) {
                throw new IllegalArgumentException("传输参数无效");
            }
            this.url = url;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.maxBytes = maxBytes;
            this.timeoutMillis = timeoutMillis;
        }
    }

    /** 响应头只交还发起请求的插件；二维码登录需读取自身响应的 Set-Cookie。 */
    final class PreviewResult {
        public final byte[] body;
        public final String contentType;
        public final String finalUrl;
        public final Map<String, java.util.List<String>> headers;
        public PreviewResult(byte[] body, String contentType, String finalUrl,
                             Map<String, java.util.List<String>> headers) {
            this.body = body;
            this.contentType = contentType;
            this.finalUrl = finalUrl;
            Map<String, java.util.List<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, java.util.List<String>> header : headers.entrySet()) {
                copy.put(header.getKey(), Collections.unmodifiableList(new java.util.ArrayList<>(header.getValue())));
            }
            this.headers = Collections.unmodifiableMap(copy);
        }
    }

    Cancellation preview(Request request, Callback<byte[]> callback);
    Cancellation previewWithMetadata(Request request, Callback<PreviewResult> callback);
    Cancellation download(Request request, File destination, String sha256,
                          Progress progress, Callback<File> callback);
    MediaStream openMedia(Request request, long offset, long length,
                          CancellationSignal cancellation) throws IOException;

    interface CancellationSignal {
        boolean isCancelled();
        void onCancel(Runnable listener);
    }
}
